package spheenik.claritybench.v500;

import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.state.s2.S2EntityStateType;
import spheenik.claritybench.BenchAdapter;
import spheenik.claritybench.Capabilities;
import spheenik.claritybench.Engines;

import java.util.Map;

public class V500Adapter implements BenchAdapter {

    @UsesEntities
    public static class EmptyProcessor {}

    @Override
    public String version() {
        return "5.0.0-SNAPSHOT";
    }

    @Override
    public Capabilities capabilities() {
        return Capabilities.builder()
                .supportedEngines(Engines.S1, Engines.S2, Engines.DEADLOCK)
                .entityStateImpls(
                        S2EntityStateType.NESTED_ARRAY.name(),
                        S2EntityStateType.TREE_MAP.name(),
                        S2EntityStateType.FLAT.name())
                .build();
    }

    @Override
    public void parse(String replayPath, Map<String, String> config) throws Exception {
        String impl = config.get("impl");
        try (var src = new MappedFileSource(replayPath)) {
            var runner = new SimpleRunner(src);
            if (impl != null && !"DEFAULT".equals(impl)) {
                runner.withS2EntityState(S2EntityStateType.valueOf(impl));
            }
            runner.runWith(new EmptyProcessor());
        }
    }
}
