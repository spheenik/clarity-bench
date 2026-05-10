package spheenik.claritybench.v400;

import skadistats.clarity.model.EngineId;
import skadistats.clarity.model.state.EntityStateFactoryShim;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.source.Source;
import spheenik.claritybench.BenchAdapter;
import spheenik.claritybench.Capabilities;
import spheenik.claritybench.Engines;
import spheenik.claritybench.Workloads;
import spheenik.claritybench.v400.processors.Baseline;
import spheenik.claritybench.v400.processors.Lifecycle;
import spheenik.claritybench.v400.processors.Updated1;
import spheenik.claritybench.v400.processors.Updated8;
import spheenik.claritybench.v400.processors.WildcardSingle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class V400Adapter implements BenchAdapter {

    @UsesEntities
    public static class EmptyProcessor {}

    @Override
    public String version() {
        return "4.0.1";
    }

    // Translate this version's frozen EngineId names to the harness canonical
    // vocabulary. clarity 4.0.1 uses CSGO_S2 / CSGO_S1 — names that were later
    // renamed back to CS2 / CSGO in the live tree.
    private static String toCanonical(String engineName) {
        return switch (engineName) {
            case "CSGO_S2" -> Engines.CS2;
            case "CSGO_S1" -> Engines.CSGO;
            default -> engineName;
        };
    }

    @Override
    public Capabilities capabilities() {
        String[] knownEngines = Arrays.stream(EngineId.values())
                .map(Enum::name)
                .map(V400Adapter::toCanonical)
                .toArray(String[]::new);
        var b = Capabilities.builder()
                .supportedEngines(knownEngines)
                .entityStateImpl("OBJECT_ARRAY", Engines.S1_FAMILY)
                .entityStateImpl("NESTED_ARRAY", Engines.S2_FAMILY)
                .entityStateImpl("TREE_MAP",     Engines.S2_FAMILY);

        Set<String> allEngines = Engines.ALL;
        for (String v : new String[]{"Baseline", "Lifecycle", "Updated1", "Updated8"}) {
            b.dispatchVariant(v, allEngines);
        }
        for (String v : new String[]{"Baseline", "WildcardSingle"}) {
            b.propertyChangeVariant(v, allEngines);
        }
        return b.build();
    }

    @Override
    public String implSelectionProvenance() {
        return "S2 entity-state impls selected via shadow EntityStateFactory shim "
                + "(no public runtime API in clarity 4.0.1).";
    }

    @Override
    public String detectEngine(Path replayPath) throws IOException {
        try (Source src = new MappedFileSource(replayPath.toString())) {
            return toCanonical(src.determineEngineType().getId().name());
        }
    }

    @Override
    public void parse(String replayPath, Map<String, String> config) throws Exception {
        String workload = config.getOrDefault(Workloads.KEY_WORKLOAD, Workloads.PARSE);
        String variant  = config.getOrDefault(Workloads.KEY_VARIANT,  Workloads.DEFAULT);
        String impl     = config.getOrDefault(Workloads.KEY_IMPL,     Workloads.DEFAULT);

        Object processor = createProcessor(workload, variant);

        try {
            if (Workloads.PARSE.equals(workload) && !Workloads.DEFAULT.equals(impl)) {
                EntityStateFactoryShim.setRequestedImpl(impl);
            }
            try (var src = new MappedFileSource(replayPath)) {
                new SimpleRunner(src).runWith(processor);
            }
        } finally {
            EntityStateFactoryShim.clearRequestedImpl();
        }
    }

    private Object createProcessor(String workload, String variant) {
        if (Workloads.PARSE.equals(workload) || Workloads.DEFAULT.equals(variant)) {
            return new EmptyProcessor();
        }
        return switch (variant) {
            case "Baseline"        -> new Baseline();
            case "Lifecycle"       -> new Lifecycle();
            case "Updated1"        -> new Updated1();
            case "Updated8"        -> new Updated8();
            case "WildcardSingle"  -> new WildcardSingle();
            default -> throw new IllegalArgumentException(
                    "Unknown variant '" + variant + "' for workload '" + workload + "'");
        };
    }
}
