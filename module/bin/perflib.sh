#!/system/bin/sh
# shellcheck shell=dash
# shellcheck disable=SC3043  # Android /system/bin/sh is mksh and supports local
# perflib.sh - shared runtime library for ace5ultra_perfkit.
# Sourced by perfctl and perfd. POSIX sh only. Runs as root on the phone.
#
# Device (OnePlus Ace 5 Ultra, MT Dimensity 9400+ / MT6991T, mt6991):
#   3 cpufreq policies:
#     policy0 = cpu0-3 Cortex-A720 : 339000-2400000 kHz
#     policy4 = cpu4-6 Cortex-X4   : 622000-3300000 kHz
#     policy7 = cpu7   Cortex-X925 : 798000-3730000 kHz
#   stock governor = sugov_ext; available: scx sugov_ext conservative powersave performance schedutil
#   sugov_ext tunables (per policy): up_rate_limit_us, down_rate_limit_us
#   GPU = Mali Immortalis-G925 MC12, DVFS via root-only /proc/gpufreqv2; NO util node (report -1)
#   MTK HPS (/proc/hps) ABSENT -> never targeted
#   vm.swappiness stock = 125; block UFS scheduler [none], read_ahead sda/sdb=1024 sdc=512
#
# Everything else is data-driven: nodes are discovered by probing live sysfs/procfs
# paths; a node that is absent is never touched. Every write is guarded
# (exists + writable) and logged as node= old= new= ok=. The first write to a node
# snapshots its original value; restore/uninstall writes them back.

# ---------- caller / module layout ----------
# shellcheck disable=SC2034  # CALLER/MODROOT/BINDIR used by sourced scripts
CALLER="${CALLER:-$0}"
_PDIR=$(cd "$(dirname "$CALLER")" 2>/dev/null && pwd)   # .../module/bin
MODROOT=$(dirname "$_PDIR")                              # module root
BINDIR="$_PDIR"

RUNDIR=/data/adb/ace5ultra_perfkit
STATE="$RUNDIR/state.json"
SNAP="$RUNDIR/snapshot"
LOGDIR="$RUNDIR/logs"
LOG="$LOGDIR/perfkit.log"
CAP="$RUNDIR/capabilities.json"

# shellcheck disable=SC2034  # read by perfctl version/status
VERSION="1.0.0"
VERSIONCODE=10000
CONTRACT=1

# ---------- infra ----------
log() {
  mkdir -p "$LOGDIR" 2>/dev/null
  printf '%s %s\n' "$(date '+%m-%d %H:%M:%S')" "$*" >> "$LOG" 2>/dev/null
}

need_root() {
  if [ "$(id -u 2>/dev/null)" != "0" ]; then
    printf '{"error":"root_required"}\n'
    exit 3
  fi
}

ensure_runtime() {
  mkdir -p "$RUNDIR" "$SNAP" "$LOGDIR" 2>/dev/null
  chmod 0700 "$RUNDIR" "$SNAP" "$LOGDIR" 2>/dev/null
  if [ ! -f "$STATE" ]; then
    printf '{"profile":"balanced","adaptive":true,"reason":"","load":0.0,"tempC":0.0}\n' > "$STATE"
  fi
  chmod 0600 "$STATE" 2>/dev/null
}

# string field reader from $STATE
sg() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$STATE" 2>/dev/null | head -n1; }
# number field reader from $STATE
ng() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\([0-9.][0-9.]*\).*/\1/p" "$STATE" 2>/dev/null | head -n1; }

save_state() {
  _prof=$(sg profile);   [ -z "$_prof" ]   && _prof=balanced
  _adap=$(sg adaptive);  [ -z "$_adap" ]   && _adap=true
  _reason=$(sg reason);  [ -z "$_reason" ] && _reason=""
  _load=$(ng load);      [ -z "$_load" ]   && _load=0.0
  _tc=$(ng tempC);       [ -z "$_tc" ]      && _tc=0.0
  _tmp="$STATE.tmp"
  printf '{"profile":"%s","adaptive":%s,"reason":"%s","load":%s,"tempC":%s}\n' \
    "$_prof" "$_adap" "$_reason" "$_load" "$_tc" > "$_tmp"
  chmod 0600 "$_tmp" 2>/dev/null
  mv -f "$_tmp" "$STATE" 2>/dev/null
  chmod 0600 "$STATE" 2>/dev/null
}

# ---------- snapshot ----------
_snap_file_for() { echo "$1" | sed 's#^/##; s#/#_#g'; }

snap_path() {
  local node="$1"; local old="$2"
  local fn
  fn=$(_snap_file_for "$node")
  if [ ! -f "$SNAP/$fn" ]; then
    printf '%s' "$old" > "$SNAP/$fn" 2>/dev/null
    chmod 0600 "$SNAP/$fn" 2>/dev/null
    printf '%s\t%s\n' "$node" "$fn" >> "$SNAP/manifest.tsv" 2>/dev/null
  fi
}

# write a sysfs/procfs node, guarded + logged + snapshotted
# usage: write_node PATH VALUE
write_node() {
  local node="$1"; local new="$2"
  local old
  ensure_runtime
  if [ ! -e "$node" ]; then
    printf 'node=%s old= new=%s ok=0 reason=absent\n' "$node" "$new"
    return 1
  fi
  if [ ! -w "$node" ]; then
    printf 'node=%s old= new=%s ok=0 reason=readonly\n' "$node" "$new"
    return 1
  fi
  old=$(tr -d '\n\r' < "$node" 2>/dev/null)
  snap_path "$node" "$old"
  if printf '%s' "$new" > "$node" 2>/dev/null; then
    printf 'node=%s old=%s new=%s ok=1\n' "$node" "$old" "$new"
    log "write node=$node old=$old new=$new ok=1"
  else
    printf 'node=%s old=%s new=%s ok=0\n' "$node" "$old" "$new"
    log "write FAIL node=$node old=$old new=$new"
  fi
}

# restore every snapshot value; prints count restored
do_restore() {
  ensure_runtime
  local n=0 node fn orig
  if [ -f "$SNAP/manifest.tsv" ]; then
    while IFS=$(printf '\t') read -r node fn; do
      [ -z "$node" ] && continue
      orig=$(cat "$SNAP/$fn" 2>/dev/null)
      if [ -e "$node" ] && [ -w "$node" ]; then
        if printf '%s' "$orig" > "$node" 2>/dev/null; then
          n=$((n+1))
          log "restore node=$node -> $orig"
        fi
      fi
    done < "$SNAP/manifest.tsv"
  fi
  echo "$n"
}

wipe_runtime() { rm -rf "$RUNDIR" 2>/dev/null; }

# ---------- discovery -> capabilities.json ----------
# Only runs once per device (guarded by $CAP existence). Records the node map;
# application itself globs live paths so absent nodes are never written.
discover() {
  [ -f "$CAP" ] && return 0
  ensure_runtime
  local _c="$CAP.tmp"
  local first p pol up down c cc f d b zn z
  {
    printf '{\n'
    # policies + sugov_ext tunables
    printf '  "policies": ['
    first=1
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
      [ -d "$p" ] || continue
      pol=$(basename "$p" | sed 's/policy//')
      [ "$first" -eq 1 ] || printf ','
      first=0
      up="$p/sugov_ext/up_rate_limit_us"; down="$p/sugov_ext/down_rate_limit_us"
      printf '\n    {"policy":%s,"path":"%s","gov":"%s","min":"%s","max":"%s","cur":"%s","ag":"%s","af":"%s","fmin":"%s","fmax":"%s","sugovUp":"%s","sugovDown":"%s"}' \
        "$pol" "$p" \
        "$p/scaling_governor" "$p/scaling_min_freq" "$p/scaling_max_freq" \
        "$p/scaling_cur_freq" "$p/scaling_available_governors" \
        "$p/scaling_available_frequencies" "$p/cpuinfo_min_freq" "$p/cpuinfo_max_freq" \
        "$up" "$down"
    done
    printf '\n  ],\n'
    # cpus
    printf '  "cpus": ['
    first=1
    for c in /sys/devices/system/cpu/cpu[0-9]*; do
      [ -d "$c" ] || continue
      cn=$(basename "$c" | sed 's/cpu//')
      case "$cn" in *[!0-9]*) continue;; esac
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    {"cpu":%s,"online":"%s","corectl":"%s/core_ctl"}' "$cn" "$c/online" "$c"
    done
    printf '\n  ],\n'
    # core_ctl (per-cluster, root-only)
    printf '  "corectl": ['
    first=1
    for cc in /sys/devices/system/cpu/cpu[0-9]*/core_ctl; do
      [ -d "$cc" ] || continue
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    "%s"' "$cc"
    done
    printf '\n  ],\n'
    # sched EAS/PELT knobs
    printf '  "sched": ['
    first=1
    for f in /proc/sys/kernel/sched_*; do
      [ -f "$f" ] || continue
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    "%s"' "$f"
    done
    printf '\n  ],\n'
    # schedtune boost
    printf '  "schedtune": ['
    first=1
    for f in /dev/cpuctl/cpu.schedtune.boost /dev/cpuctl/foreground/cpu.schedtune.boost; do
      [ -e "$f" ] || continue
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    "%s"' "$f"
    done
    printf '\n  ],\n'
    # boost modules (recorded; touch_boost/oplus_boost_pool have no standard enabled knob -> skipped)
    printf '  "boost": ['
    first=1
    for f in /sys/module/*boost*/parameters/enabled; do
      [ -e "$f" ] || continue
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    "%s"' "$f"
    done
    printf '\n  ],\n'
    # MTK HPS: known absent on this device; recorded only if it appears
    printf '  "hps": ['
    if [ -d /proc/hps ]; then
      for f in /proc/hps/*; do [ -e "$f" ] && printf '\n    "%s"' "$f"; done
    fi
    printf '\n  ],\n'
    # block (UFS sda/sdb/sdc; scheduler left as stock)
    printf '  "block": ['
    first=1
    for d in /sys/block/*; do
      [ -d "$d" ] || continue
      b=$(basename "$d")
      case "$b" in loop*|ram*|zram*|dm-*|md*|bd*) continue;; esac
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    {"dev":"%s","scheduler":"%s","ra":"%s"}' \
        "$b" "$d/queue/scheduler" "$d/queue/read_ahead_kb"
    done
    printf '\n  ],\n'
    # vm
    printf '  "vm": {"swappiness": "/proc/sys/vm/swappiness"},\n'
    # GPU: MTK /proc/gpufreqv2 only (root-only)
    printf '  "gpu": ['
    first=1
    for f in /proc/gpufreqv2/*; do
      [ -e "$f" ] || continue
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    "%s"' "$f"
    done
    printf '\n  ],\n'
    # thermal zones (read only; safety thermal never disabled)
    printf '  "thermal": ['
    first=1
    for z in /sys/class/thermal/thermal_zone[0-9]*; do
      [ -d "$z" ] || continue
      zn=$(basename "$z" | sed 's/thermal_zone//')
      [ "$first" -eq 1 ] || printf ','
      first=0
      printf '\n    {"zone":%s,"temp":"%s","type":"%s"}' "$zn" "$z/temp" "$z/type"
    done
    printf '\n  ]\n'
    printf '}\n'
  } > "$_c"
  chmod 0600 "$_c" 2>/dev/null
  mv -f "$_c" "$CAP" 2>/dev/null
  chmod 0600 "$CAP" 2>/dev/null
  log "discover wrote capabilities.json"
}

# ---------- profile application ----------
# pick_gov AVAIL PREF1 PREF2 ... -> first available governor, else first listed
pick_gov() {
  local avail="$1"; shift
  local want
  for want in "$@"; do
    case " $avail " in *" $want "*) echo "$want"; return 0;; esac
  done
  # governor list is space-separated on purpose; pick the first listed
  # shellcheck disable=SC2086
  set -- $avail; echo "$1"
}

# sugov_ext rate-limit tunables per policy
apply_sugov_tunables() {
  local p="$1"; local prof="$2"
  local up down
  up="$p/sugov_ext/up_rate_limit_us"; down="$p/sugov_ext/down_rate_limit_us"
  { [ -e "$up" ] || [ -e "$down" ]; } || return 0
  case "$prof" in
    powersave)
      [ -e "$up" ]   && write_node "$up"   "20000"   # slow ramp up
      [ -e "$down" ] && write_node "$down" "0"        # quick drop
      ;;
    balanced)
      [ -e "$up" ]   && write_node "$up"   "0"        # stock
      [ -e "$down" ] && write_node "$down" "1000"     # stock
      ;;
    performance)
      [ -e "$up" ]   && write_node "$up"   "0"        # instant ramp
      [ -e "$down" ] && write_node "$down" "50000"     # hold freq
      ;;
    game)
      [ -e "$up" ]   && write_node "$up"   "0"
      [ -e "$down" ] && write_node "$down" "20000"
      ;;
  esac
}

apply_cpufreq_policy() {
  local p="$1"; local prof="$2"
  local gov mn mx avail fmin fmax g mid
  gov="$p/scaling_governor"; mn="$p/scaling_min_freq"; mx="$p/scaling_max_freq"
  [ -w "$gov" ] || return 0
  avail=$(tr '\n' ' ' < "$p/scaling_available_governors" 2>/dev/null)
  fmin=$(tr -d '\n' < "$p/cpuinfo_min_freq" 2>/dev/null)
  fmax=$(tr -d '\n' < "$p/cpuinfo_max_freq" 2>/dev/null)
  case "$fmin" in ''|*[!0-9]*) fmin=0;; esac
  case "$fmax" in ''|*[!0-9]*) fmax=0;; esac
  case "$prof" in
    powersave)
      g=$(pick_gov "$avail" powersave conservative schedutil)
      write_node "$gov" "$g"
      [ "$fmin" -gt 0 ] && write_node "$mn" "$fmin"
      [ "$fmin" -gt 0 ] && write_node "$mx" "$fmin"      # cap to min
      ;;
    balanced)
      g=$(pick_gov "$avail" sugov_ext schedutil scx)       # stock on this device
      write_node "$gov" "$g"
      [ "$fmin" -gt 0 ] && write_node "$mn" "$fmin"
      [ "$fmax" -gt 0 ] && write_node "$mx" "$fmax"       # full range
      ;;
    performance)
      g=$(pick_gov "$avail" performance schedutil)
      write_node "$gov" "$g"
      [ "$fmax" -gt 0 ] && write_node "$mn" "$fmax"       # floor = max
      [ "$fmax" -gt 0 ] && write_node "$mx" "$fmax"
      ;;
    game)
      g=$(pick_gov "$avail" performance schedutil)
      write_node "$gov" "$g"
      if [ "$fmax" -gt 0 ]; then
        mid=$(( (fmin + fmax) / 2 )); [ "$mid" -lt 1 ] && mid=$fmax
        write_node "$mn" "$mid"                            # sustained floor
        write_node "$mx" "$fmax"
      fi
      ;;
  esac
  apply_sugov_tunables "$p" "$prof"
}

apply_block() {
  local prof="$1"
  local ra d node
  case "$prof" in
    powersave) ra=128 ;;
    balanced) ra=1024 ;;        # matches stock sda/sdb
    performance|game) ra=2048 ;;
  esac
  for d in /sys/block/sd*; do
    [ -d "$d" ] || continue
    node="$d/queue/read_ahead_kb"
    [ -e "$node" ] && write_node "$node" "$ra"
  done
}

apply_vm() {
  local node=/proc/sys/vm/swappiness
  local v
  [ -e "$node" ] || return 0
  case "$1" in
    powersave) v=160 ;;          # more aggressive reclaim on battery
    balanced) v=125 ;;           # this device's OEM stock
    performance|game) v=100 ;;   # lighter swap penalty
  esac
  write_node "$node" "$v"
}

apply_schedtune() {
  local v node
  case "$1" in
    powersave|balanced) v=0 ;;
    performance|game) v=1 ;;
  esac
  for node in /dev/cpuctl/cpu.schedtune.boost /dev/cpuctl/foreground/cpu.schedtune.boost; do
    [ -e "$node" ] && write_node "$node" "$v"
  done
}

# GPU DVFS: MTK /proc/gpufreqv2 only. The inventory shows status/opp/limit
# tables but no confirmed writable min/max setter; only write a node if a known
# setter name exists AND is writable. Never guess.
apply_gpu() {
  local prof="$1"
  local node
  case "$prof" in
    game) ;;
    *) return 0 ;;
  esac
  for node in \
    /proc/gpufreqv2/gpufreq_min_freq \
    /proc/gpufreqv2/gpu_min_freq \
    /proc/gpufreqv2/gpufreq_min_power_limit ; do
    [ -e "$node" ] && [ -w "$node" ] && write_node "$node" "338000"
  done
}

apply_profile() {
  local prof="$1"
  case "$prof" in
    powersave|balanced|performance|game) ;;
    *) log "apply_profile bad=$prof"; return 1 ;;
  esac
  log "apply_profile $prof"
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    apply_cpufreq_policy "$p" "$prof"
  done
  apply_block "$prof"
  apply_vm "$prof"
  apply_schedtune "$prof"
  apply_gpu "$prof"
  # MTK HPS is ABSENT on this device; we never touch /proc/hps.
}

# snapshot current values of every tracked candidate node (no change applied)
snapshot_all() {
  ensure_runtime
  local n=0 p f node v
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    for f in scaling_governor scaling_min_freq scaling_max_freq; do
      node="$p/$f"
      [ -e "$node" ] || continue
      v=$(tr -d '\n\r' < "$node" 2>/dev/null)
      snap_path "$node" "$v"; n=$((n+1))
    done
    for f in sugov_ext/up_rate_limit_us sugov_ext/down_rate_limit_us; do
      node="$p/$f"; [ -e "$node" ] || continue
      snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)"; n=$((n+1))
    done
  done
  for d in /sys/block/sd*; do
    [ -d "$d" ] || continue
    node="$d/queue/read_ahead_kb"; [ -e "$node" ] || continue
    snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)"; n=$((n+1))
  done
  if [ -e /proc/sys/vm/swappiness ]; then
    snap_path /proc/sys/vm/swappiness "$(tr -d '\n\r' < /proc/sys/vm/swappiness 2>/dev/null)" && n=$((n+1))
  fi
  for node in /dev/cpuctl/cpu.schedtune.boost /dev/cpuctl/foreground/cpu.schedtune.boost; do
    [ -e "$node" ] && snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)" && n=$((n+1))
  done
  echo "$n"
}

# ---------- readers for status ----------
gprop() { getprop "$1" 2>/dev/null; }

memfield() { awk -v k="$1" '$1==k":"{print $2}' /proc/meminfo 2>/dev/null | head -n1; }

max_temp_c() {
  local best=-1 z t
  for z in /sys/class/thermal/thermal_zone[0-9]*/temp; do
    [ -f "$z" ] || continue
    t=$(tr -d '\n' < "$z" 2>/dev/null)
    case "$t" in ''|*[!0-9]*) continue ;; esac
    [ "$t" -gt "$best" ] 2>/dev/null && best=$t
  done
  if [ "$best" -gt 0 ] 2>/dev/null; then
    awk -v b="$best" 'BEGIN{printf "%.1f", b/1000}'
  else
    echo "0.0"
  fi
}

# two-sample average total cpu util 0..100
cpu_util_pct() {
  local s1 s2 t1 i1 t2 i2 dt di
  s1=$(awk '/^cpu /{t=0; for(i=2;i<=NF;i++)t+=$i; print t, $5+$6}' /proc/stat)
  # sample pair is whitespace-separated on purpose
  # shellcheck disable=SC2086
  set -- $s1; t1=$1; i1=$2
  sleep 0.2
  s2=$(awk '/^cpu /{t=0; for(i=2;i<=NF;i++)t+=$i; print t, $5+$6}' /proc/stat)
  # shellcheck disable=SC2086
  set -- $s2; t2=$1; i2=$2
  dt=$((t2 - t1)); di=$((i2 - i1))
  if [ "$dt" -gt 0 ] 2>/dev/null; then
    awk -v dt="$dt" -v di="$di" 'BEGIN{printf "%.1f", (1 - di/dt)*100}'
  else
    echo "-1.0"
  fi
}

fg_app() {
  local a
  a=$(dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp' | sed -E 's/.*[ \/]([A-Za-z0-9_.]+)\/.*/\1/')
  [ -z "$a" ] && a=$(dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|ResumedActivity' | sed -E 's/.*[ \/]([A-Za-z0-9_.]+)\/.*/\1/')
  echo "$a"
}

is_game_pkg() {
  case "$1" in
    *miHoYo*|*mihoyo*|*tencent*|*pubg*|*netease*|*game*) echo 1;;
    *) echo 0;;
  esac
}

num_cores() {
  local n lo hi
  n=$(cat /sys/devices/system/cpu/present 2>/dev/null)
  case "$n" in *-*) lo=${n%-*}; hi=${n#*-}; echo $((hi - lo + 1));; *) echo 1;; esac
}
