# clarity-bench

Cross-version benchmark harness for the [Clarity](https://github.com/skadistats/clarity) replay parser. Multi-workload, multi-engine, all results comparable across the long arc of releases.

## What this is

One repository whose only job is benching Clarity across pinned releases. Each version has its own subproject and bench-runs in isolation, so a v3.1.3 number and a v5.0.0 number on the same replay/JDK/hardware are directly comparable.

Three workloads are measured per release:

- **`parse`** — end-to-end replay parse with a configurable entity-state implementation
- **`dispatch`** — parse with N `@OnEntityUpdated` listeners attached, scaling listener count
- **`propchange`** — parse with `@OnEntityPropertyChanged` listeners attached

## Layout

```
harness/   shared bench code (no clarity imports — verified at compile time)
v3.1.3/    pinned to released 3.1.3 from Maven Central; impl selection via shadow
v4.0.1/    pinned to released 4.0.1 from Maven Central; impl selection via shadow
v5.0.0/    pinned to 5.0.0-SNAPSHOT from mavenLocal (= the in-development candidate)
replays/   on-disk replay corpus (gitignored; manifest pinned by sha256)
results/   tracked baseline runs
```

## Engine vocabulary

Matches clarity's `EngineId` enum exactly:

```
DOTA_S1     Dota 2, Source 1 (pre-Reborn)
DOTA_S2     Dota 2, Source 2 (Reborn)
CSGO_S1     CS:GO, Source 1
CSGO_S2     CS2 (Valve kept the protobuf prefix)
DEADLOCK    Deadlock
```

Convenience set constants `S1_FAMILY` and `S2_FAMILY` are defined in `harness/Engines.java` for adapter capability declarations.

## Entity-state impls

Globally-unique names; the adapter declares per-engine applicability:

| Bench impl   | Concrete class            | Family |
|--------------|---------------------------|--------|
| `OBJECT_ARRAY` | `S1ObjectArrayEntityState` | S1     |
| `S1_FLAT`      | `S1FlatEntityState`        | S1     |
| `NESTED_ARRAY` | `S2NestedArrayEntityState` | S2     |
| `TREE_MAP`     | `S2TreeMapEntityState`     | S2     |
| `S2_FLAT`      | `S2FlatEntityState`        | S2     |

`S1_FLAT` and `S2_FLAT` exist only in v5.0.0+. `OBJECT_ARRAY`, `NESTED_ARRAY`, and `TREE_MAP` are exposed by all three adapters — for v3.1.3 / v4.0.1 the public runtime API never exposed selection, so each adapter ships a shadow `skadistats.clarity.model.state.EntityStateFactory` class that the JVM loads in preference to the released-jar copy. See "Shadow entity-state factory" below.

## Running a bench

Each `:vX.Y.Z:run` is a fresh JVM with that version's classpath.

```bash
# released versions
./gradlew :v3.1.3:run --args="--replays-root /home/spheenik/projects/replays"
./gradlew :v4.0.1:run --args="--replays-root /home/spheenik/projects/replays"

# candidate (5.x SNAPSHOT) — publish first
cd ../clarity && ./gradlew publishToMavenLocal && cd -
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays"
```

Add `--record` to persist a run into `results/`:

```bash
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays --record"
```

### Filters

`--workload`, `--impl`, `--variant` are repeatable. They restrict the matrix expansion. Names are validated strictly; applicability against the active adapter is loose (a filter that doesn't apply silently skips, with one summary line). If the resulting cell set is empty, the run aborts with a clear message.

```bash
# one impl across all versions
./gradlew :v3.1.3:run :v4.0.1:run :v5.0.0:run \
  --args="--replays-root /home/spheenik/projects/replays --impl NESTED_ARRAY"

# only the propchange workload
./gradlew :v5.0.0:run --args="--replays-root … --workload propchange"

# one specific variant
./gradlew :v5.0.0:run --args="--replays-root … --workload propchange --variant WildcardSingle"
```

### List-replays

Inspect engine classification across the corpus without running any benchmark:

```bash
./gradlew :v5.0.0:run --args="--replays-root /home/spheenik/projects/replays --list-replays"
```

Prints `<relative-path> → <detected-engine> → <manifest-tag>` per entry, with a marker on rows where the detector disagrees with the manifest. Useful when adding new replays — copy the detected value into the manifest.

## Replay corpus policy

- `replays/MANIFEST.sha256` is **append-only**. A replay's hash, once recorded, is never changed.
- Each entry is `<sha256> <size_bytes> <engine> <relative_path>` with `<engine>` one of the five `EngineId` values above.
- `BenchMain` refuses to run any replay whose actual sha256 doesn't match the manifest, or that isn't in the manifest at all.
- At startup, `BenchMain` also verifies each replay's manifest engine tag against the detector (which delegates to clarity's own `Source.determineEngineType()` per the active adapter). Disagreement aborts before any cell runs. A version that doesn't recognize a particular engine (older release, newer game) skips the cross-check for that replay and trusts the manifest tag.
- **Adding a replay obligates a backfill**: the new file must be benched against every version present in the repo, so the longitudinal matrix stays full.

## Shadow entity-state factory

clarity 3.1.3 and 4.0.1 do not expose a runtime entity-state-selection API — `SimpleRunner` has no `withS2EntityState`/`withS1EntityState` method, and `EntityStateFactory.forS1`/`forS2` are static utilities with hardcoded impls. To bench `TREE_MAP` (or any non-default impl) on those versions, each subproject ships a same-package class:

```
v4.0.1/src/main/java/skadistats/clarity/model/state/EntityStateFactory.java   # shadow
v4.0.1/src/main/java/skadistats/clarity/model/state/EntityStateFactoryShim.java # config knob
```

Gradle's classpath places the project's compiled classes ahead of `clarity-X.Y.Z.jar`, so the JVM resolves to the shadow rather than the class inside the jar. The shadow's `forS2` reads a `ThreadLocal` impl name set by the adapter before `runner.runWith(...)` and routes to the requested `EntityState` constructor. When the knob is unset or `DEFAULT`, the shadow's behavior is byte-for-byte equivalent to the released jar's (verified via `javap -c`).

Recorded results carry an `impl selection:` line in `context.txt` for any version that used the shim — the score-table column heading stays unprefixed (`TREE_MAP`, not `TREE_MAP-injected`) so the comparison identity is the impl, with the per-version mechanism captured as provenance metadata.

**Brittleness note:** the shim covers `EntityStateFactory.forS1`/`forS2` only. If a clarity 4.0.x patch had reorganized internal callers off this static factory (e.g. inlined construction inside `DTClasses`), those code paths would silently fall back to the released-jar default. The runtime test is: a TREE_MAP cell vs a NESTED_ARRAY cell on the same replay should produce different numbers.

## Logging

Every adapter pins root logger to WARN. `BenchMain` refuses to start if anything below WARN is enabled, since debug logging would distort measurement.

## What "comparison" means here

These numbers reflect "what JDK 21 makes of clarity vX.Y.Z bytecode on this hardware." They are NOT a reconstruction of historical performance under the JDKs each version was originally built for. And later versions do strictly *more work per packet* than earlier ones (more decoded fields, more state) — a slowdown is "users get more for the same time," not a regression.

For impls reached via the shadow shim on v3.1.3 / v4.0.1: the recorded result is *"that parser era + that impl class"*, not "what that release shipped to users." That's the deliberate counterfactual — the comparison is "would TREE_MAP have helped in the 4.0 parser?" rather than "what was production performance in 4.0?"

See `openspec/specs/cross-version-bench/spec.md` for the full requirement set.
