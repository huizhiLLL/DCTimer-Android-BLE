# DCTimer-BLE 软件关键信息

日期：`2026-05-11`

## 基本信息

- 软件名称：`DCTimer-BLE`
- 当前版本：`2.2 Beta`
- Android 包名：`com.dctimer.ble`
- APK 输出名：`DCTimer-BLE-v2.2 Beta.apk`

## 底层系统支持

- 支持平台：`Android`
- 当前仅维护 Android 版本，暂不维护 iOS、桌面端或 Web 端。
- 构建基线：`AndroidX + AGP 8.9.2 + Gradle 8.11.1 + JDK 17`
- `compileSdk / targetSdk`：`35`
- `minSdk`：`14`

说明：蓝牙扫描、连接和权限表现会受 Android 系统版本与厂商系统影响；当前重点保障 Android 12 / Android 15 上的蓝牙硬件主流程稳定性。

## 蓝牙设备支持

### 智能魔方

- `MoYu32`
- `QiYi / Tornado V4 智能版`
- `GAN v2 / v3 / v4`

### 蓝牙计时器

- `QiYi Smart Timer`

## 当前支持边界

- 当前硬件主线只分为 `智能魔方` 和 `蓝牙计时器` 两类。
- 真机未验证的能力不作为正式支持能力对外承诺。
- 后续如新增设备协议，再同步更新本文件。
