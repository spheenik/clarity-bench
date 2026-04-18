## Context

clarity-bench today is a single-axis cross-version harness: one JMH `@Benchmark` (`ParseBench`) parameterized on `(replay, impl)`, one `BenchAdapter` interface per pinned clarity version, one orchestrator (`BenchMain`) that loads the active adapter via `ServiceLoader` and feeds the JMH runner.

Two adjacent measurement workloads — event dispatch cost and property-change dispatch cost — currently live as standalone Mains in `clarity-examples/bench/` (no warmup discipline, no recorded results, no cross-version comparison). The propertychange subsystem in particular was optimized between clarity 3.x and 4.x, and there is no published longitudinal data to point at.

The engine vocabulary in `Engines.java` is also degenerate: `S1` / `S2` / `DEADLOCK`, where `S2` collapses Dota 2, CS2, and (incorrectly) Deadlock under one tag. clarity itself models this as a 5-value `EngineId` enum (`DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK`) — the bench's vocabulary should match.

## Goals / Non-Goals

**Goals:**
- Three workloads (`parse`, `dispatch`, `propchange`) measurable cross-version with the same harness, JMH config, and replay corpus.
- Engine vocabulary that matches clarity's `EngineId` exactly, with manifest verified against on-disk file headers.
- All three pinned versions (v3.1.3, v4.0.0, v5.0.0) able to bench `OBJECT_ARRAY` / `NESTED_ARRAY` / `TREE_MAP` even though only v5 has a public runtime-selection API.
- CLI filtering that lets a user run a focused subset (e.g. "all versions, NESTED_ARRAY only, propchange only") without code changes.
- A first-run signal that validates the harness: propchange v3 → v4 should show a real, expected delta (not a flat line).

**Non-Goals:**
- Engine-specific variants (Dota class regexes, CSGO class regexes, etc.) — deferred to a follow-up change. First-cut variants are engine-agnostic only.
- Removing/migrating clarity-examples bench code — separate change in the other repo.
- Adding a `parser-comparison-bench` capability (clarity vs demoinfocs) — agreed-future, not now.
- Backporting `S1_FLAT` / `S2_FLAT` to v3.1.3/v4.0.0 — those impl classes don't exist in those jars; nothing for the shim to construct.

## Decisions

### D1 — Engine vocabulary matches clarity's `EngineId` verbatim

`Engines.java` is rewritten as `DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK`. Two convenience set constants are added: `S1_FAMILY = {DOTA_S1, CSGO_S1}` and `S2_FAMILY = {DOTA_S2, CSGO_S2, DEADLOCK}` for adapter declarations.

**Why:** Drift between clarity's identity for an engine and the bench's identity for an engine is a documentation-rot hazard. Mirroring clarity's enum makes per-version capability declarations transparently legible.

**Alternative rejected:** Auto-import `EngineId` directly. Rejected because the harness must remain clarity-free (per the existing `harness/   shared bench code (no clarity imports)` invariant), and an auto-import would silently mask compatibility issues if a future clarity release adds or renames an engine.

### D2 — Independent `EngineDetector` in `harness/`

A new `EngineDetector` class reads the demo file header (`PBDEMS2` magic vs Source 1 marker) and, for Source 2 files, peeks at protobuf descriptor names to pick `DOTA_S2` / `CSGO_S2` / `DEADLOCK`. Source 1 disambiguation similarly picks `DOTA_S1` / `CSGO_S1`.

**Why:** The manifest stores an explicit engine tag per replay (so adapters can skip an inapplicable replay without reading it). Manual tagging is human-error-prone. An independent detector run at startup, comparing detector output against the manifest tag, catches mismatches the same way the existing sha256 verification catches corrupted files.

**Alternative rejected:** Have the active clarity adapter detect the engine. Rejected because the detector must work for *every* adapter (including ones that fail to parse a given engine entirely) and must be available before the adapter is even invoked.

**Alternative rejected:** Trust the manifest tag and skip detection. Rejected because the cost is negligible (a header read per replay at startup) and the detection-vs-tag check is a useful invariant — drives errors when someone adds a misclassified replay.

### D3 — Globally-unique impl names; no grouping abstraction

Bench impl identifiers are `OBJECT_ARRAY`, `NESTED_ARRAY`, `TREE_MAP`, `S1_FLAT`, `S2_FLAT`. The S1 vs S2 `FLAT` collision (clarity has both as enum values named `FLAT` in their respective `S1EntityStateType` / `S2EntityStateType`) is broken in the bench by prefixing with the family. CLI filter `--impl S2_FLAT` is unambiguous.

**Why:** A grouping abstraction (one logical name `FLAT` that maps to either family) is conceptually neat but adds an indirection layer that buys only typing convenience on the CLI. The cross-version comparison story still reads as "down a column per impl," with each column being a unique identity.

**Alternative rejected:** Two `Capabilities` slots — `s1EntityStateImpls`, `s2EntityStateImpls` — with overlapping `FLAT` names per family. Rejected because the per-engine applicability is more naturally expressed as per-impl metadata than as two parallel slots.

### D4 — `Capabilities` per-engine applicability, expressed as `Map<String, Set<String>>`

```java
public record Capabilities(
    Set<String> supportedEngines,
    Map<String, Set<String>> entityStateImpls,        // impl name → applicable engines
    Map<String, Set<String>> dispatchVariants,        // variant name → applicable engines
    Map<String, Set<String>> propertyChangeVariants   // variant name → applicable engines
) {}
```

`BenchMain` matrix expansion intersects per-cell: for each `(replay, impl)` cell, the cell exists iff `entityStateImpls[impl]` contains the replay's engine. Same logic for variants per workload.

**Why:** A flat name → engine-set map naturally captures both "TREEMAP only applies to S2 family" and "all dispatch variants apply to all engines" without per-axis special cases in `BenchMain`.

### D5 — Shadow `EntityStateFactory` for v3.1.3 / v4.0.0

The released public API in clarity 3.1.3 and 4.0.0 has no runtime impl-selection method. `SimpleRunner` exposes only `(Source)` constructor and `runWith(Object...)`. Impl selection is hardcoded in `EntityStateFactory.forS1()` / `forS2()` (a static utility).

Both adapter subprojects ship a same-package class:

```
v3.1.3/src/main/java/skadistats/clarity/model/state/EntityStateFactory.java
v4.0.0/src/main/java/skadistats/clarity/model/state/EntityStateFactory.java
```

Gradle's classpath places compiled project classes ahead of `clarity-X.Y.Z.jar`, so the JVM resolves to the shadow rather than the released-jar version. The shadow reads the requested impl from a thread-local set by the adapter before calling `runWith` and constructs the corresponding `EntityState` impl.

**Constructor signatures (from javap on released jars):**
- `OBJECT_ARRAY` → `ObjectArrayEntityState(...)` (S1 only)
- `NESTED_ARRAY` → `NestedArrayEntityState(SerializerField)` (S2)
- `TREE_MAP` → `TreeMapEntityState()` no-arg (S2). The shadow's `forS2(SerializerField sf)` ignores `sf` when routing to TREE_MAP — signatures are not symmetric.

**Why:** This is the only way to bench `TREE_MAP` against an old parser without the user explicitly requesting impl-injection counterfactual data via a separate workflow. The user's stated use case is precisely this counterfactual — "is TREE_MAP a win across all parser eras, not just the era where it was the runtime-selectable default."

**Alternative rejected:** Declare each old adapter as supporting only its actual hardcoded default impl. Rejected per user direction — the comparison value of an old-parser × new-impl cell is real, even though it doesn't correspond to a release that shipped to users. Labeling makes that distinction (see D6).

**Alternative rejected:** Java agent or bytecode rewriting. Rejected as overkill — classpath shadowing is sufficient.

### D6 — Labeling injected impls in recorded results

Recorded results in `context.txt` carry a one-line note per version stating whether impl selection was via the released runtime API (v5) or via the shadow `EntityStateFactory` (v3, v4). Score-table column headings are unprefixed (`TREE_MAP`, not `TREE_MAP-injected`) — the comparison identity is the impl, and the per-version mechanism is provenance, not an axis.

**Why:** Burying the mechanism in per-cell prefixes would clutter the score table and obscure the comparison. Putting it in `context.txt` keeps the table readable while preserving the audit trail.

### D7 — Three benchmark classes, not one

Each workload gets its own JMH `@Benchmark` class (`ParseBench`, `DispatchBench`, `PropertyChangeBench`). They share `replay` as a `@Param` axis. `ParseBench` adds `impl`; `DispatchBench` adds `variant`; `PropertyChangeBench` adds `variant`. `BenchMain` builds an `OptionsBuilder` that includes only the classes whose corresponding capability set is non-empty for the active adapter.

**Why:** JMH naturally separates `@Benchmark` classes in its output. A reader thinking "show me the dispatch numbers" maps 1:1 to a benchmark class. Folding all three workloads into one parameterized `@Benchmark` would muddy that and require workload-typed branching inside the benchmark body.

**Alternative rejected:** Single benchmark class with `@Param("workload")`. Rejected for the readability reason above.

### D8 — Adapter dispatch via config-map keys, no API surface change

`BenchAdapter.parse(String, Map<String, String>)` keeps its signature. The config map gains documented keys: `workload` (`parse` | `dispatch` | `propchange`), `variant` (workload-specific), `impl` (parse only). Adapters dispatch on `workload`, instantiate the right listener processor for the variant (or the right state impl for parse), and run.

**Why:** Adding methods to `BenchAdapter` would be a breaking signature change for all three subprojects. The config map already exists and is the natural place to add per-cell parameters.

### D9 — Engine-agnostic variants only as first-cut content

Dispatch variants: `Baseline`, `Lifecycle`, `Updated1`, `Updated8`. PropertyChange variants: `Baseline`, `WildcardSingle`. None use class-name or property-name patterns.

**Why:** Engine-specific patterns (`CDOTA_Unit_Hero_.*`, `CCSPlayer.*`, etc.) produce zero-hit cells when run against replays from a different game — measuring regex eval against an empty match set instead of dispatch cost. That's noise, not signal. Per-game pattern selection is a discrete content task best done after the harness shape is settled.

### D10 — CLI filter semantics: loose applicability, strict on names

Three new repeatable flags: `--impl <name>`, `--workload <name>`, `--variant <name>`. Behavior:

- **Loose applicability**: a filter that doesn't match this adapter (e.g. `--impl S2_FLAT` on v3.1.3) silently skips the affected cells, with one summary line (matching the existing engine-skip output).
- **Strict on values**: a typo like `--workload propchang` exits 1 with the list of valid values. Filter *names* are validated against the union of all known names; filter *applicability* is loose against the active adapter.
- **Empty intersection**: if filtering eliminates every cell for this adapter, exit 1 with `"after filtering, no benchmarks remain for adapter v3.1.3"` — distinguishes "this version genuinely can't do what you asked" from a successful skip.

**Why:** Loose applicability lets a user write one cross-version invocation that "does what's possible per version" without per-version flag tuning. Strict name validation catches typos before they become silently empty runs.

### D11 — `--list-replays` mode

New `BenchMain` mode that runs only the `EngineDetector` over the corpus and prints `path → detected-engine → manifest-tag` for each entry, then exits 0. No JMH invocation, no adapter required.

**Why:** When adding a new replay, the user shouldn't have to guess the manifest tag — running this mode produces the lookup table to copy from.

## Risks / Trade-offs

- **Shadow class brittleness** → If a 4.x patch reorganized internal callers off `EntityStateFactory.forS2` (e.g. inlined construction in `DTClasses` or `S2DTClass`), the shim wouldn't catch them and the impl request would silently fall back to the released-jar default. **Mitigation:** Each adapter ships a build-time smoke test that constructs an `EntityState` for every requested impl and asserts `getClass().getSimpleName()` matches. Shim failure is loud at build time, not at bench time.

- **v3.1.3 / v4.0.0 declaring DEADLOCK support without verification** → The original adapters declare only `{S1, S2}`; this change extends to all five engines. If 3.1.3 actually chokes on a Deadlock demo (the wire format has shifted — see project memory on new `CSVCMsg_PacketEntities` sub-fields), benching DEADLOCK on those versions produces noisy failures. **Mitigation:** Confirm end-to-end parse during implementation. If 3.1.3 throws, narrow that adapter's `supportedEngines` accordingly. Partial-parse-without-throw is acceptable bench data per the "later versions do strictly more work per packet" framing.

- **Manifest re-tag is breaking** → Anyone with an in-progress local manifest based on the old vocabulary needs to re-tag. **Mitigation:** Re-tag is a one-shot migration, the change does it as part of itself. The startup engine-detector check catches stragglers immediately (errors with detector-vs-tag mismatch).

- **JMH overhead per @Benchmark class** → Three benchmark classes mean three JMH "benchmark" entries in the matrix; per-cell startup overhead is constant per class. Negligible at typical replay sizes (multi-second parse) but worth noting if anyone benches a tiny replay. **Mitigation:** None needed for normal corpus.

- **Engine-agnostic variants may produce hit-count differences across engines** that confound dispatch-cost comparison (more updates per Dota replay than per CS2 replay, etc.). **Mitigation:** Acknowledged. The reported score is per-replay anyway, so cross-engine numbers aren't compared directly. Cross-version-on-the-same-replay is the meaningful axis.

## Migration Plan

1. Land the harness changes in a branch off `main` of clarity-bench.
2. Re-tag manifest in the same change (one commit).
3. Verify all existing recorded results in `results/` remain valid (the score columns are still meaningful — only metadata in `context.txt` changes interpretation, not the numbers).
4. First post-merge bench run captures `propchange` v3 → v4 → v5 — expected to show a real delta validating the harness shape.
5. No rollback strategy beyond `git revert`; the change is internal-tooling only.

## Open Questions

- **`--list-replays` flag interaction with `--replays-root`**: should `--list-replays` require `--replays-root`, or default to `./replays` if missing? Resolved during implementation — likely require it for consistency with other modes.
- **Shadow class name spelling**: clarity 3.1.3 has `EntityStateFactory` as a static utility; clarity 4.0.0 has the same. If their internal call sites differ (e.g. 4.0.0 inlines `new NestedArrayEntityState(...)` in some path), the shim's coverage isn't symmetric across the two versions. Verify during implementation; document any per-version exception.
