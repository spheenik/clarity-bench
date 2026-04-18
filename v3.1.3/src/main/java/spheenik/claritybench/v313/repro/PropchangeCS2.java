package spheenik.claritybench.v313.repro;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityPropertyChanged;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

/**
 * Stand-alone reproducer: clarity 3.1.3 + a wildcard
 * {@link OnEntityPropertyChanged} listener + a CS2 demo. Triggers the loop
 * we observed during bench runs. No clarity-bench harness, no JMH — just
 * "load demo, add one listener, parse."
 */
public class PropchangeCS2 {

    @UsesEntities
    public static class WildcardSingle {
        long hits;
        @OnEntityPropertyChanged
        public void on(Entity e, FieldPath fp) { hits++; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: PropchangeCS2 <replay-path>");
            System.exit(1);
        }
        String replayPath = args[0];
        WildcardSingle w = new WildcardSingle();
        long t0 = System.nanoTime();
        MappedFileSource src = new MappedFileSource(replayPath);
        try {
            new SimpleRunner(src).runWith(w);
        } finally {
            src.close();
        }
        long t1 = System.nanoTime();
        System.out.printf("hits=%d  elapsed=%.1f ms%n", w.hits, (t1 - t0) / 1_000_000.0);
    }
}
