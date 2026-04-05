package com.dctimer.util;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Moyu32Cipher {
    private static final byte[] BASE_KEY = {
            21, 119, 58, 92, 103, 14, 45, 31,
            23, 103, 42, 19, -101, 103, 82, 87
    };
    private static final byte[] BASE_IV = {
            17, 35, 38, 37, -122, 42, 44, 59,
            85, 6, 127, 49, 126, 103, 33, 87
    };

    private byte[] key;
    private byte[] iv;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    public void init(String mac) throws GeneralSecurityException {
        byte[] macBytes = parseMac(mac);
        key = Arrays.copyOf(BASE_KEY, BASE_KEY.length);
        iv = Arrays.copyOf(BASE_IV, BASE_IV.length);
        for (int i = 0; i < 6; i++) {
            key[i] = (byte) (((key[i] & 0xff) + (macBytes[5 - i] & 0xff)) % 255);
            iv[i] = (byte) (((iv[i] & 0xff) + (macBytes[5 - i] & 0xff)) % 255);
        }
        SecretKeySpec spec = new SecretKeySpec(key, "AES");
        encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, spec);
        decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, spec);
    }

    public boolean isReady() {
        return encryptCipher != null && decryptCipher != null && iv != null;
    }

    public byte[] encode(byte[] value) throws GeneralSecurityException {
        byte[] ret = Arrays.copyOf(value, value.length);
        if (!isReady()) {
            return ret;
        }
        xorWithIv(ret, 0);
        writeBlock(ret, 0, encryptBlock(Arrays.copyOf(ret, 16)));
        if (ret.length > 16) {
            int offset = ret.length - 16;
            byte[] block = Arrays.copyOfRange(ret, offset, offset + 16);
            xorWithIv(block, 0);
            writeBlock(ret, offset, encryptBlock(block));
        }
        return ret;
    }

    public byte[] decode(byte[] value) throws GeneralSecurityException {
        byte[] ret = Arrays.copyOf(value, value.length);
        if (!isReady()) {
            return ret;
        }
        if (ret.length > 16) {
            int offset = ret.length - 16;
            byte[] block = decryptBlock(Arrays.copyOfRange(ret, offset, offset + 16));
            xorWithIv(block, 0);
            writeBlock(ret, offset, block);
        }
        byte[] first = decryptBlock(Arrays.copyOf(ret, 16));
        xorWithIv(first, 0);
        writeBlock(ret, 0, first);
        return ret;
    }

    private byte[] parseMac(String mac) {
        if (mac == null) {
            throw new IllegalArgumentException("mac is null");
        }
        String normalized = mac.trim().toUpperCase(Locale.US);
        String[] parts = normalized.split(":");
        if (parts.length != 6) {
            throw new IllegalArgumentException("invalid mac: " + mac);
        }
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    private void xorWithIv(byte[] target, int offset) {
        for (int i = 0; i < 16; i++) {
            target[offset + i] = (byte) (target[offset + i] ^ iv[i]);
        }
    }

    private byte[] encryptBlock(byte[] block) throws GeneralSecurityException {
        return encryptCipher.doFinal(block);
    }

    private byte[] decryptBlock(byte[] block) throws GeneralSecurityException {
        return decryptCipher.doFinal(block);
    }

    private void writeBlock(byte[] target, int offset, byte[] block) {
        System.arraycopy(block, 0, target, offset, 16);
    }
}
