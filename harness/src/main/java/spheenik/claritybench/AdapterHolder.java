package spheenik.claritybench;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Process-singleton access to the (single) {@link BenchAdapter} discovered on
 * the classpath via {@link ServiceLoader}. Loaded lazily and cached, so JMH
 * warmup iterations see the same instance the measurement iterations do.
 */
public final class AdapterHolder {

    private static volatile BenchAdapter cached;

    public static BenchAdapter get() {
        BenchAdapter local = cached;
        if (local != null) return local;
        synchronized (AdapterHolder.class) {
            if (cached != null) return cached;
            ServiceLoader<BenchAdapter> loader = ServiceLoader.load(BenchAdapter.class);
            Iterator<BenchAdapter> it = loader.iterator();
            if (!it.hasNext()) {
                throw new IllegalStateException(
                        "No BenchAdapter found on classpath. Each version subproject must "
                        + "register one in META-INF/services/spheenik.claritybench.BenchAdapter.");
            }
            BenchAdapter first = it.next();
            if (it.hasNext()) {
                throw new IllegalStateException(
                        "Multiple BenchAdapters found on classpath. A bench JVM must run "
                        + "exactly one version. Found at least: " + first.version()
                        + " and " + it.next().version());
            }
            cached = first;
            return first;
        }
    }

    private AdapterHolder() {}
}
