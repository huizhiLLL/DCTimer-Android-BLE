package com.dctimer.aes;

import java.util.Arrays;

public final class Decrypt {
    private Decrypt() { }

    public static byte[] toHexValue(byte[] value) {
        if (value == null) {
            return new byte[0];
        }
        byte[] result = new byte[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            int b = value[i] & 0xff;
            result[i * 2] = (byte) ((b >> 4) & 0x0f);
            result[i * 2 + 1] = (byte) (b & 0x0f);
        }
        return result;
    }

    public static byte[] getKey(int version, byte[] value) {
        if (value == null || value.length == 0) {
            return null;
        }
        byte[] key = new byte[16];
        int len = Math.min(value.length, key.length);
        System.arraycopy(value, 0, key, 0, len);
        return key;
    }

    public static void initAES(byte[] key) {
        // Temporary compatibility shim for legacy Bluetooth code.
    }

    public static byte[] decode(byte[] value) {
        if (value == null) {
            return new byte[0];
        }
        return Arrays.copyOf(value, value.length);
    }
}
