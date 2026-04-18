package spheenik.claritybench;

import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Single shared benchmark. The {@code replay} and {@code impl} params are
 * declared with sentinel defaults; {@link BenchMain} overrides them at
 * runtime via {@link org.openjdk.jmh.runner.options.OptionsBuilder#param}.
 *
 * <p>Replay paths are passed as relative manifest paths; the absolute root is
 * supplied via the {@code claritybench.replays.root} system property
 * (forwarded to forked JMH JVMs by {@link BenchMain}).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 10, batchSize = 1)
@Fork(1)
public class ParseBench {

    public static final String REPLAY_ROOT_PROP = "claritybench.replays.root";

    @Param({"DEFAULT"})
    public String replay;

    @Param({"DEFAULT"})
    public String impl;

    private BenchAdapter adapter;
    private String absoluteReplayPath;
    private Map<String, String> config;

    @Setup(Level.Trial)
    public void setUp() {
        this.adapter = AdapterHolder.get();
        String root = System.getProperty(REPLAY_ROOT_PROP);
        if (root == null) {
            throw new IllegalStateException(
                    "System property " + REPLAY_ROOT_PROP + " is not set. "
                    + "BenchMain must forward it to forked JMH JVMs.");
        }
        Path resolved = Paths.get(root).resolve(replay).toAbsolutePath().normalize();
        this.absoluteReplayPath = resolved.toString();
        this.config = Map.of("impl", impl);
    }

    @Benchmark
    public void parse() throws Exception {
        adapter.parse(absoluteReplayPath, config);
    }
}
