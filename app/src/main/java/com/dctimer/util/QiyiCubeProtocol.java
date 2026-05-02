package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.dctimer.activity.MainActivity;
import com.dctimer.model.SmartCube;

import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class QiyiCubeProtocol implements SmartCubeProtocol {
    private static final String TAG = "QiYiCube";
    public static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID CUBE_UUID = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] AES_KEY = {
            87, (byte) 177, (byte) 249, (byte) 171,
            (byte) 205, 90, (byte) 232, (byte) 167,
            (byte) 156, (byte) 185, (byte) 140, (byte) 231,
            87, (byte) 140, 81, 8
    };
    private static final int[] MOVE_AXIS_MAP = {4, 1, 3, 0, 2, 5};
    private static final float DEVICE_TIME_SCALE = 1.6f;
    private static final int MAX_MOVE_DELTA_MS = 0xffff;
    private static final int FALLBACK_HELLO_DELAY_MS = 1500;
    private static final int HELLO_RETRY_DELAY_MS = 3000;
    private static final int WRITE_RETRY_DELAY_MS = 80;
    private static final int MAX_HELLO_ATTEMPTS = 2;
    private static final int REQUIRED_MTU = 64;
    private static final byte[] SYNC_STATE_PREFIX = {
            0x04, 0x17, (byte) 0x88, (byte) 0x8b, 0x31
    };
    private static final byte[] SYNC_STATE_SUFFIX = {
            0x00, 0x00
    };
    private static final String FACELET_ORDER = "LRDUFB";

    private final MainActivity context;
    private final SmartCube smartCube;
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable sendNextRequestRunnable = new Runnable() {
        @Override
        public void run() {
            sendNextRequest();
        }
    };

    private final Runnable fallbackHelloRunnable = new Runnable() {
        @Override
        public void run() {
            if (helloReceived || fallbackHelloSent || TextUtils.isEmpty(fallbackMac)
                    || fallbackMac.equalsIgnoreCase(activeMac) || protocolMismatchDetected) {
                return;
            }
            fallbackHelloSent = true;
            activeMac = fallbackMac;
            Log.w(TAG, "QiYi 使用备用 MAC 发起 hello: " + fallbackMac);
            enqueueHello(true, "备用 MAC");
            mainHandler.postDelayed(helloRetryRunnable, HELLO_RETRY_DELAY_MS);
        }
    };

    private final Runnable helloRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (helloReceived || TextUtils.isEmpty(activeMac) || protocolMismatchDetected) {
                return;
            }
            if (helloAttemptCount >= MAX_HELLO_ATTEMPTS) {
                Log.w(TAG, "QiYi hello 已达到最大重试次数，停止自动重试");
                return;
            }
            Log.w(TAG, "QiYi hello 未收到响应，重试 MAC: " + activeMac);
            enqueueHello(true, "超时重试");
        }
    };

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic cubeCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic fallbackWriteCharacteristic;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean writeWithoutResponse;
    private boolean mtuReady;
    private boolean initialStateShown;
    private boolean helloReceived;
    private boolean fallbackHelloSent;
    private boolean localResetActive;
    private boolean protocolMismatchDetected;
    private String deviceName;
    private String activeMac;
    private String fallbackMac;
    private long lastTimestamp = -1L;
    private int nonProtocolMessageCount;
    private int helloAttemptCount;

    public QiyiCubeProtocol(MainActivity context, SmartCube smartCube) {
        this.context = context;
        this.smartCube = smartCube;
        try {
            SecretKeySpec spec = new SecretKeySpec(AES_KEY, "AES");
            encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, spec);
            decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("QiYi AES 初始化失败", e);
        }
    }

    public boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress) {
        clear();
        this.gatt = gatt;
        this.deviceName = deviceName == null ? "" : deviceName.trim();
        this.activeMac = normalizeMac(deviceAddress);
        this.fallbackMac = getFallbackMac(this.deviceName);
        cubeCharacteristic = service.getCharacteristic(CUBE_UUID);
        if (cubeCharacteristic == null) {
            Log.e(TAG, "QiYi 特征不存在");
            return false;
        }
        fallbackWriteCharacteristic = resolveFallbackWriteCharacteristic(service, cubeCharacteristic);
        writeCharacteristic = resolveWriteCharacteristic(service, cubeCharacteristic);
        if (writeCharacteristic == null) {
            Log.e(TAG, "QiYi 写特征不存在");
            return false;
        }
        logCharacteristicProperties(cubeCharacteristic);
        Log.w(TAG, "QiYi 默认使用 " + writeCharacteristic.getUuid() + " 作为写通道");
        if (fallbackWriteCharacteristic != null && !fallbackWriteCharacteristic.getUuid().equals(writeCharacteristic.getUuid())) {
            Log.w(TAG, "QiYi 检测到备用写特征，但当前不自动切换: " + fallbackWriteCharacteristic.getUuid());
        }
        if (requestMtuIfNeeded()) {
            return true;
        }
        setupNotifications();
        return true;
    }

    private boolean requestMtuIfNeeded() {
        mtuReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || gatt == null) {
            return false;
        }
        boolean requested = gatt.requestMtu(REQUIRED_MTU);
        if (requested) {
            Log.w(TAG, "QiYi 请求 MTU=" + REQUIRED_MTU);
        } else {
            Log.w(TAG, "QiYi 请求 MTU 失败，回退默认 MTU");
            mtuReady = true;
        }
        return requested;
    }

    private void setupNotifications() {
        if (!gatt.setCharacteristicNotification(cubeCharacteristic, true)) {
            Log.e(TAG, "QiYi 无法开启通知");
            return;
        }
        BluetoothGattDescriptor descriptor = cubeCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(resolveCccdEnableValue(cubeCharacteristic));
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "QiYi 通知描述符写入失败，直接继续");
                onNotificationsEnabled();
            }
        } else {
            onNotificationsEnabled();
        }
    }

    public void clear() {
        mainHandler.removeCallbacks(fallbackHelloRunnable);
        mainHandler.removeCallbacks(helloRetryRunnable);
        mainHandler.removeCallbacks(sendNextRequestRunnable);
        requestQueue.clear();
        notificationsReady = false;
        writePending = false;
        writeWithoutResponse = false;
        mtuReady = false;
        initialStateShown = false;
        helloReceived = false;
        fallbackHelloSent = false;
        localResetActive = false;
        cubeCharacteristic = null;
        writeCharacteristic = null;
        fallbackWriteCharacteristic = null;
        gatt = null;
        deviceName = null;
        activeMac = null;
        fallbackMac = null;
        lastTimestamp = -1L;
        protocolMismatchDetected = false;
        nonProtocolMessageCount = 0;
        helloAttemptCount = 0;
    }

    public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor == null || descriptor.getCharacteristic() == null) {
            return;
        }
        if (CUBE_UUID.equals(descriptor.getCharacteristic().getUuid())) {
            onNotificationsEnabled();
        }
    }

    public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || writeCharacteristic == null
                || !writeCharacteristic.getUuid().equals(characteristic.getUuid())) {
            return;
        }
        writePending = false;
        sendNextRequest();
    }

    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !CUBE_UUID.equals(characteristic.getUuid())) {
            return;
        }
        try {
            parseMessage(characteristic.getValue());
        } catch (Exception e) {
            Log.e(TAG, "QiYi 数据解析失败", e);
        }
    }

    @Override
    public void onMtuChanged(int mtu, int status) {
        mtuReady = true;
        Log.w(TAG, "QiYi MTU 结果 mtu=" + mtu + " status=" + status);
        if (notificationsReady) {
            return;
        }
        setupNotifications();
    }

    @Override
    public void onLocalCubeReset(String cubeState) {
        localResetActive = true;
        lastTimestamp = -1L;
        Log.w(TAG, "QiYi 接收本地手动重置，后续保持本地推演状态: " + cubeState);
        byte[] syncState = buildSyncStateContent(cubeState);
        if (syncState != null) {
            enqueueMessage(syncState, true);
        }
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) {
            return;
        }
        notificationsReady = true;
        String helloMac = !TextUtils.isEmpty(activeMac) ? activeMac : fallbackMac;
        if (TextUtils.isEmpty(helloMac)) {
            Log.e(TAG, "QiYi 缺少可用 MAC，无法发送 hello");
            return;
        }
        activeMac = helloMac;
        enqueueHello(true, "初始化");
        if (!TextUtils.isEmpty(fallbackMac) && !fallbackMac.equalsIgnoreCase(activeMac)) {
            mainHandler.postDelayed(fallbackHelloRunnable, FALLBACK_HELLO_DELAY_MS);
        }
        mainHandler.postDelayed(helloRetryRunnable, HELLO_RETRY_DELAY_MS);
    }

    private void enqueueMessage(byte[] content, boolean priority) {
        if (content == null || content.length == 0) {
            return;
        }
        byte[] message = buildMessage(content);
        if (priority) {
            requestQueue.offerFirst(message);
        } else {
            requestQueue.offer(message);
        }
        sendNextRequest();
    }

    private void enqueueHello(boolean priority, String reason) {
        if (TextUtils.isEmpty(activeMac) || protocolMismatchDetected) {
            return;
        }
        if (helloAttemptCount >= MAX_HELLO_ATTEMPTS) {
            Log.w(TAG, "QiYi 跳过 hello，已达到最大次数: " + reason);
            return;
        }
        helloAttemptCount++;
        Log.w(TAG, "QiYi 发送 hello #" + helloAttemptCount + "，原因: " + reason + "，MAC=" + activeMac);
        enqueueMessage(buildHelloContent(activeMac), priority);
    }

    private void sendNextRequest() {
        if (!notificationsReady || !mtuReady || writePending || gatt == null
                || writeCharacteristic == null || requestQueue.isEmpty()) {
            return;
        }
        byte[] request = requestQueue.poll();
        try {
            byte[] encoded = encrypt(request);
            prepareWriteCharacteristic(encoded);
            boolean writeIssued = gatt.writeCharacteristic(writeCharacteristic);
            if (!writeIssued) {
                Log.e(TAG, "QiYi 写入请求失败: " + writeCharacteristic.getUuid());
                requestQueue.offerFirst(request);
                writePending = false;
                mainHandler.removeCallbacks(sendNextRequestRunnable);
                mainHandler.postDelayed(sendNextRequestRunnable, WRITE_RETRY_DELAY_MS);
                return;
            }
            if (writeWithoutResponse) {
                writePending = false;
                if (!requestQueue.isEmpty()) {
                    mainHandler.removeCallbacks(sendNextRequestRunnable);
                    mainHandler.post(sendNextRequestRunnable);
                }
            } else {
                writePending = true;
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "QiYi 请求加密失败", e);
            writePending = false;
        }
    }

    private void parseMessage(byte[] value) throws GeneralSecurityException {
        if (value == null || value.length == 0 || value.length % 16 != 0) {
            Log.w(TAG, "QiYi 收到异常长度数据");
            return;
        }
        byte[] decoded = decrypt(value);
        if (decoded.length < 2) {
            return;
        }
        int msgLength = decoded[1] & 0xff;
        if (msgLength < 3 || msgLength > decoded.length) {
            Log.w(TAG, "QiYi 消息长度非法: " + msgLength);
            return;
        }
        byte[] msg = Arrays.copyOf(decoded, msgLength);
        if (crc16Modbus(msg, msg.length) != 0) {
            return;
        }
        if ((msg[0] & 0xff) == 0xcc && (msg[1] & 0xff) == 0x10) {
            handleQuaternionPacket(msg);
            return;
        }
        if ((msg[0] & 0xff) != 0xfe) {
            handleNonProtocolMessage(msg);
            return;
        }
        nonProtocolMessageCount = 0;
        int opcode = msg[2] & 0xff;
        long timestamp = readUint32(msg, 3);
        switch (opcode) {
            case 0x02:
                handleHello(msg, timestamp);
                break;
            case 0x03:
                handleStateChange(msg, timestamp);
                break;
            case 0x04:
                handleSyncConfirmation(msg, timestamp);
                break;
            default:
                Log.w(TAG, "QiYi 未知消息类型: " + opcode);
                break;
        }
    }

    private void handleHello(byte[] msg, long timestamp) {
        helloReceived = true;
        nonProtocolMessageCount = 0;
        mainHandler.removeCallbacks(fallbackHelloRunnable);
        mainHandler.removeCallbacks(helloRetryRunnable);
        sendAck(msg);
        if (msg.length < 36) {
            Log.w(TAG, "QiYi hello 数据长度不足");
            return;
        }
        String facelet = parseFacelet(msg, 7);
        if (!syncCubeState(facelet)) {
            return;
        }
        lastTimestamp = timestamp;
        smartCube.setBatteryValue(msg[35] & 0xff);
        context.refreshSmartCubeStateUi();
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "QiYi 初始状态: " + facelet);
    }

    private void handleStateChange(byte[] msg, long timestamp) {
        helloReceived = true;
        nonProtocolMessageCount = 0;
        mainHandler.removeCallbacks(fallbackHelloRunnable);
        mainHandler.removeCallbacks(helloRetryRunnable);
        sendAck(msg);
        if (msg.length < 36) {
            Log.w(TAG, "QiYi 状态数据长度不足");
            return;
        }
        String facelet = parseFacelet(msg, 7);
        if (TextUtils.isEmpty(smartCube.getCubeState())) {
            if (syncCubeState(facelet)) {
                lastTimestamp = timestamp;
                smartCube.setBatteryValue(msg[35] & 0xff);
                showInitialStateDialogIfNeeded();
            }
            return;
        }

        int[] todoMoves = new int[10];
        long[] todoTimestamps = new long[10];
        int moveCount = 0;
        todoMoves[moveCount] = msg[34] & 0xff;
        todoTimestamps[moveCount] = timestamp;
        moveCount++;
        while (moveCount < todoMoves.length) {
            int offset = 91 - 5 * moveCount;
            if (offset + 4 >= msg.length) {
                break;
            }
            long historyTimestamp = readUint32(msg, offset);
            if (historyTimestamp <= lastTimestamp) {
                break;
            }
            todoTimestamps[moveCount] = historyTimestamp;
            todoMoves[moveCount] = msg[offset + 4] & 0xff;
            moveCount++;
        }

        for (int i = moveCount - 1; i >= 0; i--) {
            int move = convertMove(todoMoves[i]);
            if (move < 0) {
                continue;
            }
            int delta = calcMoveDelta(todoTimestamps[i]);
            context.moveCube(smartCube, move, delta);
        }

        if (!facelet.equals(smartCube.getCubeState())) {
            if (localResetActive) {
                Log.w(TAG, "QiYi 检测到本地手动重置后的状态差异，保持本地推演，不用 facelet 回覆盖");
            } else {
                Log.w(TAG, "QiYi 状态与步序推演不一致，使用 facelet 重同步");
                syncCubeState(facelet);
                context.refreshSmartCubeStateUi();
            }
        }
        smartCube.setBatteryValue(msg[35] & 0xff);
        showInitialStateDialogIfNeeded();
        lastTimestamp = Math.max(lastTimestamp, timestamp);
    }

    private void handleSyncConfirmation(byte[] msg, long timestamp) {
        nonProtocolMessageCount = 0;
        if (msg.length < 36) {
            Log.w(TAG, "QiYi 同步确认数据长度不足");
            return;
        }
        String facelet = parseFacelet(msg, 7);
        if (!syncCubeState(facelet)) {
            return;
        }
        localResetActive = false;
        lastTimestamp = timestamp;
        smartCube.setBatteryValue(msg[35] & 0xff);
        context.refreshSmartCubeStateUi();
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "QiYi 设备状态同步确认: " + facelet);
    }

    private void sendAck(byte[] msg) {
        if (msg.length >= 7) {
            enqueueMessage(Arrays.copyOfRange(msg, 2, 7), false);
        }
    }

    private void handleQuaternionPacket(byte[] msg) {
        float[] quaternion = parseQuaternionPacket(msg);
        if (quaternion == null) {
            return;
        }
        context.smartCubeGyro(smartCube, quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
    }

    static float[] parseQuaternionPacket(byte[] msg) {
        if (msg == null || msg.length < 16 || (msg[0] & 0xff) != 0xcc || (msg[1] & 0xff) != 0x10) {
            return null;
        }
        int expectedCrc = crc16ModbusStatic(msg, 14);
        int actualCrc = (msg[14] & 0xff) | ((msg[15] & 0xff) << 8);
        if (expectedCrc != actualCrc) {
            return null;
        }
        float qx = readInt16Static(msg, 6) / 1000f;
        float qy = -readInt16Static(msg, 10) / 1000f;
        float qz = readInt16Static(msg, 8) / 1000f;
        float qw = readInt16Static(msg, 12) / 1000f;
        float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (len <= 0f) {
            return null;
        }
        return new float[] {qx / len, qy / len, qz / len, qw / len};
    }

    private boolean syncCubeState(String facelet) {
        int check = smartCube.setCubeState(facelet);
        if (check != 0) {
            Log.e(TAG, "QiYi facelet 校验失败: " + facelet);
            return false;
        }
        return true;
    }

    private void showInitialStateDialogIfNeeded() {
        if (initialStateShown) {
            return;
        }
        initialStateShown = true;
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                context.showCubeStateDialog();
            }
        });
    }

    private byte[] buildHelloContent(String mac) {
        byte[] macBytes = parseMac(mac);
        byte[] content = new byte[17];
        int[] header = {0x00, 0x6b, 0x01, 0x00, 0x00, 0x22, 0x06, 0x00, 0x02, 0x08, 0x00};
        for (int i = 0; i < header.length; i++) {
            content[i] = (byte) header[i];
        }
        for (int i = 0; i < 6; i++) {
            content[header.length + i] = macBytes[5 - i];
        }
        return content;
    }

    private byte[] buildSyncStateContent(String cubeState) {
        byte[] stateBytes = encodeFacelet(cubeState);
        if (stateBytes == null) {
            Log.e(TAG, "QiYi 构建同步状态失败，facelet 非法: " + cubeState);
            return null;
        }
        byte[] content = new byte[SYNC_STATE_PREFIX.length + stateBytes.length + SYNC_STATE_SUFFIX.length];
        System.arraycopy(SYNC_STATE_PREFIX, 0, content, 0, SYNC_STATE_PREFIX.length);
        System.arraycopy(stateBytes, 0, content, SYNC_STATE_PREFIX.length, stateBytes.length);
        System.arraycopy(SYNC_STATE_SUFFIX, 0, content,
                SYNC_STATE_PREFIX.length + stateBytes.length, SYNC_STATE_SUFFIX.length);
        return content;
    }

    private byte[] encodeFacelet(String facelet) {
        if (TextUtils.isEmpty(facelet) || facelet.length() < 54) {
            return null;
        }
        byte[] encoded = new byte[27];
        for (int i = 0; i < 54; i++) {
            int value = FACELET_ORDER.indexOf(facelet.charAt(i));
            if (value < 0) {
                return null;
            }
            int byteIndex = i >> 1;
            if ((i & 1) == 0) {
                encoded[byteIndex] = (byte) (value & 0x0f);
            } else {
                encoded[byteIndex] = (byte) ((encoded[byteIndex] & 0x0f) | ((value & 0x0f) << 4));
            }
        }
        return encoded;
    }

    private byte[] buildMessage(byte[] content) {
        int msgLength = 4 + content.length;
        int paddedLength = ((msgLength + 15) / 16) * 16;
        byte[] msg = new byte[paddedLength];
        msg[0] = (byte) 0xfe;
        msg[1] = (byte) msgLength;
        System.arraycopy(content, 0, msg, 2, content.length);
        int crc = crc16Modbus(msg, 2 + content.length);
        msg[2 + content.length] = (byte) (crc & 0xff);
        msg[3 + content.length] = (byte) ((crc >> 8) & 0xff);
        return msg;
    }

    private int calcMoveDelta(long moveTimestamp) {
        if (lastTimestamp < 0) {
            lastTimestamp = moveTimestamp;
            return 0;
        }
        long rawDelta = moveTimestamp - lastTimestamp;
        lastTimestamp = moveTimestamp;
        if (rawDelta <= 0) {
            return 0;
        }
        int delta = Math.round(rawDelta / DEVICE_TIME_SCALE);
        if (delta < 0) {
            return 0;
        }
        return Math.min(delta, MAX_MOVE_DELTA_MS);
    }

    private int convertMove(int rawMove) {
        if (rawMove <= 0) {
            return -1;
        }
        int axisIndex = (rawMove - 1) >> 1;
        if (axisIndex < 0 || axisIndex >= MOVE_AXIS_MAP.length) {
            return -1;
        }
        int axis = MOVE_AXIS_MAP[axisIndex];
        int power = (rawMove & 1) == 0 ? 0 : 2;
        return axis * 3 + power;
    }

    private String parseFacelet(byte[] msg, int offset) {
        StringBuilder state = new StringBuilder(54);
        for (int i = 0; i < 54; i++) {
            int value = (msg[offset + (i >> 1)] & 0xff) >> ((i & 1) << 2) & 0x0f;
            state.append("LRDUFB".charAt(value));
        }
        return state.toString();
    }

    private long readUint32(byte[] data, int offset) {
        return ((data[offset] & 0xffL) << 24)
                | ((data[offset + 1] & 0xffL) << 16)
                | ((data[offset + 2] & 0xffL) << 8)
                | (data[offset + 3] & 0xffL);
    }

    private byte[] parseMac(String mac) {
        if (TextUtils.isEmpty(mac)) {
            throw new IllegalArgumentException("QiYi mac 为空");
        }
        String[] parts = mac.split(":");
        if (parts.length != 6) {
            throw new IllegalArgumentException("QiYi mac 非法: " + mac);
        }
        byte[] bytes = new byte[6];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    private String normalizeMac(String mac) {
        if (TextUtils.isEmpty(mac)) {
            return null;
        }
        String normalized = mac.trim().toUpperCase(Locale.US);
        return normalized.matches("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$") ? normalized : null;
    }

    private String getFallbackMac(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.US);
        if (!(normalized.startsWith("QY-QYSC-") || normalized.startsWith("XMD-TORNADOV4-I-"))) {
            return null;
        }
        String suffix = normalized.substring(normalized.length() - 4);
        if (!suffix.matches("[0-9A-F]{4}")) {
            return null;
        }
        return "CC:A3:00:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2, 4);
    }

    private BluetoothGattCharacteristic resolveWriteCharacteristic(BluetoothGattService service,
                                                                   BluetoothGattCharacteristic notifyCharacteristic) {
        if (supportsWrite(notifyCharacteristic)) {
            return notifyCharacteristic;
        }
        return resolveFallbackWriteCharacteristic(service, notifyCharacteristic);
    }

    private BluetoothGattCharacteristic resolveFallbackWriteCharacteristic(BluetoothGattService service,
                                                                           BluetoothGattCharacteristic notifyCharacteristic) {
        BluetoothGattCharacteristic candidate = service.getCharacteristic(WRITE_UUID);
        if (candidate == null) {
            return null;
        }
        if (notifyCharacteristic != null && candidate.getUuid().equals(notifyCharacteristic.getUuid())) {
            return null;
        }
        return candidate;
    }

    private boolean supportsWrite(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return false;
        }
        int properties = characteristic.getProperties();
        return (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                || (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    private void prepareWriteCharacteristic(byte[] encoded) {
        int properties = writeCharacteristic.getProperties();
        writeWithoutResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        writeCharacteristic.setWriteType(writeWithoutResponse
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        writeCharacteristic.setValue(encoded);
        Log.w(TAG, "QiYi 写入特征 " + writeCharacteristic.getUuid()
                + " writeType=" + (writeWithoutResponse ? "NO_RESPONSE" : "DEFAULT"));
    }

    private void handleNonProtocolMessage(byte[] msg) {
        nonProtocolMessageCount++;
        if (nonProtocolMessageCount <= 3) {
            Log.w(TAG, String.format(Locale.US,
                    "QiYi 收到非 FE 帧 header=0x%02X len=%d via=%s",
                    msg[0] & 0xff, msg.length,
                    writeCharacteristic == null ? "null" : writeCharacteristic.getUuid().toString()));
        }
        if (!helloReceived && (msg[0] & 0xff) == 0xcc && nonProtocolMessageCount >= 3 && !protocolMismatchDetected) {
            protocolMismatchDetected = true;
            requestQueue.clear();
            mainHandler.removeCallbacks(fallbackHelloRunnable);
            mainHandler.removeCallbacks(helloRetryRunnable);
            mainHandler.removeCallbacks(sendNextRequestRunnable);
            Log.w(TAG, "QiYi 检测到稳定的 0xCC 协议帧，停止后续 hello/切通道重试，避免干扰主流程");
        }
    }

    private byte[] resolveCccdEnableValue(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                && (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.w(TAG, "QiYi 特征使用 indicate");
            return BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        }
        return BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
    }

    private void logCharacteristicProperties(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        Log.w(TAG, "QiYi 特征属性 notify=" + ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                + " indicate=" + ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                + " write=" + ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
                + " writeNoRsp=" + ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0));
    }

    private byte[] encrypt(byte[] value) throws GeneralSecurityException {
        byte[] result = new byte[value.length];
        for (int i = 0; i < value.length; i += 16) {
            byte[] block = encryptCipher.doFinal(Arrays.copyOfRange(value, i, i + 16));
            System.arraycopy(block, 0, result, i, 16);
        }
        return result;
    }

    private byte[] decrypt(byte[] value) throws GeneralSecurityException {
        byte[] result = new byte[value.length];
        for (int i = 0; i < value.length; i += 16) {
            byte[] block = decryptCipher.doFinal(Arrays.copyOfRange(value, i, i + 16));
            System.arraycopy(block, 0, result, i, 16);
        }
        return result;
    }

    private int crc16Modbus(byte[] data, int length) {
        return crc16ModbusStatic(data, length);
    }

    private static int readInt16Static(byte[] data, int offset) {
        int value = (data[offset] & 0xff) << 8 | (data[offset + 1] & 0xff);
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    private static int crc16ModbusStatic(byte[] data, int length) {
        int crc = 0xffff;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xff;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x1) != 0) {
                    crc = (crc >> 1) ^ 0xa001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xffff;
    }
}
