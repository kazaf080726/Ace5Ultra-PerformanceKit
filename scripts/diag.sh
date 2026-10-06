#!/system/bin/sh
# diag.sh - does root chmod/write stick on the locked cpufreq nodes, and does
# the vendor revert? Run as root on device. Logs every step to stdout.
P0=/sys/devices/system/cpu/cpufreq/policy0
MF=$P0/scaling_max_freq
GV=$P0/scaling_governor

stamp() { date '+%H:%M:%S'; }

echo "=== $(stamp) diag start ==="
echo "--- initial state ---"
ls -l "$MF" "$GV"
echo "max=$(cat $MF) gov=$(cat $GV)"

echo "--- [1] chmod 0644 max ---"
chmod 0644 "$MF"; echo "chmod rc=$?"; ls -l "$MF"
echo "--- [2] write back SAME max value ---"
cur=$(cat "$MF"); printf '%s' "$cur" > "$MF"; echo "write rc=$? (value $cur)"
echo "--- [3] chmod 0644 governor ---"
chmod 0644 "$GV"; echo "chmod rc=$?"; ls -l "$GV"
echo "--- [4] write back SAME governor ---"
gcur=$(cat "$GV"); printf '%s' "$gcur" > "$GV"; echo "write rc=$? (gov $gcur)"

echo "--- [5] poll mode+value every 3s for 30s ---"
i=0
while [ "$i" -lt 10 ]; do
  m=$(ls -l "$MF" | awk '{print $1}')
  v=$(cat "$MF" 2>/dev/null)
  gm=$(ls -l "$GV" | awk '{print $1}')
  gv2=$(cat "$GV" 2>/dev/null)
  echo "$(stamp) poll#$i max_mode=$m max=$v gov_mode=$gm gov=$gv2"
  i=$((i+1))
  sleep 3
done

echo "--- [6] restore modes to 0444 ---"
chmod 0444 "$MF"; echo "restore max rc=$?"
chmod 0444 "$GV"; echo "restore gov rc=$?"
ls -l "$MF" "$GV"
echo "=== $(stamp) diag done ==="
