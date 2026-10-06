#!/system/bin/sh
# Manual detached launch + cgroup observation (run any time, not at boot).
MOD=/data/adb/modules/ace5ultra_perfkit
RD=/data/adb/ace5ultra_perfkit
echo "== self cgroup (launcher) =="
cat /proc/self/cgroup

setsid "$MOD/bin/perfd" </dev/null >/dev/null 2>&1 &
PID=$!
echo "launched setsid pid=$PID"
sleep 2
echo "== perfd cgroup =="
cat /proc/$PID/cgroup 2>/dev/null || echo "pid gone at 2s"
kill -0 $PID 2>/dev/null && echo "alive at 2s" || echo "dead at 2s"
sleep 18
kill -0 $PID 2>/dev/null && echo "alive at 20s" || echo "dead at 20s"
echo "== ps match =="
ps -A | grep perfd | grep -v grep
echo DONE
