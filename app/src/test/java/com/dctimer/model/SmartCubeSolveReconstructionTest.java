package com.dctimer.model;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmartCubeSolveReconstructionTest {
    private static final String SOLVED = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

    @Test
    public void mergesAdjacentSameFaceTurnsIntoOneMove() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 80, 80));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertEquals("R2", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void recognizesOppositeLayerComboAsSliceWithinWindow() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(11, 90, 90));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertEquals("E", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void keepsOppositeLayerTurnsSeparateOutsideWindow() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(11, 120, 120));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertEquals("U D'", reconstruction.getMoveSequence());
        assertEquals(2, reconstruction.getMoveCount());
    }

    @Test
    public void keepsFinalAufTurnsInsidePllPhase() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertTrue(reconstruction.getPrettySolve().contains("// PLL"));
        assertTrue(!reconstruction.getPrettySolve().contains("// AUF"));
    }

    @Test
    public void emitsPhaseMetadataJson() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertTrue(reconstruction.toJson(1000).contains("\"method\":\"333-smart-cf4op\""));
    }

    @Test
    public void prettySolveOmitsPhaseMoveCountsAndAppendsSolveStats() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);
        String prettySolve = reconstruction.getPrettySolve(1000);

        assertTrue(prettySolve.contains("Total: 1 moves"));
        assertTrue(prettySolve.contains("TPS: 1.0 tps"));
        assertTrue(!prettySolve.contains("move(s)"));
    }

    @Test
    public void cf4opProgressDoesNotCountOneSolvedF2lSlotAsEverySlot() throws Exception {
        char[] facelets = SOLVED.toCharArray();
        facelets[21] = 'B';
        facelets[14] = 'F';
        facelets[39] = 'B';

        assertEquals(5, invokeCf4opProgress(new String(facelets)));
    }

    private static int invokeCf4opProgress(String facelets) throws Exception {
        Method method = SmartCubeSolveReconstruction.class.getDeclaredMethod("getCf4opProgress", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, facelets);
    }

}
