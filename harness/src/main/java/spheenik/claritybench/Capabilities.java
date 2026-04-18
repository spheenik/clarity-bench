package spheenik.claritybench;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-adapter declaration of which bench knobs apply, plus the per-engine
 * applicability of each knob.
 *
 * <p>Each of the three workload-axis maps is keyed by a knob name (impl name
 * or variant name) and maps to the set of engines for which that knob is
 * meaningful. A knob omitted from a map is not exposed by this adapter at
 * all. {@link BenchMain} matrix expansion intersects the per-cell engine
 * with the knob's applicability set: cells whose engine is not in the set
 * simply do not exist.
 *
 * <p>Additive: when a new field is appended, existing adapters built against
 * the old shape pick up the empty default via the builder and continue to work.
 */
public record Capabilities(
        Set<String> supportedEngines,
        Map<String, Set<String>> entityStateImpls,
        Map<String, Set<String>> dispatchVariants,
        Map<String, Set<String>> propertyChangeVariants
) {
    public Capabilities {
        supportedEngines = Set.copyOf(supportedEngines);
        entityStateImpls = freeze(entityStateImpls);
        dispatchVariants = freeze(dispatchVariants);
        propertyChangeVariants = freeze(propertyChangeVariants);
    }

    private static Map<String, Set<String>> freeze(Map<String, Set<String>> in) {
        if (in.isEmpty()) return Collections.emptyMap();
        Map<String, Set<String>> out = new LinkedHashMap<>(in.size());
        for (Map.Entry<String, Set<String>> e : in.entrySet()) {
            out.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Set<String> supportedEngines = new LinkedHashSet<>();
        private final Map<String, Set<String>> entityStateImpls = new LinkedHashMap<>();
        private final Map<String, Set<String>> dispatchVariants = new LinkedHashMap<>();
        private final Map<String, Set<String>> propertyChangeVariants = new LinkedHashMap<>();

        public Builder supportedEngines(String... engines) {
            for (String e : engines) supportedEngines.add(e);
            return this;
        }

        public Builder entityStateImpl(String name, Set<String> applicableEngines) {
            entityStateImpls.put(name, new LinkedHashSet<>(applicableEngines));
            return this;
        }

        public Builder dispatchVariant(String name, Set<String> applicableEngines) {
            dispatchVariants.put(name, new LinkedHashSet<>(applicableEngines));
            return this;
        }

        public Builder propertyChangeVariant(String name, Set<String> applicableEngines) {
            propertyChangeVariants.put(name, new LinkedHashSet<>(applicableEngines));
            return this;
        }

        public Capabilities build() {
            return new Capabilities(
                    supportedEngines,
                    entityStateImpls,
                    dispatchVariants,
                    propertyChangeVariants);
        }
    }
}
