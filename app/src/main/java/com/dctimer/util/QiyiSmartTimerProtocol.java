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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class QiyiSmartTimerProtocol implements SmartTimerProtocol {
    private static final String TAG = "QiyiSmartTimer";

    public static final int MANUFACTURER_ID = 0x0504;
    public static final UUID SERVICE_UUID = UUID.fromString("0000fd50-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_UUID = UUID.fromString("00000001-0000-1001-8001-00805f9b07d0");
    public static final UUID READ_UUID = UUID.fromString("00000002-0000-1001-8001-00805f9b07d0");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int REQUIRED_MTU = 517;
    private static final int WRITE_RETRY_DELAY_MS = 80;

    private static final int STATE_IDLE = 0;
    private static final int STATE_INSPECTION = 1;
    private static final int STATE_GET_SET = 2;
    private static final int STATE_RUNNING = 3;
    private static final int STATE_FINISHED = 4;
    private static final int STATE_STOPPED = 5;
    private static final int STATE_DISCONNECT = 6;

    private static final byte[] HELLO_HEADER = new byte[] {
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x21, 0x08, 0x00, 0x01, 0x05, 0x5A
    };

    private static final byte[] AES_KEY = new byte[] {
            0x77, 0x77, 0x77, 0x77, 0x77, 0x77, 0x77, 0x77,
            0x77, 0x77, 0x77, 0x77, 0x77, 0x77, 0x77, 0x77
    };

    private final SmartTimerProtocol.StateCallback callback;
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sendNextRequestRunnable = new Runnable() {
        @Override
        public void run() {
            writePending = false;
            sendNextRequest();
        }
    };

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private String deviceName;
    private String activeMac;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean writeWithoutResponse;
    private boolean mtuReady;
    private int nextPacketNum;
    private int messageLength;
    private int cipherLength;
    private int cipherPos;
    private byte[] cipherBuffer;
    private int previousState = STATE_IDLE;

    public QiyiSmartTimerProtocol(SmartTimerProtocol.StateCallback callback) {
        this.callback = callback;
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY, "AES");
            encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, key);
            decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("QiYi Smart Timer AES 初始化失败", e);
        }
    }

    @Override
    public boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress) {
        clear();
        this.gatt = gatt;
        this.deviceName = deviceName == null ? "" : deviceName.trim();
        this.activeMac = normalizeMac(deviceAddress);
        if (TextUtils.isEmpty(this.activeMac)) {
            this.activeMac = getDefaultMac(this.deviceName);
        }
        readCharacteristic = service.getCharacteristic(READ_UUID);
        writeCharacteristic = service.getCharacteristic(WRITE_UUID);
        if (readCharacteristic == null || writeCharacteristic == null) {
            Log.e(TAG, "QiYi Smart Timer 特征不存在");
            return false;
        }
        configureWriteType();
        if (requestMtuIfNeeded()) {
            return true;
        }
        setupNotifications();
        return true;
    }

    @Override
    public void clear() {
        handler.removeCallbacks(sendNextRequestRunnable);
        requestQueue.clear();
        gatt = null;
        readCharacteristic = null;
        writeCharacteristic = null;
        deviceName = null;
        activeMac = null;
        notificationsReady = false;
        writePending = false;
        writeWithoutResponse = false;
        mtuReady = false;
        previousState = STATE_IDLE;
        resetPacketBuffer();
    }

    @Override
    public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor == null || descriptor.getCharacteristic() == null) {
            return;
        }
        if (READ_UUID.equals(descriptor.getCharacteristic().getUuid())) {
            onNotificationsEnabled();
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || writeCharacteristic == null
                || !writeCharacteristic.getUuid().equals(characteristic.getUuid())) {
            return;
        }
        writePending = false;
        sendNextRequest();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !READ_UUID.equals(characteristic.getUuid())) {
            return;
        }
        byte[] value = characteristic.getValue();
        if (value == null || value.length == 0) {
            return;
        }
        onPacket(value);
    }

    @Override
    public void onMtuChanged(int mtu, int status) {
        mtuReady = true;
        if (!notificationsReady) {
            setupNotifications();
        }
    }

    private void configureWriteType() {
        int properties = writeCharacteristic.getProperties();
        writeWithoutResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        writeCharacteristic.setWriteType(writeWithoutResponse
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    }

    private boolean requestMtuIfNeeded() {
        mtuReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || gatt == null) {
            return false;
        }
        boolean requested = gatt.requestMtu(REQUIRED_MTU);
        if (!requested) {
            mtuReady = true;
        }
        return requested;
    }

    private void setupNotifications() {
        if (gatt == null || readCharacteristic == null) {
            return;
        }
        if (!gatt.setCharacteristicNotification(readCharacteristic, true)) {
            Log.e(TAG, "QiYi Smart Timer 无法开启通知");
            return;
        }
        BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor == null) {
            onNotificationsEnabled();
            return;
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(descriptor)) {
            onNotificationsEnabled();
        }
    }

    private void onNotificationsEnabled() {
        notificationsReady = true;
        if (TextUtils.isEmpty(activeMac)) {
            Log.e(TAG, "QiYi Smart Timer MAC 非法，无法发送 hello");
            return;
        }
        sendHello(activeMac);
    }

    private void sendHello(String macAddress) {
        String[] parts = macAddress.split(":");
        if (parts.length != 6) {
            Log.e(TAG, "QiYi Smart Timer MAC 非法: " + macAddress);
            return;
        }
        byte[] payload = new byte[HELLO_HEADER.length + 6];
        System.arraycopy(HELLO_HEADER, 0, payload, 0, HELLO_HEADER.length);
        for (int i = 0; i < 6; i++) {
            payload[HELLO_HEADER.length + 5 - i] = (byte) Integer.parseInt(parts[i], 16);
        }
        sendMessage(1, 0, 0x0001, payload);
    }

    private void sendAck(long sendSeqNum, long ackSeqNum, int cmd) {
        sendMessage(sendSeqNum, ackSeqNum, cmd, new byte[] {0x00});
    }

    private void sendMessage(long seqNum, long respNum, int cmd, byte[] payload) {
        int payloadLength = payload != null ? payload.length : 0;
        ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
        messageStream.write((byte) ((seqNum >> 24) & 0xFF));
        messageStream.write((byte) ((seqNum >> 16) & 0xFF));
        messageStream.write((byte) ((seqNum >> 8) & 0xFF));
        messageStream.write((byte) (seqNum & 0xFF));
        messageStream.write((byte) ((respNum >> 24) & 0xFF));
        messageStream.write((byte) ((respNum >> 16) & 0xFF));
        messageStream.write((byte) ((respNum >> 8) & 0xFF));
        messageStream.write((byte) (respNum & 0xFF));
        messageStream.write((byte) ((cmd >> 8) & 0xFF));
        messageStream.write((byte) (cmd & 0xFF));
        messageStream.write((byte) ((payloadLength >> 8) & 0xFF));
        messageStream.write((byte) (payloadLength & 0xFF));
        if (payloadLength > 0) {
            messageStream.write(payload, 0, payloadLength);
        }
        byte[] message = messageStream.toByteArray();
        int crc = crc16Modbus(message);
        messageStream.write((byte) ((crc >> 8) & 0xFF));
        messageStream.write((byte) (crc & 0xFF));
        message = messageStream.toByteArray();

        int packetNumber = 0;
        for (int offset = 0; offset < message.length; offset += 16) {
            int blockLength = Math.min(16, message.length - offset);
            byte[] block = new byte[16];
            for (int i = 0; i < 16; i++) {
                block[i] = i < blockLength ? message[offset + i] : 0x01;
            }
            byte[] encrypted;
            try {
                encrypted = encryptCipher.doFinal(block);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "QiYi Smart Timer 加密失败", e);
                return;
            }
            ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
            packetStream.write((byte) (packetNumber & 0xFF));
            if (packetNumber == 0) {
                packetStream.write((byte) ((message.length + 2) & 0xFF));
                packetStream.write(0x40);
                packetStream.write(0x00);
            }
            try {
                packetStream.write(encrypted);
            } catch (IOException e) {
                Log.e(TAG, "QiYi Smart Timer 封包失败", e);
                return;
            }
            requestQueue.offer(packetStream.toByteArray());
            packetNumber++;
        }
        sendNextRequest();
    }

    private void sendNextRequest() {
        if (!notificationsReady || gatt == null || writeCharacteristic == null || writePending) {
            return;
        }
        byte[] packet = requestQueue.poll();
        if (packet == null) {
            return;
        }
        writePending = true;
        writeCharacteristic.setValue(packet);
        boolean success = gatt.writeCharacteristic(writeCharacteristic);
        if (!success) {
            writePending = false;
            Log.e(TAG, "QiYi Smart Timer 写入失败");
            return;
        }
        if (writeWithoutResponse) {
            handler.removeCallbacks(sendNextRequestRunnable);
            handler.postDelayed(sendNextRequestRunnable, WRITE_RETRY_DELAY_MS);
        }
    }

    private void onPacket(byte[] packet) {
        int packetNum = packet[0] & 0xFF;
        if (packetNum != nextPacketNum) {
            resetPacketBuffer();
            return;
        }
        int offset;
        if (packetNum == 0) {
            messageLength = (packet[1] & 0xFF) - 2;
            if (messageLength <= 0) {
                resetPacketBuffer();
                return;
            }
            cipherLength = ((messageLength + 15) / 16) * 16;
            cipherPos = 0;
            cipherBuffer = new byte[cipherLength];
            offset = 4;
        } else {
            offset = 1;
        }

        int copyLength = Math.min(packet.length - offset, cipherLength - cipherPos);
        if (copyLength < 0 || cipherBuffer == null) {
            return;
        }
        System.arraycopy(packet, offset, cipherBuffer, cipherPos, copyLength);
        cipherPos += copyLength;

        if (cipherPos < cipherLength) {
            nextPacketNum++;
            return;
        }
        nextPacketNum = 0;

        byte[] plain;
        try {
            plain = decryptCipher.doFinal(cipherBuffer);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "QiYi Smart Timer 解密失败", e);
            resetPacketBuffer();
            return;
        }
        byte[] message = Arrays.copyOfRange(plain, 0, messageLength);
        handleMessage(message);
        resetPacketBuffer();
    }

    private void handleMessage(byte[] message) {
        if (message.length < 14) {
            return;
        }
        int payloadLength = ((message[10] & 0xFF) << 8) | (message[11] & 0xFF);
        if (message.length < 12 + payloadLength + 2) {
            return;
        }
        int crc = ((message[12 + payloadLength] & 0xFF) << 8) | (message[12 + payloadLength + 1] & 0xFF);
        byte[] body = Arrays.copyOfRange(message, 0, 12 + payloadLength);
        int expected = crc16Modbus(body);
        if (crc != expected) {
            Log.w(TAG, "QiYi Smart Timer CRC 不匹配");
        }

        long sendSeqNum = readUint32Long(message, 0);
        long ackSeqNum = readUint32Long(message, 4);
        int cmd = ((message[8] & 0xFF) << 8) | (message[9] & 0xFF);
        if (cmd != 0x1003) {
            return;
        }
        byte[] payload = Arrays.copyOfRange(message, 12, 12 + payloadLength);
        int dpId = payload[0] & 0xFF;
        int dpType = payload[1] & 0xFF;
        int dpLength = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
        byte[] dp = Arrays.copyOfRange(payload, 4, 4 + dpLength);

        if (dpId == 1 && dpType == 1 && payload.length >= 16) {
            int solveTime = readUint32(dp, 4);
            callback.onTimerStopped(solveTime);
            sendAck(ackSeqNum + 1, sendSeqNum, 0x1003);
        } else if (dpId == 4 && dpType == 4 && payload.length >= 9) {
            int state = dp[0] & 0xFF;
            int solveTime = readUint32(dp, 1);
            if (previousState != STATE_IDLE && state == STATE_IDLE) {
                callback.onTimerIdle(solveTime);
            } else if (state == STATE_INSPECTION) {
                callback.onTimerIdle(solveTime);
            } else if (state == STATE_GET_SET) {
                callback.onTimerReady(solveTime);
            } else if (state == STATE_RUNNING) {
                callback.onTimerRunning(solveTime);
            } else if (state == STATE_FINISHED || state == STATE_STOPPED) {
                callback.onTimerStopped(solveTime);
            } else if (state == STATE_DISCONNECT) {
                callback.onTimerDisconnected();
            } else if (state == STATE_IDLE) {
                callback.onTimerIdle(solveTime);
            }
            previousState = state;
        } else if (dpId == 4 && dpType == 1) {
            callback.onTimerReady(0);
        }
    }

    private int readUint32(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return 0;
        }
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private long readUint32Long(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return 0L;
        }
        return ((data[offset] & 0xFFL) << 24)
                | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private void resetPacketBuffer() {
        nextPacketNum = 0;
        messageLength = 0;
        cipherLength = 0;
        cipherPos = 0;
        cipherBuffer = null;
    }

    private int crc16Modbus(byte[] buffer) {
        int crc = 0xFFFF;
        for (byte b : buffer) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    private String normalizeMac(String mac) {
        if (TextUtils.isEmpty(mac)) {
            return null;
        }
        String upper = mac.trim().toUpperCase();
        return upper.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}") ? upper : null;
    }

    private String getDefaultMac(String currentDeviceName) {
        if (TextUtils.isEmpty(currentDeviceName)) {
            return null;
        }
        String upperName = currentDeviceName.trim().toUpperCase();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^QY-(?:TIMER|ADAPTER).*-([0-9A-F]{4})$")
                .matcher(upperName);
        if (!matcher.matches()) {
            return null;
        }
        String suffix = matcher.group(1);
        String prefix = upperName.startsWith("QY-ADAPTER") ? "CC:A8" : "CC:A1";
        return prefix + ":00:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2, 4);
    }
}
