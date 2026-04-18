package spheenik.claritybench;

import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dispatch-workload benchmark. Measures end-to-end replay parse time with a
 * dispatch-listener variant installed (selected by the {@code variant}
 * param). The active variant determines how many {@code @OnEntityUpdated}
 * listeners are wired into the adapter's processor and what filtering they
 * apply.
 *
 * <p>The {@code replay} and {@code variant} params are declared with sentinel
 * defaults; {@link BenchMain} overrides them at runtime.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 10, batchSize = 1)
@Fork(1)
public class DispatchBench {

    @Param({"DEFAULT"})
    public String replay;

    @Param({"DEFAULT"})
    public String variant;

    private BenchAdapter adapter;
    private String absoluteReplayPath;
    private Map<String, String> config;

    @Setup(Level.Trial)
    public void setUp() {
        this.adapter = AdapterHolder.get();
        String root = System.getProperty(ParseBench.REPLAY_ROOT_PROP);
        if (root == null) {
            throw new IllegalStateException(
                    "System property " + ParseBench.REPLAY_ROOT_PROP + " is not set. "
                    + "BenchMain must forward it to forked JMH JVMs.");
        }
        Path resolved = Paths.get(root).resolve(replay).toAbsolutePath().normalize();
        this.absoluteReplayPath = resolved.toString();
        this.config = Map.of(
                Workloads.KEY_WORKLOAD, Workloads.DISPATCH,
                Workloads.KEY_IMPL,     Workloads.DEFAULT,
                Workloads.KEY_VARIANT,  variant);
    }

    @Benchmark
    public void dispatch() throws Exception {
        Watchdog.runWithTimeout(() -> {
            adapter.parse(absoluteReplayPath, config);
            return null;
        });
    }
}
