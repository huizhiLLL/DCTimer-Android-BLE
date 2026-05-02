# DCTimer-Android 路线图与决策备忘

日期：`2026-05-02`

## 当前路线图

### 近期

- 完成 `QiYi / Tornado V4` 陀螺仪重建链路真机验证。
- 继续观察智能魔方和蓝牙计时器在 Android 12 / 15 上的扫描、连接、断开与自动计时稳定性。
- 保持文档只维护 `docs/project.md`、`docs/architecture.md`、`docs/roadmap.md` 三个长期开发文档。

### 中期

- 如果线上房间对战正式立项，优先作为智能魔方主线的新产品方向单独设计。
- 如果需要继续扩展蓝牙计时器品牌，按“独立 timer 协议类 + `SmartTimerProtocol` 分发”的方式接入。
- 如果 `MoYu32 / GAN` 后续能提供陀螺仪事件，再考虑扩展真实视角转体和宽层识别。

### 暂不推进

- 暂不恢复旧 `GAN Timer` 分支。
- 暂不承诺支持所有蓝牙魔方或所有蓝牙计时器。
- 暂不把真机未验证的陀螺仪重建能力写成已完成产品能力。
- 暂不继续拆分新的长期 `plan/` 或 `tasks/` 子目录文档。

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
- 当前版本号基线已进入 `2.0 Beta`。
- 保留 `rel.bat` 作为本地 release 构建快捷入口。
- release APK 输出名固定为 `DCTimer-BLE-v版本号.apk`。

### 智能魔方协议主线

- 智能魔方协议不再停留在单一 `MoYu32` 方案，当前覆盖 `MoYu32`、`QiYi / Tornado V4`、`GAN v2 / v3 / v4`。
- 新协议统一收口到 `SmartCubeProtocol`。
- BLE 扫描弹窗以扫描结果直列和连接阶段自动识别为准。
- 智能魔方状态展示以自定义 3D 渲染控件为主。
- 打乱流程以“打乱进度提示 + 偏离纠错 + READY 等待首转”为准，不回退到连接后直接起表。

### 解法重建决策

- `333-smart-cf4op` 分段按 `Cross / F2L 1-4 / OLL / PLL` 展示。
- 分段判定按 `cstimer` 的 `cf4op` 思路收口为 6 轴向进度计算，并先按阶段分桶原始转动再逐段重建。
- `AUF` 默认并入 `PLL`，避免在缺少真实视角事件时产生不稳定判断。
- `100ms` 内对向层组合识别为 `E / M / S`。
- TPS 使用重建后的步数统计，`U2` 与 `E / M / S` 按一步计，`x / y / z` 不计入主 TPS。
- `QiYi / Tornado V4` 陀螺仪识别依赖手动“初始化视角”，未初始化时不参与重建。
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
- `docs/product/` 只维护面向产品展示的信息。
- `docs/other/` 只保留非主线参考资料。
- 不再维护长期 `docs/plan/` 和 `docs/tasks/` 子目录。

