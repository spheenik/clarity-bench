## Why

The Clarity replay parser has evolved across many releases (3.1.3, 4.0.0, 4.1.0, 5.0-SNAPSHOT, ...) but there is no apples-to-apples way to measure how parse throughput has changed across that arc. The existing JMH harness in `clarity-examples` is bound to the 5.x API surface (uses `S2EntityStateType`, `runner.withS2EntityState(...)`) and cannot compile against older versions. It also lives entangled with the examples build, which conflates "code that teaches users" with "code that measures the parser."

This change bootstraps a dedicated repository whose only job is cross-version benchmarking, with each historical version benched against the same JDK, replays, and JMH configuration.

## What Changes

- Create a new Gradle project layout with a shared `harness/` module wired into per-version subdirs via composite include.
- The harness contains all version-agnostic bench code (JMH benchmarks, the `BenchAdapter` interface, the `Capabilities` model, runtime parameter wiring) and **imports zero classes from Clarity**.
- Each per-version subdir (`v3.1.3/`, `v4.0.0/`, `v5.0.0/`, ...) provides a thin `BenchAdapter` implementation — the only place version-specific Clarity API is touched.
- A `Capabilities` model declares which knobs apply to a given version: `supportedEngines` (which game engines a version can parse — S1/S2/Deadlock); `entityStateImpls` (alternative entity-state impls the version exposes — `{NESTED_ARRAY, TREE_MAP, FLAT}` on 5.x, empty on 3.1.3 meaning "run once with the built-in default"). The shared bench drives JMH `@Param` sets and replay-set filtering at runtime from these capabilities, so old versions don't break when new dimensions are added — they just don't run the inapplicable ones.
- Released versions resolve from Maven Central. The candidate version (`5.0-SNAPSHOT`) resolves from `mavenLocal()`; bench workflow is `publishToMavenLocal` in clarity, then run.
- No replay chooser; replay path is a JMH parameter / CLI argument.
- Results are persisted into a tracked `results/` directory when invoked with `--record`, tagged by hardware so the same bench run on different machines is captured side-by-side. The longitudinal record lives in the repo, not on the maintainer's disk.

## Capabilities

### New Capabilities
- `cross-version-bench`: a JMH-based benchmark suite that can be run against any pinned Clarity version through a per-version adapter, with version-specific knobs declared via a capability model and injected into JMH parameters at runtime.

### Modified Capabilities
<!-- None — this is a greenfield repository. -->

## Impact

- **New repository**: `github.com/spheenik/clarity-bench` (sibling to `clarity`, `clarity-examples`).
- **No impact on `clarity` or `clarity-examples` in this change.** Removing the now-redundant `src/jmh/` harness from `clarity-examples` and rewriting its CLAUDE.md "Cross-version benchmarking" section is a separate follow-up proposal, sequenced after this repo is real and produces results.
- **External dependencies**: Maven Central (for released Clarity versions), `mavenLocal()` (for the candidate), `me.champeau.jmh` Gradle plugin, JMH 1.x.
- **Workflow change for maintainer**: benching the in-development version requires a `publishToMavenLocal` step in the `clarity` checkout before each measurement run.
