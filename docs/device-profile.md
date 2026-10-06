# OnePlus Ace 5 Ultra — Read-Only Device Profile

Profiled strictly read-only over `adb shell` (getprop / cat / ls / dumpsys, plus
`su 0 cat` on root-only nodes only). No device node was written, no `setprop`,
no `push`/`install`/remount/reboot. Raw evidence: `docs/device/raw/*.txt`.

## 1. Identity & SoC evidence

| Field | Value | Source |
|---|---|---|
| Market name (EN) | OnePlus Ace 5 Ultra | `ro.vendor.oplus.market.enname` (product-props.txt) |
| Market name (CN) | 一加 Ace 5 至尊版 | `ro.vendor.oplus.market.name` |
| Product series | OnePlus_Ace_Series | `ro.oplus.product.series` |
| Brand / Manufacturer | OnePlus / OnePlus | `ro.product.brand`, `ro.product.manufacturer` |
| Model / Name | PLC110 / PLC110 | `ro.product.model`, `ro.product.name` |
| Device codename | OP60EDL1 | `ro.product.device` |
| Board platform | mt6991 / `k6991v1_64` | `ro.board.platform`, `ro.product.board` |
| SoC (CPUINFO prop) | **MT6991T** (internal part string) | `ro.product.oplus.cpuinfo` |
| SoC (marketing) | **MediaTek Dimensity 9400+** | per OnePlus official spec; config below |
| MediaTek platform | mt6991, branch `alps-mp-v0.mp1.tc16sp-pr5` | `ro.vendor.mediatek.platform` |
| Android / SDK | 16 / 36 | `ro.build.version.release`, `.sdk` |
| Security patch | 2026-08-01 | `ro.build.version.security_patch` |
| Build fingerprint | `OnePlus/PLC110/OP60EDL1:16/BP2A.250605.015/V.dff485_173d7fc_1705300:user/release-keys` | getprop.txt |
| OPlus ROM | V16.1.0 (display 16.0.10), manifest `PLC110_11.C.35_1350` | product-props.txt |
| Kernel | `6.6.118-android15-8-...-4k aarch64` | uname.txt |
| RAM (prop) | 16 GB (`ro.oplus.memory.size=16`) | product-props.txt |

SoC conclusion: **MediaTek Dimensity 9400+**, internal part string **MT6991T** on the
`mt6991` platform. Identified from the ARM MIDR CPU-part numbers in `/proc/cpuinfo`,
mapped per the authoritative ARM Cortex TR part table:

| Cores | ARM MIDR CPU part | Core | Min–max (kHz) |
|---|---|---|---|
| cpu0-3 | 0xd81 | Cortex-A720 | 339000 – 2400000 |
| cpu4-6 | 0xd82 | Cortex-X4 | 622000 – 3300000 |
| cpu7 | 0xd85 | Cortex-X925 | 798000 – 3730000 |
| GPU | — | Immortalis-G925 MC12 | 338000 – 1612000 |

The 1× Cortex-X925 @ 3.73 GHz + 3× Cortex-X4 @ 3.30 GHz + 4× Cortex-A720 @ 2.40 GHz +
Mali-Immortalis-G925 MC12 configuration is MediaTek's official **Dimensity 9400+** spec
(the base Dimensity 9400 primes at 3.626 GHz). The full Dimensity 9500 is a different
design (1× C1-Ultra @ 4.21 + 3× C1-Premium @ 3.5 + 4× C1-Pro @ 2.7, Mali-G1 Ultra), so
"Dimensity 9500 class" was incorrect and is removed.

> Note: the same silicon is listed in some channels as "Dimensity 9500s" (identical official
> configuration). The OnePlus Ace 5 Ultra is sold and specified by OnePlus as the
> Dimensity 9400+ (oneplus.com/cn/ace-5ultra-specs), which is the identification used here.

## 2. CPU topology & policies

8 CPUs online 0-7, 3 clusters (topology.txt). cpufreq policies = one per cluster (cpufreq.txt).

| Cluster | Cores (CPU part) | policy | cpuinfo min–max (kHz) | current governor | Governors available |
|---|---|---|---|---|---|
| cluster0 (little) | cpu0-3 (0xd81 Cortex-A720) | policy0 | 339000 – 2400000 | **sugov_ext** | scx, sugov_ext, conservative, powersave, performance, schedutil |
| cluster1 (mid) | cpu4-6 (0xd82 Cortex-X4) | policy4 | 622000 – 3300000 | **sugov_ext** | same set |
| cluster2 (prime) | cpu7 (0xd85 Cortex-X925) | policy7 | 798000 – 3730000 | **sugov_ext** | same set |

- `affected_cpus` == `related_cpus` per policy; no cross-cluster sharing.
- Runtime caps at capture: policy0 max 2000000, policy4 max 2600000, policy7 max 3000000 kHz (scaling_max_freq, thermal/PPM capped).
- Per-core `thread_siblings_list` is itself (no SMT); `core_siblings_list` = 0-7.
- Governor tunables: only `cpufreq/policyN/sugov_ext/{up_rate_limit_us, down_rate_limit_us}` exist (no schedutil dir). Values: up=0, down=1000 µs on all three (governor-tunables.txt).

**core_ctl** (per-cluster hotplug, corectl-root.txt): exists at cpu0/4/7.
- cpu0: enable=1, min=max=4, up_thres=30, busy_up=60, thermal_up=80, boost=0.
- cpu4: enable=0, min=max=3, up_thres=50, busy_up=80.
- cpu7: enable=0, min=max=1, up_thres=INT_MAX (never offlined).
- Writable (root) fields: `enable`, `core_ctl_boost`, `up_thres`, `cpu_busy_up_thres`, `not_preferred`, `offline_throttle_ms`, `thermal_up_thres`.

**MTK knobs**: `/proc/hps` and `/sys/kernel/hps` are **absent** (mtk-hps.txt).
`/sys/devices/system/cpu/cpu_boost` absent. Boost modules present:
`usb_boost` (enabled=1), `touch_boost`, `mtk_ioctl_touch_boost`,
`oplus_boost_pool_mtk`, `oplus_connectivity_routerboost` (boost-modules.txt;
no `parameters/enabled` for touch/pool — skip).

**sched knobs** (sched-knobs.txt, EAS/WALT present):
`sched_energy_aware=1`, `sched_pelt_multiplier=1`, `sched_util_clamp_min/max=1024`,
`sched_schedstats=1`. No `sched_walt_*` (PELT, not WALT).
`/dev/cpuctl/cpu.schedtune.boost=0` (root group; foreground=0) (schedtune-boost.txt).

## 3. GPU

| Item | Value | Source |
|---|---|---|
| GPU | **Arm Mali-G925-Immortalis, 12 cores, r0p1** (0x0D080300) | gpuinfo in gpu.txt |
| Device node | `/sys/devices/platform/soc/48000000.mali` | gpu-sysfs.txt |
| EGL/Vulkan hint | `ro.hardware.egl=vulkan`-class (mali), GLES 3.2 | gpu-props.txt |
| GPU OPP range | **338000 – 1612000 kHz** (52 working OPPs) | gpu_working_opp_table in gpu.txt |
| Idle state | OutFreq 26000, DVFSState 0x22 (powered off) | gpufreq_status |
| DVFS control | MTK `/proc/gpufreqv2/*` (root-only, 0660); NOT `/sys/kernel/gpu`, NOT `/sys/class/mitk_gpu`, NOT `/proc/gpufreq` | gpu-nodes.txt |
| Limiters | 14 in `/proc/gpufreqv2/limit_table` (SEGMENT, GPM3.0, PEAK_POWER, THERMAL_AP/EB, BATT_*, PBM, APIBOOST, POWERHAL…) | gpu.txt |
| Util node | none exposed in standard nodes (whitebox_test empty) | gpu.txt |

## 4. Memory

| Item | Value | Source |
|---|---|---|
| MemTotal | 15,743,168 kB (~15.0 GiB; ~16 GB SKU) | meminfo.txt |
| MemAvailable | ~6.7 GB at capture | meminfo.txt |
| Swap | /dev/block/zram0, 16 GiB disksize (17179869184 B), ~5.48 GiB used | swaps-swappiness.txt, zram.txt |
| ZRAM algo | **zstd** (available: lzo,lzo-rle,lz4,zstd,lz4k,zstdn,zstdn_o) | zram.txt |
| vm.swappiness | 125 | swaps-swappiness.txt |

## 5. Thermal

- **86 thermal_zone\*** (thermal-count.txt) and **8 cooling_device\*** (cooling-devices.txt).
- Key zones (thermal-zones.txt, deci-°C): `soc_max` 54635; `cpu-medium-core6-0/1` ~54969/52914;
  `cpu-little-core0..3`, `cpu-medium-core4/5/6`, `cpu-big-core7-0/1`, `cpu-dsu0..3`,
  `soc-top0..3`, `soc-bot0..3`, `gpu0/gpu1`, `vtskin-max` + vtskin1..10, `battery` 39400.
- Cooling devices: `cpufreq-cpu0/4/7` (max 21/27/29), `devfreq-48000000.mali` (max 45),
  `charger-cooler` (14), `lcd-backlight` (100), `wifi-cooler` (5), `flashlight_cooler` (4).
- `dumpsys thermalservice` / `dumpsys battery` saved to raw.

## 6. Battery / power (read-only snapshot)

capacity 90%, status Charging, charge_type Fast, voltage_now 4336 mV,
current_now ~378 (mA sign convention on this driver), temp 394 (39.4 °C);
USB online=1 (battery.txt, dumpsys-battery.txt). Multiple MTK charger nodes
present (mtk-master/slave-charger, primary_chg, mt6379-gauge1).

## 7. Storage / IO

Block devices `sda`,`sdb`,`sdc` (storage-mounts.txt). Active scheduler = **`[none]`**
(blk-mq passthrough; available mq-deadline/kyber/adios/bfq). read_ahead:
sda=1024, sdb=1024, sdc=512 KB. `/data` = f2fs on dm-73; system/vendor/product = erofs RO.

## 8. Root environment

- `adb shell su -c id` → `uid=0(root) ... context=u:r:ksu:s0` → **root manager = SukiSU / KernelSU** (ksud in /data/adb).
- `/data/adb/modules` (names only): TA_utl, WorkSettingPro, adb-ndk, droidspaces,
  duck-toolbox, extreme_gt, ffc, hma-uidfake, hma_oss_zygisk, hybrid_mount,
  kazaf_ko_killer, pathmask, playintegrityfix, scene_swap_controller,
  scene_systemless, susfs4ksu, teesim, tricky_store, zygisk_lsposed, zygisksu.
- No `ace5ultra_perfkit` module present yet (this kit not installed).

## Measured vs unknown

**Measured (traced to raw files above):** brand/model/codename/SoC/build; 8-core 3-cluster
layout; 3 cpufreq policies with exact min/max kHz, governors, and current governor;
sugov_ext tunables; core_ctl values; seden knobs; boost modules; schedtune boost;
RAM/swap/zram/swappiness; GPU model + full OPP table + idle state; 86 thermal zones
& 8 cooling devices with types; battery snapshot; block scheduler/read-ahead; kernel;
root manager & module list.

**Unknown / not probed or not exposed:**
- Per-core live utilization (would need two `/proc/stat` samples — not required statically).
- GPU real-time utilization node (none exposed; `whitebox_test` empty).
- `touch_boost` / `oplus_boost_pool_mtk` writable parameter names (module dirs exist but no
  standard `parameters/enabled`; controller must skip).
- MTK HPS entirely (absent on this build).
