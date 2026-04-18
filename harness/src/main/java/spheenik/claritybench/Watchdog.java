package spheenik.claritybench;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Per-iteration kill switch installed inside the JMH-forked JVM. Wraps the
 * benchmark body in a worker thread; if it doesn't return within
 * {@link #TIMEOUT_SECONDS}, the fork hard-exits with code 124.
 *
 * <p>Why hard exit and not just throw: clarity's parse loop ignores
 * {@code Thread.interrupt()}. JMH's own {@code OptionsBuilder.timeout()} is
 * therefore a placebo for this codebase — it sends an interrupt and then
 * waits indefinitely for the iteration to return. The only reliable way to
 * unblock the run is to kill the fork's JVM. The per-axis-value Runner
 * pattern means losing one fork is acceptable: the host JVM detects the
 * fork's abnormal exit, marks the in-flight iteration failed, returns from
 * {@code Runner.run()}, and the next axis value gets a fresh fork.
 *
 * <p>Bounded waste: at most one fork's worth of compute per stuck cell.
 */
public final class Watchdog {

    /**
     * Per-iteration ceiling. Above our heaviest legitimate cells
     * (CS2 with listeners ≈ 5–8 s) so a runaway loop surfaces within ~30 s.
     */
    public static final long TIMEOUT_SECONDS = 30;

    private Watchdog() {}

    public static void runWithTimeout(Callable<Void> action) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "claritybench-watchdog-worker");
            t.setDaemon(true);
            return t;
        });
        Future<Void> f = pool.submit(action);
        try {
            f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            System.err.println("ERROR: bench iteration exceeded " + TIMEOUT_SECONDS
                    + "s timeout — hard-exiting fork (orphan worker thread will be reaped with the JVM)");
            System.err.flush();
            System.exit(124);
        } finally {
            pool.shutdownNow();
        }
    }
}
