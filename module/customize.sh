#!/system/bin/sh
# customize.sh - install-time customization (run inside $MODPATH by the manager's
# embedded installer, which has already auto-extracted every non-META-INF path).
# We only tighten permissions; all runtime tuning is done by perfd/perfctl.

ui_print "- Ace5Ultra PerformanceKit"
ui_print "- Setting permissions"

# Default recursive perms (dir 0755 / file 0644)
set_perm_recursive "$MODPATH" 0 0 0755 0644

# Hardened root CLI + daemon: 0700 root, no group/world access
set_perm "$MODPATH/bin/perfctl" 0 0 0700
set_perm "$MODPATH/bin/perfd"   0 0 0700
set_perm "$MODPATH/bin/perfmount.sh" 0 0 0700
# shared library is sourced (not executed): 0600 root
set_perm "$MODPATH/bin/perflib.sh" 0 0 0600

ui_print "- Runtime dir /data/adb/ace5ultra_perfkit (0700 root) is created on first boot."
ui_print "- All tuning is reversible sysfs/procfs only; nothing touches boot/vendor/system."

# Detect mount provider now (re-confirmed at boot by post-fs-data.sh).
# Mounting itself is non-persistent and is (re)done at boot.
if [ -f "$MODPATH/bin/perfmount.sh" ]; then
  sh "$MODPATH/bin/perfmount.sh" 2>/dev/null
fi
