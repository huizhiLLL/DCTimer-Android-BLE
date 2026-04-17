package com.dctimer.model;

public interface BattleRepository {
    BattleLobbyUiModel getLobbyUiModel();

    BattleRoomUiModel getRoomUiModel(String roomId);

    BattleRoomUiModel getCreatedRoomUiModel();
}
