## MODIFIED Requirements

### Requirement: Capability declaration

Each adapter SHALL return a `Capabilities` value declaring which engines it can parse, which entity-state implementations it exposes as choosable (with their per-engine applicability), and which dispatch and property-change variants it supports (with their per-engine applicability). The `Capabilities` type SHALL be additive: adding a new field MUST NOT require changes to existing adapters that do not provide that capability.

The capability declarations for entity-state impls and workload variants SHALL be expressed as `Map<String, Set<String>>` from a name (impl name or variant name) to the set of engines for which that name applies. A name omitted from the map is not exposed by that adapter at all.

#### Scenario: Empty entity-state impl map means version uses its default

- **WHEN** an adapter returns `Capabilities.entityStateImpls()` as the empty map
- **THEN** the bench MUST run that adapter exactly once per compatible replay, with no impl override applied

#### Scenario: Per-engine impl applicability drives matrix expansion

- **WHEN** an adapter returns `Capabilities.entityStateImpls() = { "OBJECT_ARRAY" → S1_FAMILY, "NESTED_ARRAY" → S2_FAMILY, "TREE_MAP" → S2_FAMILY }`
- **THEN** the bench MUST run `OBJECT_ARRAY` only against replays whose engine is in `S1_FAMILY`, and MUST run `NESTED_ARRAY` and `TREE_MAP` only against replays whose engine is in `S2_FAMILY`

#### Scenario: Per-engine variant applicability drives workload matrix expansion

- **WHEN** an adapter returns `Capabilities.dispatchVariants() = { "Updated8" → ALL_ENGINES }`
- **THEN** the bench MUST run the `dispatch` workload's `Updated8` variant once per compatible replay (any engine in `ALL_ENGINES`)

#### Scenario: Variant set empty for a workload skips that benchmark class

- **WHEN** an adapter returns `Capabilities.propertyChangeVariants()` as the empty map
- **THEN** the bench MUST NOT include `PropertyChangeBench` in the JMH run for that adapter

### Requirement: Engine-based replay filtering

The bench SHALL skip replays whose engine is not present in the running adapter's `supportedEngines`. The engine identifier vocabulary SHALL be defined in the harness module so all adapters share the same string set. The vocabulary SHALL match clarity's `EngineId` enum values exactly: `DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK`.

The harness SHALL also expose convenience constants `S1_FAMILY = {DOTA_S1, CSGO_S1}` and `S2_FAMILY = {DOTA_S2, CSGO_S2, DEADLOCK}` for use in adapter capability declarations.

#### Scenario: Adapter without one engine support skips matching replays

- **WHEN** the bench is run against an adapter whose `supportedEngines` does not contain `DEADLOCK`
- **THEN** any replay tagged as `DEADLOCK` MUST be skipped without invoking `parse()`, and the skip MUST be visible in the run output

#### Scenario: Adapter inventing an unknown engine string is rejected

- **WHEN** an adapter declares an engine string not present in the harness vocabulary
- **THEN** `BenchMain` MUST refuse to start the run and emit a clear error naming the offending adapter and string

#### Scenario: Engine vocabulary matches clarity's EngineId

- **WHEN** the harness's `Engines` constants are inspected
- **THEN** they MUST be exactly `DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK` — same names and count as `skadistats.clarity.model.EngineId`

### Requirement: Per-cell config sentinel contract

The shared workload `@Benchmark` classes SHALL declare each tunable knob with a single sentinel default (`"DEFAULT"`). When the adapter's `parse()` receives `"DEFAULT"` for a knob, it MUST NOT touch the corresponding runner configuration and MUST run the version's built-in behavior for that knob.

The config map handed to `parse()` SHALL carry these documented keys:

- `workload`: one of `parse`, `dispatch`, `propchange` — selects which listener wiring the adapter uses
- `variant`: workload-specific identifier (e.g., `Updated8`, `WildcardSingle`) — selects the listener processor configuration; `"DEFAULT"` for the `parse` workload (no variant axis)
- `impl`: entity-state implementation name (e.g., `NESTED_ARRAY`, `S2_FLAT`); `"DEFAULT"` for the `dispatch` and `propchange` workloads (impl axis does not apply)

#### Scenario: Sentinel passes through untouched

- **WHEN** `parse(replay, {"workload": "parse", "impl": "DEFAULT", "variant": "DEFAULT"})` is invoked on any adapter
- **THEN** the adapter MUST run the version's default entity-state impl without calling any version-specific configuration method for impl

#### Scenario: Non-sentinel impl is honored

- **WHEN** `parse(replay, {"workload": "parse", "impl": "S2_FLAT", "variant": "DEFAULT"})` is invoked on a v5.x adapter whose capabilities expose `S2_FLAT`
- **THEN** the adapter MUST configure the runner to use the `S2_FLAT` entity-state implementation before parsing

#### Scenario: Workload selects listener wiring

- **WHEN** `parse(replay, {"workload": "dispatch", "variant": "Updated8", "impl": "DEFAULT"})` is invoked
- **THEN** the adapter MUST instantiate the dispatch workload's `Updated8` listener processor and pass it to `runner.runWith(...)`, with no impl override

## ADDED Requirements

### Requirement: Multi-workload benchmark axis

The harness SHALL expose three workloads through three dedicated JMH `@Benchmark` classes: `ParseBench` (existing), `DispatchBench` (new), `PropertyChangeBench` (new). Each class SHALL declare `replay` as a `@Param` and the workload-relevant additional axis (`impl` for parse, `variant` for dispatch and propchange) as a second `@Param`. All three classes SHALL delegate to the active adapter's `parse(...)` method, distinguishing themselves only by what they put in the config map.

`BenchMain` SHALL build the JMH `OptionsBuilder` to include only the benchmark classes whose corresponding capability set is non-empty for the active adapter.

#### Scenario: Adapter with empty dispatch and propchange skips both classes

- **WHEN** an adapter declares non-empty `entityStateImpls` but empty `dispatchVariants` and empty `propertyChangeVariants`
- **THEN** `BenchMain` MUST run only `ParseBench` for that adapter

#### Scenario: All three workloads declared, all three classes run

- **WHEN** an adapter declares non-empty maps for all three workload capability slots
- **THEN** `BenchMain` MUST include all three `@Benchmark` classes in the JMH run

#### Scenario: Workload-specific axis is parameterized by adapter capabilities

- **WHEN** `DispatchBench` runs against an adapter whose `dispatchVariants` is `{ "Baseline" → ALL, "Updated8" → ALL }`
- **THEN** the JMH `variant` `@Param` for `DispatchBench` MUST take exactly the values `Baseline` and `Updated8`

### Requirement: Engine detector verifies manifest tags

The harness SHALL include an `EngineDetector` class in the `harness/` module that determines a replay's engine from the demo file's content (header magic, protobuf descriptor names) without importing any class from `skadistats.clarity.*`. `BenchMain` SHALL run the detector at startup against every replay listed in the manifest and SHALL exit with a non-zero status if any detected engine disagrees with that replay's manifest tag.

#### Scenario: Detector and manifest agree, run proceeds

- **WHEN** every manifest entry's recorded engine matches the detector's classification of the corresponding file
- **THEN** `BenchMain` MUST proceed to the JMH invocation phase

#### Scenario: Detector disagrees with manifest, run aborts

- **WHEN** the detector classifies a replay as `DOTA_S2` but the manifest tag for that replay is `CSGO_S2`
- **THEN** `BenchMain` MUST exit with a non-zero status before any benchmark cell is executed, naming the offending replay, the manifest tag, and the detected engine

#### Scenario: Detector module remains clarity-free

- **WHEN** the `harness/` module is compiled
- **THEN** `EngineDetector` MUST NOT depend on any class from `skadistats.clarity.*`

### Requirement: List-replays mode

`BenchMain` SHALL support a `--list-replays` mode that runs only the engine detector across the manifest, prints one line per entry in the form `<relative-path> → <detected-engine> → <manifest-tag>`, and exits 0 without invoking the JMH runner.

Engine detection in this mode SHALL use the active adapter's `detectEngine(Path)` method (which delegates to the adapter's clarity-version-specific detection logic). The mode therefore requires an adapter to be discoverable on the classpath, and produces canonical results when invoked against the most recent adapter (whose clarity recognizes every shipped engine).

#### Scenario: List-replays produces classification table

- **WHEN** `BenchMain --list-replays --replays-root <path>` is invoked from a version subproject
- **THEN** stdout MUST contain one line per manifest entry with the path, detected engine, and manifest-tagged engine, and the process MUST exit 0 without invoking the JMH runner

#### Scenario: List-replays delegates to the active adapter for classification

- **WHEN** `BenchMain --list-replays` is invoked from `:vX.Y.Z:run`
- **THEN** the engine column MUST reflect what `vX.Y.Z`'s clarity recognizes (older adapters that don't recognize a newer engine MUST surface that as `<unknown>` rather than failing the run)

### Requirement: CLI subset filters

`BenchMain` SHALL support three new repeatable command-line flags that filter the matrix expansion: `--impl <name>`, `--workload <name>`, `--variant <name>`. Filtering semantics SHALL be:

- **Loose applicability**: a filter value that does not appear in the running adapter's capability declarations SHALL silently skip the affected cells, with a single summary line printed (matching the existing engine-skip output).
- **Strict on names**: a filter value not present in the harness's union of all known names (impls / workloads / variants across the codebase) SHALL cause `BenchMain` to exit with non-zero status, listing the valid names.
- **Empty intersection**: if filtering eliminates every cell that this adapter would otherwise have run, `BenchMain` SHALL exit with a non-zero status and a clear message stating that filtering produced no benchmarks for this adapter.

#### Scenario: Loose filter on inapplicable impl skips silently

- **WHEN** `BenchMain --impl S2_FLAT` is invoked against an adapter whose `entityStateImpls` does not include `S2_FLAT`
- **THEN** the run MUST continue with whatever other cells remain (if any), printing a one-line skip note for the unmet filter

#### Scenario: Strict rejection on unknown filter value

- **WHEN** `BenchMain --workload propchang` is invoked (typo)
- **THEN** the process MUST exit with non-zero status before any benchmark runs, listing valid workload names

#### Scenario: Empty intersection aborts run

- **WHEN** filters reduce the cell set for the active adapter to zero
- **THEN** `BenchMain` MUST exit with non-zero status and print a message naming the adapter and the active filters

### Requirement: Shadow EntityStateFactory for pre-runtime-API versions

For any version subproject whose pinned clarity release does not expose a public runtime entity-state-selection API, the subproject MAY ship a class at `skadistats/clarity/model/state/EntityStateFactory.java` that shadows the same-named class in the released `clarity-X.Y.Z.jar`. The shadow class SHALL preserve the public method signatures of the released class so that internal clarity callers continue to compile and link against it. The shadow SHALL read the requested impl from a knob set by the adapter prior to invoking `runner.runWith(...)` and SHALL construct the corresponding `EntityState` implementation using the public constructors of `ObjectArrayEntityState`, `NestedArrayEntityState`, and `TreeMapEntityState`.

Each subproject using this mechanism SHALL include a build-time smoke test that constructs an `EntityState` for every impl declared in its `Capabilities.entityStateImpls()` and asserts that the concrete class matches the requested impl. Smoke-test failure SHALL fail the build.

#### Scenario: Shadow takes classpath precedence over released jar

- **WHEN** the v3.1.3 subproject is built and `EntityStateFactory.forS2(serializerField)` is called
- **THEN** the JVM MUST resolve to the subproject's shadow class, not the class inside `clarity-3.1.3.jar`

#### Scenario: Shadow honors requested impl

- **WHEN** the adapter sets the impl knob to `TREE_MAP` and invokes `runner.runWith(...)` against an S2 replay
- **THEN** the shadow's `forS2(...)` MUST construct and return a `TreeMapEntityState` instance (ignoring its `SerializerField` argument when constructing the no-arg `TreeMapEntityState`)

#### Scenario: Smoke test catches drift

- **WHEN** any impl declared in a subproject's capabilities cannot be constructed by the shadow (signature mismatch, missing class, etc.)
- **THEN** the build MUST fail before the bench can be invoked

### Requirement: Provenance of injected impls is recorded, not promoted to score axis

For runs that include a version using the shadow `EntityStateFactory` mechanism, the recorded `context.txt` SHALL include a one-line note per such version stating that impl selection used the shadow shim rather than a public runtime API. The score table column headings SHALL remain unprefixed (e.g., `TREE_MAP`, not `TREE_MAP-injected`) so that the comparison identity is the impl, with the per-version mechanism captured as provenance metadata.

#### Scenario: Recorded run notes shim usage

- **WHEN** a `--record` run includes the v3.1.3 adapter and the run included at least one cell that resolved an impl through the shadow
- **THEN** `context.txt` MUST contain a line under the `--- 3.1.3 ---` block stating that S2 entity-state impls were selected via the shadow `EntityStateFactory` shim

#### Scenario: Score table is impl-only

- **WHEN** a recorded results file is read
- **THEN** the score-table column headings for entity-state impls MUST be the impl names alone (no per-version qualifier), so an impl can be tracked across versions by reading down a single column
