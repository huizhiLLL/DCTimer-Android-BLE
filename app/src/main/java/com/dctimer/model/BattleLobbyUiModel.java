package com.dctimer.model;

import java.util.List;

public class BattleLobbyUiModel {
    private final String nicknameText;
    private final String roomCountText;
    private final String lobbyTipText;
    private final List<RoomItem> rooms;

    public BattleLobbyUiModel(String nicknameText, String roomCountText,
                              String lobbyTipText, List<RoomItem> rooms) {
        this.nicknameText = nicknameText;
        this.roomCountText = roomCountText;
        this.lobbyTipText = lobbyTipText;
        this.rooms = rooms;
    }

    public String getNicknameText() {
        return nicknameText;
    }

    public String getRoomCountText() {
        return roomCountText;
    }

    public String getLobbyTipText() {
        return lobbyTipText;
    }

    public List<RoomItem> getRooms() {
        return rooms;
    }

    public static class RoomItem {
        private final String roomId;
        private final String roomName;
        private final String roomMeta;

        public RoomItem(String roomId, String roomName, String roomMeta) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.roomMeta = roomMeta;
        }

        public String getRoomId() {
            return roomId;
        }

        public String getRoomName() {
            return roomName;
        }

        public String getRoomMeta() {
            return roomMeta;
        }
    }
}
