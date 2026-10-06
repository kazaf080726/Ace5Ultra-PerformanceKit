# PerfKit Android app (module owner notes)

The app module under `app/` is a standalone Gradle project. It owns the root
bridge, dashboard, profile/tuning/adaptive controls and the OTA updater. It
never touches `module/` or `release/` build scripts.

## Layout

- `config/RepoConfig.kt` — single GitHub owner/repo constant for OTA.
- `root/RootBridge.kt` — libsu wrapper; root-shell only.
- `data/` — JSON models, `PerfRepository` (perfctl + direct /proc fallback),
  `Updater` (GitHub `release/update.json`), `Settings` (3–5 s refresh).
- `ui/` — Compose Material3, liquid-glass (RenderEffect blur on API 31+,
  translucent fallback below), floating blurred bottom nav, predictive back.

## Behaviour rules

- Root is never assumed. No-root users still get direct /proc/meminfo + Build
  info; everything else renders as `N/A`.
- The dashboard reads through `perfctl status --json`; direct world-readable
  `/proc` reads are the fallback.
- Manual tuning only exposes keys perfctl marks `safe:true` with `min`/`max`.
- Predictive back: `android:enableOnBackInvokedCallback="true"` in the manifest
  plus `PredictiveBackHandler` in `ui/nav/AppNav.kt`.

## Build

See `docs/release-signing.md`. Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk` / `app-release.apk`
