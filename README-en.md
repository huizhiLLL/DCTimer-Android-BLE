<div align="center">
  <img src=".github/assets/dctimer-logo.png" alt="DCTimer-Android-BLE logo" width="128" height="128" />

  <h1>DCTimer-Android-BLE</h1>

  <p>
    <a href="README.md">中文</a>
    ·
    <strong>English</strong>
  </p>

  <p>
    An Android Bluetooth hardware timer based on DCTimer-Android, focused on smart cubes and QiYi Smart Timer support.
  </p>

  <p>
    <img alt="Android" src="https://img.shields.io/badge/Android-targetSdk%2035-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
    <img alt="Java" src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
    <img alt="Gradle" src="https://img.shields.io/badge/Gradle-8.11.1-02303A?style=for-the-badge&logo=gradle&logoColor=white" />
  </p>
</div>

## Current Progress

Supported devices:
- `Moyu32`
- `QYSC` / `Tornado V4`
- `GAN` (`v2 / v3 / v4`)
- `QiYi Smart Timer`

Key changes:
- Upgraded to `AndroidX / AGP 8.9.2 / Gradle 8.11.1 / targetSdk 35`
- Database import/export, scramble import/export, and background image selection have been migrated to the system document picker
- Bluetooth hardware entry points have been split into `Smart Cube` / `Bluetooth Timer`

## Acknowledgements

- [DCTimer-Android](https://github.com/MeigenChou/DCTimer-Android): original DCTimer-Android repository
- [cstimer](https://github.com/cs0x7f/cstimer): smart cube protocol reference
- [qiyi_smartcube_protocol](https://codeberg.org/Flying-Toast/qiyi_smartcube_protocol): QiYi smart protocol reference
- [CubicTimer](https://github.com/hato-ya/CubicTimer): QiYi Smart Timer integration reference
- [Soda](https://space.bilibili.com/400839068): provided QiYi smart cube test hardware and key ideas, including multi-brand compatibility and room battles
- [Visionary](https://space.bilibili.com/674586122): helped test GAN smart cube support
- [codex](https://github.com/codex): development partner

## License

GPLv3
