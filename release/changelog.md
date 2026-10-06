# Ace5Ultra-PerformanceKit v1.0.0

First release.

- Root module `ace5ultra_perfkit` flashable in SukiSU Ultra, BakaSU and KernelSU.
- Profiles: Powersave, Balanced, Performance, Game, Adaptive.
- Root controller (`perfctl`) and adaptive daemon (`perfd`) on a hardened root-only channel.
- Automatic snapshot and full restore on disable/uninstall; no partition modifications.
- Compose app: live 3–5s dashboard (per-core frequency/utilization history, memory, GPU,
  thermal, battery), liquid-glass UI, floating blurred bottom bar, predictive back.
- In-app and in-manager over-the-air updates.
