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
        assertTrue(reconstruction.toJson(1000).contains("\"version\":\"2\""));
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

    @Test
    public void insertsRotationFromGyroWithoutCountingItAsMove() {
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 80));
        gyro.add(gyroX(90, 160));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(
                SOLVED, new ArrayList<SmartCubeSolveReconstruction.MoveEvent>(), gyro);

        assertEquals("x", reconstruction.getMoveSequence());
        assertEquals(0, reconstruction.getMoveCount());
    }

    @Test
    public void recognizesWideMoveWhenFaceTurnAndRotationAreClose() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 80));
        gyro.add(gyroX(90, 160));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertEquals("r", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void keepsFaceTurnAndRotationSeparateOutsideWideWindow() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 400));
        gyro.add(gyroX(90, 480));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertEquals("R x", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void ignoresGyroWithoutManualBaseCalibration() {
        SmartCube cube = new SmartCube();
        cube.setCubeState(SOLVED);
        cube.markSolveStarted(SOLVED, 1000);
        cube.applyGyro(0f, 0f, 0f, 1f, 1000);
        cube.applyGyro((float) Math.sin(Math.toRadians(45)), 0f, 0f,
                (float) Math.cos(Math.toRadians(45)), 1080);
        cube.applyGyro((float) Math.sin(Math.toRadians(45)), 0f, 0f,
                (float) Math.cos(Math.toRadians(45)), 1160);

        cube.calcResult();

        assertEquals("", cube.getReconstruction().getMoveSequence());
        assertEquals(0, cube.getReconstructedMovesCount());
    }

    @Test
    public void recordsShortestInitialViewChangeBeforeFirstTurn() {
        SmartCube cube = new SmartCube();
        cube.setCubeState(SOLVED);
        cube.applyGyro(0f, 0f, 0f, 1f, 900);
        assertTrue(cube.setGyroBaseToLatest());
        SmartCubeSolveReconstruction.GyroEvent z2 = gyroZ(180, 990);
        cube.applyGyro(z2.qx, z2.qy, z2.qz, z2.qw, 990);
        cube.markSolveStarted(SOLVED, 1000);

        cube.calcResult();

        assertEquals("z2", cube.getReconstruction().getMoveSequence());
        assertEquals(0, cube.getReconstructedMovesCount());
    }

    @Test
    public void keepsInitialViewChangeBeforeCrossMoves() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 120, 120));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroZ(180, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertTrue(reconstruction.getMoveSequence().startsWith("z2 "));
    }

    @Test
    public void keepsInitialViewChangeAtStartOfCrossLine() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 120, 120));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroZ(180, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertTrue(reconstruction.getPrettySolve().startsWith("z2 // Cross"));
    }

    @Test
    public void ignoresTransientGyroOrientation() {
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 80));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(
                SOLVED, new ArrayList<SmartCubeSolveReconstruction.MoveEvent>(), gyro);

        assertEquals("", reconstruction.getMoveSequence());
        assertEquals(0, reconstruction.getMoveCount());
    }

    @Test
    public void keepsSliceMoveFromBecomingRotationOrWideMove() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(11, 80, 80));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 90));
        gyro.add(gyroX(90, 160));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertEquals("E", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void compactifiesWideMoveAndOuterTurnBackToSliceMove() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(5, 160, 160));
        List<SmartCubeSolveReconstruction.GyroEvent> gyro = new ArrayList<>();
        gyro.add(gyroX(0, 0));
        gyro.add(gyroX(90, 80));
        gyro.add(gyroX(90, 120));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw, gyro);

        assertEquals("M'", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    private static int invokeCf4opProgress(String facelets) throws Exception {
        Method method = SmartCubeSolveReconstruction.class.getDeclaredMethod("getCf4opProgress", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, facelets);
    }

    private static SmartCubeSolveReconstruction.GyroEvent gyroX(double degrees, int elapsedMs) {
        double half = Math.toRadians(degrees) / 2.0;
        return new SmartCubeSolveReconstruction.GyroEvent(
                (float) Math.sin(half), 0f, 0f, (float) Math.cos(half), elapsedMs);
    }

    private static SmartCubeSolveReconstruction.GyroEvent gyroZ(double degrees, int elapsedMs) {
        double half = Math.toRadians(degrees) / 2.0;
        return new SmartCubeSolveReconstruction.GyroEvent(
                0f, 0f, (float) Math.sin(half), (float) Math.cos(half), elapsedMs);
    }

}
