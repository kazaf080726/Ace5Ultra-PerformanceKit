#!/system/bin/sh
# Runtime profile-switch verification: read real sysfs before/after each set.
P=/data/adb/modules/ace5ultra_perfkit/bin/perfctl
BASE=/sys/devices/system/cpu/cpufreq

snap() {
  for pol in policy0 policy4 policy7; do
    d=$BASE/$pol
    gov=$(cat "$d/scaling_governor" 2>/dev/null)
    mn=$(cat "$d/scaling_min_freq" 2>/dev/null)
    mx=$(cat "$d/scaling_max_freq" 2>/dev/null)
    echo "$pol gov=$gov min=$mn max=$mx"
  done
}

echo "### adaptive off for deterministic manual test"
"$P" adaptive off
sleep 1
echo "=== state: before explicit set ==="; snap

echo "### profile set performance"
"$P" profile set performance
sleep 2
echo "=== performance ==="; snap

echo "### profile set powersave"
"$P" profile set powersave
sleep 2
echo "=== powersave ==="; snap

echo "### profile set game"
"$P" profile set game
sleep 2
echo "=== game ==="; snap

echo "### profile set balanced"
"$P" profile set balanced
sleep 2
echo "=== balanced ==="; snap
echo "### switch test complete"
