#!/system/bin/sh
# service.sh - late service, started by the root manager after boot.
# Starts perfd which restores the last profile and runs the adaptive loop.
#
# Daemon survival: the Magisk/KSU service event kills the whole process group
# when it ends, and plain nohup does NOT protect against that. We detach perfd
# into its own session with setsid (when available), with stdin/stdout/stderr
# fully redirected away, so it survives after the service process exits.

MODDIR=${0%/*}

log() {
  mkdir -p /data/adb/ace5ultra_perfkit/logs 2>/dev/null
  printf '%s service: %s\n' "$(date '+%m-%d %H:%M:%S')" "$*" >> /data/adb/ace5ultra_perfkit/logs/perfkit.log 2>/dev/null
}

# Belt-and-suspenders: harden the executable modes (installer already set them).
chmod 0700 "$MODDIR/bin/perfctl" "$MODDIR/bin/perfd" 2>/dev/null
chmod 0600 "$MODDIR/bin/perflib.sh" 2>/dev/null

RUNDIR=/data/adb/ace5ultra_perfkit
PIDFILE="$RUNDIR/perfd.pid"
APK="$MODDIR/app/PerfKit.apk"
APKVC="$MODDIR/app/version_code.txt"
APKMARKER="$RUNDIR/apk_applied.vc"
PKG=com.ace5ultra.perfkit

# ---- joint app update: install bundled APK only if it is newer than the
# installed user app. Never downgrade; never force-install if absent; one-shot
# per bundled versionCode; bounded by timeout.
if [ -f "$APK" ] && [ -f "$APKVC" ]; then
  bvc=$(tr -d '\r\n' < "$APKVC" 2>/dev/null)
  if [ -n "$bvc" ] && [ "$(cat "$APKMARKER" 2>/dev/null)" != "$bvc" ]; then
    ivc=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 'versionCode=' | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p')
    if [ -n "$ivc" ]; then
      if [ "$bvc" -gt "$ivc" ] 2>/dev/null; then
        log "app update: installed=$ivc bundled=$bvc -> installing"
        if timeout 180 pm install -r "$APK" >> "$RUNDIR/logs/perfkit.log" 2>&1; then
          log "app update: install ok"
        else
          log "app update: install failed"
        fi
      else
        log "app update: bundled=$bvc not newer than installed=$ivc; skip"
      fi
    else
      log "app update: app not installed; leaving it (no force install)"
    fi
    echo "$bvc" > "$APKMARKER" 2>/dev/null
  fi
fi

# Is a PID really our perfd? Check BOTH liveness AND that /proc/PID/cmdline
# actually contains bin/perfd. A stale pidfile can hold a PID reused by an
# unrelated system service (e.g. oplus_gaia), which would otherwise fool
# `kill -0` into thinking we're already running.
perfd_alive() {
  pid="$1"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q "bin/perfd" || return 1
  return 0
}

# Already running?
if [ -f "$PIDFILE" ]; then
  old=$(cat "$PIDFILE" 2>/dev/null)
  if perfd_alive "$old"; then
    exit 0
  fi
  # stale pidfile (PID reused by another process, or perfd died): drop it
  rm -f "$PIDFILE" 2>/dev/null
fi

# Launch detached. Prefer setsid (new session, immune to the service group kill);
# fall back to plain nohup + full redirection if setsid is absent. perfd itself
# rewrites the pidfile once it is up, so we don't trust $! here.
if command -v setsid >/dev/null 2>&1; then
  setsid "$MODDIR/bin/perfd" </dev/null >/dev/null 2>&1 &
else
  nohup "$MODDIR/bin/perfd" </dev/null >/dev/null 2>&1 &
fi
