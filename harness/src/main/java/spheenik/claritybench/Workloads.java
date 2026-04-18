package spheenik.claritybench;

import java.util.Set;

/**
 * Canonical workload identifiers and the documented config-map keys that
 * {@link BenchAdapter#parse} reads. Adapters dispatch on
 * {@link #KEY_WORKLOAD}; the harness validates filter values against
 * {@link #ALL}.
 */
public final class Workloads {

    public static final String PARSE      = "parse";
    public static final String DISPATCH   = "dispatch";
    public static final String PROPCHANGE = "propchange";

    public static final Set<String> ALL = Set.of(PARSE, DISPATCH, PROPCHANGE);

    /** Sentinel value for any axis that does not apply to this workload. */
    public static final String DEFAULT = "DEFAULT";

    public static final String KEY_WORKLOAD = "workload";
    public static final String KEY_VARIANT  = "variant";
    public static final String KEY_IMPL     = "impl";

    /**
     * Universe of known entity-state impl names across all adapters in the
     * codebase. Used by the harness's strict-name CLI filter validation.
     * Adapter applicability is per-adapter and per-engine; this set is for
     * typo detection only.
     */
    public static final Set<String> KNOWN_IMPLS = Set.of(
            "OBJECT_ARRAY",
            "NESTED_ARRAY",
            "TREE_MAP",
            "FLAT",        // v5.x S2EntityStateType.FLAT until renamed in adapter cleanup
            "S1_FLAT",
            "S2_FLAT");

    /**
     * Universe of known dispatch + propchange variant names across all
     * adapters in the codebase. Used by strict-name CLI filter validation.
     */
    public static final Set<String> KNOWN_VARIANTS = Set.of(
            "Baseline",
            "Lifecycle",
            "Updated1",
            "Updated8",
            "WildcardSingle");

    private Workloads() {}
}
