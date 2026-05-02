package com.dctimer.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QiyiCubeProtocolTest {

    @Test
    public void parsesValidQuaternionPacket() {
        byte[] packet = quaternionPacket(100, 200, -300, 900);

        float[] q = QiyiCubeProtocol.parseQuaternionPacket(packet);

        float len = (float) Math.sqrt(0.1f * 0.1f + 0.3f * 0.3f + 0.2f * 0.2f + 0.9f * 0.9f);
        assertEquals(0.1f / len, q[0], 0.0001f);
        assertEquals(0.3f / len, q[1], 0.0001f);
        assertEquals(0.2f / len, q[2], 0.0001f);
        assertEquals(0.9f / len, q[3], 0.0001f);
    }

    @Test
    public void rejectsQuaternionPacketWithBadCrc() {
        byte[] packet = quaternionPacket(100, 200, -300, 900);
        packet[15] ^= 0x01;

        assertNull(QiyiCubeProtocol.parseQuaternionPacket(packet));
    }

    @Test
    public void rejectsShortQuaternionPacket() {
        assertNull(QiyiCubeProtocol.parseQuaternionPacket(new byte[] {(byte) 0xcc, 0x10}));
    }

    private static byte[] quaternionPacket(int x, int y, int z, int w) {
        byte[] packet = new byte[16];
        packet[0] = (byte) 0xcc;
        packet[1] = 0x10;
        putInt16(packet, 6, x);
        putInt16(packet, 8, y);
        putInt16(packet, 10, z);
        putInt16(packet, 12, w);
        int crc = crc16Modbus(packet, 14);
        packet[14] = (byte) (crc & 0xff);
        packet[15] = (byte) ((crc >> 8) & 0xff);
        return packet;
    }

    private static void putInt16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xff);
        data[offset + 1] = (byte) (value & 0xff);
    }

    private static int crc16Modbus(byte[] data, int length) {
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
