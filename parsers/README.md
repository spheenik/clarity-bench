# Parser comparison

Benchmarks clarity against the other open-source Source 2 replay parsers:
[demoparser2](https://github.com/LaihoE/demoparser) (Rust, CS2),
[demoinfocs-golang](https://github.com/markus-wa/demoinfocs-golang) (Go, CS2) and
[manta](https://github.com/dotabuff/manta) (Go, Dota 2).

Unlike the JMH bench in the version subprojects, this is a wall-clock harness
across four languages. The write-up of the results lives in clarity's
`PARSER-COMPARISON.md`.

## Running

Requirements: JDK 21, Go, Python 3 with `venv`, `/usr/bin/time`.

```bash
# clarity side comes from mavenLocal, like :v5.0.0
cd ../clarity-protobuf && ./gradlew publishToMavenLocal && cd -
cd ../clarity && ./gradlew publishToMavenLocal && cd -

python3 parsers/run.py --replays-root /home/spheenik/projects/replays \
  --clarity-label "next@$(git -C ../clarity rev-parse --short HEAD)"
```

Add `--record` to write the run into `results/parsers/<date>_<host>/`
(`results.json`, `summary.md`); without it, output goes to `parsers/build/`.
`--only <substring>` restricts to matching target ids (repeatable), e.g.
`--only manta --only clarity`.

The driver builds every target itself: Go binaries per pinned version under
`parsers/build/`, and one virtualenv per demoparser2 version.

## What is measured

Each target parses each applicable replay and prints one line per parse with
the wall time and the process CPU time spent in it.

- **process**: one parse per process, `--rounds` (7) rounds interleaved across
  targets. The parse is timed inside the process, so JVM startup is excluded but
  JIT warmup is included. `/usr/bin/time` adds whole-process CPU (including JIT
  compiler and GC threads) and peak RSS. This is what a one-shot CLI run costs.
- **loop**: `--loop-iterations` (10) parses in one process; the median of the
  iterations after `--loop-warmup` (3) is the steady state. This is what a
  long-lived batch worker pays per demo, and it is the number to use for batch
  throughput (CPU seconds per demo).

The workload is a bare entity decode wherever the parser allows it:

| Parser | Harness | Notes |
|---|---|---|
| clarity | `SimpleRunner` with `@UsesEntities`, no listeners | same as `dev:entityrun` in clarity-examples |
| manta | `NewStreamParser` + `Start()`, no callbacks | entities are always decoded |
| demoinfocs | `NewParserWithConfig` + `ParseToEnd()`, no handlers | always maintains its CS2 game state; `ST` sets `MsgQueueBufferSize = 0` |
| demoparser2 | `parse_ticks(["X", "Y", "Z", "health"])` | builds a DataFrame; `ST` sets `RAYON_NUM_THREADS=1` |

RSS is not like-for-like: clarity memory-maps the replay and demoparser2 reads
it whole, so both include the file; manta and demoinfocs stream it.

## Targets and replays

Targets (parser, pinned version, mode) are the `TARGETS` list at the top of
`run.py`; older versions are kept next to the current ones so that a parser's
own speedup is measured on the same machine rather than against old numbers.

Replays are pinned by sha256 in `replays.txt` and verified before each run.
They are deliberately separate from `replays/MANIFEST.sha256`, which drives the
cross-version JMH matrix and its backfill obligation.
