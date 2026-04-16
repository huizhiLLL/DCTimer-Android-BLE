# DCTimer-Android-BLE

基于 `DCTimer-Android` 二次开发，完善了智能魔方计时功能（也可能加点更好玩的东西）

## 当前进度

已兼容：
- `Moyu32`
- `QYSC` / `Tornado V4`
- `GAN`（`v2 / v3 / v4`）
- `QiYi Smart Timer`

关键改动：
- 升级到 `AndroidX / AGP 8.9.2 / Gradle 8.11.1 / targetSdk 35`
- 导入导出数据库、导入/导出打乱、背景图选择已切换到系统文档选择器（`SAF`）
- 蓝牙硬件入口已拆分为 `智能魔方` / `蓝牙计时器`

## 致谢

- [DCTimer-Android](https://github.com/MeigenChou/DCTimer-Android)：DCTimer-Android 原仓库
- [cstimer](https://github.com/cs0x7f/cstimer)：智能魔方协议参考
- [qiyi_smartcube_protocol](https://codeberg.org/Flying-Toast/qiyi_smartcube_protocol)：奇艺智能协议参考
- [CubicTimer](https://github.com/hato-ya/CubicTimer)：奇艺智能计时器接入参考
- [Soda](https://space.bilibili.com/400839068)：奇艺智能测试魔方来源并且提供了关键想法（多品牌兼容房间对战等）
- [Visionary](https://space.bilibili.com/674586122)：配合测试了 GAN 智能
- [codex](https://github.com/codex)：开发伙伴

## License

GPLv3
