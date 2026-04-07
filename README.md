# DCTimer-Android-BLE

基于 `DCTimer-Android` 二次开发，完善了智能魔方计时功能（也可能加点更好玩的东西）

## 当前进度

已兼容：
- `Moyu32`
- `QiYi` / `Tornado V4`
- `GAN` 主流智能魔方协议（`v2 / v3 / v4`）

当前交互：
- 蓝牙设备弹窗直接列出扫描结果，连接时自动识别协议
- 旧 `GAN Timer / Giiker / 老 GANi` 逻辑已退出当前扫描连接主链，仅保留代码备份

## 致谢

- [DCTimer-Android](https://github.com/MeigenChou/DCTimer-Android)：DCTimer-Android 原仓库
- [cstimer](https://github.com/cs0x7f/cstimer)：智能魔方协议参考
- [Soda](https://space.bilibili.com/400839068)：奇艺智能测试魔方来源并且提供了关键想法（多品牌兼容房间对战等）

## License

GPLv3
