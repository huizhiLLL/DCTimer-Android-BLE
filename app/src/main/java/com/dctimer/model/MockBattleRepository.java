package com.dctimer.model;

import java.util.ArrayList;
import java.util.List;

public class MockBattleRepository implements BattleRepository {
    public static final String ROOM_ID_A = "mock_room_a";
    public static final String ROOM_ID_B = "mock_room_b";
    public static final String ROOM_ID_C = "mock_room_c";
    public static final String ROOM_ID_CREATED = "mock_room_created";

    private static final MockBattleRepository INSTANCE = new MockBattleRepository();

    public static MockBattleRepository getInstance() {
        return INSTANCE;
    }

    private MockBattleRepository() {
    }

    @Override
    public BattleLobbyUiModel getLobbyUiModel() {
        List<BattleLobbyUiModel.RoomItem> rooms = new ArrayList<>();
        rooms.add(new BattleLobbyUiModel.RoomItem(
                ROOM_ID_A,
                "MoYu POC",
                "2/4 人 · 第 3 轮 · 无密码"
        ));
        rooms.add(new BattleLobbyUiModel.RoomItem(
                ROOM_ID_B,
                "QiYi Round 1",
                "3/6 人 · 第 1 轮 · 需要密码"
        ));
        rooms.add(new BattleLobbyUiModel.RoomItem(
                ROOM_ID_C,
                "GAN Sprint",
                "1/4 人 · 等待开始 · 无密码"
        ));
        return new BattleLobbyUiModel(
                "昵称：会枝",
                rooms.size() + " 个房间",
                "MOCK-DATA · 当前由 MockBattleRepository 提供大厅与房间数据",
                rooms
        );
    }

    @Override
    public BattleRoomUiModel getRoomUiModel(String roomId) {
        if (ROOM_ID_B.equals(roomId)) {
            return createRoomB();
        }
        if (ROOM_ID_C.equals(roomId)) {
            return createRoomC();
        }
        if (ROOM_ID_CREATED.equals(roomId)) {
            return createCreatedRoom();
        }
        return createRoomA();
    }

    @Override
    public BattleRoomUiModel getCreatedRoomUiModel() {
        return createCreatedRoom();
    }

    private BattleRoomUiModel createRoomA() {
        List<BattleRoomUiModel.RoundPlayerItem> players = new ArrayList<>();
        players.add(new BattleRoomUiModel.RoundPlayerItem("会枝（你）", "8.41", 0xff202020));
        players.add(new BattleRoomUiModel.RoundPlayerItem("CubeFox", "9.02", 0xff202020));
        players.add(new BattleRoomUiModel.RoundPlayerItem("Nora", "计时中", 0xff757575));
        return new BattleRoomUiModel(
                ROOM_ID_A,
                "MoYu POC",
                "智能魔方",
                "第 2 轮 · 已完成 2/3 · 全员完成后自动切换下一轮",
                "R U2 R' F2 U' R U R' U2 F2 R2 R2 R2 R2 R2 R2 R2",
                "8.41",
                "等待其他玩家完成 (2/3)",
                "点击查看当前轮次详情",
                "当前轮次详情",
                "统一打乱，所有成员完成后自动切换下一轮。",
                players
        );
    }

    private BattleRoomUiModel createRoomB() {
        List<BattleRoomUiModel.RoundPlayerItem> players = new ArrayList<>();
        players.add(new BattleRoomUiModel.RoundPlayerItem("会枝（你）", "未开始", 0xff757575));
        players.add(new BattleRoomUiModel.RoundPlayerItem("Lin", "未开始", 0xff757575));
        players.add(new BattleRoomUiModel.RoundPlayerItem("Tornado", "未开始", 0xff757575));
        return new BattleRoomUiModel(
                ROOM_ID_B,
                "QiYi Round 1",
                "智能魔方",
                "第 1 轮 · 等待房主开始",
                "U2 F2 R2 U' R2 U' B2 R2 U  F' U2 R  D' L2 U' B  L'",
                "--.--",
                "当前轮次未开始",
                "点击查看当前轮次详情",
                "当前轮次详情",
                "首版 POC 仅验证房间静态快照与成员状态展示。",
                players
        );
    }

    private BattleRoomUiModel createRoomC() {
        List<BattleRoomUiModel.RoundPlayerItem> players = new ArrayList<>();
        players.add(new BattleRoomUiModel.RoundPlayerItem("会枝（你）", "12.36", 0xff202020));
        return new BattleRoomUiModel(
                ROOM_ID_C,
                "GAN Sprint",
                "智能魔方",
                "第 4 轮 · 仅 1/4 到场",
                "L2 U' B2 D2 R2 U' B2 D2 F2 U  L2 D' B  R  U' R'",
                "12.36",
                "等待更多玩家进入房间",
                "点击查看当前轮次详情",
                "当前轮次详情",
                "当前房间只有静态成员快照，尚未接入实时同步。",
                players
        );
    }

    private BattleRoomUiModel createCreatedRoom() {
        List<BattleRoomUiModel.RoundPlayerItem> players = new ArrayList<>();
        players.add(new BattleRoomUiModel.RoundPlayerItem("会枝（房主）", "未开始", 0xff757575));
        return new BattleRoomUiModel(
                ROOM_ID_CREATED,
                "我的房间",
                "智能魔方",
                "第 1 轮 · 房主等待其他玩家加入",
                "R2 U2 F2 L2 D2 B2 U  R' U2 F' R2 D' L2 F  U'",
                "--.--",
                "POC：后续接入真实创建房间逻辑",
                "点击查看当前轮次详情",
                "当前轮次详情",
                "当前仍为 mock 房间，用于占位创建房间后的页面流转。",
                players
        );
    }
}
