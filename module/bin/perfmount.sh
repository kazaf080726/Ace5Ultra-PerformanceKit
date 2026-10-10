#!/system/bin/sh
# shellcheck shell=dash
# shellcheck disable=SC3043
# perfmount.sh - decide and perform the read-optimization mount setup.
# Called BOTH at flash (customize.sh) and at boot (post-fs-data.sh).
#
# Two modes:
#   external - another enabled module is a mount provider (carries the marker
#              file perfkit-mount-provider OR exposes /data/adb/perfkit-mount/
#              overrides.d). We drop our override fragments there and let IT
#              perform the mounts; we do not mount anything ourselves.
#   local    - no provider: we mount our own tmpfs at /dev/perfkit (0755),
#              generate a static world-readable topology.json once at boot.
#
# Hard rules: only touch our own /dev/perfkit and the runtime dir; NEVER mount
# over /system /vendor /dev/cpuset or other critical paths; non-persistent;
# every mount logged ok/fail; failures degrade to no-mount (direct node writes
# still work). Decision persisted to $RUNDIR/mount.mode.

RUNDIR=/data/adb/ace5ultra_perfkit
LOG="$RUNDIR/logs/perfkit.log"
SELF_ID=ace5ultra_perfkit
OVRDIR=/data/adb/perfkit-mount/overrides.d

log() {
  mkdir -p "$RUNDIR/logs" 2>/dev/null
  printf '%s perfmount: %s\n' "$(date '+%m-%d %H:%M:%S')" "$*" >> "$LOG" 2>/dev/null
}

# is a module dir enabled? (Magisk/KSU: no "disable" file, not in update mode)
mod_enabled() {
  d="$1"
  [ -f "$d/disable" ] && return 1
  [ -f "$d/remove" ] && return 1
  return 0
}

# look for an external provider among OTHER enabled modules
find_provider() {
  local d id
  # explicit shared include directory wins
  if [ -d "$OVRDIR" ]; then
    # find which module owns it (best effort: report 'shared')
    echo shared; return 0
  fi
  for d in /data/adb/modules/*; do
    [ -d "$d" ] || continue
    id=$(basename "$d")
    [ "$id" = "$SELF_ID" ] && continue
    mod_enabled "$d" || continue
    if [ -f "$d/perfkit-mount-provider" ]; then
      echo "$id"; return 0
    fi
  done
  echo ""
}

# static topology -> /dev/perfkit/topology.json (0644 world-readable)
build_topology() {
  local out=/dev/perfkit/topology.json
  local brand model device soc android ncores
  brand=$(getprop ro.product.brand 2>/dev/null)
  model=$(getprop ro.product.model 2>/dev/null)
  device=$(getprop ro.product.device 2>/dev/null)
  soc=$(getprop ro.board.platform 2>/dev/null)
  android=$(getprop ro.build.version.release 2>/dev/null)
  ncores=$(cat /sys/devices/system/cpu/present 2>/dev/null)
  {
    printf '{\n'
    printf ' "brand":"%s","model":"%s","device":"%s","soc":"%s","android":"%s","presentCpus":"%s",\n' \
      "$brand" "$model" "$device" "$soc" "$android" "$ncores"
    # policies
    printf ' "policies":['
    first=1
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
      [ -d "$p" ] || continue
      pol=$(basename "$p" | sed 's/policy//')
      [ "$first" -eq 1 ] || printf ','
      first=0
      cmin=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
      cmax=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
      govs=$(tr '\n' ' ' < "$p/scaling_available_governors" 2>/dev/null)
      govs_json=$(echo "$govs" | sed 's/^/"/;s/$/"/;s/ /","/g')
      printf '\n  {"policy":%d,"minKhz":%s,"maxKhz":%s,"governors":[%s]}' \
        "$pol" "${cmin:-0}" "${cmax:-0}" "$govs_json"
    done
    printf '],\n'
    # GPU OPP table bounds
    gmin=0; gmax=0
    if [ -f /proc/gpufreqv2/gpu_working_opp_table ]; then
      gmin=$(awk 'match($0,/freq: *[0-9]+/){s=substr($0,RSTART,RLENGTH); sub(/freq: */,"",s); last=s} END{print last}' /proc/gpufreqv2/gpu_working_opp_table 2>/dev/null)
      gmax=$(awk 'match($0,/freq: *[0-9]+/){s=substr($0,RSTART,RLENGTH); sub(/freq: */,"",s); print s; exit}' /proc/gpufreqv2/gpu_working_opp_table 2>/dev/null)
    fi
    # RAM / zram
    mt=$(awk '/MemTotal:/{print $2}' /proc/meminfo 2>/dev/null)
    zs=$(cat /sys/block/zram0/disksize 2>/dev/null)
    printf ' "gpu":{"minKhz":%s,"maxKhz":%s},\n' "${gmin:-0}" "${gmax:-0}"
    printf ' "ramTotalBytes":%s,"zramBytes":%s\n' "$(( ${mt:-0} * 1024 ))" "${zs:-0}"
    printf '}\n'
  } > "$out"
  chmod 0644 "$out" 2>/dev/null
  log "topology.json written to $out"
}

do_local() {
  mkdir -p /dev/perfkit 2>/dev/null
  if mount -t tmpfs tmpfs /dev/perfkit 2>/dev/null; then
    chmod 0755 /dev/perfkit 2>/dev/null
    log "local mount tmpfs /dev/perfkit ok"
  else
    # already mounted or failed: continue if the dir exists
    log "local mount /dev/perfkit: $(mountpoint /dev/perfkit >/dev/null 2>&1 && echo 'already' || echo 'fail')"
  fi
  [ -d /dev/perfkit ] && build_topology
  printf 'local\nself\n' > "$RUNDIR/mount.mode" 2>/dev/null
  chmod 0600 "$RUNDIR/mount.mode" 2>/dev/null
}

do_external() {
  local prov="$1"
  # drop a trivial marker fragment into the provider's overrides dir. We do NOT
  # mount ourselves; the provider performs the mounts.
  mkdir -p "$OVRDIR" 2>/dev/null
  if [ -d "$OVRDIR" ]; then
    printf '# perfkit read-overlay\n' > "$OVRDIR/ace5ultra_perfkit.list" 2>/dev/null
    log "external: dropped override fragment into $OVRDIR (provider=$prov)"
  else
    log "external: provider=$prov but $OVRDIR unavailable; degrading to no-mount"
  fi
  printf 'external\n%s\n' "$prov" > "$RUNDIR/mount.mode" 2>/dev/null
  chmod 0600 "$RUNDIR/mount.mode" 2>/dev/null
}

main() {
  mkdir -p "$RUNDIR/logs" 2>/dev/null
  chmod 0700 "$RUNDIR" "$RUNDIR/logs" 2>/dev/null
  prov=$(find_provider)
  if [ -n "$prov" ]; then
    do_external "$prov"
  else
    do_local
  fi
  log "mount decision: $(tr '\n' ' ' < "$RUNDIR/mount.mode" 2>/dev/null)"
}

main "$@"
