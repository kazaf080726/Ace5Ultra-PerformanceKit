# Node map — intended sysfs/procfs targets (OnePlus Ace 5 Ultra)

The controller is data-driven: it probes these paths at runtime and only writes a
node that **exists AND is writable**. Every write is logged
`node= old= new= ok=`. This file records what the controller targets and how
confident we are about each, based on the read-only device profile
(`docs/device-profile.md`, `docs/device/node-inventory.json`) plus offline review.

Legend:
- **VERIFIED** — path and current value traced to a raw `adb`/`su 0 cat` read in
  `docs/device/raw/`. The controller will find and (for writable ones) may use it.
- **VERIFIED absent** — confirmed not present; the controller explicitly skips it.
- **UNVERIFIED** — expected by class/SoC but not personally confirmed on this exact
  build; existence-guarded at runtime, never written if absent.

## CPU / cpufreq (VERIFIED)

| Node | Value / range | Profile use |
|---|---|---|
| `/sys/devices/system/cpu/cpufreq/policy0/{scaling_governor,scaling_min_freq,scaling_max_freq,scaling_cur_freq}` | governor `sugov_ext`; 339000–2400000 kHz | governor + min/max per profile |
| `policy4/...` | `sugov_ext`; 622000–3300000 kHz | same |
| `policy7/...` | `sugov_ext`; 798000–3730000 kHz | same |
| `policyN/scaling_available_governors` | `scx sugov_ext conservative powersave performance schedutil` | `pick_gov` preference order |
| `policyN/sugov_ext/up_rate_limit_us` | 0 µs | powersave 20000 / balanced 0 / performance·game 0 |
| `policyN/sugov_ext/down_rate_limit_us` | 1000 µs | powersave 0 / balanced 1000 / performance 50000 / game 20000 |

Per-profile caps (kHz, within measured ranges):
- powersave: governor `powersave` (fallback conservative/schedutil), min=max=`cpuinfo_min_freq` (cap low).
- balanced: governor `sugov_ext` (stock), min=`cpuinfo_min_freq`, max=`cpuinfo_max_freq`.
- performance: governor `performance`, min=max=`cpuinfo_max_freq`.
- game: governor `performance`, min=midpoint((min+max)/2), max=`cpuinfo_max_freq`.

## core_ctl / hotplug (VERIFIED, root-only)

| Node | Notes |
|---|---|
| `/sys/devices/system/cpu/cpu{0,4,7}/core_ctl/{enable,core_ctl_boost,up_thres,cpu_busy_up_thres,min_cpus,max_cpus,...}` | recorded in capabilities; **read-only in v1** (we do not offline big cores) |

## sched / EAS-PELT (VERIFIED, read-only)

| Node | Value |
|---|---|
| `/proc/sys/kernel/sched_energy_aware` | 1 |
| `/proc/sys/kernel/sched_pelt_multiplier` | 1 |
| `/proc/sys/kernel/sched_util_clamp_min` | 1024 |
| `/proc/sys/kernel/sched_util_clamp_max` | 1024 |
| `/proc/sys/kernel/sched_schedstats` | 1 |
| `/dev/cpuctl/cpu.schedtune.boost` | 0 (writable root) → powersave/balanced 0, performance/game 1 |
| `/dev/cpuctl/foreground/cpu.schedtune.boost` | 0 |

## Boost (VERIFIED present / absent)

| Node | State |
|---|---|
| `/sys/module/usb_boost/parameters/enabled` | present (=1); recorded, not changed |
| `/sys/module/touch_boost/parameters/enabled` | **VERIFIED absent** (no standard knob) → skip |
| `/sys/module/oplus_boost_pool_mtk/parameters/enabled` | **VERIFIED absent** → skip |
| `/sys/devices/system/cpu/cpu_boost` | **VERIFIED absent** → skip |

## MTK HPS (VERIFIED absent)

- `/proc/hps` and `/sys/kernel/hps`: **absent**. The controller never touches
  them (the `apply_hps` probe self-skips).

## Block / storage (VERIFIED)

| Node | Value |
|---|---|
| `/sys/block/{sda,sdb,sdc}/queue/scheduler` | `[none] mq-deadline kyber adios bfq` (left as stock) |
| `/sys/block/sda/queue/read_ahead_kb` | 1024 |
| `/sys/block/sdc/queue/read_ahead_kb` | 512 |
| read_ahead target per profile | powersave 128 / balanced 1024 / performance·game 2048 |

## VM (VERIFIED)

| Node | Stock | Target |
|---|---|---|
| `/proc/sys/vm/swappiness` | 125 | powersave 160 / balanced 125 / performance·game 100 |

## GPU (VERIFIED present, root-only)

| Node | Notes |
|---|---|
| `/proc/gpufreqv2/gpufreq_status` | OPP51 idle; working table 338000–1612000 kHz |
| `/proc/gpufreqv2/gpu_working_opp_table` | 52 OPPs |
| `/proc/gpufreqv2/limit_table` | 14 limiters (NEVER disabled) |
| GPU util node | **none exposed** → status reports `gpu.util = -1.0` |
| `/proc/gpufreq` / `/sys/kernel/gpu` / `/sys/class/mitk_gpu` | **VERIFIED absent** |
| writable GPU min/max setter | **UNVERIFIED** (not enumerated in the inventory); `apply_gpu` writes only if a known setter (`gpufreq_min_freq`/`gpu_min_freq`/`gpufreq_min_power_limit`) exists AND is writable, else skips |

## Thermal (VERIFIED, read-only; safety never touched)

- 86 `/sys/class/thermal/thermal_zone*/{type,temp}` read for `status --json`
  (max drives the adaptive `thermal_high` reason).
- 8 cooling devices (`cpufreq-cpu0/4/7`, `devfreq-...mali`, charger, lcd, wifi,
  flashlight). The module **never** disables a cooling device or thermal zone.

## Battery (VERIFIED)

| Node | Value |
|---|---|
| `/sys/class/power_supply/battery/capacity` | 90 |
| `/sys/class/power_supply/battery/temp` | 394 (39.4 °C → `/10`) |
| `/sys/class/power_supply/battery/current_now` | 378 mA |
| `/sys/class/power_supply/battery/status` | Charging |

## Memory / swap (VERIFIED)

- `/proc/meminfo`: MemTotal ~15.7 GB, MemAvailable ~6.7 GB, SwapTotal/SwapFree.
- `/sys/block/zram0/disksize` = 17179869184 B (16 GiB, zstd).

## What is confirmed vs assumed

CONFIRMED on this exact device (read-only traced): every node in the tables above
marked VERIFIED, including exact kHz ranges, governor set, sugov_ext tunables,
swappiness, UFS read-ahead, /proc/gpufreqv2 presence, and the absence of HPS /
cpu_boost / touch_boost knobs.

NOT YET CONFIRMED (guarded at runtime, skipped if absent):
- The exact writable GPU DVFS setter filename inside `/proc/gpufreqv2` (game
  profile raises the GPU floor only if it appears).
- Live per-core utilization at runtime (two `/proc/stat` samples, ~200 ms apart).
- Whether `core_ctl` tuning is safe to apply (left read-only in v1).
- Actual write success on the device (device is offline; all writes are
  existence/permission-guarded and logged, so unknown nodes are skipped).
