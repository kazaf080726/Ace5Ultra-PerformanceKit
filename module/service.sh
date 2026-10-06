#!/system/bin/sh
# service.sh - late service, started by the root manager after boot.
# Starts perfd which restores the last profile and runs the adaptive loop.

MODDIR=${0%/*}

# Belt-and-suspenders: harden the executable modes (installer already set them).
chmod 0700 "$MODDIR/bin/perfctl" "$MODDIR/bin/perfd" 2>/dev/null
chmod 0600 "$MODDIR/bin/perflib.sh" 2>/dev/null

# Background the daemon; it self-daemonizes and loops forever.
nohup "$MODDIR/bin/perfd" >/dev/null 2>&1 &
