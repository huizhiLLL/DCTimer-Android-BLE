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
    <img src="website/assets/web1.svg" alt="DCTimer-BLE timer screen" width="360" />
    <img src="website/assets/web3.svg" alt="DCTimer-BLE feature improvements" width="360" />
  </p>
</div>

---

## Current Progress

Supported devices:
- `Moyu32`
- `QYSC` / `Tornado V4`
- `GAN` (`v2 / v3 / v4`)
- `QiYi Smart Timer`

Key changes:
- Upgraded to `AndroidX / AGP 8.9.2 / Gradle 8.11.1 / targetSdk 35`
- Database import/export, scramble import/export, and background image selection have been migrated to the system document picker
- The solve entry now supports separate `Smart Cube` / `Bluetooth Timer` modes
- Added 8s/12s voice reminders for WCA inspection mode
- Manual time entry now auto-splits the time, so no extra decimal point is needed
- PB history markers and sorting in the solve list

## Acknowledgements

- [DCTimer-Android](https://github.com/MeigenChou/DCTimer-Android): original DCTimer-Android repository
- [cstimer](https://github.com/cs0x7f/cstimer): smart cube protocol reference
- [qiyi_smartcube_protocol](https://codeberg.org/Flying-Toast/qiyi_smartcube_protocol): QiYi smart protocol reference
- [CubicTimer](https://github.com/hato-ya/CubicTimer): QiYi Smart Timer integration reference
- [Soda](https://space.bilibili.com/400839068): provided the QiYi smart cube test hardware
- [Visionary](https://space.bilibili.com/674586122): helped test GAN smart cube support
- [Codex](https://github.com/codex): development partner

## License

GPLv3
