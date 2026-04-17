package com.dctimer.model;

import java.util.List;

public class BattleRoomUiModel {
    private final String roomId;
    private final String roomName;
    private final String roomModeText;
    private final String roomRoundText;
    private final String scrambleText;
    private final String timerText;
    private final String statusText;
    private final String summaryText;
    private final String detailTitle;
    private final String detailRule;
    private final List<RoundPlayerItem> roundPlayers;

    public BattleRoomUiModel(String roomId, String roomName, String roomModeText,
                             String roomRoundText, String scrambleText, String timerText,
                             String statusText, String summaryText, String detailTitle,
                             String detailRule, List<RoundPlayerItem> roundPlayers) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomModeText = roomModeText;
        this.roomRoundText = roomRoundText;
        this.scrambleText = scrambleText;
        this.timerText = timerText;
        this.statusText = statusText;
        this.summaryText = summaryText;
        this.detailTitle = detailTitle;
        this.detailRule = detailRule;
        this.roundPlayers = roundPlayers;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getRoomModeText() {
        return roomModeText;
    }

    public String getRoomRoundText() {
        return roomRoundText;
    }

    public String getScrambleText() {
        return scrambleText;
    }

    public String getTimerText() {
        return timerText;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getDetailTitle() {
        return detailTitle;
    }

    public String getDetailRule() {
        return detailRule;
    }

    public List<RoundPlayerItem> getRoundPlayers() {
        return roundPlayers;
    }

    public static class RoundPlayerItem {
        private final String playerName;
        private final String resultText;
        private final int resultColor;

        public RoundPlayerItem(String playerName, String resultText, int resultColor) {
            this.playerName = playerName;
            this.resultText = resultText;
            this.resultColor = resultColor;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getResultText() {
            return resultText;
        }

        public int getResultColor() {
            return resultColor;
        }
    }
}
