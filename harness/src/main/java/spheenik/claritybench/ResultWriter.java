package spheenik.claritybench;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.format.OutputFormat;
import org.openjdk.jmh.runner.format.OutputFormatFactory;
import org.openjdk.jmh.util.Statistics;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders {@link RunResult}s into the human-readable text format used in
 * recorded baselines, plus a tiny JSON sidecar for tooling.
 */
public final class ResultWriter {

    public record FailureRecord(String replay, String impl, String exceptionClass, String message) {}

    public static void writeText(
            PrintStream out,
            String adapterVersion,
            Collection<RunResult> results,
            List<FailureRecord> failures
    ) {
        out.println("ParseBench — clarity " + adapterVersion);
        out.println("generated: " + Instant.now());
        out.println();

        Map<String, Map<String, RunResult>> byReplayThenImpl = new LinkedHashMap<>();
        for (RunResult r : results) {
            String replay = r.getParams().getParam("replay");
            String impl = r.getParams().getParam("impl");
            byReplayThenImpl.computeIfAbsent(replay, k -> new LinkedHashMap<>()).put(impl, r);
        }

        for (var entry : byReplayThenImpl.entrySet()) {
            out.println("=== " + entry.getKey() + " ===");
            out.printf(Locale.ROOT, "  %-16s %12s %12s %12s %12s   %s%n",
                    "impl", "median", "min", "max", "p95", "score ± err (ms)");
            for (var cell : entry.getValue().entrySet()) {
                Statistics s = cell.getValue().getPrimaryResult().getStatistics();
                out.printf(Locale.ROOT,
                        "  %-16s %10.1f ms %10.1f ms %10.1f ms %10.1f ms   %8.1f ± %4.1f%n",
                        cell.getKey(),
                        s.getPercentile(50),
                        s.getMin(),
                        s.getMax(),
                        s.getPercentile(95),
                        s.getMean(),
                        s.getMeanErrorAt(0.999));
            }
            out.println();
            out.printf(Locale.ROOT, "  %-16s %14s %14s %10s %12s%n",
                    "impl", "alloc/op", "alloc rate", "GCs", "GC time");
            for (var cell : entry.getValue().entrySet()) {
                var sec = cell.getValue().getSecondaryResults();
                out.printf(Locale.ROOT, "  %-16s %14s %14s %10s %12s%n",
                        cell.getKey(),
                        formatBytes(secondary(sec, "gc.alloc.rate.norm")),
                        formatBytesPerSec(secondary(sec, "gc.alloc.rate")),
                        formatCount(secondary(sec, "gc.count")),
                        formatMillis(secondary(sec, "gc.time")));
            }
            out.println();
        }

        if (!failures.isEmpty()) {
            out.println("=== FAILURES ===");
            for (FailureRecord f : failures) {
                out.printf(Locale.ROOT, "  replay=%s impl=%s%n", f.replay(), f.impl());
                out.printf(Locale.ROOT, "    %s: %s%n", f.exceptionClass(), f.message());
            }
            out.println();
        }
    }

    public static void writeJson(Path file,
                                 String adapterVersion,
                                 Collection<RunResult> results,
                                 List<FailureRecord> failures) throws Exception {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"adapter\": \"").append(adapterVersion).append("\",\n");
        sb.append("  \"generated\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"cells\": [\n");
        boolean first = true;
        for (RunResult r : results) {
            if (!first) sb.append(",\n");
            first = false;
            Statistics s = r.getPrimaryResult().getStatistics();
            sb.append("    {");
            sb.append("\"replay\": ").append(jstr(r.getParams().getParam("replay"))).append(", ");
            sb.append("\"impl\": ").append(jstr(r.getParams().getParam("impl"))).append(", ");
            sb.append("\"unit\": \"ms\", ");
            sb.append("\"score\": ").append(fmt(s.getMean())).append(", ");
            sb.append("\"error\": ").append(fmt(s.getMeanErrorAt(0.999))).append(", ");
            sb.append("\"min\": ").append(fmt(s.getMin())).append(", ");
            sb.append("\"max\": ").append(fmt(s.getMax())).append(", ");
            sb.append("\"median\": ").append(fmt(s.getPercentile(50))).append(", ");
            sb.append("\"p95\": ").append(fmt(s.getPercentile(95))).append(", ");
            sb.append("\"n\": ").append(s.getN());
            sb.append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  \"failures\": [\n");
        first = true;
        for (FailureRecord f : failures) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {");
            sb.append("\"replay\": ").append(jstr(f.replay())).append(", ");
            sb.append("\"impl\": ").append(jstr(f.impl())).append(", ");
            sb.append("\"exceptionClass\": ").append(jstr(f.exceptionClass())).append(", ");
            sb.append("\"message\": ").append(jstr(f.message()));
            sb.append("}");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        Files.writeString(file, sb.toString());
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
        return b.toString();
    }

    private static String fmt(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        return String.format(Locale.ROOT, "%.4f", d);
    }

    private static double secondary(Map<String, ? extends org.openjdk.jmh.results.Result> secondaries, String key) {
        var r = secondaries.get(key);
        return r == null ? Double.NaN : r.getScore();
    }

    private static String formatBytes(double bytes) {
        if (Double.isNaN(bytes)) return "n/a";
        if (bytes < 1024) return String.format(Locale.ROOT, "%.0f B", bytes);
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.2f KB", bytes / 1024);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatBytesPerSec(double mbPerSec) {
        if (Double.isNaN(mbPerSec)) return "n/a";
        return String.format(Locale.ROOT, "%.0f MB/s", mbPerSec);
    }

    private static String formatCount(double count) {
        if (Double.isNaN(count)) return "n/a";
        return String.format(Locale.ROOT, "%.0f", count);
    }

    private static String formatMillis(double ms) {
        if (Double.isNaN(ms)) return "n/a";
        return String.format(Locale.ROOT, "%.0f ms", ms);
    }

    /** JMH OutputFormat that writes to stderr at the same verbosity as the default. */
    public static OutputFormat jmhOutput() {
        return OutputFormatFactory.createFormatInstance(System.err, org.openjdk.jmh.runner.options.VerboseMode.NORMAL);
    }

    private ResultWriter() {}
}
