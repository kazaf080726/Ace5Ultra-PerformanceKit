#!/system/bin/sh
# post-fs-data.sh - early hook (post-fs-data). Pre-creates the root-only runtime
# directory, then runs the mount detector (external provider vs local tmpfs).
# cpufreq tuning itself is done by perfd from service.sh.

MODDIR=${0%/*}
RUNDIR=/data/adb/ace5ultra_perfkit
mkdir -p "$RUNDIR/snapshot" "$RUNDIR/logs" 2>/dev/null
chmod 0700 "$RUNDIR" "$RUNDIR/snapshot" "$RUNDIR/logs" 2>/dev/null

# read-optimization mount decision (external provider or local /dev/perfkit)
[ -f "$MODDIR/bin/perfmount.sh" ] && sh "$MODDIR/bin/perfmount.sh" 2>/dev/null
