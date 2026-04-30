package com.dctimer.model;

import com.dctimer.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class SmartCubeSolveReconstruction {
    private static final int SLICE_COMBO_WINDOW_MS = 100;
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
        List<MoveEvent> safeRawMoves = rawMoves == null ? new ArrayList<MoveEvent>() : new ArrayList<>(rawMoves);
        PhaseBuildResult phaseResult = buildPhases(startFacelet, safeRawMoves);
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
        appendJsonField(sb, "version", "1");
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
            return String.valueOf(PRETTY_FACES.charAt(axis)) + SUFFIXES.charAt(pow);
        }

        boolean isRotation() {
            return false;
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
        List<PrettyMove> ret = new ArrayList<>();
        int[] center = {0, 1, 2, 3, 4, 5};
        for (int i = 0; i < rawMoves.size(); i++) {
            MoveEvent current = rawMoves.get(i);
            int axis = indexOf(center, current.move / 3);
            int pow = current.move % 3;
            if (axis < 0) {
                continue;
            }
            if (i < rawMoves.size() - 1) {
                MoveEvent next = rawMoves.get(i + 1);
                int gap = next.elapsedMs - current.elapsedMs;
                int axis2 = indexOf(center, next.move / 3);
                int pow2 = next.move % 3;
                if (gap <= SLICE_COMBO_WINDOW_MS
                        && axis2 >= 0
                        && axis != axis2
                        && axis % 3 == axis2 % 3
                        && pow + pow2 == 2) {
                    int axisM = axis % 3;
                    int powM = (pow - 1) * new int[] {1, 1, -1, -1, -1, 1}[axis] + 1;
                    pushMove(ret, axisM + 6, powM, i, i + 1, current.elapsedMs, next.elapsedMs);
                    for (int p = 0; p < powM + 1; p++) {
                        center = rotateCenter(center, axisM);
                    }
                    i++;
                    continue;
                }
            }
            pushMove(ret, axis, pow, i, i, current.elapsedMs, current.elapsedMs);
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

    private static PhaseBuildResult buildPhases(String startFacelet, List<MoveEvent> rawMoves) {
        if (rawMoves.isEmpty() || isEmpty(startFacelet)) {
            return new PhaseBuildResult(reconstructMoves(rawMoves), createEmptyPhases());
        }
        CubieCube cube = new CubieCube();
        if (Util.toCubieCube(startFacelet, cube) != 0) {
            return new PhaseBuildResult(reconstructMoves(rawMoves), createEmptyPhases());
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
        List<List<PrettyMove>> phasePrettyMoves = reconstructMoveGroups(phaseRawMoves);
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
        List<List<PrettyMove>> result = new ArrayList<>();
        int[] center = {0, 1, 2, 3, 4, 5};
        for (List<MoveEvent> rawMoveGroup : rawMoveGroups) {
            List<PrettyMove> ret = new ArrayList<>();
            for (int i = 0; i < rawMoveGroup.size(); i++) {
                MoveEvent current = rawMoveGroup.get(i);
                int axis = indexOf(center, current.move / 3);
                int pow = current.move % 3;
                if (axis < 0) {
                    continue;
                }
                if (i < rawMoveGroup.size() - 1) {
                    MoveEvent next = rawMoveGroup.get(i + 1);
                    int gap = next.elapsedMs - current.elapsedMs;
                    int axis2 = indexOf(center, next.move / 3);
                    int pow2 = next.move % 3;
                    if (gap <= SLICE_COMBO_WINDOW_MS
                            && axis2 >= 0
                            && axis != axis2
                            && axis % 3 == axis2 % 3
                            && pow + pow2 == 2) {
                        int axisM = axis % 3;
                        int powM = (pow - 1) * new int[] {1, 1, -1, -1, -1, 1}[axis] + 1;
                        pushMove(ret, axisM + 6, powM, i, i + 1, current.elapsedMs, next.elapsedMs);
                        for (int p = 0; p < powM + 1; p++) {
                            center = rotateCenter(center, axisM);
                        }
                        i++;
                        continue;
                    }
                }
                pushMove(ret, axis, pow, i, i, current.elapsedMs, current.elapsedMs);
            }
            result.add(ret);
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
