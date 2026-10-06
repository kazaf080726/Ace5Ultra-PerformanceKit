# Ace5Ultra-PerformanceKit

A device-tuned performance toolkit for the **OnePlus Ace 5 Ultra (一加 Ace 5 至尊版)**,
built around a simple idea: **the root module owns the low-level tuning, and a polished
Jetpack Compose app owns the experience.**

It ships five scheduling profiles, a live hardware dashboard that refreshes every few
seconds, a hardened root control channel, and over-the-air updates that install straight
from your root manager — no manual unzipping, ever.

> **Target device (verified on real hardware):** OnePlus Ace 5 Ultra · model `PLC110` ·
> MediaTek Dimensity 9400+ (`mt6991`, 3 nm) · 8 CPU cores (4 super + 4 big, up to
> 3.73 GHz) · Immortalis-G925 GPU · Android 16. See
> [`docs/device-profile.md`](docs/device-profile.md) for the measured topology.

---

## Why this exists

Generic "tuner" modules guess their node paths and bake changes into places that are hard
to undo. This kit takes the opposite approach:

- **Data-driven.** On first run the controller discovers the CPU policies, frequencies,
  governors, GPU DVFS and thermal nodes that *actually exist on this device* and records
  them in a capability manifest. It never writes to a node that isn't there.
- **Reversible by design.** Before the first change it snapshots every original value.
  Disabling or uninstalling the module restores all of them. Nothing is ever written into
  the boot, vendor or system partitions — all changes are runtime-only and are re-applied
  by the module after each reboot.
- **Talks to a real app.** A root-side controller exposes a strict, root-only
  command/response interface; the app is a thin, beautiful client on top of it.

## Features

### Root module
- Five profiles — **Powersave, Balanced, Performance, Game, Adaptive** — acting on
  per-policy governors and frequency limits, core/hotplug control, MTK HPS, scheduler
  (EAS/WALT) knobs, CPU boost, block I/O scheduler, swappiness and GPU DVFS.
- An **Adaptive daemon** that chooses a profile from CPU load, temperature and the
  foreground app, and records *why* it switched.
- Boot persistence, one-tap action, full snapshot/restore, and per-node logging.

### Management app
- **Live dashboard (3–5 s, configurable):** CPU core count, per-core online state,
  current / min / max frequency, governor, per-core utilization with scrolling history
  curves grouped by cluster, memory and swap/ZRAM, GPU frequency/load, temperatures,
  battery and uptime — every value labelled with its unit.
- Profile switching plus safe, range-checked manual tuning.
- Detects whether the module is installed and its version, and works with
  **SukiSU Ultra, BakaSU and KernelSU**.
- **Built-in updater:** compares `versionCode` and flashes the new module through your
  root manager in one tap; it can also update the app itself.
- **Liquid-glass UI:** frosted translucent blur (RenderEffect on Android 12+, with a
  graceful fallback), a floating rounded blur bottom bar, **predictive back gestures** and
  edge-to-edge layout, in a clean glass-neutral palette with a OnePlus accent.

---

## Installation

1. Flash `ace5ultra_perfkit-vx.y.z.zip` in SukiSU Ultra / BakaSU / KernelSU and reboot.
2. Install `PerfKit-vx.y.z.apk` and open it. The app will request root through your
   manager; grant it once.
3. That's it — pick a profile and watch the dashboard.

### Updates (OTA)
The module's `module.prop` points at [`release/update.json`](release/update.json). Your
root manager periodically checks it and offers an in-app update. The PerfKit app has the
same check under **Updates**, and can hand the new zip to the manager for you. To publish a
new version yourself, see [Releasing](#releasing).

---

## Building from source

A JDK 17 and the Android SDK are required. On Windows, set the variables explicitly in
each shell (fresh terminals otherwise pick them up automatically):

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\android-dev\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "C:\Users\Administrator\android-dev\sdk"
```

```sh
# App (debug + release APKs land in app/build/outputs/apk/)
./gradlew assembleDebug assembleRelease

# Module zip (lands in release/)
python3 scripts/build_module.py
```

Release APKs are signed with a keystore you control; document it via a
`keystore.properties` file (the keystore itself is git-ignored). See
[`docs/signing.md`](docs/signing.md).

## Releasing

The release is one command. It bumps the module and app versions, regenerates
`update.json`, builds the zip and APK, commits and tags, then creates the GitHub Release
with every asset attached:

```sh
python3 scripts/bump_release.py 1.0.1
# or: scripts/bump-release.sh 1.0.1   /   scripts\bump-release.ps1 1.0.1
```

The GitHub owner is read from your `origin` remote or `gh api user`. Pushing a `v*` tag
also triggers the [build workflow](.github/workflows/build.yml), which builds and publishes
a release on GitHub Actions.

---

## Project structure

```
Ace5Ultra-PerformanceKit/
├── module/          # The root module (module.prop, scripts, bin/perfctl + perfd, system/)
├── app/             # Kotlin + Jetpack Compose management app
├── docs/            # Device profile, control contract, signing & format notes
├── scripts/         # Cross-platform build & release tooling
├── release/         # Built module zip, update.json, changelog
└── .github/         # Tag-based build & release workflow
```

The exact wire format between the module and app is frozen in
[`docs/control-contract.md`](docs/control-contract.md).

## Safety

- The module only ever writes to standard, runtime tunables; it never disables safety
  thermal protection and never touches read-only partitions.
- Original values are snapshotted before the first write and restored on disable/uninstall.
- The control channel is root-only: the controller is mode `0700`, its runtime directory is
  `0700`, and nothing is exposed over the network.

## Credits

See [CREDITS.md](CREDITS.md) for the full list. This project stands on the Magisk module
installer, KernelSU / SukiSU / BakaSU, libsu, Jetpack Compose, and the liquid-glass design
references (Liquid-Glass-Android, miuix, LCardView). Sincere thanks to their authors.

## License

Copyright © 2026 the Ace5Ultra-PerformanceKit contributors. Licensed under the
**GNU General Public License v3.0 or later** — see [LICENSE](LICENSE). Third-party
components keep their own licenses as documented in [CREDITS.md](CREDITS.md).

This is a community project and is not affiliated with OnePlus, MediaTek or the root
manager projects. Use it at your own discretion.
