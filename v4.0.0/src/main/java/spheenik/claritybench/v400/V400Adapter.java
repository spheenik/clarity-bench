package spheenik.claritybench.v400;

import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import spheenik.claritybench.BenchAdapter;
import spheenik.claritybench.Capabilities;
import spheenik.claritybench.Engines;

import java.util.Map;

public class V400Adapter implements BenchAdapter {

    @UsesEntities
    public static class EmptyProcessor {}

    @Override
    public String version() {
        return "4.0.0";
    }

    @Override
    public Capabilities capabilities() {
        return Capabilities.builder()
                .supportedEngines(Engines.S1, Engines.S2)
                .build();
    }

    @Override
    public void parse(String replayPath, Map<String, String> config) throws Exception {
        try (var src = new MappedFileSource(replayPath)) {
            new SimpleRunner(src).runWith(new EmptyProcessor());
        }
    }
}
