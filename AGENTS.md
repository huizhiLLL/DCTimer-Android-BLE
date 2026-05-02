# DCTimer-Android AGENTS

## 项目简介

- 本项目是基于 `DCTimer-Android` 的二次开发版本，当前主方向是完善蓝牙硬件计时能力。
- 当前硬件主线分为两类：
  - `智能魔方`
  - `蓝牙计时器`
- 当前已接入设备：
  - 智能魔方：`MoYu32`、`QiYi / Tornado V4 智能版`、`GAN v2 / v3 / v4`
  - 蓝牙计时器：`QiYi Smart Timer`

## 当前基线

- Android 工程基线：`AndroidX + AGP 8.9.2 + Gradle 8.11.1 + JDK 17`
- `compileSdk / targetSdk`：`35`
- 文件导入导出、打乱导入导出、背景图选择已迁移到 `SAF / Uri`
- 蓝牙硬件入口已拆分为 `智能魔方` / `蓝牙计时器`

## 开发约束

- 开始功能开发、修复或较大改动前，先阅读 `docs/` 下相关文档：
  - `docs/project.md`
  - `docs/architecture.md`
  - `docs/roadmap.md`
- 文档优先级：当前项目代码 > `docs/` 文档 > `ref/` 参考资料。
- 当前重点保障 `3x3` 智能魔方计时主流程，以及 `QiYi Smart Timer` 主链稳定性。
- 智能魔方协议继续沿用“独立协议类 + `BluetoothTools` 扫描/连接/分发”的结构。
- 蓝牙计时器和智能魔方业务链保持分离，不混用页面语义和协议状态。
- 对真机未验证的能力，文档和说明中必须明确标注为“待验证”或“未完成”。
- 优先做最小必要改动，避免为了顺手重构扩大提交面。

