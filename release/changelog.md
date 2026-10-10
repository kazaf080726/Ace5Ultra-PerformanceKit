# Ace5Ultra-PerformanceKit v1.0.1

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
