package spheenik.claritybench;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Orchestrator. Each {@code :vX.Y.Z:run} invocation enters here. */
public final class BenchMain {

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);

        LoggingGuard.enforceWarnOrAbove();

        BenchAdapter adapter = AdapterHolder.get();
        Capabilities caps = adapter.capabilities();
        validateCapabilities(adapter, caps);

        printCapabilitySummary(adapter, caps);

        Path replaysRoot = parsed.replaysRoot.toAbsolutePath().normalize();
        Path manifestFile = replaysRoot.resolve("MANIFEST.sha256");
        if (!Files.isRegularFile(manifestFile)) {
            die("Manifest not found at " + manifestFile);
        }
        Manifest manifest = Manifest.load(manifestFile);

        // Resolve which manifest entries to actually bench.
        List<Manifest.Entry> requested = resolveRequestedReplays(manifest, parsed.replayFilter);

        // Verify sha256 + size before any bench cell runs.
        verifyAll(replaysRoot, requested);

        // Filter by adapter's supported engines.
        List<Manifest.Entry> compatible = new ArrayList<>();
        List<Manifest.Entry> skipped = new ArrayList<>();
        for (Manifest.Entry e : requested) {
            if (caps.supportedEngines().contains(e.engine())) compatible.add(e);
            else skipped.add(e);
        }
        if (!skipped.isEmpty()) {
            System.out.println("Skipping " + skipped.size() + " replay(s) not supported by " + adapter.version() + ":");
            for (Manifest.Entry e : skipped) {
                System.out.println("  - " + e.relativePath() + " (engine " + e.engine() + ")");
            }
            System.out.println();
        }
        if (compatible.isEmpty()) {
            die("No replays compatible with adapter " + adapter.version()
                    + " (supportedEngines=" + caps.supportedEngines() + ")");
        }

        String[] replayParams = compatible.stream().map(Manifest.Entry::relativePath).toArray(String[]::new);
        String[] implParams = caps.entityStateImpls().isEmpty()
                ? new String[]{"DEFAULT"}
                : caps.entityStateImpls().toArray(new String[0]);

        OptionsBuilder ob = new OptionsBuilder();
        ob.include(ParseBench.class.getName());
        ob.shouldFailOnError(false);
        ob.param("replay", replayParams);
        ob.param("impl", implParams);
        ob.jvmArgsAppend("-D" + ParseBench.REPLAY_ROOT_PROP + "=" + replaysRoot);
        ob.addProfiler(org.openjdk.jmh.profile.GCProfiler.class);
        Options opts = ob.build();

        Collection<RunResult> results;
        try {
            results = new Runner(opts).run();
        } catch (NoBenchmarksException e) {
            die("JMH found no benchmarks. Is harness/build output on the classpath?");
            return;
        } catch (RunnerException e) {
            // shouldFailOnError(false) means individual cell failures don't reach
            // here; this catch is for harness-level setup failures.
            die("JMH refused to run: " + e.getMessage());
            return;
        }

        // Extract cell-level failures from JMH output.
        // With shouldFailOnError(false), failed iterations leave NaN scores in
        // the RunResult but still appear in the collection. JMH already prints
        // per-iteration error details to stderr; we surface them here too.
        List<ResultWriter.FailureRecord> failures = extractFailures(results);

        ResultWriter.writeText(System.out, adapter.version(), results, failures);

        if (parsed.record) {
            persist(adapter, caps, replaysRoot, requested, results, failures);
        }

        if (!failures.isEmpty()) {
            System.err.println("Bench completed with " + failures.size() + " failed cell(s).");
            System.exit(2);
        }
    }

    private static void validateCapabilities(BenchAdapter adapter, Capabilities caps) {
        for (String engine : caps.supportedEngines()) {
            if (!Engines.ALL.contains(engine)) {
                die("Adapter " + adapter.version() + " declares unknown engine '" + engine
                        + "'. Allowed: " + Engines.ALL + ". Add it to spheenik.claritybench.Engines first.");
            }
        }
    }

    private static void printCapabilitySummary(BenchAdapter adapter, Capabilities caps) {
        System.out.println("=== bench startup ===");
        System.out.println("adapter:           " + adapter.version());
        System.out.println("supportedEngines:  " + caps.supportedEngines());
        System.out.println("entityStateImpls:  " + (caps.entityStateImpls().isEmpty()
                ? "{} (will run once with version default)"
                : caps.entityStateImpls().toString()));
        System.out.println();
    }

    private static List<Manifest.Entry> resolveRequestedReplays(Manifest manifest, List<String> filter) {
        if (filter.isEmpty()) return new ArrayList<>(manifest.entries().values());
        List<Manifest.Entry> out = new ArrayList<>();
        for (String f : filter) {
            Manifest.Entry e = manifest.entries().get(f);
            if (e == null) die("Replay '" + f + "' not in MANIFEST.sha256");
            out.add(e);
        }
        return out;
    }

    private static void verifyAll(Path replaysRoot, List<Manifest.Entry> entries) throws Exception {
        for (Manifest.Entry e : entries) {
            Path file = replaysRoot.resolve(e.relativePath());
            if (!Files.isRegularFile(file)) {
                die("Replay file missing: " + file);
            }
            String err = Manifest.verify(file, e);
            if (err != null) die(err);
        }
    }

    private static List<ResultWriter.FailureRecord> extractFailures(Collection<RunResult> results) {
        List<ResultWriter.FailureRecord> out = new ArrayList<>();
        for (RunResult r : results) {
            double score = r.getPrimaryResult().getScore();
            if (Double.isNaN(score)) {
                out.add(new ResultWriter.FailureRecord(
                        r.getParams().getParam("replay"),
                        r.getParams().getParam("impl"),
                        "BenchmarkFailure",
                        "JMH iteration produced NaN — see stderr above for the actual exception"));
            }
        }
        return out;
    }

    private static void persist(
            BenchAdapter adapter,
            Capabilities caps,
            Path replaysRoot,
            List<Manifest.Entry> manifestUsed,
            Collection<RunResult> results,
            List<ResultWriter.FailureRecord> failures
    ) throws Exception {
        HardwareFingerprint hw = HardwareFingerprint.capture();
        String date = LocalDate.now().toString();
        String jdk = "jdk" + System.getProperty("java.specification.version", "?");
        String dirName = date + "_" + hw.tag() + "_" + jdk;
        Path resultsRoot = locateResultsRoot();
        Path runDir = resultsRoot.resolve(dirName);
        Files.createDirectories(runDir);
        // The directory is shared across versions for the same date/hw/jdk;
        // refuse only if THIS version's file already exists.
        String safeVersion = adapter.version().replaceAll("[^A-Za-z0-9._-]", "_");
        Path textFile = runDir.resolve(safeVersion + ".txt");
        if (Files.exists(textFile)) {
            die("Refusing to overwrite existing recorded result: " + textFile);
        }

        // context.txt — written once on first version; appended for subsequent versions.
        Path contextFile = runDir.resolve("context.txt");
        boolean firstVersionInDir = !Files.exists(contextFile);
        try (PrintStream p = new PrintStream(Files.newOutputStream(contextFile,
                firstVersionInDir ? java.nio.file.StandardOpenOption.CREATE_NEW
                                  : java.nio.file.StandardOpenOption.APPEND))) {
            if (firstVersionInDir) {
                p.println("hardware tag:    " + hw.tag());
                p.println("CPU model:       " + hw.cpuModel());
                p.println("cores:           " + hw.logicalCores() + " (logical) / " + hw.physicalCores() + " (physical)");
                p.println("RAM bytes:       " + hw.ramBytes());
                p.println("OS:              " + hw.osName() + " " + hw.osVersion());
                p.println("JVM:             " + System.getProperty("java.vm.name") + " "
                        + System.getProperty("java.runtime.version"));
                p.println("JVM args:        " + ManagementFactory.getRuntimeMXBean().getInputArguments());
                p.println("bench repo SHA:  " + readBenchRepoSha());
                p.println();
                p.println("replays used:");
                for (Manifest.Entry e : manifestUsed) {
                    p.println("  " + e.sha256() + "  " + e.sizeBytes() + "  " + e.engine() + "  " + e.relativePath());
                }
                p.println();
                p.println("=== adapters benched ===");
            }
            p.println();
            p.println("--- " + adapter.version() + " ---");
            p.println("clarity JAR:     " + describeClarityJar(adapter));
        }

        try (PrintStream p = new PrintStream(Files.newOutputStream(textFile))) {
            ResultWriter.writeText(p, adapter.version(), results, failures);
        }
        ResultWriter.writeJson(runDir.resolve(safeVersion + ".json"), adapter.version(), results, failures);

        System.out.println();
        System.out.println("Recorded to: " + runDir);
    }

    private static Path locateResultsRoot() {
        // Walk up from CWD looking for a 'results' directory; fall back to ./results.
        Path cwd = Path.of("").toAbsolutePath();
        Path p = cwd;
        for (int i = 0; i < 6 && p != null; i++) {
            Path candidate = p.resolve("results");
            if (Files.isDirectory(candidate)) return candidate;
            p = p.getParent();
        }
        Path fallback = cwd.resolve("results");
        try {
            Files.createDirectories(fallback);
        } catch (Exception e) {
            // best effort
        }
        return fallback;
    }

    private static String readBenchRepoSha() {
        try {
            Path resultsRoot = locateResultsRoot();
            Path repoRoot = resultsRoot.getParent();
            Path headFile = repoRoot.resolve(".git").resolve("HEAD");
            if (!Files.isRegularFile(headFile)) return "(no .git found)";
            String head = Files.readString(headFile).strip();
            if (head.startsWith("ref: ")) {
                Path refFile = repoRoot.resolve(".git").resolve(head.substring(5));
                if (Files.isRegularFile(refFile)) return Files.readString(refFile).strip();
            }
            return head;
        } catch (Exception e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    private static String describeClarityJar(BenchAdapter adapter) {
        try {
            CodeSource cs = adapter.getClass().getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return "(unknown location)";
            Path location = Path.of(cs.getLocation().toURI());
            // adapter is in the version subproject's jar; find clarity in classpath via classloader of an adapter-touched class.
            // Simplest: look for clarity by name on the classpath roots system property is fragile;
            // instead, walk java.class.path and find anything matching 'clarity'.
            String classpath = System.getProperty("java.class.path", "");
            for (String entry : classpath.split(java.io.File.pathSeparator)) {
                String lower = entry.toLowerCase(Locale.ROOT);
                if (lower.contains("clarity") && lower.endsWith(".jar")
                        && !lower.contains("clarity-bench") && !lower.contains("claritybench")) {
                    Path jar = Path.of(entry);
                    if (Files.isRegularFile(jar)) {
                        return jar + "  sha256=" + Manifest.sha256Hex(jar)
                                + "  mtime=" + Files.getLastModifiedTime(jar);
                    }
                }
            }
            return "(clarity JAR not found on classpath; adapter at " + location + ")";
        } catch (Exception e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    private static void die(String msg) {
        System.err.println("ERROR: " + msg);
        System.exit(1);
    }

    /** Minimal CLI parser; sufficient for our handful of flags. */
    public record Args(Path replaysRoot, boolean record, List<String> replayFilter) {
        public static Args parse(String[] argv) {
            Path root = null;
            boolean rec = false;
            List<String> filter = new ArrayList<>();
            for (int i = 0; i < argv.length; i++) {
                String a = argv[i];
                switch (a) {
                    case "--replays-root" -> root = Path.of(req(argv, ++i, "--replays-root"));
                    case "--record" -> rec = true;
                    case "--replay" -> filter.add(req(argv, ++i, "--replay"));
                    case "-h", "--help" -> {
                        usage();
                        System.exit(0);
                    }
                    default -> {
                        System.err.println("Unknown argument: " + a);
                        usage();
                        System.exit(1);
                    }
                }
            }
            if (root == null) {
                System.err.println("Missing required --replays-root");
                usage();
                System.exit(1);
            }
            return new Args(root, rec, filter);
        }

        private static String req(String[] argv, int i, String flag) {
            if (i >= argv.length) {
                System.err.println("Flag " + flag + " requires a value");
                System.exit(1);
            }
            return argv[i];
        }

        private static void usage() {
            System.err.println("usage: BenchMain --replays-root <path> [--record] [--replay <relative-path>]...");
        }
    }

    private BenchMain() {}
}
