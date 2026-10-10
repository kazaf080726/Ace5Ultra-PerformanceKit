# Control Contract — v1.0.1 addendum (Ace5Ultra PerformanceKit)

This addendum extends `docs/control-contract.md`. Where they differ, **this file
wins**. Target device: OnePlus Ace 5 Ultra, `PLC110`, platform `mt6991`,
Dimensity 9400+, Android 16 / SDK 36. Root: SukiSU/KernelSU (`u:r:ksu:s0`).

Both module and app ship **version `1.0.1`, versionCode `10100`**
(formula MAJOR*10000 + MINOR*100 + PATCH). App and module versionCode stay equal
for a joint release.

Hard rules unchanged: overlay-only (manager `/data/adb/modules`), snapshot-before-
write + restore on disable/uninstall, never touch boot/vendor/system partitions,
existence+mode checks before every node write, all-English file/script names, no
absolute paths in shipped scripts, no TODOs/stubs. Thermal safety must NEVER be
disabled.

---

## 1. Profiles — now FIVE

`perfctl profile list` returns: `powersave balanced performance game system`.
State still stored in the daemon runtime state JSON; `service.sh` restores the last
profile at boot. The per-profile tunable matrix (only write nodes that exist and
are writable; the 0444 chmod-write-restore flow already in `perflib.sh` applies):

| Knob | system | powersave | balanced | performance | game (极限) |
|---|---|---|---|---|---|
| scaling_max_freq all policies | stock | capped toward efficient min | **hw max** | **hw max** | **hw max** |
| scaling_min_freq | stock | stock/min | stock | moderate raise (mid/prime) | high floor on mid/prime |
| governor | stock `sugov_ext` | `powersave` (else conservative) | stock EAS (`sugov_ext`/`schedutil`), fastest up-ramp | `performance` | `performance` |
| governor up_rate_limit | stock | relaxed | minimized (instant ramp) | minimized | minimized |
| CPU/ sched boost | stock | off/low | **on** | max | max |
| schedtune.boost (if node exists) | stock | 0 | stock/on | high | high |
| core_ctl | stock | allow offline | keep all reachable | keep online | keep online |
| GPU DVFS | untouched | capped/conservative if setter | default governor | `performance` + raised floor if setter | highest floor OPP + `performance` if setter |
| swappiness | stock (125) | high | stock | low | low |
| block IO scheduler / read-ahead | stock | powersave bias | default | perf (mq-deadline/none), low RA | lowest-latency (none), small RA |
| display peak refresh | untouched | untouched | untouched | untouched | **best-effort max** via writable setting, reverted on switch |

Explicit user requirements encoded here:
- **Except `powersave`, every profile keeps the CPU at peak dispatch state**:
  `scaling_max_freq` = hardware max on all three policies, boost enabled, and the
  fastest up-ramp — i.e. the CPU is always *allowed* to reach max immediately.
  `balanced` keeps the stock EAS governor (it still clocks down at idle for
  efficiency but the ceiling/ramp are maximal); `performance`/`game` pin the
  `performance` governor.
- **`game` pushes everything to the limit**: CPU performance + high big-core
  floor, GPU performance governor / high OPP floor (only when a writable setter
  exists under `/proc/gpufreqv2` or the mali devfreq), scheduler/boost max,
  lowest-latency IO, and a best-effort peak display refresh. Frame rate is
  ultimately gated by per-game and system display settings, so the refresh action
  is best-effort, logged, and reverted when leaving game; do NOT promise what the
  node does not allow. Never disable thermal/cooling devices.
- **`system` = return to the OS's own adaptive mode**: apply the snapshot RESTORE
  (governor/freq/boost/swappiness/IO/GPU all back to the boot-captured stock
  values), then put `perfd` into **monitor-only** for as long as the profile is
  `system` (it keeps sampling/logging but performs no writes). This hands control
  back to ColorOS adaptive scheduling. Selecting any other profile re-arms writes.

`perfctl profile set system` must: run the existing restore path, persist
`profile=system`, and signal the daemon (state file) to stop writing. `status`
reports the active profile including `system`.

---

## 2. Mount subsystem (`bin/perfmount.sh`) — boot + read optimization

Goal: use mounting to (a) shorten boot work and (b) speed up app reads, with a
two-strategy choice decided BOTH at flash time and at boot.

Detection:
- At flash (`customize.sh`) and again at boot (`post-fs-data.sh` → `perfmount.sh`),
  look for an **existing compatible mount provider**: another ENABLED module under
  `/data/adb/modules/*` (never our own id) that either matches a known
  mount/optimizer provider OR carries the marker file `perfkit-mount-provider`
  and/or exposes the include directory `/data/adb/perfkit-mount/overrides.d`.
- **external mode** — a provider exists: drop our override fragments into that
  provider's `overrides.d` and let IT perform the mounts; record
  `mode=external provider=<id>`.
- **local mode** — no provider (the expected case on this device): perform our own
  mounts. Record `mode=local provider=self`.
- Persist the decision to `$RUNDIR/mount.mode` and the module dir; `status --json`
  exposes it (see §3).

Local-mode mounts (all NON-persistent, under our own namespace, torn down on
uninstall; NEVER mount over /system /vendor /dev/cpuset or other critical paths):
1. Create a tmpfs working+cache area `/dev/perfkit` (0755, root).
2. Build the **static topology once** at boot (model/SoC, core count, per-policy
   min/max frequencies, governor list, GPU OPP table, RAM/zram sizes) and write it
   to `/dev/perfkit/topology.json` (0644, world-readable). The app reads this
   static file directly with NO root round-trip; only live values go through
   `perfctl`. This is the concrete read-path optimization (fewer `su` spawns and
   no re-scan of immutable topology every refresh).
3. `perflib.sh`/`perfctl` cache discovered node existence/frequency tables for the
   boot lifetime and use a short (~1 s) TTL cache for expensive thermal/GPU/top
   aggregations, so repeated `status --json` polls are cheap.
4. Boot work is minimized: discovery results are cached to the module's private
   persistent dir on first boot and revalidated (cheap stat checks) instead of
   re-walking all of sysfs each boot.

Any claim of faster boot/app-read must be measurable; report the mechanism and
mark measured-vs-not. Do not mask services or "debloat" via mount. Every mount is
logged with target/options/result; failures degrade to no-mount (the module still
works via direct node writes).

---

## 3. `perfctl status --json` schema additions (backward compatible)

Keep every existing field; ADD:
- Top level `"mount": {"mode":"local|external|none", "provider":"self|<id>",
  "worldReadableTopology":true|false}`.
- `cpu.loadPercent`: global 0..100 (aggregate across cores; -1 if unavailable).
- `cpu.topProcs`: array, top 5 by CPU, each `{"pid":int,"name":"...","cpu":pct}`
  from a root `top`/proc aggregation bounded by `timeout`; `[]` if unavailable.
- `memory.swapCachedBytes`, `memory.ramPercent`, `memory.swapPercent`
  (use the 64-bit `kb2b()` path; -1 when unknown).
- `battery` becomes an object: `{"percent":int,"powerWatts":float,
  "voltageVolts":float,"tempC":float,"currentUa":int}` from
  `/sys/class/power_supply/battery/*`; null/fields -1 when missing. Power sign
  follows the kernel current sign; present magnitude + charging state.
- `gpu.loadPercent` (-1 when no util node, as today), plus `curMhz/minMhz/maxMhz`.
- `"profile"` active name included in status (one of the five).
- Keep `"cpufreqLocked"`.

All numeric byte values must be exact (no 32-bit overflow). JSON must remain
strictly valid (comma-separated arrays); re-validate with Python json after edits.

---

## 4. Dual update channels (the core OTA rule)

Two published manifests in `release/`:

- **Module channel — `release/update.json`** (existing; consumed by the manager and
  by the app). For 1.0.1:
  ```json
  {
    "version": "1.0.1",
    "versionCode": 10100,
    "zipUrl": "https://github.com/kazaf080726/Ace5Ultra-PerformanceKit/releases/download/v1.0.1/ace5ultra_perfkit-v1.0.1.zip",
    "changelog": "https://raw.githubusercontent.com/kazaf080726/Ace5Ultra-PerformanceKit/main/release/changelog.md",
    "appVersionCode": 10100,
    "apkUrl": "https://github.com/kazaf080726/Ace5Ultra-PerformanceKit/releases/download/v1.0.1/PerfKit-v1.0.1.apk"
  }
  ```
- **App-only channel — `release/app-update.json`** (NEW; consumed only by the app):
  ```json
  {
    "version": "1.0.1",
    "versionCode": 10100,
    "apkUrl": "https://github.com/kazaf080726/Ace5Ultra-PerformanceKit/releases/download/v1.0.1/PerfKit-v1.0.1.apk",
    "changelog": "https://raw.githubusercontent.com/kazaf080726/Ace5Ultra-PerformanceKit/main/release/changelog.md"
  }
  ```

Decision logic in the app (compare against installed module code from
`perfctl version` and installed app code from `BuildConfig.VERSION_CODE`):
1. If module `versionCode` **> installed module code** → show **“Module + App
   update”**. The module zip BUNDLES the matching APK and installs it on the
   post-flash reboot (see §5), so the single manager flash updates BOTH. The app
   downloads the zip and hands it to the root manager (FileProvider), exactly like
   today; it must NOT also offer a standalone APK install in this branch.
2. Else if app-only `versionCode` **> installed app code** (module unchanged) →
   show **“App update”** which downloads only the APK and launches the package
   installer (app-only self-update).
3. Else “Up to date”. The two channels are compared every refresh of the Updater
   screen and the result (which channel, from→to versions) is shown explicitly.

This realizes: **a module release updates module+app together; an app-only release
updates just the app.**

---

## 5. Joint update packaging (module side)

- The flashable zip bundles the current release APK at
  `module/app/PerfKit.apk` (kept OUT of `system/` so it is not a static system app).
- `service.sh` (late boot, root) checks the bundled APK versionCode vs the
  installed user app; if the bundle is NEWER, run `pm install -r` (or
  `cmd package install`/`pm install-create` as available) with a `timeout`, logging
  the result; never downgrade, never loop, guard with a one-shot marker per
  versionCode. If the app is not installed at all, leave it (do not force-install
  to an unwilling user) — but on a normal joint update it is already present.
- The build packager (`scripts/build_module.py`) must copy
  `app/build/outputs/apk/release/app-release.apk` → `module/app/PerfKit.apk` before
  zipping when that APK exists; fail loudly if the APK versionCode does not match
  the module versionCode (no mismatched bundles).

---

## 6. App: icon, i18n, modern redesign

### 6.1 Icon
Use `design/icon-source.jpg` (neon cyan pulse waveform in a ring on a starry blue
field) as the launcher icon. Produce an adaptive icon: full-bleed bitmap as the
legacy/foreground with a dark `#0B1020`-ish background color so the waveform reads
at small sizes; generate all `mipmap-*` densities (or a density-independent
foreground drawable + `ic_launcher.xml`/`ic_launcher_round.xml` adaptive XML) and
replace the current icon set. Keep monochrome/notification optional.

### 6.2 Localization (full i18n)
- Move EVERY user-facing string into resources. Default `values/strings.xml` =
  English; add `values-zh-rCN/strings.xml` (简体中文) and
  `values-zh-rTW/strings.xml` (繁體中文，台灣用語，如「記憶體/啟動/效能/遊戲」).
  Structure so additional languages are drop-in.
- Add an in-app language switcher (Settings/Updater area) using the AndroidX
  per-app language API (`AppCompatDelegate.setApplicationLocales` /
  `LocaleListCompat`); choices: System default, English, 简体中文, 繁體中文.
- All numbers/units stay locale-neutral in formatting but labels localize; profile
  names sent to `perfctl` remain the ASCII keys (`powersave/balanced/performance/
  game/system`) while displaying localized labels.

### 6.3 Modern redesign (reference: `design/layout-reference-scene.jpg`, Scene app)
Rebuild the screens with a modern, light, card-based layout and large numerals,
FUSED with the already-required liquid-glass language (frosted/translucent
surfaces, soft shadows, rounded 20–28 dp cards), edge-to-edge, the **floating
rounded blurred bottom navigation bar**, and **predictive back** retained. Accent
moves to the icon’s neon cyan/blue (no purple/indigo); neutral light surfaces with
an optional dark glass theme if cheap, otherwise light only.

Dashboard information architecture (mirror the reference):
- Memory card: a **ring/donut** with center label (RAM %), rows 物理内存/RAM and
  交换分区/Swap with progress bars and GB, plus Used % and SwapCached.
- GPU card: **ring/donut** (load if available, else frequency-vs-max), big current
  MHz, load %, GPU name and driver lines, min–max.
- CPU card: left = **top processes** by CPU with icon/name/% (from
  `cpu.topProcs`); right = SoC model `MT6991 (4+3+1)`, aggregate load and a bar
  sparkline; below, a per-core **grid of mini bar charts**, each with big current
  MHz and the `min~max MHz` range and util %.
- Two compact cards: battery (power W, percent + voltage, temp) and device
  (Android version, uptime).
- Keep the live 3/4/5 s refresh and history curves; ensure all data still parses
  the §3 schema and renders N/A gracefully.
- Profiles screen: five glass cards (powersave/balanced/performance/game/system)
  with localized names + descriptions, an ACTIVE state, Apply; game shows an
  “极限/Extreme” hint; system is captioned as “return to system adaptive”.
- Tuning / Adaptive / Updater screens restyled to the same system; Updater shows
  the two-channel outcome from §4 (App-only vs Module+App) with from→to versions,
  progress, and install/flash actions.

---

## 7. Build / verify gates for 1.0.1
- Module: `shellcheck --shell sh -x` and `bash -n` clean on all scripts; WSL
  functional smoke for all five profiles incl `system` (monitor-only), mount mode
  detection (simulate external marker and local), status JSON parses and contains
  the new fields; zip layout/module.prop/versionCode verified; bundled APK version
  matches. No phone writes during build.
- App: `gradlew assembleRelease assembleDebug` BUILD SUCCESSFUL; release signed
  with the existing keystore; APK versionName 1.0.1 / versionCode 10100; confirm
  adaptive icon and three locales render (screenshots if a device write is
  authorized — otherwise build-only and report).
- Publish is owned by the organizer after both artifacts land.
