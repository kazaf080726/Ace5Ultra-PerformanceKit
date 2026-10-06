#!/system/bin/sh
# post-fs-data.sh - early hook (post-fs-data). We do not tune here; cpufreq nodes
# are touched by perfd from service.sh. This only pre-creates the root-only runtime
# directory with correct modes so service.sh/perfd find it ready.

RUNDIR=/data/adb/ace5ultra_perfkit
mkdir -p "$RUNDIR/snapshot" "$RUNDIR/logs" 2>/dev/null
chmod 0700 "$RUNDIR" "$RUNDIR/snapshot" "$RUNDIR/logs" 2>/dev/null
