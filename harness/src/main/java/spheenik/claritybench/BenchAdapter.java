package spheenik.claritybench;

import java.nio.file.Path;
import java.util.Map;

/**
 * One implementation per supported clarity version. Lives in the version
 * subproject, never in {@code harness/}. The harness discovers adapters via
 * {@link java.util.ServiceLoader}; each version subproject contributes one
 * service entry under {@code META-INF/services/spheenik.claritybench.BenchAdapter}.
 */
public interface BenchAdapter {

    /** A short identifier for this adapter, e.g. {@code "5.0.0"} or {@code "5.0.0-SNAPSHOT"}. */
    String version();

    /** Capabilities this version exposes; drives matrix expansion in {@code BenchMain}. */
    Capabilities capabilities();

    /**
     * Optional provenance note for the recorded {@code context.txt}. Adapters
     * that select entity-state impls through a non-public mechanism (e.g. a
     * shadowed {@code EntityStateFactory} class for releases that predate the
     * runtime selection API) SHOULD return a one-line note describing the
     * mechanism; the harness writes it under this version's section in
     * {@code context.txt} so future readers can audit how the cells were
     * produced. Default: {@code null} (no provenance note — the version uses
     * the public runtime API).
     */
    default String implSelectionProvenance() {
        return null;
    }

    /**
     * Classify a replay by engine using this version's clarity detection
     * logic (typically a thin wrapper over {@code EngineMagic} or its
     * version-specific equivalent). Returns one of the {@link Engines}
     * constants on success, or {@code null} if this clarity version does
     * not recognize the file's engine (e.g. an older release that predates
     * a newer game). Throws {@link java.io.IOException} for genuine I/O
     * problems with the file itself.
     *
     * <p>Used by {@code BenchMain} at startup to verify manifest tags and
     * by the {@code --list-replays} mode.
     */
    String detectEngine(Path replayPath) throws java.io.IOException;

    /**
     * Parse the given replay end-to-end with the workload, variant, and impl
     * selected by the harness.
     *
     * <p>The {@code config} map carries documented keys:
     * <ul>
     *   <li>{@code workload}: one of {@code "parse"}, {@code "dispatch"},
     *       {@code "propchange"}. Selects which listener wiring the adapter
     *       installs before invoking the runner.</li>
     *   <li>{@code variant}: workload-specific name (e.g.
     *       {@code "Updated8"}, {@code "WildcardSingle"}). For the
     *       {@code parse} workload this is always {@code "DEFAULT"} (parse
     *       has no variant axis).</li>
     *   <li>{@code impl}: entity-state implementation name (e.g.
     *       {@code "NESTED_ARRAY"}, {@code "S2_FLAT"}). For the
     *       {@code dispatch} and {@code propchange} workloads this is always
     *       {@code "DEFAULT"} (impl axis does not apply).</li>
     * </ul>
     *
     * <p>The sentinel {@code "DEFAULT"} for any knob means: do not touch the
     * corresponding runner option, use the version's built-in behavior.
     */
    void parse(String replayPath, Map<String, String> config) throws Exception;
}
