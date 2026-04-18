package spheenik.claritybench.v500;

import skadistats.clarity.model.EngineId;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.source.Source;
import skadistats.clarity.state.s1.S1EntityStateType;
import skadistats.clarity.state.s2.S2EntityStateType;
import spheenik.claritybench.BenchAdapter;
import spheenik.claritybench.Capabilities;
import spheenik.claritybench.Engines;
import spheenik.claritybench.Workloads;
import spheenik.claritybench.v500.processors.Baseline;
import spheenik.claritybench.v500.processors.Lifecycle;
import spheenik.claritybench.v500.processors.Updated1;
import spheenik.claritybench.v500.processors.Updated8;
import spheenik.claritybench.v500.processors.WildcardSingle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class V500Adapter implements BenchAdapter {

    @UsesEntities
    public static class EmptyProcessor {}

    @Override
    public String version() {
        return "5.0.0-SNAPSHOT";
    }

    @Override
    public Capabilities capabilities() {
        String[] knownEngines = Arrays.stream(EngineId.values())
                .map(Enum::name)
                .toArray(String[]::new);
        var b = Capabilities.builder()
                .supportedEngines(knownEngines)
                .entityStateImpl("OBJECT_ARRAY", Engines.S1_FAMILY)
                .entityStateImpl("S1_FLAT",      Engines.S1_FAMILY)
                .entityStateImpl("NESTED_ARRAY", Engines.S2_FAMILY)
                .entityStateImpl("TREE_MAP",     Engines.S2_FAMILY)
                .entityStateImpl("S2_FLAT",      Engines.S2_FAMILY);

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
    public String detectEngine(Path replayPath) throws IOException {
        try (Source src = new MappedFileSource(replayPath.toString())) {
            return src.determineEngineType().getId().name();
        }
    }

    @Override
    public void parse(String replayPath, Map<String, String> config) throws Exception {
        String workload = config.getOrDefault(Workloads.KEY_WORKLOAD, Workloads.PARSE);
        String variant  = config.getOrDefault(Workloads.KEY_VARIANT,  Workloads.DEFAULT);
        String impl     = config.getOrDefault(Workloads.KEY_IMPL,     Workloads.DEFAULT);

        Object processor = createProcessor(workload, variant);

        try (var src = new MappedFileSource(replayPath)) {
            var runner = new SimpleRunner(src);
            if (Workloads.PARSE.equals(workload) && !Workloads.DEFAULT.equals(impl)) {
                applyParseImpl(runner, impl);
            }
            runner.runWith(processor);
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

    /**
     * Map the bench's globally-unique impl names to clarity 5.x's
     * {@code S1EntityStateType} / {@code S2EntityStateType} enum values
     * via the runner's {@code with*EntityState} public API.
     */
    private void applyParseImpl(SimpleRunner runner, String impl) {
        switch (impl) {
            case "OBJECT_ARRAY" -> runner.withS1EntityState(S1EntityStateType.OBJECT_ARRAY);
            case "S1_FLAT"      -> runner.withS1EntityState(S1EntityStateType.FLAT);
            case "NESTED_ARRAY" -> runner.withS2EntityState(S2EntityStateType.NESTED_ARRAY);
            case "TREE_MAP"     -> runner.withS2EntityState(S2EntityStateType.TREE_MAP);
            case "S2_FLAT"      -> runner.withS2EntityState(S2EntityStateType.FLAT);
            default -> throw new IllegalArgumentException("Unknown impl '" + impl + "'");
        }
    }
}
