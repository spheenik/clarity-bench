# clarity-bench

Cross-version JMH benchmark harness for the [Clarity](https://github.com/skadistats/clarity) replay parser.

## What this is

One repository whose only job is benching Clarity across the long arc of releases. Each pinned version has its own subdirectory and bench-runs in isolation, so a v3.1.3 number and a v5.0.0 number on the same replay/JDK/hardware are directly comparable.

## Layout

```
harness/   shared bench code (no clarity imports)
v3.1.3/    pinned to released 3.1.3 from Maven Central
v4.0.0/    pinned to released 4.0.0 from Maven Central
v5.0.0/    pinned to 5.0.0-SNAPSHOT from mavenLocal (= the in-development candidate)
replays/   on-disk replay corpus (gitignored; manifest pinned by sha256)
results/   tracked baseline runs
work/      ad-hoc/exploratory output (gitignored)
```

## Running a bench

Run each version separately. Each `:vX.Y.Z:run` is a fresh JVM with that version's classpath.

```bash
# released versions
./gradlew :v3.1.3:run --args="--replays-root /home/spheenik/projects/replays"
./gradlew :v4.0.0:run --args="--replays-root /home/spheenik/projects/replays"

# candidate (5.x SNAPSHOT) — publish first
cd ../clarity && ./gradlew publishToMavenLocal && cd -
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays"
```

Add `--record` to persist a run into `results/`:

```bash
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays --record"
```

## Replay corpus policy

- `replays/MANIFEST.sha256` is **append-only**. A replay's hash, once recorded, is never changed.
- `BenchMain` refuses to run any replay whose actual sha256 doesn't match the manifest, or that isn't in the manifest at all.
- **Adding a replay obligates a backfill**: the new file must be benched against every version present in the repo, so the longitudinal matrix stays full.
- Each replay is tagged with its engine (S1, S2, DEADLOCK). Versions that don't support an engine silently skip those replays.

## Logging

Every adapter pins root logger to WARN. `BenchMain` refuses to start if anything below WARN is enabled, since debug logging would distort measurement.

## What "comparison" means here

These numbers reflect "what JDK 21 makes of clarity vX.Y.Z bytecode on this hardware." They are NOT a reconstruction of historical performance under the JDKs each version was originally built for. And later versions do strictly *more work per packet* than earlier ones (more decoded fields, more state) — a slowdown is "users get more for the same time," not a regression.

See `openspec/specs/cross-version-bench/spec.md` for the full requirement set.
