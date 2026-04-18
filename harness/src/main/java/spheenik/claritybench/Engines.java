package spheenik.claritybench;

import java.util.Set;

/**
 * The canonical engine vocabulary. Adapters MUST use these strings;
 * inventing a new one is rejected at startup.
 */
public final class Engines {
    public static final String S1 = "S1";
    public static final String S2 = "S2";
    public static final String DEADLOCK = "DEADLOCK";

    public static final Set<String> ALL = Set.of(S1, S2, DEADLOCK);

    private Engines() {}
}
