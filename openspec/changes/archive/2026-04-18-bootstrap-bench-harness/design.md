## Context

Clarity has shipped many releases (3.x → 5.x) with substantial internal evolution: the entity-state representation went through multiple impls (`NESTED_ARRAY`, `TREE_MAP`, `FLAT`), packet decoding was reworked, and 5.x carries a package reorganization on top. There is no harness today that can measure the same parser-level workload across that history.

The existing JMH harness in `clarity-examples/src/jmh/` is bound to 5.x: it imports `skadistats.clarity.state.s2.S2EntityStateType` and calls `runner.withS2EntityState(...)`. It also lives next to teaching examples and the `bench/` subproject, which has caused confusion (`CLAUDE.md` already calls out the split). It cannot be made cross-version by editing in place.

The candidate (in-development) version is `5.0-SNAPSHOT`, currently consumed in `clarity-examples` via a Gradle composite include of the sibling `clarity` checkout. Released versions are on Maven Central.

Stakeholders: the maintainer (single-developer project), wanting honest, reproducible numbers across the long arc to inform future decisions and to attribute regressions when they appear.

## Goals / Non-Goals

**Goals:**
- One repository whose only job is benching Clarity, with each historical version bench-runnable in isolation.
- A shared bench codebase (single source of truth for benchmark logic) consumed by every per-version subdir.
- A capability model that lets each version declare which knobs apply to it, so adding a new bench dimension does not break older versions.
- Self-contained subdirs: no dependency on a sibling `clarity` checkout *for released versions*. The candidate version uses `mavenLocal()`, which requires a `publishToMavenLocal` step but no source-tree coupling.
- Honest comparison framing: same JDK, same JMH config, same replays for every version.

**Non-Goals:**
- Replicating clarity-examples' teaching/repro/dev surface. The bench repo has no examples, no repros, no GUI tools.
- A `ReplayChooser`-style interactive picker. Replay paths are passed in.
- Profiling or production observability — JMH wall-clock and standard JMH profilers (alloc, GC) are the entire surface.
- Same-API "compatibility shims" that pretend e.g. `S2EntityStateType` exists on 3.1.3. Capabilities are honestly empty when a feature isn't present.
- Removing the harness from `clarity-examples` — that is a separate, sequenced follow-up proposal.

## Decisions

### D1: Adapter pattern with zero-clarity-import harness

The `harness/` module imports nothing from Clarity. It defines a `BenchAdapter` interface; every `vX.Y.Z/` subdir provides its own implementation. All version-specific Clarity API calls are confined to adapters.

**Why over alternatives:**
- *Reflection-based shared code* — works but is unreadable, hard to JIT, easy to silently break across version skews. Rejected.
- *Lowest-common-denominator API in the harness* — would couple the harness to whatever subset survives all versions (e.g. assume `SimpleRunner` exists with a stable signature back to 3.1.3). Brittle: the moment Clarity changes that signature in some future version, the floor moves. Rejected.
- *Adapter pattern* — each adapter is ~20 LOC, the harness stays clean, version skews are contained. Chosen.

### D2: Capabilities as a builder-style record, additive over time

```java
public record Capabilities(
    Set<String> supportedEngines,    // e.g. {S1, S2, DEADLOCK}; replays from
                                     //   unsupported engines are skipped for
                                     //   this version
    Set<String> entityStateImpls,    // alternative impls the version exposes;
                                     //   empty = no choice, version uses its
                                     //   default impl (run once per replay)
    // future knobs added here
) {
    public static Builder builder() { ... }
}
```

Adapters return a `Capabilities` instance from `capabilities()`. New bench dimensions are added by appending fields (with sensible empty defaults via the builder), so existing adapters need *no* changes when a new dimension lands — they implicitly opt out.

**`supportedEngines` semantics:** every replay in the bench corpus is tagged (by directory, filename convention, or a manifest) with its engine. `BenchMain` cross-products replays × versions and drops pairs where the replay's engine is not in the version's `supportedEngines`. A v3.1.3 run thus silently skips Deadlock replays; a v5.0.0 run includes them.

**`entityStateImpls` semantics (empty case):** see D3 — `ParseBench` declares `@Param({"DEFAULT"})`, and the adapter treats `"DEFAULT"` as "do not touch the runner; run with whatever this version uses natively." So 3.1.3 runs once per (compatible) replay; 5.x runs three times per replay.

**Why builder over plain record constructor:** plain constructor forces every adapter to update when a field is added, defeating the "additive without churn" property. Builder + defaults means adding a field is a one-line change to the record only.

### D3: Single shared `ParseBench` with runtime `@Param` injection

The benchmark class declares each tunable knob with a single sentinel default — e.g. `@Param({"DEFAULT"})` for the entity-state impl knob. Per-version `BenchMain` programs override at runtime via `OptionsBuilder().param("impl", caps.entityStateImpls().toArray(...))` only when the version's capabilities provide a non-empty set.

The sentinel `"DEFAULT"` is a contract: when an adapter's `parse()` sees it, it must not touch that knob and instead use the version's built-in behavior. This way the bench runs *exactly once per replay* on versions that don't expose the knob, and *N times per replay* on versions that do.

**Why over the alternative (one bench class per version):**
- Per-version classes mean N copies of JMH annotation boilerplate that drift over time.
- Runtime injection keeps benchmark logic in exactly one place.
- JMH supports overriding `@Param` defaults via `OptionsBuilder.param(...)`; the sentinel-default trick avoids the empty-`@Param({})` edge case.

### D4: Composite include for the harness module

The bench repo's `settings.gradle.kts` `includeBuild("harness")` and each `vX.Y.Z` subproject `dependencies { implementation(project(":harness")) }` (or via the included build's coordinates). No publishing of the harness; it lives only inside this repo.

**Why over publishing harness to mavenLocal:** publishing dance for an internal module would add friction with zero benefit — the harness is never consumed outside this repo.

### D5: Maven Central for released, `mavenLocal()` for candidate

Released subdirs (`v3.1.3/`, `v4.0.0/`, ...) declare only `mavenCentral()`. The candidate subdir (`v5.0.0/` while 5.x is unreleased) declares `mavenLocal()` *first*, then `mavenCentral()`, and pins `5.0.0-SNAPSHOT`.

**Workflow for candidate runs:**
```
cd ~/projects/clarity/clarity && ./gradlew publishToMavenLocal
cd ~/projects/clarity/clarity-bench/v5.0.0 && ./gradlew jmh
```

**Why not a composite include for the candidate:** it would re-introduce the sibling-checkout coupling we just rejected for the released subdirs, breaking the "every subdir is self-contained" property. The publish step is the cost of that consistency.

**Released-version `mavenLocal()` is omitted on purpose:** prevents a stale local snapshot from silently shadowing the Central artifact and producing meaningless numbers.

### D6: Repository / package layout

```
clarity-bench/
├── settings.gradle.kts          includeBuild + each vX.Y.Z subproject
├── build.gradle.kts             root config (Java toolchain, repos)
├── README.md                    methodology, replay-set policy, how to add a version
├── harness/
│   └── src/main/java/spheenik/claritybench/
│       ├── BenchAdapter.java    interface
│       ├── Capabilities.java    record + builder
│       ├── ParseBench.java      JMH @Benchmark, talks to adapter
│       └── BenchMain.java       runtime Options builder
├── v3.1.3/  (Maven Central, no impl knob)
├── v4.0.0/  (Maven Central, impl knob TBD by capability check)
└── v5.0.0/  (mavenLocal, full impl knob set)
```

Package naming uses `spheenik.claritybench` — this is a separate project from `skadistats.clarity.*`, and reusing the upstream namespace would be confusing.

### D7: Initial version set

Bootstrap with **v3.1.3, v4.0.0, v5.0.0**. Intermediate releases (4.1.0, etc.) can be added later by copying a subdir and bumping the pin — the architecture explicitly supports this without harness changes.

### D8: Logging policy — silence everything below WARN

Every adapter configures the runtime logger to emit only WARN and ERROR; DEBUG and INFO never fire during a bench run. This is enforced inside the adapter (or via a shared logback config bundled in `harness/`), not left to ambient configuration.

**Why:** if logging never fires below WARN, the version of the logging backend is *irrelevant* to the measurement — no debug-statement formatting cost, no level-check cost beyond the trivial one. This dissolves the entire "logback/slf4j era skew" risk: the adapters can all use modern logback (or even a no-op binding) without distorting numbers, because the relevant log statements never execute.

WARN/ERROR are kept enabled so that genuine misbehavior (a corrupt replay, an unexpected message type) is not silently swallowed during a bench run.

### D9: Run failures are first-class output



A bench run can fail for legitimate reasons: a replay is corrupt, a version cannot decode something it should, the entity-state impl encounters an edge case, the candidate JAR was never published to `mavenLocal`. The harness must **never silently swallow** these failures, and must **never let one failure abort the whole run**.

**Policy:**
- `OptionsBuilder.shouldFailOnError(false)` — JMH keeps going across the rest of the matrix when one cell throws.
- Exceptions inside `adapter.parse(...)` propagate up to JMH (so its iteration report records the failure) — the harness does *not* try/catch around the parse call. Catching there would deny JMH visibility and silently produce a "succeeded with 0 ops" row.
- After JMH returns, `BenchMain` post-processes the `RunResult` collection, extracts the failed cells (replay × version × impl), and emits a separate **FAILURES** section in the structured output (alongside the success tables), with: cell coordinates, exception class, exception message, root-cause class+message if different.
- Process exit code is non-zero if any cell failed. CI / scripted bench runs can detect this.
- Failures don't block reporting of *successful* cells in the same run — partial results are valuable.

**Why explicit:** JMH does report errors but folds them into its own text format that's easy to skim past; a 30-cell run with two silent failures looks fine at a glance. A dedicated FAILURES section + non-zero exit makes failures impossible to miss.

### D10: Replay corpus policy — inherit, append-only, backfill on add

The initial corpus is whatever `clarity-examples` is currently benchmarking. As of bootstrap, that is:

```
replays/dota/s2/340/8168882574_1198277651.dem   (186.8 MiB, sha256 0bc3f548…)
```

**Policy:**
- The corpus is **append-only**: a replay, once recorded in the manifest, is never removed or substituted. Substitution would silently invalidate every historical number measured against that file.
- Each replay is identified by **path + size + sha256**, recorded in `replays/MANIFEST.sha256`. `BenchMain` refuses to run any replay whose sha256 doesn't match the manifest.
- **Adding a replay implies a backfill**: the new file must be benched against *every* version in the repo (including the historical ones), so the matrix `replays × versions × impls` stays full. Without backfill, the new replay only has data points for whatever version added it, breaking longitudinal comparison along that row.
- Each replay carries an engine tag (via its directory under `replays/<engine>/...` and/or the manifest), used by `BenchMain` to skip replays from engines a version doesn't support (see D2).

### D11: Result persistence — opt-in, hardware-tagged

`BenchMain` accepts a `--record` flag (off by default). When off, results print to stdout / `work/` (gitignored). When on, results are persisted to `results/<date>_<hardware-tag>_<jdk>/` and intended to be committed.

**Why opt-in:** most invocations during development are exploratory — you're tweaking the harness, smoke-testing an adapter, debugging a failure. Auto-persisting those would flood `results/` with noise. The maintainer is the only person running this; explicit `--record` is the right gate.

**Hardware tag** is a short slug for the directory name (e.g. `ryzen9950x`), with the full hardware fingerprint in `context.txt`:

```
hardware tag:   ryzen9950x
CPU model:      AMD Ryzen 9 9950X 16-Core Processor
cores:          32 (logical) / 16 (physical)
RAM:            64 GiB
OS:             Arch Linux 6.19.6
JVM:            OpenJDK 21.0.10+7
JVM args:       [-Xmx4g]
git HEAD:       <bench repo SHA>
clarity SHA:    <resolved JAR's SHA, for SNAPSHOT runs>
replay manifest: <path → sha256 mapping for every replay actually run>
```

**Cross-hardware comparison is a first-class use case.** A run on the Ryzen 9950X and a run on a laptop are *not* directly comparable in absolute ms, but the *deltas* (impl A vs impl B, version X vs version Y) should be reasonably stable across hardware — that's how you sanity-check an unexpected result. Having hardware in the directory name (not just the file) means `results/` self-organizes into per-hardware groups when listed.

**Result file format** mirrors clarity-examples' current text format (median/min/max/p95, alloc/op, GCs, GC time) — small (1–20 KB per cell), human-readable, diffable. JSON sidecar (`results.json`) for tooling.

**Old result directories are never edited.** Adding a replay (per D10 backfill) produces a new tracked dir; the previous dir is left as a frozen snapshot of what was known on its date.

## Risks / Trade-offs

- **JMH runtime `@Param` override** → smoke-test the sentinel-default approach (`@Param({"DEFAULT"})` overridden by `OptionsBuilder.param(...)`) before committing the layout. If JMH refuses to override declared defaults, fall back to one bench class per version (D3 alternative).
- **Engine taxonomy drift** → if Clarity introduces a new engine (or splits S2 into Dota-S2/CSGO-S2/Deadlock at the engine-id level) the `supportedEngines` vocabulary needs to follow. Keep the engine strings defined in the harness module so all adapters share the same vocabulary; never let an adapter invent its own.
- **Replay tagging** → every replay file must be unambiguously tagged with its engine (directory layout under `replays/<engine>/...`, or a manifest). Without that, `BenchMain` cannot enforce the `supportedEngines` filter.
- **Cross-version "fairness" is meaningful only with the same workload definition** → the corpus policy in D10 (append-only, manifest with sha256, backfill on add, `BenchMain` refuses sha256 mismatches) is the enforcement. Without that enforcement this risk is the project's #1 long-term failure mode — silent workload drift makes every historical number meaningless.
- **Logging accidentally fires during a run** → D8 says nothing below WARN should execute, but a forgotten config (system property, `logback.xml` on classpath, etc.) could re-enable DEBUG silently. Mitigation: the adapter sets log levels programmatically in its setup, *and* `BenchMain` asserts at startup that root logger is at WARN or above; refuse to run otherwise. Stronger than relying on a config file.
- **JDK 21 running 3.x bytecode** → not what 3.1.3 originally ran on. Bytecode runs fine, but JIT behavior may differ from a contemporaneous JDK. Document this explicitly when reporting; do *not* try to run multiple JDKs (way out of scope).
- **5.x → 4.x → 3.x do different work per packet** → a throughput delta is not "the parser got slower," it is "users get more for the same time." README must frame results that way.
- **Forgetting `publishToMavenLocal` before benching the candidate** → bench yesterday's bytecode silently. Mitigation: a tiny convenience shell script `bench-candidate.sh` at repo root that does both steps.

## Migration Plan

This is a greenfield repo bootstrap; there is nothing to migrate. Sequencing:

1. Land this change → repo is real, `harness/` + `v5.0.0/` work end-to-end against the current candidate, plus `v3.1.3/` and `v4.0.0/` produce numbers.
2. Record an initial baseline run on the standard replay set (separate, post-bootstrap activity).
3. Open a follow-up proposal in `clarity-examples` to delete `src/jmh/` and rewrite the "Cross-version benchmarking" section of its `CLAUDE.md` to point at this repo.

No rollback concerns — the bench repo is independent of `clarity` and `clarity-examples`. If the approach proves wrong, the repo is simply abandoned without affecting anything else.

## Open Questions

- **Q1: JMH version pin.** `me.champeau.jmh` 0.7.3 is what `clarity-examples` currently uses; reuse it or pick a fresh version? Default: reuse, to match what was used historically for any `clarity-examples` numbers we want to compare against.
- **Q2: Replay-set governance.** Where does the canonical replay list live — in the bench repo's README, or in a `replays/MANIFEST` checked into both repos? The latter is more rigorous but adds coupling between repos. Defer — README is fine for v0.
- **Q3: Should `BenchMain` print a per-version capability summary at start of run?** Useful for sanity-checking what was actually benched. Cheap to add. Lean yes, decide during tasks.
