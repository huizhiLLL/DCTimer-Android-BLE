# 在线房间对战 POC UI 骨架

日期：`2026-04-13`

## 目标

先落一版可编译、可挂在主壳中的页面骨架，不接后端、不接真实房间逻辑，只用模拟数据验证：

- 顶部导航新增第 `4` 项
- 在线房间入口页保持贴近当前 `DCTimer` 的工具型 UI 风格
- 房间内计时页单独拆分，主体结构复用首页计时器页面思路：
  - 顶部保留房间与轮次信息
  - 中部仍是打乱 + 计时器主体
  - 底部简要统计区替换为房间简要状态
  - 点击后展开完整成员信息

## 已完成

- [x] 在主壳顶部 `RadioGroup` 中新增第 `4` 个导航项
- [x] 在 `TabHost` 中新增 `tab_battle`
- [x] `tab_battle` 已修正为“房间大厅页”，包含昵称、创建房间、刷新、房间列表
- [x] 新增独立的房间计时页 `BattleRoomActivity`
- [x] 房间计时页主体复用首页计时器结构：房间头部 + 打乱 + 大计时器 + 底部状态卡
- [x] 房间计时页增加“查看完整信息 / 收起完整信息”切换
- [x] 使用模拟数据填充大厅和房间页
- [x] 大厅页与房间页样式已收敛为 `DCTimer` 原生的列表式扁平风格：
  - 纯白底
  - 细分割线
  - 蓝色小标题
  - 纯文本操作
  - 去卡片化
- [x] 大厅页操作布局已进一步收敛：
  - `刷新` 放到房间列表标题行右侧
  - `创建房间` 改为全宽扁平主按钮
  - 房间副信息字号进一步压低
- [x] 房间页交互已进一步收敛：
  - 打乱公式字号和留白增强
  - 底部状态栏改为精简摘要
  - 详情改为底部滑出面板（Bottom Sheet 风格）
  - 玩家成绩明细从主界面移出
- [x] 已把大厅模拟数据抽成独立的 `BattleLobbyUiModel`
- [x] 已把房间页模拟数据抽成独立的 `BattleRoomUiModel`
- [x] 已增加最小 `BattleRepository / MockBattleRepository` 骨架，页面不再直接写死 mock 文本
- [x] 大厅房间入口已能按 `roomId` 进入对应 mock 房间页
- [x] `assembleDebug` 构建通过

## 当前完成度概括

- 当前分支已经完成“可挂主壳、可编译、可演示”的在线房间 `UI POC`，入口、大厅页、房间页和轮次详情面板都已落地。
- 完成度判断应按“UI 骨架”理解，而不是“真实联机功能”理解：
  - 如果按 `UI POC` 计算，当前已基本完成
  - 如果按“可真实创建房间、加入房间、同步状态、完成一轮对战”计算，当前仍处于前期骨架阶段
- 当前价值主要是先把页面结构、视觉风格和主壳挂接方式定下来，后续联机逻辑可以在这套壳子上继续填充，不需要再重做页面层。

## 相关文件

- 主壳布局：
  [app_bar_main.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/layout/app_bar_main.xml)
- 大厅页：
  [fragment_battle.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/layout/fragment_battle.xml)
- 房间页：
  [activity_battle_room.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/layout/activity_battle_room.xml)
- 房间页逻辑：
  [BattleRoomActivity.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/activity/BattleRoomActivity.java)
- `battle` 数据模型：
  [BattleLobbyUiModel.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/model/BattleLobbyUiModel.java)
  [BattleRoomUiModel.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/model/BattleRoomUiModel.java)
- `battle` 数据入口：
  [BattleRepository.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/model/BattleRepository.java)
  [MockBattleRepository.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/model/MockBattleRepository.java)
- 轮次详情面板：
  [dialog_battle_round_detail.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/layout/dialog_battle_round_detail.xml)
  [item_battle_round_player.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/layout/item_battle_round_player.xml)
  [BattleRoundDetailDialog.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/dialog/BattleRoundDetailDialog.java)
- 资源：
  [battle_button_style.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/drawable/battle_button_style.xml)
  [battle_panel_background.xml](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/res/drawable/battle_panel_background.xml)
- 逻辑接入：
  [MainActivity.java](/C:/Users/31691/huizhiL/document/Code/github-dev/DCTimer-Android/app/src/main/java/com/dctimer/activity/MainActivity.java)

## 当前实现范围

当前仅是 UI 骨架和模拟数据：

- 已具备大厅页 -> 房间页 -> 轮次详情的静态演示链路
- 已具备 `UiModel -> Repository -> Activity` 的最小 battle 数据流骨架
- 未接真实房间列表
- 未接真实创建房间弹层
- 未接真实昵称设置弹层
- 未接真实房间详情
- 未接真实 WebSocket 状态
- 未接真实智能魔方联机计时链路
- 未处理战房内自动切换下一轮

## 面向最简单 POC 的后续实现建议

建议先做“最小可跑通链路”，不要一开始就把智能魔方联机计时、自动切轮、完整房态都绑进首版目标。

- [x] 已把大厅模拟数据抽成独立的 `BattleLobbyUiModel`
- [x] 已把房间页模拟数据抽成独立的 `BattleRoomUiModel`
- [x] 已抽出最小 `BattleRepository / MockBattleRepository`，后续可直接替换真实数据源
- [ ] 首个真实 POC 先只打通：大厅拉取房间列表 -> 创建房间 / 进入房间 -> 拉取房间快照
- [ ] 首版房间内先只同步最基本字段：房间名、人数、轮次、打乱、成员状态
- [ ] 首版先不把“真实智能魔方联机计时链路”设为阻塞项，可以先用手动开始 / 提交成绩，等房间状态链稳定后再接入智能魔方
- [ ] 等最小联机链路跑通后，再补 WebSocket 增量同步、自动切下一轮、智能魔方自动起停表
