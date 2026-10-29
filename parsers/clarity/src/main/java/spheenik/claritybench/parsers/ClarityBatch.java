package spheenik.claritybench.parsers;

import com.sun.management.OperatingSystemMXBean;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import spheenik.claritybench.LoggingGuard;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@UsesEntities
public final class ClarityBatch {

    // args: <threads> <demosPerThread> <replay>...
    public static void main(String[] args) throws Exception {
        LoggingGuard.enforceWarnOrAbove();
        var threads = Integer.parseInt(args[0]);
        var perThread = Integer.parseInt(args[1]);
        var replays = Arrays.copyOfRange(args, 2, args.length);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        var warm = new ArrayList<Future<?>>();
        for (var t = 0; t < threads; t++) {
            var off = t;
            warm.add(pool.submit(() -> {
                for (var i = 0; i < replays.length; i++) {
                    parse(replays[(i + off) % replays.length]);
                }
                return null;
            }));
        }
        for (var f : warm) {
            f.get();
        }

        var total = threads * perThread;
        var next = new AtomicInteger();
        var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var gcs = ManagementFactory.getGarbageCollectorMXBeans();
        long gc0 = 0, gcN0 = 0;
        for (var g : gcs) {
            gc0 += g.getCollectionTime();
            gcN0 += g.getCollectionCount();
        }
        var p0 = os.getProcessCpuTime();
        var t0 = System.nanoTime();
        var work = new ArrayList<Future<?>>();
        for (var t = 0; t < threads; t++) {
            work.add(pool.submit(() -> {
                int i;
                while ((i = next.getAndIncrement()) < total) {
                    parse(replays[i % replays.length]);
                }
                return null;
            }));
        }
        for (var f : work) {
            f.get();
        }
        var t1 = System.nanoTime();
        var p1 = os.getProcessCpuTime();
        long gc1 = 0, gcN1 = 0;
        for (var g : gcs) {
            gc1 += g.getCollectionTime();
            gcN1 += g.getCollectionCount();
        }
        pool.shutdown();
        var wall = (t1 - t0) / 1e9;
        var proc = (p1 - p0) / 1e9;
        System.out.printf(Locale.ROOT, "batch: threads %d demos %d wall %.2f s proc %.2f s demos/s %.2f cpu/demo %.3f s gc %d ms in %d%n",
                threads, total, wall, proc, total / wall, proc / total, gc1 - gc0, gcN1 - gcN0);
    }

    private static void parse(String replay) throws Exception {
        try (var source = new MappedFileSource(replay)) {
            new SimpleRunner(source).runWith(new ClarityBatch());
        }
    }

}
