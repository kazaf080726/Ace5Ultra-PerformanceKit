# Changelog

All notable changes to Ace5Ultra-PerformanceKit are documented here.
The format is based on Keep a Changelog, and the project follows Semantic Versioning.
The module `versionCode` is a monotonic integer derived as `MAJOR*10000 + MINOR*100 + PATCH`.

## [1.0.1] - 2026-10-10

### Added
- New **System** profile that restores every tuned node to stock and puts the daemon in
  monitor-only mode, returning scheduling control to ColorOS adaptive mode.
- **Mount subsystem** (`perfmount.sh`): at flash and boot it detects an existing compatible
  mount provider module and piggybacks it, otherwise creates its own non-persistent local
  mounts; a world-readable static topology is exposed under `/dev/perfkit` so the app reads
  immutable hardware info without a root round-trip, and node discovery/thermal/GPU/top
  aggregations are cached to speed up polls.
- **Dual update channels**: `release/update.json` (module channel — a module release now
  bundles the matching APK and updates module **and** app together after the manager flash)
  and the new `release/app-update.json` (app-only channel — an app release updates just the
  APK in place). The Updater screen shows which channel applies and the from→to versions.
- Full localisation: English, 简体中文, 繁體中文, with an in-app language switcher
  (per-app language API); more languages are drop-in.
- New neon pulse launcher icon (adaptive + legacy).
- Modern Scene-style redesign: ring/donut Memory and GPU cards, a CPU card with top
  processes and per-core mini bar charts with large frequencies and min~max ranges, plus
  compact battery/device cards — fused with the liquid-glass surfaces, floating blurred
  bottom bar and predictive back.
- Extended `status --json`: mount mode, aggregate CPU load, top-5 CPU processes,
  SwapCached / RAM / swap percentages, a full battery object (power W, voltage, temp,
  current), GPU MHz/load and the active profile.

### Changed
- All profiles except **Powersave** now keep the CPU at peak dispatch state (max frequency
  unlocked, boost on, fastest up-ramp); Balanced keeps the stock EAS governor while
  Performance/Game pin the performance governor.
- **Game (Extreme)** pushes CPU, GPU DVFS floor, scheduler/boost and I/O latency to the
  limit and makes a best-effort, reversible attempt to set the peak display refresh rate;
  thermal safety is never disabled.
- One-command release script now renders both OTA manifests, builds and stages canonical
  named APKs, and bundles the matching APK into the module zip.

### Fixed
- Module `status --json` emitted invalid comma-less `affectedCpus` arrays.
- 32-bit shell arithmetic overflowed for >2 GiB memory figures (now 64-bit via awk).
- GPU live frequency/OPP now read from the mali devfreq node and `/proc/gpufreqv2`.
- App ANR: blocking root/network calls ran on the main thread and made ColorOS close the
  app shortly after launch; all blocking work now runs on `Dispatchers.IO`.

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
