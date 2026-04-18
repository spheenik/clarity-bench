package spheenik.claritybench;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the SLF4J root logger is configured so that nothing below WARN
 * fires during a bench run. Refuses to proceed otherwise.
 */
public final class LoggingGuard {

    public static void enforceWarnOrAbove() {
        Logger root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.isDebugEnabled() || root.isInfoEnabled()) {
            throw new IllegalStateException(
                    "Logging guard: root logger has DEBUG or INFO enabled. "
                    + "A bench run requires WARN or above; debug logging would distort measurement. "
                    + "Check logback.xml or any -Dlogback.* / -Dorg.slf4j.* JVM args.");
        }
    }

    private LoggingGuard() {}
}
