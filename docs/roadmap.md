# DCTimer-Android 路线图与决策备忘

日期：`2026-05-15`

## 当前路线图

### 近期

- 保持文档只维护 `docs/project.md`、`docs/architecture.md`、`docs/roadmap.md` 三个长期开发文档。

### 中期

- 如果需要继续扩展蓝牙计时器品牌，按“独立 timer 协议类 + `SmartTimerProtocol` 分发”的方式接入。

### 暂不推进

- 暂不承诺支持所有蓝牙魔方或所有蓝牙计时器。

## 已收口决策

### Android 底座升级

- 已升级到 `AndroidX + AGP 8.9.2 + Gradle 8.11.1 + JDK 17`。
- 已将 `compileSdk / targetSdk` 提升到 `35`。
- 文件导入导出、打乱导入导出、背景图选择已迁移到 `SAF / Uri`。
- 旧 `FileSelectorDialog`、APK 自更新入口、安装权限与 `FileProvider` 已退出主线。
- BLE 权限链按 Android 新权限模型和定位兼容模式收口。

### Release 打包配置

- release 签名入口已标准化。
- 支持 `key.properties` 和 `-P` 参数两种签名注入方式。
- 未提供签名信息时直接报错，不生成不可分发包。
- 当前版本号基线已进入 `2.2` 正式版。
- 保留 `rel.bat` 作为本地 release 构建快捷入口。
- release APK 输出名固定为 `DCTimer-BLE-v版本号.apk`。

### 智能魔方协议主线

- 智能魔方协议当前覆盖 `MoYu32`、`QiYi / Tornado V4`、`GAN v2 / v3 / v4`。
- 新协议统一收口到 `SmartCubeProtocol`。
- BLE 扫描弹窗以扫描结果直列和连接阶段自动识别为准。
- 智能魔方状态展示以自定义 3D 渲染控件为主；第一阶段采用 `GLSurfaceView + OpenGL ES 2.0`，先覆盖计时页 `3x3` 固定视角、实时状态刷新和基础层转动画，暂不引入 Rajawali。
- 打乱流程以“打乱进度提示 + 偏离纠错 + READY 等待首转”为准，不回退到连接后直接起表。
- `GAN v4` MOVE 通知按 `72-bit` chunk 循环解析；`M / E / S` 快速双层转动不再依赖 `MOVE_HISTORY` 才补齐同包中的第二个转动。
- `GAN v3 / v4` 的 `MOVE_HISTORY` 仅作为丢包兜底，尾部缺失时间戳按可用真实时间戳和本地触发时间估算，避免影响保存成绩。

### 解法重建决策

- `333-smart-cf4op` 分段按 `Cross / F2L 1-4 / OLL / PLL` 展示。
- 分段判定按 `cstimer` 的 `cf4op` 思路收口为 6 轴向进度计算，并先按阶段分桶原始转动再逐段重建。
- `AUF` 默认并入 `PLL`，避免在缺少真实视角事件时产生不稳定判断。
- `100ms` 内对向层组合识别为 `E / M / S`。
- TPS 使用重建后的步数统计，`U2` 与 `E / M / S` 按一步计，`x / y / z` 不计入主 TPS。
- `QiYi / QYSC` 与 `QiYi / Tornado V4` 智能链路已完成真机验证，当前暂无已知问题。
- `QiYi / QYSC` 的状态帧可能提前携带晚于当前帧的 history 步，协议层按时间戳将当前帧步与 future history 步分段处理；future history 步只用于实时状态/3D 更新，不参与打乱偏离累计。
- 打乱完成后、首转起表前的视角变化按“初始化姿态 -> 起手姿态”的最短转体路径插入 `Cross` 开头，作为 setup 前缀。

### 蓝牙计时器决策

- `enterTime` 已从旧的单一“蓝牙设备”语义拆成“智能魔方 / 蓝牙计时器”两条入口。
- `SmartTimerProtocol` 是蓝牙计时器协议分发入口。
- `QiYi Smart Timer` 按独立 `QiyiSmartTimerProtocol` 接入。
- 蓝牙计时器模式不启用应用内 `WCA` 观察和观察提示。
- 智能魔方和蓝牙计时器断联后，应回退普通计时器。

## 文档维护决策

- `docs/project.md` 维护项目现状、当前能力和当前任务。
- `docs/architecture.md` 维护架构边界、协议边界和数据边界。
- `docs/roadmap.md` 维护路线图、已收口决策和暂不推进事项。
- `docs/product/` 只保留一份软件关键信息文档，用于记录名称、当前版本、底层系统和蓝牙设备支持列表。
