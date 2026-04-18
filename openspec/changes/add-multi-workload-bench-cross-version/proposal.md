## Why

Today clarity-bench measures exactly one thing — end-to-end replay parsing — across pinned releases. Two other parser subsystems with known performance histories aren't covered: event dispatch and property-change dispatch (the latter was demonstrably optimized between clarity 3.x and 4.x and currently has no longitudinal data to point at). Standalone bench Mains in clarity-examples cover these workloads in v5-only, ad-hoc fashion (no warmup discipline, no recorded results, no cross-version comparison). Folding them into clarity-bench's harness gives them rigor and turns "we think 4.x got faster" into "here's the number, every release."

The engine taxonomy is also wrong: the harness collapses CSGO and CS2 under a single `S2` tag and never distinguishes Dota S2 from CS2/Deadlock, even though clarity itself models them as five distinct `EngineId` values. Fixing this is a precondition to any meaningful per-game bench result.

## What Changes

- **BREAKING (manifest)**: Re-tag every entry in `replays/MANIFEST.sha256` from the 3-engine vocabulary (`S1`, `S2`, `DEADLOCK`) to the 5-engine vocabulary (`DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK`) matching clarity's `EngineId` enum.
- **BREAKING (BenchAdapter / Capabilities API)**: `Capabilities.entityStateImpls` changes from `Set<String>` to `Map<String, Set<String>>` (impl name → applicable engines). `Capabilities` gains `dispatchVariants` and `propertyChangeVariants` of the same shape. `BenchAdapter.parse(...)` config now carries `workload` and `variant` keys in addition to `impl`.
- Add **multi-workload axis**: three workloads — `parse` (existing), `dispatch` (new), `propchange` (new) — with separate JMH `@Benchmark` classes (`ParseBench`, `DispatchBench`, `PropertyChangeBench`) and per-workload variant declarations.
- Add **engine-agnostic variants only** as first-cut content. Engine-specific patterns (Dota class trees etc.) are deferred.
- All three adapters (v3.1.3, v4.0.0, v5.0.0) declare `supportedEngines = {all 5}` (correcting the missing DEADLOCK declaration on v3/v4).
- All three adapters expose `OBJECT_ARRAY` / `NESTED_ARRAY` / `TREE_MAP`. v5.0.0 additionally exposes `S1_FLAT` / `S2_FLAT`. Older adapters reach the alternate impls via a **shadow `EntityStateFactory`** in the same package, since the 3.x/4.x public API never exposed `withS2EntityState`.
- Add **`EngineDetector`** in `harness/` (clarity-free). `BenchMain` runs it at startup and errors if the detected engine disagrees with the manifest tag — same shape as the existing sha256 check.
- Add **CLI filters**: `--impl`, `--workload`, `--variant` (repeatable). Loose semantics on applicability, strict on unknown values, exit 1 on empty intersection.
- Add **`--list-replays`** mode that prints `path → detected-engine → manifest-tag` for the whole corpus.
- **Manifest backfill**: add three default replays from the original clarity-examples Mains if absent, with the per-version sha256 backfill the existing append-only policy requires.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `cross-version-bench`: vocabulary expansion (3 → 5 engines), multi-workload axis, per-engine capability declarations, CLI filter semantics, engine-detector startup verification, `--list-replays` mode, shadow `EntityStateFactory` mechanism for older adapters.

## Impact

**Affected code in `clarity-bench`:**

- `harness/src/main/java/spheenik/claritybench/`:
  - `Engines.java` — rewritten (3 → 5 constants, plus `S1_FAMILY` / `S2_FAMILY` set constants)
  - `Capabilities.java` — `entityStateImpls` shape change; new `dispatchVariants`, `propertyChangeVariants` slots
  - `BenchAdapter.java` — config map contract documented for new `workload` / `variant` keys (no signature change)
  - `BenchMain.java` — matrix expansion respects per-engine impl/variant applicability; new CLI flags + `--list-replays` mode; engine-detector startup check
  - `ParseBench.java` — unchanged conceptually (still keyed on `replay` + `impl`); becomes one of three benchmark classes
  - `DispatchBench.java`, `PropertyChangeBench.java` — new
  - `EngineDetector.java` — new (clarity-free)
  - `Manifest.java` — engine-tag values widen to the 5-engine vocabulary
  - `ResultWriter.java` — gains workload/variant columns

- `v3.1.3/`, `v4.0.0/`, `v5.0.0/`:
  - Adapter `capabilities()` declarations updated for engines, impls, dispatch/propchange variants
  - Adapter `parse()` routes by `workload` config key, wires the right listener processor per variant
  - v3.1.3 + v4.0.0: new `src/main/java/skadistats/clarity/model/state/EntityStateFactory.java` shadow class
  - v3.1.3 + v4.0.0: build-time smoke test that the shadow takes precedence over clarity-X.Y.Z.jar's class

- `replays/MANIFEST.sha256`: re-tagged to 5-engine vocabulary; appended with the three default replays (verified sha256, backfilled across all versions).

- `README.md`: engine vocabulary section updated; add `--list-replays`, `--impl`, `--workload`, `--variant` to the usage examples.

**Out of scope (explicitly deferred):**

- Engine-specific variant content (Updated8Filtered, NarrowSingle, ManyListeners with Dota-specific class/property patterns) — follow-up change. Reason: Dota-flavored regexes produce zero-hit cells on CS2/Deadlock replays, which is noise rather than signal. Per-engine pattern selection is a discrete content task best done after the harness shape is settled.
- Removing the `bench/` subproject and `src/jmh/` from clarity-examples — Step 2 of the broader plan, separate change in clarity-examples.
- Amending the in-flight clarity-examples change `add-entity-bindings-cs2-state` to point its cs2state-bench work at clarity-bench — also Step 2.
- A `parser-comparison-bench` capability (clarity vs demoinfocs at a point in time). Agreed it should live in clarity-bench's openspec eventually, but not part of this change.
