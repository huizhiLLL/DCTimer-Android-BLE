<h4 align="right">English | <strong><a href="README.md">简体中文</a></strong></h4>

<div align="center">
  <img src=".github/assets/dctimer-logo.png" alt="DCTimer-BLE logo" width="128" height="128" />

  <h1>DCTimer-BLE</h1>

  <p>
    A speedcubing timer based on DCTimer-Android, with support for smart cubes and the QiYi Smart Timer
  </p>

  <p>
    <img alt="Android" src="https://img.shields.io/badge/Android-targetSdk%2035-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
    <img alt="Java" src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
    <img alt="Gradle" src="https://img.shields.io/badge/Gradle-8.11.1-02303A?style=for-the-badge&logo=gradle&logoColor=white" />
  </p>

  <p>
    <img src="website/assets/web1.svg" alt="DCTimer-BLE timer screen" height="280" />
    <img src="website/assets/web3.svg" alt="DCTimer-BLE feature improvements" height="280" />
  </p>
</div>

---

## Download

- [GitHub Releases](https://github.com/huizhiLLL/DCTimer-Android-BLE/releases/latest)
- [Official direct download](https://dctimer.huizhi.ink/assets/DCTimer-BLE-v2.2.4.apk)

> DCTimer-BLE uses a different package name from the original DCTimer, so it will not conflict during installation
> It is compatible with the original data format. Export data from the original DCTimer and import it into DCTimer-BLE to migrate your data.

## Features

- Compatible with mainstream smart cube brands
- Draggable real-time 3D rendering for smart cubes
- Carefully optimized smart scramble guidance and correction flow
- Fast connection, with no manual MAC address entry required. From app launch to connected, it usually takes only 4-6 seconds.

## Support

- `Moyu32` (MoYu smart cube)
- `QYSC` / `Tornado V4` (QiYi smart cube and Tornado series)
- `GAN` (`v2 / v3 / v4`) (GAN smart cube)
- `QiYi Smart Timer` (QiYi smart timer)

## Improvements

- Upgraded to `AndroidX / AGP 8.9.2 / Gradle 8.11.1 / targetSdk 35` for better stability on newer Android devices
- Database import/export, scramble import/export, and background image selection have been migrated to the system document picker
- The solve entry now supports separate `Smart Cube` / `Bluetooth Timer` modes
- Added 8s/12s voice reminders for WCA inspection mode
- Manual time entry now auto-splits the time, so no extra decimal point is needed
- PB history markers and sorting in the solve list

## Acknowledgements

- [DCTimer-Android](https://github.com/MeigenChou/DCTimer-Android): original DCTimer-Android repository
- [cstimer](https://github.com/cs0x7f/cstimer): smart cube protocol reference
- [qiyi_smartcube_protocol](https://codeberg.org/Flying-Toast/qiyi_smartcube_protocol): smart cube protocol reference
- [CubicTimer](https://github.com/hato-ya/CubicTimer): QiYi Smart Timer integration reference
- [Miaoyan](https://miaoyan.app): official website design reference
- [Codex](https://github.com/codex): development partner

---

- [Soda](https://space.bilibili.com/400839068): provided QiYi and Tornado smart cube test hardware
- [Visionary](https://space.bilibili.com/674586122): GAN smart cube testing

If this project is helpful to you, I hope you can give it a star, which will be the motivation for my future maintenance ~

## License

GPLv3
