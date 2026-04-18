# Cross-version bench specification

## Purpose

Define the contract for a JMH-based benchmark suite that can be run against any pinned Clarity version through a per-version adapter, with version-specific knobs declared via a capability model and injected into JMH parameters at runtime. Enables apples-to-apples comparison of parser throughput and allocations across the long arc of Clarity releases.

## Requirements


### Requirement: Per-version adapter contract

Each Clarity version supported by the bench SHALL be exposed to the harness through exactly one `BenchAdapter` implementation. The harness SHALL NOT import any class from `skadistats.clarity.*`; all version-specific Clarity API access MUST occur inside an adapter.

#### Scenario: Harness purity check
- **WHEN** the `harness/` module is compiled
- **THEN** it MUST compile without any dependency on a `clarity` artifact (only its own sources, JMH, and standard libraries)

#### Scenario: Each version subdirectory provides exactly one adapter
- **WHEN** a `vX.Y.Z/` subdirectory is added to the repository
- **THEN** it MUST contain a class implementing `BenchAdapter` and a Gradle build that pins `com.skadistats:clarity:X.Y.Z` (or a `5.0-SNAPSHOT`-style coordinate for the candidate)

### Requirement: Capability declaration

Each adapter SHALL return a `Capabilities` value declaring which engines it can parse and which entity-state implementations it exposes as choosable. The `Capabilities` type SHALL be additive: adding a new field MUST NOT require changes to existing adapters that do not provide that capability.

#### Scenario: Empty entity-state impl set means version uses its default
- **WHEN** an adapter returns `Capabilities.entityStateImpls()` as the empty set
- **THEN** the bench MUST run that adapter exactly once per compatible replay, with no impl override applied

#### Scenario: Non-empty entity-state impl set drives JMH parameter expansion
- **WHEN** an adapter returns `Capabilities.entityStateImpls() = {NESTED_ARRAY, TREE_MAP, FLAT}`
- **THEN** the bench MUST run that adapter three times per compatible replay, once per declared impl, passing the impl name in the config map handed to `parse()`

### Requirement: Engine-based replay filtering

The bench SHALL skip replays whose engine is not present in the running adapter's `supportedEngines`. The engine identifier vocabulary SHALL be defined in the harness module so all adapters share the same string set.

#### Scenario: Adapter without Deadlock support skips Deadlock replays
- **WHEN** the bench is run against an adapter whose `supportedEngines` does not contain `DEADLOCK`
- **THEN** any replay tagged as `DEADLOCK` MUST be skipped without invoking `parse()`, and the skip MUST be visible in the run output

#### Scenario: Adapter inventing an unknown engine string is rejected
- **WHEN** an adapter declares an engine string not present in the harness vocabulary
- **THEN** `BenchMain` MUST refuse to start the run and emit a clear error naming the offending adapter and string

### Requirement: Entity-state impl sentinel contract

The shared `ParseBench` SHALL declare each tunable knob with a single sentinel default (`"DEFAULT"`). When the adapter's `parse()` receives `"DEFAULT"` for a knob, it MUST NOT touch the corresponding runner configuration and MUST run the version's built-in behavior.

#### Scenario: Sentinel passes through untouched
- **WHEN** `parse(replay, {"impl": "DEFAULT"})` is invoked on any adapter
- **THEN** the adapter MUST run the version's default entity-state impl without calling any version-specific configuration method for that knob

#### Scenario: Non-sentinel impl is honored
- **WHEN** `parse(replay, {"impl": "FLAT"})` is invoked on a v5.x adapter whose capabilities include `FLAT`
- **THEN** the adapter MUST configure the runner to use the `FLAT` entity-state implementation before parsing

### Requirement: Logging is silenced below WARN

Every adapter SHALL configure the runtime logger so that no log statement below WARN level fires during a bench run. `BenchMain` SHALL verify this at startup and refuse to run if the configuration permits DEBUG or INFO output.

#### Scenario: WARN-level guard refuses to run
- **WHEN** `BenchMain` starts and detects that the root logger is configured at INFO or below
- **THEN** it MUST exit with a non-zero status and emit an error stating the misconfiguration

#### Scenario: WARN and ERROR remain enabled
- **WHEN** the bench runs and a parse path emits a WARN-level log
- **THEN** the message MUST appear in the run output (so genuine misbehavior surfaces)

### Requirement: Replay manifest with sha256 enforcement

Every replay used in a bench run SHALL be listed in `replays/MANIFEST.sha256` with its path, byte size, and sha256 digest. `BenchMain` SHALL verify each replay's actual sha256 against the manifest before invoking `parse()` on it. The manifest is append-only.

#### Scenario: Manifest mismatch refuses the run
- **WHEN** a replay file's actual sha256 differs from the value recorded in `MANIFEST.sha256`
- **THEN** `BenchMain` MUST exit with a non-zero status before any benchmark cell is executed, naming the offending replay

#### Scenario: Replay missing from manifest refuses the run
- **WHEN** a replay path is supplied to the bench but is not present in `MANIFEST.sha256`
- **THEN** `BenchMain` MUST exit with a non-zero status, naming the unmanifested replay

#### Scenario: Adding a replay obligates a backfill
- **WHEN** a new replay is appended to `MANIFEST.sha256`
- **THEN** the next persisted result run (per the persistence requirement) MUST include rows for the new replay against every version present in the repository

### Requirement: Run failures are reported, never silently swallowed

A failure in `adapter.parse(...)` SHALL NOT abort the surrounding bench run. The harness SHALL allow JMH to continue with the remaining matrix cells, collect failed cells, and emit them in a dedicated `FAILURES` section of the run output. The process exit code SHALL be non-zero if any cell failed.

#### Scenario: One cell fails, others continue
- **WHEN** `parse()` throws on one (replay, version, impl) cell during a multi-cell run
- **THEN** the remaining cells MUST still execute, and the output MUST contain both their successful results and a `FAILURES` entry naming the failed cell with its exception class and message

#### Scenario: Non-zero exit on any failure
- **WHEN** any cell in a run failed
- **THEN** `BenchMain` MUST exit with a non-zero status code, even if other cells succeeded

#### Scenario: Harness does not catch parse exceptions
- **WHEN** `adapter.parse()` throws inside `ParseBench`
- **THEN** the exception MUST propagate to JMH (the harness MUST NOT wrap the call in `try`/`catch`)

### Requirement: Result persistence is opt-in and hardware-tagged

`BenchMain` SHALL persist results to the tracked `results/` directory only when invoked with `--record`. Persisted runs SHALL be written under `results/<date>_<hardware-tag>_<jdk>/`. Each persisted run SHALL include a `context.txt` capturing JDK version+vendor, OS, CPU model, physical/logical core counts, RAM, JVM args, the bench repo's git SHA, the resolved Clarity JAR's SHA256, and the replay manifest entries actually used.

#### Scenario: Without --record, nothing is written to results/
- **WHEN** `BenchMain` is invoked without `--record`
- **THEN** no file under `results/` MUST be created or modified

#### Scenario: With --record, run is committed to a hardware-tagged directory
- **WHEN** `BenchMain` is invoked with `--record` on a host with hardware tag `ryzen9950x` and JDK 21
- **THEN** the run output MUST be written to `results/<date>_ryzen9950x_jdk21/`, including `context.txt` with the full hardware fingerprint

#### Scenario: Existing result directories are immutable
- **WHEN** a bench run completes with `--record` and a directory matching the same `<date>_<hardware-tag>_<jdk>/` already exists
- **THEN** `BenchMain` MUST refuse to overwrite it (it MUST either create a disambiguated sibling or exit with an error)

### Requirement: Released versions resolve from Maven Central; candidate uses mavenLocal

For each `vX.Y.Z` subdirectory whose pin is a released version, the build SHALL declare only `mavenCentral()` as a Clarity source. For the candidate subdirectory pinned to a `-SNAPSHOT` coordinate, the build SHALL declare `mavenLocal()` ahead of `mavenCentral()`.

#### Scenario: Released subdir does not include mavenLocal
- **WHEN** a released `vX.Y.Z/build.gradle.kts` is inspected
- **THEN** its `repositories {}` block MUST NOT contain `mavenLocal()` (preventing a stale local artifact from shadowing the Central one)

#### Scenario: Candidate subdir resolves SNAPSHOT from mavenLocal
- **WHEN** the candidate subdirectory is built and a matching `-SNAPSHOT` artifact has been published to `~/.m2`
- **THEN** the candidate subdirectory MUST resolve the Clarity dependency from `~/.m2`, not from a remote snapshots repository

### Requirement: Candidate JAR identity is recorded

For runs targeting a `-SNAPSHOT` Clarity version, `BenchMain` SHALL record the resolved Clarity JAR's sha256 (and last-modified timestamp) in the run's `context.txt`, so a future reader can identify exactly which build of the snapshot was measured.

#### Scenario: Snapshot run records JAR sha256
- **WHEN** a `--record` run targets a `-SNAPSHOT` Clarity version
- **THEN** `context.txt` MUST contain a `clarity SHA:` line with the sha256 of the resolved JAR file
