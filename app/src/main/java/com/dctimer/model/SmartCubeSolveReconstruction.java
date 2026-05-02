package com.dctimer.model;

import com.dctimer.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class SmartCubeSolveReconstruction {
    private static final int SLICE_COMBO_WINDOW_MS = 100;
    private static final int WIDE_COMBO_WINDOW_MS = 250;
    private static final int COMPACT_GROUP_WINDOW_MS = 500;
    private static final int OUTER_ROTATION_WIDE_STRICT_MS = 300;
    private static final int GYRO_STABLE_SAMPLE_COUNT = 2;
    private static final double ORIENTATION_MIN_SCORE = 2.55;
    private static final String SOLVED_FACELET = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    private static final String PRETTY_FACES = "URFDLBEMS";
    private static final String SUFFIXES = " 2'";
    private static final String[] PHASE_NAMES = {"Cross", "F2L 1", "F2L 2", "F2L 3", "F2L 4", "OLL", "PLL"};

    private static final int[][] CENTER_ROT = {
            {0, 2, 4, 3, 5, 1}, // y'
            {5, 1, 0, 2, 4, 3}, // x'
            {4, 0, 2, 1, 3, 5}  // z
    };

    private static final int[][] CROSS_MASK = toEqus("----U--------R--R-----F--F--D-DDD-D-----L--L-----B--B-");
    private static final int[][] F2L1_MASK = toEqus("----U-------RR-RR-----FF-FF-DDDDD-D-----L--L-----B--B-");
    private static final int[][] F2L2_MASK = toEqus("----U--------R--R----FF-FF-DD-DDD-D-----LL-LL----B--B-");
    private static final int[][] F2L3_MASK = toEqus("----U--------RR-RR----F--F--D-DDD-DD----L--L----BB-BB-");
    private static final int[][] F2L4_MASK = toEqus("----U--------R--R-----F--F--D-DDDDD----LL-LL-----BB-BB");
    private static final int[][] F2L_MASK = toEqus("----U-------RRRRRR---FFFFFFDDDDDDDDD---LLLLLL---BBBBBB");
    private static final int[][] OLL_MASK = toEqus("UUUUUUUUU---RRRRRR---FFFFFFDDDDDDDDD---LLLLLL---BBBBBB");
    private static final int[][] SOLVED_MASK = toEqus(SOLVED_FACELET);

    private final List<MoveEvent> rawMoves;
    private final List<PrettyMove> reconstructedMoves;
    private final List<Phase> phases;
    private final String prettySolve;
    private final String moveSequence;
    private final int moveCount;

    private SmartCubeSolveReconstruction(List<MoveEvent> rawMoves, List<PrettyMove> reconstructedMoves, List<Phase> phases) {
        this.rawMoves = rawMoves;
        this.reconstructedMoves = reconstructedMoves;
        this.phases = phases;
        this.prettySolve = buildPrettySolve(phases, reconstructedMoves);
        this.moveSequence = buildMoveSequence(reconstructedMoves);
        this.moveCount = countNonRotationMoves(reconstructedMoves);
    }

    public static SmartCubeSolveReconstruction fromRawMoves(String startFacelet, List<MoveEvent> rawMoves) {
        return fromRawMoves(startFacelet, rawMoves, null);
    }

    public static SmartCubeSolveReconstruction fromRawMoves(String startFacelet, List<MoveEvent> rawMoves, List<GyroEvent> gyroEvents) {
        List<MoveEvent> safeRawMoves = rawMoves == null ? new ArrayList<MoveEvent>() : new ArrayList<>(rawMoves);
        List<GyroEvent> safeGyroEvents = gyroEvents == null ? new ArrayList<GyroEvent>() : new ArrayList<>(gyroEvents);
        PhaseBuildResult phaseResult = buildPhases(startFacelet, safeRawMoves, safeGyroEvents);
        return new SmartCubeSolveReconstruction(safeRawMoves, phaseResult.reconstructedMoves, phaseResult.phases);
    }

    public String getPrettySolve() {
        return prettySolve;
    }

    public String getPrettySolve(int solveTimeMs) {
        return appendSolveStats(prettySolve, solveTimeMs);
    }

    public String getMoveSequence() {
        return moveSequence;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public String toJson(int solveTimeMs) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendJsonField(sb, "version", "2");
        sb.append(',');
        appendJsonField(sb, "method", "333-smart-cf4op");
        sb.append(',');
        sb.append("\"moveCount\":").append(moveCount);
        sb.append(',');
        sb.append("\"solveTimeMs\":").append(Math.max(0, solveTimeMs));
        sb.append(',');
        appendJsonField(sb, "moves", moveSequence);
        sb.append(',');
        appendJsonField(sb, "prettySolve", getPrettySolve(solveTimeMs));
        sb.append(',');
        sb.append("\"phases\":[");
        for (int i = 0; i < phases.size(); i++) {
            if (i > 0) sb.append(',');
            Phase phase = phases.get(i);
            sb.append('{');
            appendJsonField(sb, "name", phase.name);
            sb.append(',');
            sb.append("\"moveCount\":").append(phase.moveCount);
            sb.append(',');
            sb.append("\"startMs\":").append(phase.startMs);
            sb.append(',');
            sb.append("\"endMs\":").append(phase.endMs);
            sb.append(',');
            appendJsonField(sb, "moves", phase.moves);
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    public static class MoveEvent {
        public final int move;
        public final int deltaMs;
        public final int elapsedMs;

        public MoveEvent(int move, int deltaMs, int elapsedMs) {
            this.move = move;
            this.deltaMs = deltaMs;
            this.elapsedMs = elapsedMs;
        }
    }

    public static class GyroEvent {
        public final float qx;
        public final float qy;
        public final float qz;
        public final float qw;
        public final int elapsedMs;

        public GyroEvent(float qx, float qy, float qz, float qw, int elapsedMs) {
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class RotationEvent {
        final int axis;
        final int pow;
        final int elapsedMs;

        RotationEvent(int axis, int pow, int elapsedMs) {
            this.axis = axis;
            this.pow = pow;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class OrientationState {
        final int[][] matrix;
        final int elapsedMs;

        OrientationState(int[][] matrix, int elapsedMs) {
            this.matrix = matrix;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class PrettyMove {
        final int axis;
        int pow;
        int startRawIndex;
        int endRawIndex;
        int startMs;
        int endMs;

        PrettyMove(int axis, int pow, int startRawIndex, int endRawIndex, int startMs, int endMs) {
            this.axis = axis;
            this.pow = pow;
            this.startRawIndex = startRawIndex;
            this.endRawIndex = endRawIndex;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        String notation() {
            return getMoveName(axis) + SUFFIXES.charAt(pow);
        }

        boolean isRotation() {
            return axis >= 9 && axis <= 11;
        }
    }

    private static class Phase {
        final String name;
        final String moves;
        final int moveCount;
        final int startMs;
        final int endMs;

        Phase(String name, String moves, int moveCount, int startMs, int endMs) {
            this.name = name;
            this.moves = moves;
            this.moveCount = moveCount;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class PhaseBuildResult {
        final List<PrettyMove> reconstructedMoves;
        final List<Phase> phases;

        PhaseBuildResult(List<PrettyMove> reconstructedMoves, List<Phase> phases) {
            this.reconstructedMoves = reconstructedMoves;
            this.phases = phases;
        }
    }

    private static List<PrettyMove> reconstructMoves(List<MoveEvent> rawMoves) {
        return reconstructMoves(rawMoves, null);
    }

    private static List<PrettyMove> reconstructMoves(List<MoveEvent> rawMoves, List<GyroEvent> gyroEvents) {
        List<RotationEvent> setupRotations = buildInitialRotationEvents(gyroEvents);
        List<PrettyMove> body = compactMoveSequence(
                buildMergedMoveStream(rawMoves, gyroEvents, setupRotations),
                gyroEvents != null && !gyroEvents.isEmpty());
        return prependRotationEvents(body, setupRotations);
    }

    private static List<PrettyMove> buildMergedMoveStream(List<MoveEvent> rawMoves, List<GyroEvent> gyroEvents,
                                                          List<RotationEvent> setupRotations) {
        List<RotationEvent> rotations = buildRotationEvents(gyroEvents);
        List<PrettyMove> ret = new ArrayList<>();
        int[] center = {0, 1, 2, 3, 4, 5};
        for (RotationEvent setupRotation : setupRotations) {
            center = applyPrettyRotation(center, setupRotation.axis, setupRotation.pow);
        }
        int rotationIndex = 0;
        for (int i = 0; i < rawMoves.size(); i++) {
            MoveEvent current = rawMoves.get(i);
            while (rotationIndex < rotations.size()
                    && rotations.get(rotationIndex).elapsedMs <= current.elapsedMs) {
                RotationEvent rotation = rotations.get(rotationIndex++);
                pushMove(ret, rotation.axis, rotation.pow, -1, -1, rotation.elapsedMs, rotation.elapsedMs);
                center = applyPrettyRotation(center, rotation.axis, rotation.pow);
            }
            int axis = indexOf(center, current.move / 3);
            int pow = current.move % 3;
            if (axis < 0) {
                continue;
            }
            pushMove(ret, axis, pow, i, i, current.elapsedMs, current.elapsedMs);
        }
        while (rotationIndex < rotations.size()) {
            RotationEvent rotation = rotations.get(rotationIndex++);
            pushMove(ret, rotation.axis, rotation.pow, -1, -1, rotation.elapsedMs, rotation.elapsedMs);
        }
        return ret;
    }

    private static void pushMove(List<PrettyMove> moves, int axis, int pow, int startRawIndex, int endRawIndex, int startMs, int endMs) {
        if (moves.isEmpty() || moves.get(moves.size() - 1).axis != axis) {
            moves.add(new PrettyMove(axis, pow, startRawIndex, endRawIndex, startMs, endMs));
            return;
        }
        PrettyMove last = moves.get(moves.size() - 1);
        int mergedPow = (pow + last.pow + 1) % 4;
        if (mergedPow == 3) {
            moves.remove(moves.size() - 1);
        } else {
            last.pow = mergedPow;
            last.endRawIndex = endRawIndex;
            last.endMs = endMs;
        }
    }

    private static PhaseBuildResult buildPhases(String startFacelet, List<MoveEvent> rawMoves, List<GyroEvent> gyroEvents) {
        if (rawMoves.isEmpty() || isEmpty(startFacelet)) {
            return new PhaseBuildResult(reconstructMoves(rawMoves, gyroEvents), createEmptyPhases());
        }
        CubieCube cube = new CubieCube();
        if (Util.toCubieCube(startFacelet, cube) != 0) {
            return new PhaseBuildResult(reconstructMoves(rawMoves, gyroEvents), createEmptyPhases());
        }

        List<List<MoveEvent>> statusBuckets = new ArrayList<>();
        for (int i = 0; i < PHASE_NAMES.length; i++) {
            statusBuckets.add(new ArrayList<MoveEvent>());
        }

        int status = updatePhaseStatus(PHASE_NAMES.length, getCf4opProgress(startFacelet));
        for (int i = 0; i < rawMoves.size(); i++) {
            MoveEvent rawMove = rawMoves.get(i);
            statusBuckets.get(status - 1).add(rawMove);
            int move = rawMove.move;
            if (move >= 0 && move < 18) {
                cube = cube.move(move);
            }
            status = updatePhaseStatus(status, getCf4opProgress(Util.toFaceCube(cube)));
        }

        List<List<MoveEvent>> phaseRawMoves = new ArrayList<>();
        List<String> phaseNames = new ArrayList<>();
        for (int i = PHASE_NAMES.length - 1; i >= 0; i--) {
            phaseRawMoves.add(statusBuckets.get(i));
            phaseNames.add(PHASE_NAMES[PHASE_NAMES.length - 1 - i]);
        }
        List<List<GyroEvent>> phaseGyroEvents = splitGyrosByPhase(gyroEvents, phaseRawMoves);
        List<List<PrettyMove>> phasePrettyMoves = reconstructMoveGroups(
                phaseRawMoves, phaseGyroEvents, buildInitialRotationEvents(gyroEvents));
        List<Phase> phases = new ArrayList<>();
        List<PrettyMove> reconstructedMoves = new ArrayList<>();
        for (int i = 0; i < phaseNames.size(); i++) {
            List<PrettyMove> moves = phasePrettyMoves.get(i);
            reconstructedMoves.addAll(moves);
            phases.add(createPhase(phaseNames.get(i), moves, 0, moves.size() - 1));
        }
        return new PhaseBuildResult(reconstructedMoves, phases);
    }

    private static int updatePhaseStatus(int status, int progress) {
        int nextStatus = Math.min(progress, status);
        return nextStatus == 0 ? 1 : nextStatus;
    }

    private static List<Phase> createEmptyPhases() {
        List<Phase> phases = new ArrayList<>();
        for (String phaseName : PHASE_NAMES) {
            phases.add(new Phase(phaseName, "", 0, 0, 0));
        }
        return phases;
    }

    private static List<List<PrettyMove>> reconstructMoveGroups(List<List<MoveEvent>> rawMoveGroups) {
        List<List<GyroEvent>> emptyGyroGroups = new ArrayList<>();
        for (int i = 0; i < rawMoveGroups.size(); i++) {
            emptyGyroGroups.add(new ArrayList<GyroEvent>());
        }
        return reconstructMoveGroups(rawMoveGroups, emptyGyroGroups, new ArrayList<RotationEvent>());
    }

    private static List<List<PrettyMove>> reconstructMoveGroups(List<List<MoveEvent>> rawMoveGroups, List<List<GyroEvent>> gyroGroups) {
        return reconstructMoveGroups(rawMoveGroups, gyroGroups, new ArrayList<RotationEvent>());
    }

    private static List<List<PrettyMove>> reconstructMoveGroups(List<List<MoveEvent>> rawMoveGroups,
                                                               List<List<GyroEvent>> gyroGroups,
                                                               List<RotationEvent> initialSetupRotations) {
        List<List<PrettyMove>> result = new ArrayList<>();
        int[] center = {0, 1, 2, 3, 4, 5};
        for (int groupIndex = 0; groupIndex < rawMoveGroups.size(); groupIndex++) {
            List<MoveEvent> rawMoveGroup = rawMoveGroups.get(groupIndex);
            List<GyroEvent> groupGyros = gyroGroups != null && groupIndex < gyroGroups.size()
                    ? gyroGroups.get(groupIndex) : null;
            List<RotationEvent> setupRotations = groupIndex == 0
                    ? initialSetupRotations : new ArrayList<RotationEvent>();
            for (RotationEvent setupRotation : setupRotations) {
                center = applyPrettyRotation(center, setupRotation.axis, setupRotation.pow);
            }
            List<RotationEvent> rotations = buildRotationEvents(
                    groupGyros);
            List<PrettyMove> ret = new ArrayList<>();
            int rotationIndex = 0;
            for (int i = 0; i < rawMoveGroup.size(); i++) {
                MoveEvent current = rawMoveGroup.get(i);
                while (rotationIndex < rotations.size()
                        && rotations.get(rotationIndex).elapsedMs <= current.elapsedMs) {
                    RotationEvent rotation = rotations.get(rotationIndex++);
                    pushMove(ret, rotation.axis, rotation.pow, -1, -1, rotation.elapsedMs, rotation.elapsedMs);
                    center = applyPrettyRotation(center, rotation.axis, rotation.pow);
                }
                int axis = indexOf(center, current.move / 3);
                int pow = current.move % 3;
                if (axis < 0) {
                    continue;
                }
                pushMove(ret, axis, pow, i, i, current.elapsedMs, current.elapsedMs);
            }
            while (rotationIndex < rotations.size()) {
                RotationEvent rotation = rotations.get(rotationIndex++);
                pushMove(ret, rotation.axis, rotation.pow, -1, -1, rotation.elapsedMs, rotation.elapsedMs);
                center = applyPrettyRotation(center, rotation.axis, rotation.pow);
            }
            List<PrettyMove> body = compactMoveSequence(ret, groupGyros != null && !groupGyros.isEmpty());
            result.add(prependRotationEvents(body, setupRotations));
        }
        return result;
    }

    private static Phase createPhase(String name, List<PrettyMove> moves, int start, int end) {
        if (moves.isEmpty() || start >= moves.size() || end < start) {
            return new Phase(name, "", 0, 0, 0);
        }
        int safeEnd = Math.min(end, moves.size() - 1);
        String moveText = joinMoves(moves, start, safeEnd);
        int count = countNonRotationMoves(moves, start, safeEnd);
        return new Phase(name, moveText, count, moves.get(start).startMs, moves.get(safeEnd).endMs);
    }

    private static List<List<GyroEvent>> splitGyrosByPhase(List<GyroEvent> gyroEvents, List<List<MoveEvent>> phaseRawMoves) {
        List<List<GyroEvent>> result = new ArrayList<>();
        for (int i = 0; i < phaseRawMoves.size(); i++) {
            result.add(new ArrayList<GyroEvent>());
        }
        if (gyroEvents == null || gyroEvents.isEmpty() || phaseRawMoves == null || phaseRawMoves.isEmpty()) {
            return result;
        }
        for (GyroEvent gyro : gyroEvents) {
            int target = -1;
            for (int i = 0; i < phaseRawMoves.size(); i++) {
                List<MoveEvent> moves = phaseRawMoves.get(i);
                if (moves.isEmpty()) {
                    continue;
                }
                int start = moves.get(0).elapsedMs;
                int end = moves.get(moves.size() - 1).elapsedMs + WIDE_COMBO_WINDOW_MS;
                boolean hasNextGroup = false;
                for (int j = i + 1; j < phaseRawMoves.size(); j++) {
                    List<MoveEvent> nextMoves = phaseRawMoves.get(j);
                    if (!nextMoves.isEmpty()) {
                        end = Math.max(end, nextMoves.get(0).elapsedMs - 1);
                        hasNextGroup = true;
                        break;
                    }
                }
                if (!hasNextGroup) {
                    end = Integer.MAX_VALUE;
                }
                if (gyro.elapsedMs >= start && gyro.elapsedMs <= end) {
                    target = i;
                    break;
                }
            }
            if (target >= 0) {
                result.get(target).add(gyro);
            }
        }
        return result;
    }

    private static List<RotationEvent> buildInitialRotationEvents(List<GyroEvent> gyroEvents) {
        List<RotationEvent> rotations = new ArrayList<>();
        if (gyroEvents == null || gyroEvents.isEmpty()) {
            return rotations;
        }
        for (GyroEvent gyroEvent : gyroEvents) {
            GyroEvent current = normalized(gyroEvent);
            int[][] orientation = quantizeOrientation(current);
            if (orientation != null) {
                rotations.addAll(toRotationEvents(identityMatrix(), orientation, current.elapsedMs));
                return rotations;
            }
        }
        return rotations;
    }

    private static List<PrettyMove> prependRotationEvents(List<PrettyMove> body, List<RotationEvent> setupRotations) {
        List<PrettyMove> result = new ArrayList<>();
        if (setupRotations != null) {
            for (RotationEvent rotation : setupRotations) {
                pushMove(result, rotation.axis, rotation.pow, -1, -1, rotation.elapsedMs, rotation.elapsedMs);
            }
        }
        if (body != null) {
            result.addAll(body);
        }
        return result;
    }

    private static List<RotationEvent> buildRotationEvents(List<GyroEvent> gyroEvents) {
        List<RotationEvent> rotations = new ArrayList<>();
        if (gyroEvents == null || gyroEvents.isEmpty()) {
            return rotations;
        }
        OrientationState stable = null;
        int[][] candidate = null;
        int candidateCount = 0;
        int candidateStartMs = 0;
        for (GyroEvent gyroEvent : gyroEvents) {
            GyroEvent current = normalized(gyroEvent);
            int[][] orientation = quantizeOrientation(current);
            if (orientation == null) {
                continue;
            }
            if (stable == null) {
                stable = new OrientationState(orientation, current.elapsedMs);
                candidate = orientation;
                candidateCount = 1;
                candidateStartMs = current.elapsedMs;
                continue;
            }
            if (candidate == null || !sameMatrix(candidate, orientation)) {
                candidate = orientation;
                candidateCount = 1;
                candidateStartMs = current.elapsedMs;
            } else {
                candidateCount++;
            }
            if (candidateCount < GYRO_STABLE_SAMPLE_COUNT || sameMatrix(stable.matrix, candidate)) {
                continue;
            }
            List<RotationEvent> path = toRotationEvents(stable.matrix, candidate, candidateStartMs);
            if (!path.isEmpty()) {
                rotations.addAll(path);
                stable = new OrientationState(candidate, candidateStartMs);
            }
        }
        return rotations;
    }

    private static GyroEvent normalized(GyroEvent event) {
        if (event == null) {
            return null;
        }
        double len = Math.sqrt(event.qx * event.qx + event.qy * event.qy + event.qz * event.qz + event.qw * event.qw);
        if (len <= 0) {
            return null;
        }
        return new GyroEvent((float) (event.qx / len), (float) (event.qy / len),
                (float) (event.qz / len), (float) (event.qw / len), event.elapsedMs);
    }

    private static int[][] quantizeOrientation(GyroEvent event) {
        if (event == null) {
            return null;
        }
        double[][] matrix = quaternionToMatrix(event);
        int[][] quantized = new int[3][3];
        boolean[] usedRows = new boolean[3];
        boolean[] usedCols = new boolean[3];
        for (int step = 0; step < 3; step++) {
            int bestRow = -1;
            int bestCol = -1;
            double bestAbs = -1;
            for (int r = 0; r < 3; r++) {
                if (usedRows[r]) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    if (usedCols[c]) {
                        continue;
                    }
                    double abs = Math.abs(matrix[r][c]);
                    if (abs > bestAbs) {
                        bestAbs = abs;
                        bestRow = r;
                        bestCol = c;
                    }
                }
            }
            if (bestRow < 0 || bestCol < 0) {
                return null;
            }
            usedRows[bestRow] = true;
            usedCols[bestCol] = true;
            quantized[bestRow][bestCol] = matrix[bestRow][bestCol] >= 0 ? 1 : -1;
        }
        if (determinant(quantized) < 0) {
            return null;
        }
        double score = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                score += matrix[r][c] * quantized[r][c];
            }
        }
        return score >= ORIENTATION_MIN_SCORE ? quantized : null;
    }

    private static double[][] quaternionToMatrix(GyroEvent event) {
        double x = event.qx;
        double y = event.qy;
        double z = event.qz;
        double w = event.qw;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yz = y * z;
        double wx = w * x;
        double wy = w * y;
        double wz = w * z;
        return new double[][] {
                {1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy)},
                {2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx)},
                {2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)}
        };
    }

    private static List<RotationEvent> toRotationEvents(int[][] from, int[][] to, int elapsedMs) {
        List<RotationEvent> empty = new ArrayList<>();
        if (sameMatrix(from, to)) {
            return empty;
        }
        List<RotationPathNode> queue = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        queue.add(new RotationPathNode(from, new ArrayList<RotationEvent>()));
        seen.add(matrixKey(from));
        for (int index = 0; index < queue.size(); index++) {
            RotationPathNode node = queue.get(index);
            if (node.path.size() >= 4) {
                continue;
            }
            for (int[] move : ROTATION_SEARCH_MOVES) {
                int[][] next = multiply(rotationMatrixFor(move[0], move[1]), node.matrix);
                if (seen.contains(matrixKey(next))) {
                    continue;
                }
                List<RotationEvent> path = new ArrayList<>(node.path);
                path.add(new RotationEvent(move[0], move[1], elapsedMs));
                if (sameMatrix(next, to)) {
                    return path;
                }
                queue.add(new RotationPathNode(next, path));
                seen.add(matrixKey(next));
            }
        }
        return empty;
    }

    private static final int[][] ROTATION_SEARCH_MOVES = {
            {9, 0}, {9, 2}, {10, 0}, {10, 2}, {11, 0}, {11, 2}
    };

    private static class RotationPathNode {
        final int[][] matrix;
        final List<RotationEvent> path;

        RotationPathNode(int[][] matrix, List<RotationEvent> path) {
            this.matrix = matrix;
            this.path = path;
        }
    }

    private static int[][] rotationMatrixFor(int axis, int pow) {
        int sign = pow == 2 ? -1 : 1;
        if (axis == 0) {
            return sign > 0
                    ? new int[][] {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}}
                    : new int[][] {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
        } else if (axis == 1) {
            return sign > 0
                    ? new int[][] {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}}
                    : new int[][] {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}};
        } else if (axis == 9) {
            return rotationMatrixFor(0, pow);
        } else if (axis == 10) {
            return rotationMatrixFor(1, pow);
        } else {
            return sign > 0
                    ? new int[][] {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}}
                    : new int[][] {{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}};
        }
    }

    private static int[][] identityMatrix() {
        return new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    }

    private static String matrixKey(int[][] matrix) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                sb.append(matrix[r][c]).append(',');
            }
        }
        return sb.toString();
    }

    private static int[][] multiply(int[][] a, int[][] b) {
        int[][] result = new int[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int value = 0;
                for (int k = 0; k < 3; k++) {
                    value += a[r][k] * b[k][c];
                }
                result[r][c] = value;
            }
        }
        return result;
    }

    private static int[][] transpose(int[][] matrix) {
        int[][] result = new int[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                result[r][c] = matrix[c][r];
            }
        }
        return result;
    }

    private static boolean sameMatrix(int[][] a, int[][] b) {
        if (a == null || b == null) {
            return false;
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (a[r][c] != b[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int determinant(int[][] matrix) {
        return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
    }

    private static List<PrettyMove> compactMoveSequence(List<PrettyMove> moves, boolean hasGyro) {
        if (moves == null || moves.size() < 2) {
            return moves;
        }
        List<List<PrettyMove>> groups = new ArrayList<>();
        for (PrettyMove move : moves) {
            PrettyMove copy = copyMove(move);
            if (groups.isEmpty()) {
                List<PrettyMove> group = new ArrayList<>();
                group.add(copy);
                groups.add(group);
                continue;
            }
            List<PrettyMove> lastGroup = groups.get(groups.size() - 1);
            PrettyMove last = lastGroup.get(lastGroup.size() - 1);
            if (canCompactTogether(last, copy)) {
                lastGroup.add(copy);
            } else {
                List<PrettyMove> group = new ArrayList<>();
                group.add(copy);
                groups.add(group);
            }
        }
        List<PrettyMove> result = new ArrayList<>();
        for (List<PrettyMove> group : groups) {
            result.addAll(compactGroup(group, hasGyro));
        }
        return dropRotationsNearSliceMoves(result);
    }

    private static List<PrettyMove> compactGroup(List<PrettyMove> group, boolean hasGyro) {
        List<PrettyMove> working = new ArrayList<>();
        for (PrettyMove move : group) {
            working.add(copyMove(move));
        }
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            for (int i = 0; i < working.size(); i++) {
                for (int j = i + 1; j < working.size(); j++) {
                    CompactResult compact = combineMoves(working.get(i), working.get(j), hasGyro);
                    if (compact == null) {
                        continue;
                    }
                    if (compact.baseMove == null) {
                        working.remove(j);
                        working.remove(i);
                    } else if (compact.remainingMove == null) {
                        working.set(i, compact.baseMove);
                        working.remove(j);
                    } else {
                        working.set(i, compact.baseMove);
                        working.set(j, compact.remainingMove);
                    }
                    changed = true;
                    break;
                }
                if (changed) {
                    break;
                }
            }
            guard++;
        } while (changed && guard < 100);
        sortMovesByTime(working);
        List<PrettyMove> result = new ArrayList<>();
        for (PrettyMove move : working) {
            if (move.pow >= 0) {
                pushMove(result, move.axis, move.pow, move.startRawIndex, move.endRawIndex, move.startMs, move.endMs);
            }
        }
        return result;
    }

    private static CompactResult combineMoves(PrettyMove first, PrettyMove second, boolean hasGyro) {
        if (first == null || second == null || !sameAxis(first, second)) {
            return null;
        }
        int gap = Math.abs(second.startMs - first.startMs);
        CompactResult sameSlice = combineSameSlice(first, second);
        if (sameSlice != null) {
            return sameSlice;
        }
        CompactResult wide = combineOuterAndRotation(first, second, gap);
        if (wide != null) {
            return wide;
        }
        CompactResult sliceFromWide = combineWideAndOuter(first, second, gap);
        if (sliceFromWide != null) {
            return sliceFromWide;
        }
        return combineOppositeOuterSlices(first, second, gap, hasGyro);
    }

    private static CompactResult combineSameSlice(PrettyMove first, PrettyMove second) {
        if (first.axis != second.axis) {
            return null;
        }
        int pow = addPows(first.pow, second.pow);
        PrettyMove base = copyMove(first);
        base.pow = pow;
        base.startMs = Math.min(first.startMs, second.startMs);
        base.endMs = Math.max(first.endMs, second.endMs);
        base.startRawIndex = minRawIndex(first.startRawIndex, second.startRawIndex);
        base.endRawIndex = Math.max(first.endRawIndex, second.endRawIndex);
        return new CompactResult(pow < 0 ? null : base, null);
    }

    private static CompactResult combineOuterAndRotation(PrettyMove first, PrettyMove second, int gap) {
        PrettyMove outer = isFaceMove(first) ? first : isFaceMove(second) ? second : null;
        PrettyMove rotation = first.isRotation() ? first : second.isRotation() ? second : null;
        if (outer == null || rotation == null || gap >= OUTER_ROTATION_WIDE_STRICT_MS) {
            return null;
        }
        int wideAxis = toWideAxis(outer.axis, rotation.axis);
        if (wideAxis < 0 || !hasSameDirection(outer.pow, rotation.pow)) {
            return null;
        }
        PrettyMove wide = mergedMove(wideAxis, outer.pow, first, second);
        return new CompactResult(wide, null);
    }

    private static CompactResult combineWideAndOuter(PrettyMove first, PrettyMove second, int gap) {
        PrettyMove wide = isWideMove(first) ? first : isWideMove(second) ? second : null;
        PrettyMove outer = isFaceMove(first) ? first : isFaceMove(second) ? second : null;
        if (wide == null || outer == null || gap >= OUTER_ROTATION_WIDE_STRICT_MS || sameRotationDirection(wide, outer)) {
            return null;
        }
        int sliceAxis = sliceFromWideAndOuter(wide.axis, outer.axis);
        if (sliceAxis < 0) {
            return null;
        }
        int slicePow = powFromSigned(signedPow(wide.pow) * sliceDirectionForWide(wide.axis));
        int remainingSigned = signedPow(wide.pow) + signedPow(outer.pow);
        PrettyMove slice = mergedMove(sliceAxis, slicePow, first, second);
        PrettyMove remaining = remainingSigned == 0 ? null : copyWithPow(
                Math.abs(signedPow(wide.pow)) >= Math.abs(signedPow(outer.pow)) ? wide : outer,
                powFromSigned(remainingSigned));
        return new CompactResult(slice, remaining);
    }

    private static CompactResult combineOppositeOuterSlices(PrettyMove first, PrettyMove second, int gap, boolean hasGyro) {
        if (!isFaceMove(first) || !isFaceMove(second) || first.axis == second.axis || first.axis % 3 != second.axis % 3) {
            return null;
        }
        if (gap > SLICE_COMBO_WINDOW_MS) {
            return null;
        }
        if (first.pow + second.pow != 2) {
            return null;
        }
        int axisM = first.axis % 3;
        int powM = (first.pow - 1) * new int[] {1, 1, -1, -1, -1, 1}[first.axis] + 1;
        PrettyMove slice = mergedMove(axisM + 6, powM, first, second);
        return new CompactResult(slice, null);
    }

    private static boolean canCompactTogether(PrettyMove previous, PrettyMove current) {
        if (previous == null || current == null || !sameAxis(previous, current)) {
            return false;
        }
        return current.startMs - previous.startMs < COMPACT_GROUP_WINDOW_MS;
    }

    private static boolean sameAxis(PrettyMove first, PrettyMove second) {
        return axisGroup(first.axis) == axisGroup(second.axis);
    }

    private static int axisGroup(int axis) {
        switch (axis) {
            case 0:
            case 3:
            case 6:
            case 10:
            case 12:
            case 15:
                return 0;
            case 1:
            case 4:
            case 7:
            case 9:
            case 13:
            case 16:
                return 1;
            case 2:
            case 5:
            case 8:
            case 11:
            case 14:
            case 17:
                return 2;
            default:
                return -1;
        }
    }

    private static boolean sameRotationDirection(PrettyMove first, PrettyMove second) {
        return effectiveDirection(first) == effectiveDirection(second);
    }

    private static int effectiveDirection(PrettyMove move) {
        return signedPow(move.pow) * (isDirectionReversed(move.axis) ? -1 : 1);
    }

    private static boolean isDirectionReversed(int axis) {
        return axis == 3 || axis == 4 || axis == 5 || axis == 6 || axis == 7 || axis == 17;
    }

    private static int signedPow(int pow) {
        if (pow == 1) {
            return 2;
        }
        return pow == 2 ? -1 : 1;
    }

    private static int powFromSigned(int signed) {
        int mod = signed % 4;
        if (mod < 0) {
            mod += 4;
        }
        if (mod == 0) {
            return -1;
        }
        return mod == 1 ? 0 : mod == 2 ? 1 : 2;
    }

    private static int addPows(int first, int second) {
        return powFromSigned(signedPow(first) + signedPow(second));
    }

    private static PrettyMove mergedMove(int axis, int pow, PrettyMove first, PrettyMove second) {
        return new PrettyMove(axis, pow,
                minRawIndex(first.startRawIndex, second.startRawIndex),
                Math.max(first.endRawIndex, second.endRawIndex),
                Math.min(first.startMs, second.startMs),
                Math.max(first.endMs, second.endMs));
    }

    private static PrettyMove copyMove(PrettyMove move) {
        return new PrettyMove(move.axis, move.pow, move.startRawIndex, move.endRawIndex, move.startMs, move.endMs);
    }

    private static PrettyMove copyWithPow(PrettyMove move, int pow) {
        PrettyMove copy = copyMove(move);
        copy.pow = pow;
        return copy;
    }

    private static int minRawIndex(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static void sortMovesByTime(List<PrettyMove> moves) {
        for (int i = 1; i < moves.size(); i++) {
            PrettyMove move = moves.get(i);
            int j = i - 1;
            while (j >= 0 && moves.get(j).startMs > move.startMs) {
                moves.set(j + 1, moves.get(j));
                j--;
            }
            moves.set(j + 1, move);
        }
    }

    private static boolean isWideMove(PrettyMove move) {
        return move.axis >= 12 && move.axis <= 17;
    }

    private static int sliceFromWideAndOuter(int wideAxis, int outerAxis) {
        if ((wideAxis == 13 && outerAxis == 1) || (wideAxis == 16 && outerAxis == 4)) {
            return 7;
        }
        if ((wideAxis == 12 && outerAxis == 0) || (wideAxis == 15 && outerAxis == 3)) {
            return 6;
        }
        if ((wideAxis == 14 && outerAxis == 2) || (wideAxis == 17 && outerAxis == 5)) {
            return 8;
        }
        return -1;
    }

    private static int sliceDirectionForWide(int wideAxis) {
        switch (wideAxis) {
            case 13: // r
            case 12: // u
            case 17: // b
                return -1;
            default:
                return 1;
        }
    }

    private static class CompactResult {
        final PrettyMove baseMove;
        final PrettyMove remainingMove;

        CompactResult(PrettyMove baseMove, PrettyMove remainingMove) {
            this.baseMove = baseMove;
            this.remainingMove = remainingMove;
        }
    }

    private static List<PrettyMove> dropRotationsNearSliceMoves(List<PrettyMove> moves) {
        if (moves == null || moves.size() < 2) {
            return moves;
        }
        List<PrettyMove> result = new ArrayList<>();
        for (PrettyMove move : moves) {
            if (move.isRotation() && hasNearbySliceMove(moves, move)) {
                continue;
            }
            result.add(move);
        }
        return result;
    }

    private static boolean hasNearbySliceMove(List<PrettyMove> moves, PrettyMove rotation) {
        for (PrettyMove move : moves) {
            if (move == rotation || !isSliceMove(move)) {
                continue;
            }
            if (Math.abs(rotation.startMs - move.endMs) <= WIDE_COMBO_WINDOW_MS
                    || Math.abs(move.startMs - rotation.endMs) <= WIDE_COMBO_WINDOW_MS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSliceMove(PrettyMove move) {
        return move.axis >= 6 && move.axis <= 8;
    }

    private static boolean hasSameDirection(int facePow, int rotationPow) {
        return facePow == rotationPow;
    }

    private static boolean isFaceMove(PrettyMove move) {
        return move.axis >= 0 && move.axis < 6;
    }

    private static int toWideAxis(int faceAxis, int rotationAxis) {
        if (faceAxis < 0 || faceAxis >= 6) {
            return -1;
        }
        if ((faceAxis == 1 || faceAxis == 4) && rotationAxis == 9) {
            return faceAxis == 1 ? 13 : 16;
        }
        if ((faceAxis == 0 || faceAxis == 3) && rotationAxis == 10) {
            return faceAxis == 0 ? 12 : 15;
        }
        if ((faceAxis == 2 || faceAxis == 5) && rotationAxis == 11) {
            return faceAxis == 2 ? 14 : 17;
        }
        return -1;
    }

    private static int getCf4opProgress(String facelet) {
        List<String> variants = Utils.getAxisOrientationVariants(facelet);
        if (variants.isEmpty()) {
            variants.add(facelet);
        }
        int minProgress = 99;
        for (String variant : variants) {
            int progress = getCf4opProgressForAxis(variant);
            if (progress < minProgress) {
                minProgress = progress;
            }
        }
        return minProgress == 99 ? 7 : minProgress;
    }

    private static int getCf4opProgressForAxis(String facelet) {
        if (isUnsolvedForMask(facelet, CROSS_MASK)) {
            return 7;
        } else if (isUnsolvedForMask(facelet, F2L_MASK)) {
            return 2
                    + (isUnsolvedForMask(facelet, F2L1_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L2_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L3_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L4_MASK) ? 1 : 0);
        } else if (isUnsolvedForMask(facelet, OLL_MASK)) {
            return 2;
        } else if (isUnsolvedForMask(facelet, SOLVED_MASK)) {
            return 1;
        }
        return 0;
    }

    private static boolean isUnsolvedForMask(String facelet, int[][] mask) {
        return !isSolvedForMask(facelet, mask);
    }

    private static boolean isSolvedForMask(String facelet, int[][] mask) {
        if (isEmpty(facelet) || facelet.length() < 54) {
            return false;
        }
        for (int[] equ : mask) {
            if (equ.length == 0) {
                continue;
            }
            char color = facelet.charAt(equ[0]);
            for (int i = 1; i < equ.length; i++) {
                if (facelet.charAt(equ[i]) != color) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] toEqus(String facelet) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < facelet.length(); i++) {
            char color = facelet.charAt(i);
            if (color == '-') {
                continue;
            }
            List<Integer> indices = new ArrayList<>();
            for (int j = i; j < facelet.length(); j++) {
                if (facelet.charAt(j) == color) {
                    indices.add(j);
                }
            }
            if (indices.size() > 1) {
                int[] equ = new int[indices.size()];
                for (int j = 0; j < indices.size(); j++) {
                    equ[j] = indices.get(j);
                }
                result.add(equ);
            }
            facelet = facelet.replace(color, '-');
        }
        return result.toArray(new int[result.size()][]);
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static int[] rotateCenter(int[] center, int axisM) {
        int[] rotated = new int[6];
        for (int c = 0; c < 6; c++) {
            rotated[c] = center[CENTER_ROT[axisM][c]];
        }
        return rotated;
    }

    private static int[] applyPrettyRotation(int[] center, int axis, int pow) {
        int axisM = rotationAxisToCenterAxis(axis);
        if (axisM < 0) {
            return center;
        }
        int[] rotated = center;
        for (int p = 0; p < pow + 1; p++) {
            rotated = rotateCenter(rotated, axisM);
        }
        return rotated;
    }

    private static int rotationAxisToCenterAxis(int axis) {
        if (axis == 10) {
            return 0;
        } else if (axis == 9) {
            return 1;
        } else if (axis == 11) {
            return 2;
        }
        return -1;
    }

    private static String getMoveName(int axis) {
        if (axis >= 0 && axis < PRETTY_FACES.length()) {
            return String.valueOf(PRETTY_FACES.charAt(axis));
        }
        switch (axis) {
            case 9:
                return "x";
            case 10:
                return "y";
            case 11:
                return "z";
            case 12:
                return "u";
            case 13:
                return "r";
            case 14:
                return "f";
            case 15:
                return "d";
            case 16:
                return "l";
            case 17:
                return "b";
            default:
                return "";
        }
    }

    private static String buildMoveSequence(List<PrettyMove> moves) {
        return joinMoves(moves, 0, moves.size() - 1);
    }

    private static String buildPrettySolve(List<Phase> phases, List<PrettyMove> moves) {
        if (phases.isEmpty()) {
            return buildMoveSequence(moves);
        }
        StringBuilder sb = new StringBuilder();
        for (Phase phase : phases) {
            if (isEmpty(phase.moves)) {
                continue;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(phase.moves)
                    .append(" // ")
                    .append(phase.name);
        }
        if (sb.length() == 0) {
            return buildMoveSequence(moves);
        }
        return sb.toString();
    }

    private String appendSolveStats(String solveText, int solveTimeMs) {
        StringBuilder sb = new StringBuilder();
        if (!isEmpty(solveText)) {
            sb.append(solveText.trim());
            sb.append('\n');
        }
        sb.append("Total: ")
                .append(moveCount)
                .append(" moves");
        sb.append('\n');
        sb.append("TPS: ")
                .append(String.format(Locale.US, "%.1f", solveTimeMs > 0 ? moveCount * 1000f / solveTimeMs : 0f))
                .append(" tps");
        return sb.toString();
    }

    private static String joinMoves(List<PrettyMove> moves, int start, int end) {
        if (moves == null || moves.isEmpty() || end < start) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(end, moves.size() - 1);
        for (int i = safeStart; i <= safeEnd; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(moves.get(i).notation().trim());
        }
        return sb.toString();
    }

    private static int countNonRotationMoves(List<PrettyMove> moves) {
        return countNonRotationMoves(moves, 0, moves.size() - 1);
    }

    private static int countNonRotationMoves(List<PrettyMove> moves, int start, int end) {
        if (moves == null || moves.isEmpty() || end < start) {
            return 0;
        }
        int count = 0;
        int safeEnd = Math.min(end, moves.size() - 1);
        for (int i = Math.max(0, start); i <= safeEnd; i++) {
            if (!moves.get(i).isRotation()) {
                count++;
            }
        }
        return count;
    }

    private static void appendJsonField(StringBuilder sb, String key, String value) {
        sb.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
