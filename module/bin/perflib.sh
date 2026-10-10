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
VERSION="1.0.1"
VERSIONCODE=10100
CONTRACT=2

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
# boolean reader from $STATE (JSON booleans are unquoted true/false)
read_bool() { if grep -q "\"$1\"[[:space:]]*:[[:space:]]*true" "$STATE" 2>/dev/null; then echo true; else echo false; fi; }

save_state() {
  _prof=$(sg profile);   [ -z "$_prof" ]   && _prof=balanced
  _adap=$(read_bool adaptive)
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

# node_mode PATH -> octal permission bits (e.g. 644), empty on failure
node_mode() { stat -c '%a' "$1" 2>/dev/null; }

# snap_path NODE OLDVAL MODE
# Records the original value (and original mode, for nodes the vendor locks to
# 0444) so restore can put both back. Manifest line: node<TAB>fn<TAB>mode
snap_path() {
  local node="$1"; local old="$2"; local mode="$3"
  local fn
  fn=$(_snap_file_for "$node")
  if [ ! -f "$SNAP/$fn" ]; then
    printf '%s' "$old" > "$SNAP/$fn" 2>/dev/null
    chmod 0600 "$SNAP/$fn" 2>/dev/null
    printf '%s\t%s\t%s\n' "$node" "$fn" "$mode" >> "$SNAP/manifest.tsv" 2>/dev/null
  fi
}

# write a sysfs/procfs node, guarded + logged + snapshotted.
# If the node is read-only (vendor locks cpufreq governor/min/max to 0444 after
# boot), root chmods it to 0644, writes, then restores the original mode.
# usage: write_node PATH VALUE
write_node() {
  local node="$1"; local new="$2"
  local old omode need_chmod
  ensure_runtime
  if [ ! -e "$node" ]; then
    printf 'node=%s old= new=%s ok=0 reason=absent\n' "$node" "$new"
    return 1
  fi
  omode=$(node_mode "$node")
  need_chmod=0
  # root bypasses [ -w ], so inspect the owner-write bit directly. The vendor
  # locks cpufreq governor/min/max to 0444 after boot; unlock with chmod 0644.
  case "$omode" in
    [2367]??) : ;;            # owner already has write (x2x/x3x/x6x/x7x)
    *)
      chmod 0644 "$node" 2>/dev/null && need_chmod=1
      ;;
  esac
  # if we could not unlock and the node truly is not writable, skip
  if [ "$need_chmod" -eq 0 ] && [ ! -w "$node" ]; then
    printf 'node=%s old= new=%s ok=0 reason=readonly\n' "$node" "$new"
    return 1
  fi
  old=$(tr -d '\n\r' < "$node" 2>/dev/null)
  snap_path "$node" "$old" "$omode"
  if printf '%s' "$new" > "$node" 2>/dev/null; then
    printf 'node=%s old=%s new=%s ok=1\n' "$node" "$old" "$new"
    log "write node=$node old=$old new=$new ok=1 mode=$omode unlocked=$need_chmod"
  else
    printf 'node=%s old=%s new=%s ok=0\n' "$node" "$old" "$new"
    log "write FAIL node=$node old=$old new=$new"
  fi
  # restore the vendor's original mode so we never leave it writable
  if [ "$need_chmod" -eq 1 ] && [ -n "$omode" ]; then
    chmod "$omode" "$node" 2>/dev/null
  fi
}

# restore every snapshot value (and mode); prints count restored
do_restore() {
  ensure_runtime
  local n=0 node fn mode orig
  if [ -f "$SNAP/manifest.tsv" ]; then
    while IFS=$(printf '\t') read -r node fn mode; do
      [ -z "$node" ] && continue
      orig=$(cat "$SNAP/$fn" 2>/dev/null)
      if [ -e "$node" ]; then
        # unlock if it was locked, restore the value, then restore its mode
        [ -n "$mode" ] && chmod 0644 "$node" 2>/dev/null
        if printf '%s' "$orig" > "$node" 2>/dev/null; then
          n=$((n+1))
          log "restore node=$node -> $orig mode=$mode"
        fi
        [ -n "$mode" ] && chmod "$mode" "$node" 2>/dev/null
      fi
    done < "$SNAP/manifest.tsv"
  fi
  restore_display_refresh
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

# sugov_ext rate-limit tunables per policy. Except powersave, every profile keeps
# the CPU allowed to ramp up instantly (up_rate_limit=0 = fastest).
apply_sugov_tunables() {
  local p="$1"; local prof="$2"
  local up down
  up="$p/sugov_ext/up_rate_limit_us"; down="$p/sugov_ext/down_rate_limit_us"
  { [ -e "$up" ] || [ -e "$down" ]; } || return 0
  case "$prof" in
    powersave)
      [ -e "$up" ]   && write_node "$up"   "20000"   # slow ramp up (relaxed)
      [ -e "$down" ] && write_node "$down" "0"        # quick drop
      ;;
    balanced)
      [ -e "$up" ]   && write_node "$up"   "0"        # instant ramp up
      [ -e "$down" ] && write_node "$down" "1000"     # stock down-rate
      ;;
    performance)
      [ -e "$up" ]   && write_node "$up"   "0"
      [ -e "$down" ] && write_node "$down" "50000"     # hold freq once up
      ;;
    game)
      [ -e "$up" ]   && write_node "$up"   "0"
      [ -e "$down" ] && write_node "$down" "20000"
      ;;
  esac
}

# freq_fraction FMIN FMAX FRAC_KEEP -> floor freq = min + (max-min)*frac (kHz)
freq_floor() {
  awk -v lo="$1" -v hi="$2" -v f="$3" 'BEGIN{
    lo=lo+0; hi=hi+0
    if (hi<=lo) { print lo; exit }
    printf "%d", lo + (hi-lo)*f
  }'
}

apply_cpufreq_policy() {
  local p="$1"; local prof="$2"
  local gov mn mx avail fmin fmax g pol mid_prime
  gov="$p/scaling_governor"; mn="$p/scaling_min_freq"; mx="$p/scaling_max_freq"
  pol=$(basename "$p" | sed 's/policy//')
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
      [ "$fmin" -gt 0 ] && write_node "$mx" "$fmin"      # cap to efficient min
      ;;
    balanced)
      g=$(pick_gov "$avail" sugov_ext schedutil scx)       # stock EAS governor
      write_node "$gov" "$g"
      [ "$fmin" -gt 0 ] && write_node "$mn" "$fmin"        # min stays stock
      [ "$fmax" -gt 0 ] && write_node "$mx" "$fmax"        # unlock hw ceiling
      ;;
    performance)
      g=$(pick_gov "$avail" performance schedutil)
      write_node "$gov" "$g"
      [ "$fmax" -gt 0 ] && write_node "$mx" "$fmax"
      # raise min floor only on mid (policy4) / prime (policy7), little cluster stays stock
      case "$pol" in
        4) [ "$fmin" -gt 0 ] && write_node "$mn" "$(freq_floor "$fmin" "$fmax" 0.35)" ;;
        7) [ "$fmin" -gt 0 ] && write_node "$mn" "$(freq_floor "$fmin" "$fmax" 0.40)" ;;
        *) [ "$fmin" -gt 0 ] && write_node "$mn" "$fmin" ;;
      esac
      ;;
    game)
      g=$(pick_gov "$avail" performance schedutil)
      write_node "$gov" "$g"
      [ "$fmax" -gt 0 ] && write_node "$mx" "$fmax"
      # high sustained floor on mid/prime
      case "$pol" in
        4) [ "$fmin" -gt 0 ] && write_node "$mn" "$(freq_floor "$fmin" "$fmax" 0.55)" ;;
        7) [ "$fmin" -gt 0 ] && write_node "$mn" "$(freq_floor "$fmin" "$fmax" 0.65)" ;;
        *) [ "$fmin" -gt 0 ] && write_node "$mn" "$fmin" ;;
      esac
      ;;
  esac
  apply_sugov_tunables "$p" "$prof"
}

# block: scheduler is best-effort (only write if the target is offered by the
# device); read-ahead is written directly.
apply_block() {
  local prof="$1"
  local ra d schednode avail want
  case "$prof" in
    powersave) ra=128 ;;
    balanced)  ra=1024 ;;        # matches stock sda/sdb
    performance) ra=512 ;;       # low RA
    game)      ra=256 ;;          # smallest RA, lowest latency
  esac
  for d in /sys/block/sd*; do
    [ -d "$d" ] || continue
    node="$d/queue/read_ahead_kb"
    [ -e "$node" ] && write_node "$node" "$ra"
    schednode="$d/queue/scheduler"
    [ -e "$schednode" ] || continue
    avail=$(cat "$schednode" 2>/dev/null)
    case "$prof" in
      performance) want="mq-deadline" ;;
      game)        want="none" ;;
      *)           want="" ;;
    esac
    if [ -n "$want" ]; then
      case " $avail " in *" $want "*) write_node "$schednode" "$want" ;; esac
    fi
  done
}

apply_vm() {
  local node=/proc/sys/vm/swappiness
  local v
  [ -e "$node" ] || return 0
  case "$1" in
    powersave) v=160 ;;          # aggressive reclaim on battery
    balanced)  v=125 ;;           # this device's OEM stock
    performance|game) v=60 ;;     # low swap penalty for perf
  esac
  write_node "$node" "$v"
}

apply_schedtune() {
  local v node
  case "$1" in
    powersave)        v=0 ;;
    balanced)         v=0 ;;      # stock on this device
    performance|game) v=1 ;;      # high boost
  esac
  for node in /dev/cpuctl/cpu.schedtune.boost /dev/cpuctl/foreground/cpu.schedtune.boost; do
    [ -e "$node" ] && write_node "$node" "$v"
  done
}

# GPU DVFS via the Mali devfreq (the writable setter on this platform). The
# /proc/gpufreqv2 tables are read-only; devfreq governor/min/max are the control.
# Only write nodes that exist AND are writable; skip and log otherwise.
apply_gpu() {
  local prof="$1"
  local dv govf minf maxf avail g floor
  case "$prof" in performance|game) ;; *) return 0 ;; esac
  dv=$(for x in /sys/class/devfreq/*mali*; do [ -d "$x" ] && { echo "$x"; break; }; done)
  [ -n "$dv" ] || return 0
  govf="$dv/governor"; minf="$dv/min_freq"; maxf="$dv/max_freq"
  avail=$(cat "$dv/available_governors" 2>/dev/null)
  g=$(pick_gov "$avail" performance simple_ondemand powersave)
  # devfreq freqs are in Hz. GPU OPP 338000..1612000 kHz -> 338000000..1612000000 Hz.
  case "$prof" in
    performance) floor=806000000 ;;   # ~50% OPP floor
    game)        floor=1128000000 ;;  # ~70% OPP floor
  esac
  [ -e "$govf" ] && [ -w "$govf" ] && write_node "$govf" "$g"
  [ -e "$minf" ] && [ -w "$minf" ] && write_node "$minf" "$floor"
}

# best-effort peak display refresh for game only. Uses the system settings provider
# (a global/system setting), snapshots the original value, and restores on switch.
# If `settings` is unavailable or the setting does not exist, skip quietly.
DISP_SNAP="$SNAP/display_refresh.tsv"
apply_display_refresh() {
  [ "$1" = "game" ] || return 0
  command -v settings >/dev/null 2>&1 || return 0
  [ -f "$DISP_SNAP" ] && return 0          # already snapshot+applied
  orig=$(settings get system peak_refresh_rate 2>/dev/null | tr -d '\r\n')
  case "$orig" in ''|null|*[!0-9.]*) orig="" ;; esac
  if [ -n "$orig" ]; then
    printf 'peak_refresh_rate\t%s\n' "$orig" > "$DISP_SNAP" 2>/dev/null
    chmod 0600 "$DISP_SNAP" 2>/dev/null
  fi
  if settings put system peak_refresh_rate 144.0 >/dev/null 2>&1; then
    log "display refresh -> 144.0 (best-effort)"
  else
    log "display refresh: set failed, skipped"
  fi
}

restore_display_refresh() {
  [ -f "$DISP_SNAP" ] || return 0
  command -v settings >/dev/null 2>&1 || return 0
  o=$(sed -n '1s/^peak_refresh_rate\t//p' "$DISP_SNAP" 2>/dev/null)
  if [ -n "$o" ]; then
    settings put system peak_refresh_rate "$o" >/dev/null 2>&1 \
      && log "display refresh restored -> $o"
  fi
  rm -f "$DISP_SNAP" 2>/dev/null
}

apply_profile() {
  local prof="$1"
  case "$prof" in
    powersave|balanced|performance|game) ;;
    *) log "apply_profile bad=$prof"; return 1 ;;
  esac
  log "apply_profile $prof"
  # leaving game -> restore display refresh first
  [ "$prof" != "game" ] && restore_display_refresh
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    apply_cpufreq_policy "$p" "$prof"
  done
  apply_block "$prof"
  apply_vm "$prof"
  apply_schedtune "$prof"
  apply_gpu "$prof"
  [ "$prof" = "game" ] && apply_display_refresh game
  # MTK HPS is ABSENT on this device; we never touch /proc/hps.
}

# snapshot current values of every tracked candidate node (no change applied)
snapshot_all() {
  ensure_runtime
  local n=0 p f node v m
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    for f in scaling_governor scaling_min_freq scaling_max_freq; do
      node="$p/$f"
      [ -e "$node" ] || continue
      v=$(tr -d '\n\r' < "$node" 2>/dev/null)
      m=$(node_mode "$node")
      snap_path "$node" "$v" "$m"; n=$((n+1))
    done
    for f in sugov_ext/up_rate_limit_us sugov_ext/down_rate_limit_us; do
      node="$p/$f"; [ -e "$node" ] || continue
      snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)" "$(node_mode "$node")"; n=$((n+1))
    done
  done
  for d in /sys/block/sd*; do
    [ -d "$d" ] || continue
    node="$d/queue/read_ahead_kb"; [ -e "$node" ] || continue
    snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)" "$(node_mode "$node")"; n=$((n+1))
  done
  if [ -e /proc/sys/vm/swappiness ]; then
    snap_path /proc/sys/vm/swappiness "$(tr -d '\n\r' < /proc/sys/vm/swappiness 2>/dev/null)" "$(node_mode /proc/sys/vm/swappiness)" && n=$((n+1))
  fi
  for node in /dev/cpuctl/cpu.schedtune.boost /dev/cpuctl/foreground/cpu.schedtune.boost; do
    [ -e "$node" ] && snap_path "$node" "$(tr -d '\n\r' < "$node" 2>/dev/null)" "$(node_mode "$node")" && n=$((n+1))
  done
  echo "$n"
}

# ---------- readers for status ----------
gprop() { getprop "$1" 2>/dev/null; }

memfield() { awk -v k="$1" '$1==k":"{print $2}' /proc/meminfo 2>/dev/null | head -n1; }

# Convert kilobytes to bytes without shell integer overflow (awk uses 64-bit
# doubles, exact for these magnitudes). Prints an integer.
kb2b() { awk -v k="$1" 'BEGIN{printf "%.0f", (k+0)*1024}'; }

max_temp_c() {
  local best=-1 z t
  for z in /sys/class/thermal/thermal_zone[0-9]*/temp; do
    [ -f "$z" ] || continue
    t=$(tr -d '\n' < "$z" 2>/dev/null)
    case "$t" in ''|*[!0-9]*) continue ;; esac
    # Ignore implausible "max" sensor reports (e.g. oled_temp=125000) -- real
    # board temps never exceed ~120C on this part. Keep the sane maximum.
    [ "$t" -gt 120000 ] 2>/dev/null && continue
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
  a=$(timeout 5 dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp' | sed -E 's/.*[ \/]([A-Za-z0-9_.]+)\/.*/\1/')
  [ -z "$a" ] && a=$(timeout 5 dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|ResumedActivity' | sed -E 's/.*[ \/]([A-Za-z0-9_.]+)\/.*/\1/')
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

# ---------- v1.0.1 status readers ----------
# topProcs: top 5 processes by CPU. Bounded by timeout; best-effort.
# Emits a JSON array fragment: {"pid":N,"name":"...","cpu":PCT},...
top_procs_json() {
  local out line pid name cpu
  out=$(timeout 3 top -b -n 1 2>/dev/null)
  [ -z "$out" ] && { echo "[]"; return; }
  echo "$out" | awk '
    /^[ ]*PID/ {hdr=1; next}
    hdr && /^[ ]*[0-9]+/ {
      # toybox top columns: USER PID ... %CPU ... NAME; pick numeric PID and last token
      pid=$2; cpu=""; name=""
      for (i=1;i<=NF;i++){ if ($i ~ /^[0-9]+(\.[0-9]+)?$/ && cpu=="") cpu=$i }
      name=$NF
      gsub(/[^A-Za-z0-9_.:_-]/,"",name)
      if (pid != "" && name != "") printf "{\"pid\":%d,\"name\":\"%s\",\"cpu\":%s},", pid, name, cpu
    }
  ' | head -c 4000
  # strip trailing comma -> wrap in array
  out=$(echo "$out" | sed 's/,$//')
  [ -z "$out" ] && out="[]" || out="[$out]"
  echo "$out"
}

# battery object fields. Missing -> -1.
battery_percent() { v=$(tr -d '\n\r' < /sys/class/power_supply/battery/capacity 2>/dev/null); case "$v" in ''|*[!0-9]*) echo -1;; *) echo "$v";; esac; }
battery_temp_c()   { v=$(tr -d '\n\r' < /sys/class/power_supply/battery/temp 2>/dev/null); case "$v" in ''|*[!0-9-]*) echo -1.0;; *) awk -v x="$v" 'BEGIN{printf "%.1f", x/10}';; esac; }
battery_voltage_uv() { v=$(tr -d '\n\r' < /sys/class/power_supply/battery/voltage_now 2>/dev/null); case "$v" in ''|*[!0-9-]*) echo -1;; *) echo "$v";; esac; }
battery_current_ua() { v=$(tr -d '\n\r' < /sys/class/power_supply/battery/current_now 2>/dev/null); case "$v" in ''|*[!0-9-]*) echo -1;; *) echo "$v";; esac; }

# GPU live frequencies in MHz (devfreq reports Hz). loadPercent=-1 (no util node).
gpu_devfreq_node() { for x in /sys/class/devfreq/*mali*; do [ -d "$x" ] && { echo "$x"; break; }; done; }
gpu_cur_mhz() {
  d=$(gpu_devfreq_node); [ -n "$d" ] || { echo 0; return; }
  v=$(tr -d '\n\r' < "$d/cur_freq" 2>/dev/null); case "$v" in ''|*[!0-9]*) echo 0;; *) echo $((v/1000000));; esac
}
gpu_min_mhz() {
  d=$(gpu_devfreq_node); [ -n "$d" ] || { echo 0; return; }
  v=$(tr -d '\n\r' < "$d/min_freq" 2>/dev/null); case "$v" in ''|*[!0-9]*) echo 0;; *) echo $((v/1000000));; esac
}
gpu_max_mhz() {
  d=$(gpu_devfreq_node); [ -n "$d" ] || { echo 0; return; }
  v=$(tr -d '\n\r' < "$d/max_freq" 2>/dev/null); case "$v" in ''|*[!0-9]*) echo 0;; *) echo $((v/1000000));; esac
}

# mount decision (written by perfmount.sh)
mount_mode()    { [ -f "$RUNDIR/mount.mode" ] && sed -n '1p' "$RUNDIR/mount.mode" 2>/dev/null || echo none; }
mount_provider() { [ -f "$RUNDIR/mount.mode" ] && sed -n '2p' "$RUNDIR/mount.mode" 2>/dev/null || echo none; }
topology_ready() { [ -r /dev/perfkit/topology.json ] && echo true || echo false; }
