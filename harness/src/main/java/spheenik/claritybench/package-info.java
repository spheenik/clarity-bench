/**
 * Cross-version Clarity benchmark harness.
 *
 * <p>One {@link spheenik.claritybench.BenchAdapter} per pinned clarity version
 * (under {@code v3.1.3/}, {@code v4.0.1/}, {@code v5.0.0/}); shared
 * orchestration, JMH wiring, manifest verification, and CLI plumbing live in
 * this package. The harness imports nothing from {@code skadistats.clarity.*}.
 *
 * <p>Three workloads:
 * <ul>
 *   <li>{@link spheenik.claritybench.ParseBench} — entity-state impl axis</li>
 *   <li>{@link spheenik.claritybench.DispatchBench} — listener-count variant axis</li>
 *   <li>{@link spheenik.claritybench.PropertyChangeBench} — propchange listener variant axis</li>
 * </ul>
 *
 * <p>See {@code openspec/specs/cross-version-bench/spec.md} for the full
 * requirement set. README.md covers operator-facing usage.
 */
package spheenik.claritybench;
