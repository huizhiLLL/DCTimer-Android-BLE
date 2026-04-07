package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
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

    private final MainActivity context;
    private final SmartCube smartCube;
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable fallbackHelloRunnable = new Runnable() {
        @Override
        public void run() {
            if (helloReceived || fallbackHelloSent || TextUtils.isEmpty(fallbackMac)
                    || fallbackMac.equalsIgnoreCase(activeMac)) {
                return;
            }
            fallbackHelloSent = true;
            activeMac = fallbackMac;
            Log.w(TAG, "QiYi 使用备用 MAC 发起 hello: " + fallbackMac);
            enqueueMessage(buildHelloContent(fallbackMac), true);
        }
    };

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic cubeCharacteristic;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean initialStateShown;
    private boolean helloReceived;
    private boolean fallbackHelloSent;
    private String deviceName;
    private String activeMac;
    private String fallbackMac;
    private long lastTimestamp = -1L;

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
        if (!gatt.setCharacteristicNotification(cubeCharacteristic, true)) {
            Log.e(TAG, "QiYi 无法开启通知");
            return false;
        }
        BluetoothGattDescriptor descriptor = cubeCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "QiYi 通知描述符写入失败，直接继续");
                onNotificationsEnabled();
            }
        } else {
            onNotificationsEnabled();
        }
        return true;
    }

    public void clear() {
        mainHandler.removeCallbacks(fallbackHelloRunnable);
        requestQueue.clear();
        notificationsReady = false;
        writePending = false;
        initialStateShown = false;
        helloReceived = false;
        fallbackHelloSent = false;
        cubeCharacteristic = null;
        gatt = null;
        deviceName = null;
        activeMac = null;
        fallbackMac = null;
        lastTimestamp = -1L;
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
        if (characteristic == null || !CUBE_UUID.equals(characteristic.getUuid())) {
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
        enqueueMessage(buildHelloContent(helloMac), true);
        if (!TextUtils.isEmpty(fallbackMac) && !fallbackMac.equalsIgnoreCase(activeMac)) {
            mainHandler.postDelayed(fallbackHelloRunnable, FALLBACK_HELLO_DELAY_MS);
        }
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

    private void sendNextRequest() {
        if (!notificationsReady || writePending || gatt == null || cubeCharacteristic == null || requestQueue.isEmpty()) {
            return;
        }
        byte[] request = requestQueue.poll();
        try {
            byte[] encoded = encrypt(request);
            cubeCharacteristic.setValue(encoded);
            writePending = gatt.writeCharacteristic(cubeCharacteristic);
            if (!writePending) {
                Log.e(TAG, "QiYi 写入请求失败");
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
        if ((msg[0] & 0xff) != 0xfe) {
            Log.w(TAG, "QiYi 消息头非法");
            return;
        }
        if (crc16Modbus(msg, msg.length) != 0) {
            Log.w(TAG, "QiYi CRC 校验失败");
            return;
        }
        int opcode = msg[2] & 0xff;
        long timestamp = readUint32(msg, 3);
        switch (opcode) {
            case 0x02:
                handleHello(msg, timestamp);
                break;
            case 0x03:
                handleStateChange(msg, timestamp);
                break;
            default:
                Log.w(TAG, "QiYi 未知消息类型: " + opcode);
                break;
        }
    }

    private void handleHello(byte[] msg, long timestamp) {
        helloReceived = true;
        mainHandler.removeCallbacks(fallbackHelloRunnable);
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
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "QiYi 初始状态: " + facelet);
    }

    private void handleStateChange(byte[] msg, long timestamp) {
        helloReceived = true;
        mainHandler.removeCallbacks(fallbackHelloRunnable);
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
            Log.w(TAG, "QiYi 状态与步序推演不一致，使用 facelet 重同步");
            syncCubeState(facelet);
        }
        smartCube.setBatteryValue(msg[35] & 0xff);
        showInitialStateDialogIfNeeded();
        lastTimestamp = Math.max(lastTimestamp, timestamp);
    }

    private void sendAck(byte[] msg) {
        if (msg.length >= 7) {
            enqueueMessage(Arrays.copyOfRange(msg, 2, 7), false);
        }
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
        if (!normalized.matches("^(QY-QYSC|XMD-TORNADOV4-I)-.-[0-9A-F]{4}$")) {
            return null;
        }
        String suffix = normalized.substring(normalized.length() - 4);
        return "CC:A3:00:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2, 4);
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
