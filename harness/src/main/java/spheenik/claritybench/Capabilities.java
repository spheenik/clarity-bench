package spheenik.claritybench;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-adapter declaration of which bench knobs apply. Additive: when a new
 * field is appended, existing adapters built against the old shape pick up
 * the empty/default value via the builder and continue to work.
 */
public record Capabilities(
        Set<String> supportedEngines,
        Set<String> entityStateImpls
) {
    public Capabilities {
        supportedEngines = Set.copyOf(supportedEngines);
        entityStateImpls = Set.copyOf(entityStateImpls);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Set<String> supportedEngines = new LinkedHashSet<>();
        private final Set<String> entityStateImpls = new LinkedHashSet<>();

        public Builder supportedEngines(String... engines) {
            for (String e : engines) supportedEngines.add(e);
            return this;
        }

        public Builder entityStateImpls(String... impls) {
            for (String i : impls) entityStateImpls.add(i);
            return this;
        }

        public Capabilities build() {
            return new Capabilities(supportedEngines, entityStateImpls);
        }
    }
}
