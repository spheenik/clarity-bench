package spheenik.claritybench.parsers;

import com.sun.management.OperatingSystemMXBean;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import spheenik.claritybench.LoggingGuard;

import java.lang.management.ManagementFactory;
import java.util.Locale;

@UsesEntities
public final class ClarityParse {

    public static void main(String[] args) throws Exception {
        LoggingGuard.enforceWarnOrAbove();
        var replay = args[0];
        var iterations = Integer.parseInt(args[1]);
        var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        for (var i = 1; i <= iterations; i++) {
            var c0 = os.getProcessCpuTime();
            var t0 = System.nanoTime();
            try (var source = new MappedFileSource(replay)) {
                new SimpleRunner(source).runWith(new ClarityParse());
            }
            var t1 = System.nanoTime();
            var c1 = os.getProcessCpuTime();
            System.out.printf(Locale.ROOT, "ITER %d wall=%.4f cpu=%.4f%n", i, (t1 - t0) / 1e9, (c1 - c0) / 1e9);
        }
    }

}
