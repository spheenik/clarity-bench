# Recorded baselines

Each subdirectory is one `--record`ed bench run.

Naming: `<YYYY-MM-DD>_<hardware-tag>_jdk<major>/`

Contents:
- `context.txt` — JDK, OS, CPU, RAM, JVM args, bench-repo git SHA, resolved clarity JAR sha256, replay manifest entries actually used
- `<version>.txt` — human-readable result table per version that ran
- `<version>.json` — machine-readable sidecar

Old result directories are immutable. Adding a replay produces a new directory (per the backfill rule); previous directories are left as frozen snapshots.
