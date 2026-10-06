#!/system/bin/sh
# uninstall.sh - run when the module is removed.
# Restore every snapshot value back to sysfs/procfs, then remove the 0700
# runtime dir. Nothing was ever written to boot/vendor/system.

MODDIR=${0%/*}

# Restore originals (writes back every snapshot)
"$MODDIR/bin/perfctl" restore >/dev/null 2>&1

# Remove the root-only runtime dir (state, snapshot, logs, capabilities)
rm -rf /data/adb/ace5ultra_perfkit 2>/dev/null
