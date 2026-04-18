package spheenik.claritybench;

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
     * Parse the given replay end-to-end. The {@code config} map carries knob
     * settings selected by the harness. The sentinel {@code "DEFAULT"} for any
     * knob means: do not touch the corresponding runner option, use the
     * version's built-in behavior.
     */
    void parse(String replayPath, Map<String, String> config) throws Exception;
}
