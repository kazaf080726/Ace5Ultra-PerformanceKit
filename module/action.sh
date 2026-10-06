#!/system/bin/sh
# action.sh - manager "action" hook (tapped in the SukiSU/BakaSU/KSU module list).
# Cycles powersave -> balanced -> performance -> game -> powersave and applies it.

MODDIR=${0%/*}
PERFCTL="$MODDIR/bin/perfctl"

cur=$("$PERFCTL" profile get 2>/dev/null | sed -n 's/.*"profile":"\([^"]*\)".*/\1/p')
case "$cur" in
  powersave)  next=balanced ;;
  balanced)   next=performance ;;
  performance) next=game ;;
  *)          next=powersave ;;
esac

"$PERFCTL" profile set "$next" >/dev/null 2>&1
