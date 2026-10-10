# On-device verification — OnePlus Ace 5 Ultra (PLC110)

Date: 2026-10-10. Device serial `3B658F00CQ600000`. All checks below were run
against the real phone over ADB (root domain `u:r:ksu:s0`, SukiSU/KernelSU).
Nothing was written to boot/vendor/system partitions; every runtime change goes
through the manager overlay (`/data/adb/modules/ace5ultra_perfkit`) and is
snapshot/restored by the module.

Curated photos are in [`../screenshots/`](../screenshots/).

## 1. Device identity (measured, not assumed)

| Item | Value | Source |
|---|---|---|
| Market name | OnePlus Ace 5 Ultra（一加 Ace 5 至尊版） | getprop / model |
| model / product | `PLC110` | `ro.product.model` |
| device / board | `OP60EDL1` / `k6991v1_64` | getprop |
| Platform | `mt6991` (`ro.board.platform`, `ro.hardware`) | getprop |
| SoC | **MediaTek Dimensity 9400+** (internal string `MT6991T`) | ARM MIDR part IDs in `/proc/cpuinfo` |
| CPU parts | cpu0-3 `0xd81` Cortex-A720; cpu4-6 `0xd82` Cortex-X4; cpu7 `0xd85` Cortex-X925 | `/proc/cpuinfo` |
| Android | 16 (SDK 36), SP 2026-08-01, OPlus V16.1.0 | getprop |
| Kernel | 6.6.118-android15-… | `uname` |
| GPU | Immortalis/Mali-G925 MC12 r0p1 | `/proc/gpufreqv2` |
| RAM / swap | ~15 GiB visible (16 GB SKU); 16 GiB zram **zstd**, swappiness 125 | `/proc/meminfo` |
| Thermal | 86 thermal zones, 8 cooling devices | `/sys/class/thermal` |

The SoC was cross-checked from CPU part IDs rather than the marketing string;
the full read-only profile is in [`../device-profile.md`](../device-profile.md)
and the 66-node map is in [`node-inventory.json`](node-inventory.json).

## 2. CPU / GPU topology used by the module

Three cpufreq policies, stock governor `sugov_ext` on all:

| Policy | CPUs | Cores | Min–Max |
|---|---|---|---|
| policy0 | 0-3 | Cortex-A720 | 339–2400 MHz |
| policy4 | 4-6 | Cortex-X4 | 622–3300 MHz |
| policy7 | 7 | Cortex-X925 | 798–3730 MHz |

- Available governors: `scx, sugov_ext, conservative, powersave, performance, schedutil`.
- MTK HPS (`/proc/hps`) is **absent** — the module does not touch it.
- After boot the vendor resets cpufreq nodes to `system:system 0444`. Verified on
  device that root can `chmod 0644`, write, and have the value hold for 30 s; the
  module snapshots the original mode, chmod-writes, and restores the mode. Root's
  `[ -w ]` returns true even on `0444`, so the controller inspects mode bits directly.
- GPU live frequency is the devfreq node
  `/sys/class/devfreq/48000000.mali/cur_freq` in **Hz** (idle-gated 26 MHz); the OPP
  table is `/proc/gpufreqv2/gpu_working_opp_table` in **kHz** (52 OPPs, 338–1612 MHz).
  No GPU-utilisation node exists, so the app shows GPU load as N/A.
- The `oled_temp` zone reports a bogus 125 °C; the daemon ignores readings > 120 °C.

## 3. Module flash / boot / profiles / adaptive / uninstall (verified)

- Flashed the manager way (`ksud module install <zip>` → reboot). Standard layout,
  `module.prop` id `ace5ultra_perfkit`, versionCode `10000`, `#MAGISK` updater,
  `update-binary` mode 0755.
- Boot restore: `service.sh` starts `perfd` via `setsid` with a pidfile and a
  stale-pid guard (validates `/proc/<pid>/cmdline` contains `bin/perfd`); a
  duplicate-instance guard is built into `perfd`. Confirmed running after reboot
  (PPID 1).
- Profile switch changes real tunables. Example captured through the app's Apply
  button (powersave → performance):
  - before: governor `sugov_ext`, policy0 max `339000`, policy4 max `622000`;
  - after: governor `performance`, policy0 max `2400000`, policy4 max `3300000`;
  - restored to balanced: governor back to `sugov_ext`, max back to hardware max.
- Adaptive loop observed live switching on load/temp/foreground
  (`thermal_high → balanced` with moving load/temp samples). Boolean state is read
  with a dedicated `read_bool` (the unquoted `true` defeated the string parser),
  and foreground detection is bounded with `timeout 5`.
- Uninstall path verified earlier in the session: `ksud module uninstall` + reboot
  restored the snapshotted original governor/freq/mode values (snapshot vs
  restore sets matched); the runtime dir is removed by `uninstall.sh`. Nothing is
  persisted into a partition.

## 4. Android app (verified on device)

Package `com.ace5ultra.perfkit` (uid 10577), Kotlin + Jetpack Compose + Material3.

- **Root bridge**: direct `su -c` per command. The persistent libsu interactive
  shell executed commands but returned empty stdout/stderr on this manager, so it
  was replaced; raw `su -c id` returns uid 0 `u:r:ksu:s0`.
- **Dashboard (3/4/5 s configurable)**: 8 cores grouped by policy, per-core
  online/frequency/governor/utilisation with scrolling history curves, memory
  total/used/available + RAM%, swap/zram, GPU name + live freq + OPP range, 86
  thermal zones, battery, uptime. See screenshots 01–03.
- **Profiles / Apply**: screenshot 06 shows `Active: performance` after tapping
  Apply, with the sysfs changes in §3.
- **Liquid glass + floating blurred bottom bar**: screenshots 01–04, 06.
- **Predictive back**: screenshot 05 captures the live back-preview (the Profile
  window shrinks/rounds and reveals the blurred launcher); releasing commits back.
  Manifest sets `android:enableOnBackInvokedCallback="true"` and Compose uses
  `PredictiveBackHandler`.
- **ANR fixed during verification**: blocking `su`/network calls were running on
  the main thread (`viewModelScope.launch` defaults to Main), which froze the UI and
  made ColorOS capture a native backtrace and close the activity ~6–14 s after
  launch. All blocking root/repo/network work now runs on `Dispatchers.IO` and UI
  callbacks hop back to Main. After the fix the app held foreground for a 42 s poll
  with no `crash_dump64`/ANR/FATAL and the dashboard kept refreshing.

## 5. Bugs found and fixed from real-device testing

1. `perfctl status --json` emitted `"affectedCpus":[0 1 2 3]` (spaces, no commas) →
   invalid JSON, app fell back. Fixed to comma-separated.
2. 32-bit shell arithmetic overflowed for >2 GiB memory (negative totals) → added
   `kb2b()` using awk 64-bit doubles.
3. GPU name/freq were empty → read devfreq `cur_freq` (Hz→kHz) and the OPP table;
   name fixed to Immortalis-G925 MC12 for this module, utilisation left N/A.
4. cpufreq `0444` lock handled via snapshot-mode → chmod → write → restore-mode;
   mode bits are checked directly (root `[ -w ]` lies on read-only files).
5. Daemon lifecycle (setsid + cmdline-validated pidfile + duplicate guard),
   adaptive boolean parser, bounded `dumpsys`, bogus thermal filter, thermal
   threshold corrected to real device temperatures.
6. App main-thread ANR (§4) and GPU negative-load rendering.

## 6. Shipped artifacts (v1.0.0, versionCode 10000)

- Module zip `ace5ultra_perfkit-v1.0.0.zip` — 16759 bytes,
  SHA-256 `BF79E9A010CEE8E530BA7EAF95B5130C4E2CCCBA9CAB8B674799A8D017038833`.
- App `PerfKit-v1.0.0.apk` (release, signed) — 41081677 bytes,
  SHA-256 `908FFB60A9A9AD7C83ED5A4069395A0CA4BB81CF0C57EE5D0FFF4532754CEE09`,
  signer `CN=Ace5Ultra PerfKit, O=kazaf080726` (cert SHA-256
  `de5851dd…807f4c5c`).
- `PerfKit-v1.0.0-debug.apk` — 56421532 bytes.
- Manager OTA `release/update.json` points `zipUrl` at the GitHub Release and
  `changelog` at the raw changelog; both, plus the zip, were re-downloaded
  anonymously and matched the local byte counts and SHA-256 above.

## 7. Verified vs inferred

- **Verified on the real device**: identity/SoC part IDs, topology and frequencies,
  module flash + boot daemon + profile switching (via app and CLI) + adaptive
  behaviour + uninstall restore, app dashboard values, glass UI, floating bar,
  predictive back gesture, and the ANR fix.
- **Not hardware-verified**: the exact GPU DVFS *setter* filename (the controller
  writes only if a known writable setter exists, otherwise skips); GPU utilisation
  (no node exists); long-term stability across OTA updates triggered inside a
  manager UI (the OTA URLs and payload were verified anonymously; the in-manager
  prompt flow uses the standard KernelSU/SukiSU `update.json` contract documented
  in [`../module-format.md`](../module-format.md)).
