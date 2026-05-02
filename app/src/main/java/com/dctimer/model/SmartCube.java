package com.dctimer.model;

import com.dctimer.util.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class SmartCube implements Serializable {
    private int type;
    private int version;
    private String deviceName;
    private String cubeState;
    private int batteryValue;
    private List<Integer> rawData;
    private CubieCube cc;
    private int preIdx;
    private int result;
    private int moves;
    private int reconstructedMoves;
    private List<Integer> preMoveList;
    private List<Integer> moveList;
    private String solveStartState;
    private long solveStartUptimeMs;
    private List<GyroSample> gyroSamples;
    private GyroSample latestGyroSample;
    private GyroSample gyroBaseSample;
    private SmartCubeSolveReconstruction reconstruction;
    private StateChangedCallback callback;
    private String targetState;
    private boolean scrambledNotified;

    public SmartCube() {
        rawData = new ArrayList<>();
        gyroSamples = new ArrayList<>();
        cc = new CubieCube();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCubeState() {
        return cubeState;
    }

    public int setCubeState(String state) {
        this.cubeState = state;
        return Util.toCubieCube(state, cc);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getBatteryValue() {
        return batteryValue;
    }

    public void setBatteryValue(int batteryValue) {
        this.batteryValue = batteryValue;
    }

    public int getResult() {
        return result;
    }

    public void setStateChangedCallback(StateChangedCallback callback) {
        this.callback = callback;
    }

    public int getMovesCount() {
        return moves;
    }

    public int getReconstructedMovesCount() {
        return reconstructedMoves;
    }

    public SmartCubeSolveReconstruction getReconstruction() {
        return reconstruction;
    }

    public void applyMove(int move, int time, String scramble) {
        rawData.add(move << 16 | time);
        cc = cc.move(move);
        cubeState = Util.toFaceCube(cc);
        if (scramble != null && !scramble.equals(targetState)) {
            targetState = scramble;
            scrambledNotified = false;
        }
        if (!scrambledNotified && callback != null && Utils.isSameStateIgnoringRotation(cubeState, scramble)) {
            scrambledNotified = true;
            callback.onScrambled(this);
        }
        if (callback != null && Utils.isSolvedIgnoringRotation(cubeState))
            callback.onSolved(this);
    }

    public void applyGyro(float qx, float qy, float qz, float qw, long localUptimeMs) {
        latestGyroSample = new GyroSample(qx, qy, qz, qw, localUptimeMs);
        gyroSamples.add(latestGyroSample);
    }

    public boolean setGyroBaseToLatest() {
        if (latestGyroSample == null) {
            return false;
        }
        gyroBaseSample = latestGyroSample;
        gyroSamples = new ArrayList<>();
        gyroSamples.add(latestGyroSample);
        return true;
    }

    public void markScrambled() {
        preIdx = rawData.size();
        solveStartState = cubeState;
        solveStartUptimeMs = 0L;
        scrambledNotified = true;
    }

    public void markSolveStarted(String startState) {
        markSolveStarted(startState, 0L);
    }

    public void markSolveStarted(String startState, long localUptimeMs) {
        if (rawData.isEmpty()) {
            preIdx = 0;
        } else {
            preIdx = rawData.size() - 1;
        }
        solveStartState = startState;
        solveStartUptimeMs = localUptimeMs;
    }

    public void markSolved() {
        cubeState = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
        cc = new CubieCube();
        rawData = new ArrayList<>();
        gyroSamples = new ArrayList<>();
        preIdx = 0;
        solveStartState = null;
        solveStartUptimeMs = 0L;
        targetState = null;
        scrambledNotified = false;
    }

    public void clearLastReconstruction() {
        reconstruction = null;
        reconstructedMoves = 0;
    }

    public void calcResult() {
        result = 0;
        moves = rawData.size() - preIdx;
        reconstructedMoves = 0;
        reconstruction = null;
        preMoveList = new ArrayList<>();
        moveList = new ArrayList<>();
        List<SmartCubeSolveReconstruction.MoveEvent> solveMoves = new ArrayList<>();
        List<SmartCubeSolveReconstruction.GyroEvent> solveGyros = new ArrayList<>();
        int elapsed = 0;
        for (int i = 0; i < preIdx; i++) {
            int move = rawData.get(i) >> 16;
            if (preMoveList.size() == 0) preMoveList.add(move);
            else if (preMoveList.get(preMoveList.size() - 1) == move) {
                if (move % 3 == 1) preMoveList.add(move);
                else {
                    int turn = move / 3;
                    preMoveList.add(turn * 3 + 1);
                }
            } else preMoveList.add(move);
        }
        //Log.w("dct", "start "+preIdx+" size "+rawData.size());
        for (int i = preIdx; i < rawData.size(); i++) {
            int delta = rawData.get(i) & 0xffff;
            if (i != preIdx) {
                result += delta;
                elapsed += delta;
            }
            //Log.w("dct", i+":"+rawData.get(i)+"/"+result);
            int move = rawData.get(i) >> 16;
            solveMoves.add(new SmartCubeSolveReconstruction.MoveEvent(move, delta, elapsed));
            if (moveList.size() == 0) moveList.add(move);
            else if (moveList.get(moveList.size() - 1) == move) {
                if (move % 3 == 1) moveList.add(move);
                else {
                    int turn = move / 3;
                    moveList.set(moveList.size() - 1, turn * 3 + 1);
                }
            } else moveList.add(move);
        }
        if (type == BLEDevice.TYPE_GANI_CUBE)
            result = (int) (result / 0.95);
        if (solveStartUptimeMs > 0 && gyroBaseSample != null) {
            GyroSample startSample = null;
            for (GyroSample sample : gyroSamples) {
                if (sample.localUptimeMs <= solveStartUptimeMs
                        && (startSample == null || sample.localUptimeMs > startSample.localUptimeMs)) {
                    startSample = sample;
                }
            }
            if (startSample != null) {
                GyroSample relative = relativeGyro(gyroBaseSample, startSample, 0);
                if (relative != null) {
                    solveGyros.add(new SmartCubeSolveReconstruction.GyroEvent(
                            relative.qx, relative.qy, relative.qz, relative.qw, 0));
                }
            }
            for (GyroSample sample : gyroSamples) {
                if (sample.localUptimeMs < solveStartUptimeMs) {
                    continue;
                }
                if (sample == startSample) {
                    continue;
                }
                long elapsedMs = sample.localUptimeMs - solveStartUptimeMs;
                if (elapsedMs > Integer.MAX_VALUE) {
                    continue;
                }
                GyroSample relative = relativeGyro(gyroBaseSample, sample, (int) elapsedMs);
                if (relative != null) {
                    solveGyros.add(new SmartCubeSolveReconstruction.GyroEvent(
                            relative.qx, relative.qy, relative.qz, relative.qw, (int) elapsedMs));
                }
            }
        }
        reconstruction = SmartCubeSolveReconstruction.fromRawMoves(solveStartState, solveMoves, solveGyros);
        reconstructedMoves = reconstruction.getMoveCount();
    }

    private static GyroSample relativeGyro(GyroSample base, GyroSample sample, int elapsedMs) {
        double baseLen = norm(base);
        double sampleLen = norm(sample);
        if (baseLen <= 0 || sampleLen <= 0) {
            return null;
        }
        double bx = base.qx / baseLen;
        double by = base.qy / baseLen;
        double bz = base.qz / baseLen;
        double bw = base.qw / baseLen;
        double sx = sample.qx / sampleLen;
        double sy = sample.qy / sampleLen;
        double sz = sample.qz / sampleLen;
        double sw = sample.qw / sampleLen;
        double x = bw * sx - bx * sw - by * sz + bz * sy;
        double y = bw * sy + bx * sz - by * sw - bz * sx;
        double z = bw * sz - bx * sy + by * sx - bz * sw;
        double w = bw * sw + bx * sx + by * sy + bz * sz;
        double len = Math.sqrt(x * x + y * y + z * z + w * w);
        if (len <= 0) {
            return null;
        }
        return new GyroSample((float) (x / len), (float) (y / len),
                (float) (z / len), (float) (w / len), elapsedMs);
    }

    private static double norm(GyroSample sample) {
        if (sample == null) {
            return 0;
        }
        return Math.sqrt(sample.qx * sample.qx + sample.qy * sample.qy
                + sample.qz * sample.qz + sample.qw * sample.qw);
    }

    public String getMoveSequence() {
        if (reconstruction != null && reconstruction.getPrettySolve() != null && reconstruction.getPrettySolve().length() > 0) {
            return reconstruction.getPrettySolve(result);
        }
        StringBuilder sb = new StringBuilder();
        String[] suff = {"", "2", "'"};
        for (int i = 0; i < moveList.size(); i++) {
            int move = moveList.get(i);
            sb.append("URFDLB".charAt(move / 3)).append(suff[move % 3]).append(" ");
        }
        return sb.toString();
    }

    public String getSolveMeta() {
        if (reconstruction == null) {
            return null;
        }
        return reconstruction.toJson(result);
    }

    public interface StateChangedCallback {
        void onScrambled(SmartCube cube);
        void onSolved(SmartCube cube);
    }

    private static class GyroSample implements Serializable {
        final float qx;
        final float qy;
        final float qz;
        final float qw;
        final long localUptimeMs;

        GyroSample(float qx, float qy, float qz, float qw, long localUptimeMs) {
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
            this.localUptimeMs = localUptimeMs;
        }
    }
}
