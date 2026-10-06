# PerfKit Control Contract (v1)

This is the single hard contract between the root module (`ace5ultra_perfkit`) and the
management app. Both sides MUST implement exactly this. Keep it stable; if it changes,
bump `contract` and the module `versionCode`, and ship an OTA update.

## 1. Identity & paths

- Module id: `ace5ultra_perfkit`
- Live module dir: `/data/adb/modules/ace5ultra_perfkit`
- Staging dir during update: `/data/adb/modules_update/ace5ultra_perfkit`
- Controller CLI (the IPC endpoint): `/data/adb/modules/ace5ultra_perfkit/bin/perfctl`
- Daemon: `/data/adb/modules/ace5ultra_perfkit/bin/perfd`
- Shared library: `/data/adb/modules/ace5ultra_perfkit/bin/perflib.sh`
- Root-only runtime dir (mode `0700`, owner root): `/data/adb/ace5ultra_perfkit/`
  - `state.json`   current profile + adaptive config (atomic temp+rename writes)
  - `snapshot/`    one file per saved original node, plus `manifest.tsv`
  - `logs/perfkit.log`
  - `capabilities.json` discovered node map for this exact device
- App package: `com.ace5ultra.perfkit`

## 2. Security model

- `bin/perfctl` and `bin/perfd` are mode `0700`, owner `root:root`. The ONLY way the app
  reaches them is through a root shell (libsu / the SukiSU/BakaSU/KSU `su`), so every
  caller has already been authorized by the root manager. This is the hardened local
  command/response channel: no network listener, no world-readable/writable files.
- The runtime dir is `0700` root. No file is ever group/world writable.
- `perfctl` refuses to run if its effective uid is not 0 and prints
  `{"error":"root_required"}` with exit code 3.
- Every sysfs/procfs WRITE is preceded by an existence/permission check and is logged as
  `node=<path> old=<v> new=<v> ok=1/0`. A missing node is skipped, never fatal.
- No node outside the discovered capability map is ever written.

## 3. Command set

Run via root shell, e.g. `su -c /data/adb/modules/ace5ultra_perfkit/bin/perfctl status --json`

| Command | Output |
|---|---|
| `version` | `{"version":"x.y.z","versionCode":N,"contract":1}` |
| `status --json` | full status object (section 4) |
| `profile get` | `{"profile":"balanced"}` |
| `profile set <powersave\|balanced\|performance\|game\|adaptive>` | `{"ok":true,"profile":...}` |
| `adaptive status` | `{"enabled":bool}` |
| `adaptive on` / `adaptive off` | `{"ok":true,"enabled":...}` |
| `tuning list` | `{"items":[{"key":...,"value":...,"min":..,"max":..,"unit":..,"safe":bool}]}` |
| `tuning set <key> <value>` | `{"ok":true,"key":...,"value":...}` (rejects unsafe/out-of-range) |
| `snapshot` | `{"ok":true,"saved":N}` |
| `restore` | `{"ok":true,"restored":N}` |
| `selftest` | `{"ok":bool,"checks":[...]}` |

Errors: exit != 0 and `{"error":"<machine_code>","message":"<human>"}`.
All frequencies are integers in kHz. Memory/bytes are integers. Temperatures are
deci-Celsius integers as exposed (e.g. 42500 = 42.5 C) AND a `tempC` float is provided.

## 4. `status --json` object

```json
{
  "version": "1.0.0", "versionCode": 10000, "contract": 1,
  "device": { "brand": "", "model": "", "device": "", "soc": "", "android": "", "kernel": "" },
  "profile": "balanced",
  "adaptive": { "enabled": true, "reason": "foreground_game", "load": 0.0, "tempC": 0.0 },
  "cpufreqLocked": true,
  "cpu": {
    "numCores": 8,
    "policies": [
      {"policy":0,"affectedCpus":[0,1,2,3,4],"governor":"","curFreq":0,"minFreq":0,
       "maxFreq":0,"availableGovernors":[],"availableFreqs":[]}
    ],
    "cores": [
      {"cpu":0,"policy":0,"online":1,"curFreq":0,"minFreq":0,"maxFreq":0,"util":0.0}
    ]
  },
  "memory": {"total":0,"used":0,"available":0,"ramPercent":0.0,
             "swapTotal":0,"swapFree":0,"zramTotal":0},
  "gpu": {"present":false,"name":"","curFreq":0,"minFreq":0,"maxFreq":0,"util":-1.0},
  "thermal": [{"type":"","name":"","tempC":0.0}],
  "battery": {"level":-1,"temperatureC":0.0,"currentNowMa":0,"status":""},
  "uptimeSeconds": 0
}
```

- Per-core `util` is 0..100 computed from two `/proc/stat` samples (the CLI samples
  internally, ~200 ms apart). On failure use -1.
- Fields that cannot be read use `-1` / empty and are never guessed. The app shows them
  as "N/A". Estimated/derived values MUST be flagged with a sibling `*Estimated:true`.

## 5. Profiles (behaviour, node targets are data-driven)

Each profile is a set of desired values applied ONLY to nodes present in
`capabilities.json`. Targets, in priority order:
per-policy `scaling_governor`, `scaling_min_freq`, `scaling_max_freq`, governor tunables
under `cpufreq/<gov>/`, per-cpu `online` (core control / hotplug), MTK HPS `/proc/hps/*`,
sched/EAS/WALT knobs under `/proc/sys/kernel/sched_*`, CPU boost
(`/sys/module/*/parameters/*boost*`, `/sys/devices/system/cpu/cpu_boost/*`), block IO
scheduler + read-ahead under `/sys/block/*/queue/`, `vm.swappiness`, Mali/MTK GPU DVFS
under `/sys/class/mitk*` / `/sys/kernel/gpu/*` / `/proc/gpufreq*` (whichever exists),
and only conservative, reversible thermal hints (never disable safety thermal).

- `powersave`   cap max freq, prefer efficient cores, lower boost
- `balanced`    stock-ish governor/freq, boost on demand (default)
- `performance`  keep freq floor raised, boost enabled, never force thermal-off
- `game`         performance + sustained (thermal) friendly GPU floor, foreground-aware
- `adaptive`     daemon picks among the four using load, temp, foreground app; records reason

Boot: `service.sh` starts `perfd`, which restores the last profile from `state.json`.
`perfd` is detached with `setsid` (new session, `</dev/null`, stdout/stderr to `/dev/null`)
so it survives the service event being torn down; a pid file prevents double-start.
Disable/uninstall: `uninstall.sh` (and the manager's remove) runs `perfctl restore` to
write every snapshot value back, then removes the runtime dir. Nothing is written to
boot/vendor/system partitions; all changes live only in writable sysfs/procfs and are
lost at boot unless the module service re-applies them.

### 5.1 cpufreq mode lock (vendor 0444)

After boot the vendor framework sets `scaling_governor`, `scaling_min_freq` and
`scaling_max_freq` on every policy to `system:system 0444` (read-only). Root can
still `chmod 0644` them and write (verified on the device; the mode and value hold
for at least 30 s with no reversion). The controller therefore:

1. records the **original mode** alongside the original value when snapshotting;
2. on write, if the owner-write bit is absent, `chmod 0644`, writes, then restores
   the original mode (never leaves the node world-writable);
3. on restore/uninstall, `chmod 0644`, writes the original value, then restores the
   original mode.

`status --json` exposes `"cpufreqLocked": true|false` (true when any policy's
`scaling_max_freq` is still `0444`) so the UI can show that the governor/freq part
is chmod-gated rather than a silent no-op. Nodes that stay writable after boot
(`sugov_ext/*_rate_limit_us`, block read-ahead, `vm.swappiness`,
`schedtune.boost`) are applied live without any chmod.

## 6. Versioning & OTA

- `module.prop`: `version=x.y.z`, `versionCode=NNN` (integer, monotonic). The canonical
  code is `MAJOR*10000 + MINOR*100 + PATCH`, so 1.0.0 = 10000. The app and module use the
  same formula.
- `updateJson` points to the raw `release/update.json`.
- App compares `versionCode` from `perfctl version` and from `release/update.json`; a
  greater remote code offers an update and hands the zip to the root manager.
- `release/update.json`:
```json
{
  "version": "1.0.0",
  "versionCode": 10000,
  "zipUrl": "https://github.com/<owner>/Ace5Ultra-PerformanceKit/releases/download/v1.0.0/ace5ultra_perfkit-v1.0.0.zip",
  "changelog": "https://raw.githubusercontent.com/<owner>/Ace5Ultra-PerformanceKit/main/release/changelog.md"
}
```
