#!/system/bin/sh
# Verify the BOOT-started daemon is actively looping.
kill 30916 2>/dev/null && echo "killed manual 30916"
BOOTPID=2140
echo "== boot daemon alive? =="
kill -0 $BOOTPID 2>/dev/null && echo "2140 ALIVE" || echo "2140 DEAD"
cat /proc/$BOOTPID/cmdline | tr '\0' ' '; echo
echo "== state sample 1 =="
cat /data/adb/ace5ultra_perfkit/state.json; echo
sleep 8
echo "== state sample 2 (8s later; load/tempC should move) =="
cat /data/adb/ace5ultra_perfkit/state.json; echo
sleep 8
echo "== state sample 3 =="
cat /data/adb/ace5ultra_perfkit/state.json; echo
echo "== recent adaptive loop log =="
grep -E "adaptive loop|adaptive boot" /data/adb/ace5ultra_perfkit/logs/perfkit.log | tail -n 8
echo DONE
