## 1. JMH viability spike (do first — invalidates D3 if it fails)

- [x] 1.1 Throwaway minimal Gradle module with one `@Benchmark` declaring `@Param({"DEFAULT"})` and a programmatic `OptionsBuilder.param("knob", "A", "B", "C")` override
- [x] 1.2 Confirm JMH runs three permutations (A, B, C) — not one ("DEFAULT") and not four
- [x] 1.3 Confirm JMH runs one permutation ("DEFAULT") when no override is supplied
- [x] 1.4 If either fails, escalate to design.md D3 alternative (one bench class per version) before proceeding — **PASSED, no escalation needed**

## 2. Repository skeleton

- [x] 2.1 Root `build.gradle.kts` with Java 21 toolchain, no Clarity dependency
- [x] 2.2 `settings.gradle.kts` with `rootProject.name = "clarity-bench"` and `includeBuild("harness")` placeholder
- [x] 2.3 Root `README.md` covering: methodology summary, replay corpus policy (D10), `--record` workflow, candidate workflow (`publishToMavenLocal` then bench), hardware-tag conventions, JDK-skew framing
- [x] 2.4 `work/` directory ignored via `.gitignore`; `results/` directory tracked with a stub `README.md` explaining its layout

## 3. `harness/` module

- [x] 3.1 Gradle build for `harness/`: Java 21, `me.champeau.jmh` plugin, JMH 1.x dependencies, **no `clarity` dependency**
- [x] 3.2 `BenchAdapter` interface (`capabilities()`, `parse(String replay, Map<String,String> config)`)
- [x] 3.3 `Capabilities` record with `supportedEngines`, `entityStateImpls`, builder with empty defaults
- [x] 3.4 `Engines` constants/enum defining the canonical engine vocabulary (`S1`, `S2`, `DEADLOCK`, ...)
- [x] 3.5 `ParseBench` JMH class: `@Param({"DEFAULT"})` for the impl knob, calls `adapter.parse(replay, config)` only
- [x] 3.6 Adapter discovery mechanism (ServiceLoader or system property) so a per-version subdir registers its single `BenchAdapter`

## 4. `BenchMain` orchestration

- [x] 4.1 CLI parsing: `--record`, replay roots / replay-set selection, optional adapter filter
- [x] 4.2 Load `replays/MANIFEST.sha256`, verify each replay's actual sha1 (refuse mismatches and unmanifested replays)
- [x] 4.3 Build matrix: cross-product of replays × discovered adapters, filtered by `supportedEngines`
- [x] 4.4 Build JMH `OptionsBuilder` with runtime `@Param` overrides per adapter (impl set or no override = `"DEFAULT"`)
- [x] 4.5 Set `shouldFailOnError(false)` so one failed cell doesn't abort the run
- [x] 4.6 Capability summary printed at startup (per design open question Q3)

## 5. Logging guard

- [x] 5.1 Bundle a `logback.xml` (or programmatic config) in `harness/` setting root level to WARN
- [x] 5.2 At `BenchMain` startup, assert root logger is at WARN or above; exit non-zero with clear message if not
- [x] 5.3 Confirm WARN/ERROR statements still surface in run output

## 6. Failure handling / `FAILURES` output

- [x] 6.1 Post-process JMH `RunResult` collection to extract failed cells (replay × adapter × impl)
- [x] 6.2 Emit dedicated `FAILURES` section in stdout output, with cell coordinates + exception class + message + root cause
- [x] 6.3 Set process exit code non-zero whenever any cell failed
- [x] 6.4 Verify by injecting a deliberate exception in a test adapter and confirming the rest of the matrix still runs

## 7. Result persistence (`--record`)

- [x] 7.1 Hardware fingerprinting helper: CPU model (parse `/proc/cpuinfo` on Linux), physical/logical core counts, RAM, OS, JVM, JVM args
- [x] 7.2 Hardware-tag derivation (short slug for directory name, e.g. `ryzen9950x`)
- [x] 7.3 `context.txt` writer: full hardware fingerprint, bench repo git SHA, resolved Clarity JAR sha1 (for SNAPSHOT runs), replay manifest entries actually used
- [x] 7.4 Result-table writer: text format (median/min/max/p95, alloc/op, GCs, GC time) + JSON sidecar
- [x] 7.5 Refuse to overwrite an existing `results/<date>_<hardware-tag>_<jdk>/` directory

## 8. `v5.0.0/` candidate subdir (build first — fastest feedback during harness work)

- [x] 8.1 Gradle build pinning `com.skadistats:clarity:5.0.0-SNAPSHOT`, `mavenLocal()` then `mavenCentral()`
- [x] 8.2 `V500Adapter`: `supportedEngines = {S1, S2, DEADLOCK}` (verify against current 5.x), `entityStateImpls = {NESTED_ARRAY, TREE_MAP, FLAT}`
- [x] 8.3 `parse()` translates `"DEFAULT"` → no `withS2EntityState` call; non-`"DEFAULT"` → `withS2EntityState(S2EntityStateType.valueOf(impl))`
- [x] 8.4 End-to-end smoke: `publishToMavenLocal` in `../clarity`, then run bench against the existing single replay; numbers should be in the same ballpark as the current `clarity-examples` results

## 9. `v4.0.0/` released subdir

- [x] 9.1 Gradle build pinning `com.skadistats:clarity:4.0.0`, `mavenCentral()` only (no `mavenLocal()`)
- [x] 9.2 `V400Adapter`: capability sets per actual 4.0.0 surface (verify which entity-state impls were exposed, which engines)
- [x] 9.3 End-to-end smoke run against the existing single replay

## 10. `v3.1.3/` released subdir

- [x] 10.1 Gradle build pinning `com.skadistats:clarity:3.1.3`, `mavenCentral()` only
- [x] 10.2 `V313Adapter`: `entityStateImpls = {}` (no choice), `supportedEngines` = whatever 3.1.3 actually decoded (likely `{S1, S2}`, no Deadlock)
- [x] 10.3 Verify slf4j/logback resolution doesn't drag in something incompatible; if 3.1.3's transitive deps clash with the harness's logback, sort out the conflict
- [x] 10.4 End-to-end smoke run against the existing single replay

## 11. Manifest bootstrap

- [x] 11.1 Compute sha1 + size for `replays/dota/s2/340/8168882574_1198277651.dem`, write `replays/MANIFEST.sha256`
- [x] 11.2 Document the exact format in `replays/README.md` so future replays follow the same scheme
- [x] 11.3 Confirm `BenchMain` refuses an obviously tampered file (rename test replay, run, expect refusal)

## 12. First recorded baseline

- [x] 12.1 Run `BenchMain --record` against all three versions on the standard replay
- [x] 12.2 Commit the resulting `results/<date>_<hw>_jdk21/` directory
- [x] 12.3 Update root `README.md` to point at this first baseline as an example of the output shape

## 13. Sanity checks before declaring complete

- [x] 13.1 `harness/` has zero references to `skadistats.clarity.*` (grep)
- [x] 13.2 Released subdirs (`v3.1.3/`, `v4.0.0/`) contain no `mavenLocal()` (grep)
- [x] 13.3 Deliberately set root logger to INFO; confirm `BenchMain` refuses to run
- [x] 13.4 Deliberately corrupt one byte of a replay; confirm `BenchMain` refuses with sha mismatch
- [ ] 13.5 Adding a fake fourth version (copy of `v5.0.0/`) drops in cleanly without harness changes — deferred (architecture proven by the three-version setup)
