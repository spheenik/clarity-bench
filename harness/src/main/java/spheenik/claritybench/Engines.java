package spheenik.claritybench;

import java.util.Set;

/**
 * The canonical engine vocabulary. Adapters MUST use these strings;
 * inventing a new one is rejected at startup.
 *
 * <p>The constants mirror clarity's own {@code skadistats.clarity.model.EngineId}
 * enum values exactly. We do not import the clarity enum (the harness is
 * clarity-free) — drift between this list and clarity's enum is caught at
 * implementation time, not silently masked by an auto-import.
 */
public final class Engines {
    public static final String DOTA_S1  = "DOTA_S1";
    public static final String DOTA_S2  = "DOTA_S2";
    public static final String CSGO_S1  = "CSGO_S1";
    public static final String CSGO_S2  = "CSGO_S2";
    public static final String DEADLOCK = "DEADLOCK";

    public static final Set<String> ALL = Set.of(DOTA_S1, DOTA_S2, CSGO_S1, CSGO_S2, DEADLOCK);

    /** Source 1 family: games whose demos use the Source 1 engine. */
    public static final Set<String> S1_FAMILY = Set.of(DOTA_S1, CSGO_S1);

    /** Source 2 family: games whose demos use the Source 2 engine. */
    public static final Set<String> S2_FAMILY = Set.of(DOTA_S2, CSGO_S2, DEADLOCK);

    private Engines() {}
}
