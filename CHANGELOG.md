# Changelog

All notable changes to Ace5Ultra-PerformanceKit are documented here.
The format is based on Keep a Changelog, and the project follows Semantic Versioning.
The module `versionCode` is a monotonic integer derived as `MAJOR*10000 + MINOR*100 + PATCH`.

## [1.0.0] - 2026-10-06

### Added
- Root performance module `ace5ultra_perfkit` for the OnePlus Ace 5 Ultra, flashable in
  SukiSU Ultra, BakaSU and KernelSU managers.
- Root-side controller (`perfctl`) and adaptive daemon (`perfd`) with a hardened,
  root-only local command/response channel.
- Five scheduling profiles: Powersave, Balanced, Performance, Game, and Adaptive
  (load / temperature / foreground-app aware).
- Data-driven node discovery (`capabilities.json`); every write is existence-checked and
  logged, with automatic original-value snapshot and full restore on disable/uninstall.
- Jetpack Compose management app with a live 3–5 second dashboard: per-core frequency and
  utilization history, CPU policies/clusters, memory and swap/ZRAM, GPU, thermal, battery.
- Liquid-glass UI with frosted blur (RenderEffect on Android 12+, graceful fallback), a
  floating blurred bottom bar, predictive back gestures and edge-to-edge layout.
- In-app and in-manager over-the-air updates via `release/update.json`.
- Build and one-command release scripts plus a tag-based GitHub Actions workflow.
