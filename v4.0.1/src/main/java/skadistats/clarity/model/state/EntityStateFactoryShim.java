package skadistats.clarity.model.state;

/**
 * Thread-local knob read by the shadowed {@link EntityStateFactory} in this
 * subproject. The adapter sets the impl name before invoking the runner;
 * the shadowed factory consults this value when clarity asks for an
 * {@link EntityState}.
 *
 * <p>Lives in {@code skadistats.clarity.model.state} so the shadowed
 * {@code EntityStateFactory} can call into it without importing across
 * packages — the tighter the coupling, the less surface to break under
 * classpath surprises.
 */
public final class EntityStateFactoryShim {

    private static final ThreadLocal<String> REQUESTED = new ThreadLocal<>();

    private EntityStateFactoryShim() {}

    /** Set the impl the next {@code forS1}/{@code forS2} call should construct. */
    public static void setRequestedImpl(String impl) {
        REQUESTED.set(impl);
    }

    /** Clear the knob; subsequent calls fall back to the version's default behavior. */
    public static void clearRequestedImpl() {
        REQUESTED.remove();
    }

    /** Returns the current request, or {@code null} if none is set. */
    public static String getRequestedImpl() {
        return REQUESTED.get();
    }
}
