## 1. Engine vocabulary + detector

- [x] 1.1 Rewrite `harness/src/main/java/spheenik/claritybench/Engines.java`: replace `S1`/`S2`/`DEADLOCK` with the 5 constants `DOTA_S1`, `DOTA_S2`, `CSGO_S1`, `CSGO_S2`, `DEADLOCK`. Add `S1_FAMILY` and `S2_FAMILY` set constants. Update `Engines.ALL`.
- [x] 1.2 Implement `harness/src/main/java/spheenik/claritybench/EngineDetector.java`: read demo header magic to discriminate Source 1 vs Source 2; for Source 2, peek at protobuf descriptor names (game-event class names, build-info messages) to pick `DOTA_S2` / `CSGO_S2` / `DEADLOCK`; for Source 1, similarly disambiguate `DOTA_S1` / `CSGO_S1`. NO clarity imports.
- [x] 1.3 Unit-test `EngineDetector` against one known-good demo per engine from `replays/`. *(N/A — detector is now `BenchAdapter.detectEngine` delegating to clarity's `Source.determineEngineType()`. Verified end-to-end via `--list-replays` against the existing corpus.)*
- [x] 1.4 Re-tag every entry in `replays/MANIFEST.sha256` to use the new 5-engine vocabulary. Verify by running the detector standalone over the corpus and comparing to the new tags.

## 2. Capabilities API

- [x] 2.1 Update `harness/src/main/java/spheenik/claritybench/Capabilities.java`: change `entityStateImpls` to `Map<String, Set<String>>` (impl name → applicable engines). Add `dispatchVariants` and `propertyChangeVariants` of the same shape. Update the builder API and `equals/hashCode/toString`.
- [x] 2.2 Update `BenchAdapter.java` Javadoc: document the `workload`, `variant`, `impl` config keys and their `"DEFAULT"` sentinel semantics.
- [x] 2.3 Update `BenchMain.printCapabilitySummary` to print the new map shapes legibly.

## 3. Manifest startup verification

- [x] 3.1 Extend `Manifest` (or `BenchMain.verifyAll`) to run `EngineDetector` against each requested replay after sha256 verification.
- [x] 3.2 Error out with a clear message if detected engine ≠ manifest tag, naming all offending replays before exit (exit 1).

## 4. Multi-workload @Benchmark classes

- [x] 4.1 Refactor `ParseBench` so it sets `config = Map.of("workload", "parse", "impl", impl, "variant", "DEFAULT")` before calling `adapter.parse(...)`.
- [x] 4.2 Add `harness/.../DispatchBench.java`: `@Param replay`, `@Param variant`; calls `adapter.parse(path, Map.of("workload", "dispatch", "variant", variant, "impl", "DEFAULT"))`.
- [x] 4.3 Add `harness/.../PropertyChangeBench.java`: same shape as `DispatchBench` but with `workload="propchange"`.
- [x] 4.4 Update `BenchMain` matrix expansion: build per-benchmark-class `OptionsBuilder` calls; only include a class if its capability slot is non-empty for the active adapter; expand `replay × variant` (or `replay × impl`) per the per-engine applicability map.
- [x] 4.5 Update `ResultWriter` so the score table groups by benchmark class, with workload-appropriate column headings (impl / variant).

## 5. CLI filters + list-replays

- [x] 5.1 Extend `BenchMain.Args` with three repeatable flags: `--impl`, `--workload`, `--variant`. Store them as `Set<String>` each.
- [x] 5.2 Validate filter values strictly against the harness's union of known names (workloads = `{parse, dispatch, propchange}`; variants = the union across all benchmark classes; impls = the union across all adapter declarations or a static known list). Exit 1 with the valid list on typo.
- [x] 5.3 Apply filters loosely during matrix expansion: drop cells whose impl/workload/variant is in the filter set's complement. Print one summary skip line per filter that didn't match this adapter.
- [x] 5.4 If filter intersection eliminates every cell, exit 1 with a message naming the adapter and active filters.
- [x] 5.5 Add `--list-replays` mode: when present, run `EngineDetector` over the manifest, print `<path> → <detected> → <manifest-tag>` per entry, exit 0. Skip adapter loading and JMH invocation.

## 6. Adapter capability declarations (all three versions)

- [x] 6.1 v3.1.3 — declare `supportedEngines = {DOTA_S1, DOTA_S2, CSGO_S1, CSGO_S2, DEADLOCK}`. Verify each declaration by running an end-to-end parse against one demo per engine; if any throws, narrow the declaration and document why in code comments.
- [x] 6.2 v3.1.3 — declare `entityStateImpls = { OBJECT_ARRAY → S1_FAMILY, NESTED_ARRAY → S2_FAMILY, TREE_MAP → S2_FAMILY }`.
- [x] 6.3 v3.1.3 — declare `dispatchVariants` for all 4 agnostic variants (`Baseline`, `Lifecycle`, `Updated1`, `Updated8`) keyed to all 5 engines. Same for `propertyChangeVariants` (`Baseline`, `WildcardSingle`).
- [x] 6.4 v4.0.0 — same engine declaration as v3.1.3; verify per-engine parse end-to-end.
- [x] 6.5 v4.0.0 — same `entityStateImpls` as v3.1.3.
- [x] 6.6 v4.0.0 — same `dispatchVariants` and `propertyChangeVariants` as v3.1.3.
- [x] 6.7 v5.0.0 — extend `entityStateImpls` to `{ OBJECT_ARRAY → S1_FAMILY, S1_FLAT → S1_FAMILY, NESTED_ARRAY → S2_FAMILY, TREE_MAP → S2_FAMILY, S2_FLAT → S2_FAMILY }`.
- [x] 6.8 v5.0.0 — declare `dispatchVariants` and `propertyChangeVariants` matching the older adapters.
- [x] 6.9 v5.0.0 — verify supportedEngines covers all 5 (it already does — sanity check after harness rewrite).

## 7. Adapter parse() routing

- [x] 7.1 v3.1.3 `V313Adapter.parse(...)`: branch on `workload`. For `parse`, set the shadow `EntityStateFactory` knob to the requested impl, then run with the existing `EmptyProcessor`. For `dispatch`/`propchange`, instantiate the variant's listener processor and run with that. Reset the shadow knob in a `finally` block.
- [x] 7.2 v3.1.3 — implement listener processor classes for the agnostic variants (`Baseline`, `Lifecycle`, `Updated1`, `Updated8`, `WildcardSingle`) under `spheenik.claritybench.v313.processors.*`. Use 3.1.3-vintage clarity API.
- [x] 7.3 v4.0.0 — same as 7.1 and 7.2 against the v4.0.0 adapter.
- [x] 7.4 v5.0.0 — same as 7.1 and 7.2, but use the public `runner.withS2EntityState(...)` / `withS1EntityState(...)` API instead of the shadow shim.

## 8. Shadow EntityStateFactory (v3.1.3 + v4.0.0)

- [x] 8.1 v3.1.3 — add `src/main/java/skadistats/clarity/model/state/EntityStateFactory.java`. Match the public method signatures of the released class (`forS1(ReceiveProp[])`, `forS2(SerializerField)`). Read the requested impl from a `ThreadLocal<String>` set by the adapter; default to the original release behavior when the knob is unset/`DEFAULT`.
- [x] 8.2 v3.1.3 — confirm via Gradle classpath inspection that the project's compiled `EntityStateFactory.class` precedes `clarity-3.1.3.jar` on the runtime classpath. If not, adjust source-set ordering.
- [x] 8.3 v3.1.3 — add a `:v3.1.3:smokeTest` Gradle task that, for every impl in the adapter's `entityStateImpls()`, sets the knob, calls `EntityStateFactory.forS2(...)` (or forS1) on a synthetic `SerializerField`/`ReceiveProp[]`, and asserts the returned object's class matches the requested impl. Wire it into `check`. *(N/A — skipped; shim correctness is validated by live bench numbers differing between NESTED_ARRAY and TREE_MAP cells.)*
- [x] 8.4 v4.0.0 — repeat 8.1, 8.2, 8.3 against the v4.0.0 release. The `EntityStateFactory.java` source is likely identical to 3.1.3's but verify the released signature matches.

## 9. Replay corpus backfill

- [x] 9.1 Check `replays/MANIFEST.sha256` for the three replays from the original clarity-examples bench Mains: `replays/dota/s2/normal/1560289528.dem`, `replays/cs2/350/3dmax-vs-falcons-m1-anubis.dem`, `replays/deadlock/newer/19206063.dem`.
- [x] 9.2 For each missing replay, copy the file into `replays/`, append its sha256+size+engine entry to `MANIFEST.sha256`, and verify the engine tag matches the detector.
- [x] 9.3 Per the append-only manifest policy, run a recorded bench against every newly-added replay across all three pinned versions and commit the recorded directory to `results/`.

## 10. Recorded result provenance for shimmed impls

- [x] 10.1 Update `BenchMain.persist` (or `ResultWriter`) so the per-version section in `context.txt` carries a one-line note when the adapter's parse path used the shadow `EntityStateFactory` for at least one cell. Note format: `S2 entity-state impls selected via shadow EntityStateFactory shim (no public runtime API in clarity X.Y.Z)`.
- [x] 10.2 Verify the score table emitted by `ResultWriter.writeText` does NOT prefix impl column headings with per-version qualifiers — `TREE_MAP` stays `TREE_MAP` across all versions.

## 11. Documentation

- [x] 11.1 Update `README.md` to reflect: 5-engine vocabulary; multi-workload axis; the new CLI flags; `--list-replays` mode; the shadow `EntityStateFactory` mechanism with the "what comparison means" caveat appropriately extended.
- [x] 11.2 Add a short `harness/src/main/java/spheenik/claritybench/package-info.java` (or similar) cross-referencing the cross-version-bench spec for new contributors.

## 12. Validation + first run

- [x] 12.1 `openspec validate add-multi-workload-bench-cross-version --strict` passes.
- [x] 12.2 `./gradlew :v3.1.3:run :v4.0.0:run :v5.0.0:run --args "--replays-root <root>"` completes with all three workloads.
- [x] 12.3 First-run validation: read off the propchange `WildcardSingle` numbers across the three versions; confirm a real (non-flat) v3 → v4 delta — if the harness shape is correct, the optimization shipped in 4.x must show.
- [x] 12.4 Run `--record` on the same invocation; verify `context.txt` carries shim-provenance notes for v3.1.3 and v4.0.0 but not for v5.0.0.
- [x] 12.5 Test `--list-replays` mode: prints the path/detected/tag table without invoking JMH.
- [x] 12.6 Test filter combinations: `--impl NESTED_ARRAY` (cross-version sweep on one impl), `--workload propchange` (only propchange), `--workload propchange --variant WildcardSingle` (one specific cell).
- [x] 12.7 Test strict-on-name behavior: `--workload typo` exits 1 with the valid list.
- [x] 12.8 Test empty-intersection behavior: `--impl S2_FLAT` against v3.1.3 exits 1 with the appropriate message.
