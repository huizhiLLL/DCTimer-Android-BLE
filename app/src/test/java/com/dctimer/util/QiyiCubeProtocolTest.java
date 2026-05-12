package com.dctimer.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QiyiCubeProtocolTest {
    @Test
    public void collectStateChangeMoves_scansAllHistorySlotsAndSortsByTimestamp() {
        byte[] msg = newStateChangeMessage(96, 9, 300);
        putHistorySlot(msg, 0, 5, 280);
        putEmptyHistorySlot(msg, 1);
        putHistorySlot(msg, 2, 3, 220);
        putHistorySlot(msg, 10, 7, 260);

        QiyiCubeProtocol.MoveSample[] moves = QiyiCubeProtocol.collectStateChangeMoves(msg, 200, 300);

        assertEquals(4, moves.length);
        assertMove(moves[0], 3, 220);
        assertMove(moves[1], 7, 260);
        assertMove(moves[2], 5, 280);
        assertMove(moves[3], 9, 300);
    }

    @Test
    public void collectStateChangeMoves_filtersOldInvalidAndDuplicateMoves() {
        byte[] msg = newStateChangeMessage(96, 4, 500);
        putHistorySlot(msg, 0, 12, 440);
        putHistorySlot(msg, 1, 13, 460);
        putHistorySlot(msg, 2, 4, 500);
        putHistorySlot(msg, 3, 1, 300);

        QiyiCubeProtocol.MoveSample[] moves = QiyiCubeProtocol.collectStateChangeMoves(msg, 400, 500);

        assertEquals(2, moves.length);
        assertMove(moves[0], 12, 440);
        assertMove(moves[1], 4, 500);
    }

    @Test
    public void collectStateChangeMoves_defersHistorySlotsNewerThanCurrentFrame() {
        byte[] msg = newStateChangeMessage(96, 3, 426604);
        putHistorySlot(msg, 0, 2, 426707);
        putHistorySlot(msg, 10, 2, 426582);

        QiyiCubeProtocol.MoveSample[] moves = QiyiCubeProtocol.collectStateChangeMoves(msg, 426582, 426604);

        assertEquals(1, moves.length);
        assertMove(moves[0], 3, 426604);
    }

    private static byte[] newStateChangeMessage(int length, int primaryMove, long primaryTimestamp) {
        byte[] msg = new byte[length];
        msg[2] = 0x03;
        putTimestamp(msg, 3, primaryTimestamp);
        msg[34] = (byte) primaryMove;
        for (int i = 0; i < 11; i++) {
            putEmptyHistorySlot(msg, i);
        }
        return msg;
    }

    private static void putHistorySlot(byte[] msg, int slot, int move, long timestamp) {
        int offset = 36 + slot * 5;
        putTimestamp(msg, offset, timestamp);
        msg[offset + 4] = (byte) move;
    }

    private static void putEmptyHistorySlot(byte[] msg, int slot) {
        int offset = 36 + slot * 5;
        for (int i = 0; i < 5; i++) {
            msg[offset + i] = (byte) 0xff;
        }
    }

    private static void putTimestamp(byte[] msg, int offset, long timestamp) {
        msg[offset] = (byte) ((timestamp >> 24) & 0xff);
        msg[offset + 1] = (byte) ((timestamp >> 16) & 0xff);
        msg[offset + 2] = (byte) ((timestamp >> 8) & 0xff);
        msg[offset + 3] = (byte) (timestamp & 0xff);
    }

    private static void assertMove(QiyiCubeProtocol.MoveSample move, int rawMove, long timestamp) {
        assertEquals(rawMove, move.move);
        assertEquals(timestamp, move.timestamp);
    }
}
