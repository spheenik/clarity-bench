# CLAUDE.md — clarity-bench

Cross-version JMH benchmark harness for the Clarity replay parser.

## Running a bench

Each version subproject runs in its own JVM with its own pinned classpath.

```bash
# released versions
./gradlew :v3.1.3:run --args="--replays-root /home/spheenik/projects/replays"
./gradlew :v4.0.0:run --args="--replays-root /home/spheenik/projects/replays"

# snapshot candidate — publish to mavenLocal first
cd ../clarity && ./gradlew publishToMavenLocal && cd -
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays"
```

Add `--record` to persist results into `results/`.

## Key flags

All flags go in `--args="..."`:

| Flag | Description |
|------|-------------|
| `--replays-root <path>` | Root of the replay corpus (required) |
| `--record` | Persist this run to `results/` |
| `--workload <name>` | Restrict to one workload (`parse`, `dispatch`, `propchange`); repeatable |
| `--impl <name>` | Restrict to one entity-state impl (e.g. `NESTED_ARRAY`, `S2_FLAT`); repeatable |
| `--variant <name>` | Restrict to one benchmark variant; repeatable |
| `--list-replays` | Print corpus entries with engine classification, no bench run |

Filters are validated strictly; unrecognized values abort. An inapplicable
filter (e.g. `--impl S2_FLAT` on v3.1.3) silently skips with a summary line.

## Layout

```
harness/     shared bench infrastructure (no clarity imports)
v3.1.3/      pinned to released 3.1.3 from Maven Central
v4.0.0/      pinned to released 4.0.0 from Maven Central
v5.0.0/      pinned to 5.0.0-SNAPSHOT from mavenLocal
results/     tracked baseline runs (committed)
```

## Replay corpus

- Replays live at `/home/spheenik/projects/replays` (gitignored here).
- `replays/MANIFEST.sha256` is append-only: `<sha256> <size> <engine> <path>`.
- `BenchMain` refuses to run a replay whose hash doesn't match the manifest.
- **Adding a replay obligates backfill**: bench it against every version
  subproject so the longitudinal matrix stays complete.
- `--list-replays` is useful when adding new replays — shows detected engine
  vs manifest tag; disagreement aborts before any bench cell runs.
