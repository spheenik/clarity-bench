package spheenik.claritybench;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Orchestrator. Each {@code :vX.Y.Z:run} invocation enters here. */
public final class BenchMain {

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);

        // Strict-name validation runs early — before adapter load, manifest read,
        // or anything else that could fail first. Catches typos like
        // `--workload propchang` immediately, regardless of project state.
        validateFilterNames(parsed);

        BenchAdapter adapter = AdapterHolder.get();
        Capabilities caps = adapter.capabilities();
        validateCapabilities(adapter, caps);

        Path replaysRoot = parsed.replaysRoot.toAbsolutePath().normalize();
        Path manifestFile = replaysRoot.resolve("MANIFEST.sha256");
        if (!Files.isRegularFile(manifestFile)) {
            die("Manifest not found at " + manifestFile);
        }
        Manifest manifest = Manifest.load(manifestFile);

        if (parsed.listReplays) {
            runListReplays(adapter, manifest, replaysRoot);
            return;
        }

        LoggingGuard.enforceWarnOrAbove();

        printCapabilitySummary(adapter, caps);

        // Resolve which manifest entries to actually bench.
        List<Manifest.Entry> requested = resolveRequestedReplays(manifest, parsed.replayFilter);

        // Verify sha256 + size before any bench cell runs.
        verifyAll(replaysRoot, requested);

        // Verify detector vs manifest tag (skips entries the adapter can't classify).
        verifyEngineTags(adapter, replaysRoot, requested);

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

        List<RunResult> allResults = new ArrayList<>();
        Counts counts = new Counts();

        // ParseBench — iterate impls, restrict replays to each impl's compatible engines.
        if (workloadActive(parsed, Workloads.PARSE)) {
            runParseMatrix(adapter, caps, parsed, replaysRoot, compatible, allResults, counts);
        }

        // DispatchBench — iterate variants, restrict replays to each variant's compatible engines.
        if (workloadActive(parsed, Workloads.DISPATCH)) {
            runVariantMatrix(
                    DispatchBench.class, adapter, caps.dispatchVariants(), parsed.variantFilter,
                    parsed, replaysRoot, compatible, allResults, counts);
        }

        // PropertyChangeBench — same shape.
        if (workloadActive(parsed, Workloads.PROPCHANGE)) {
            runVariantMatrix(
                    PropertyChangeBench.class, adapter, caps.propertyChangeVariants(), parsed.variantFilter,
                    parsed, replaysRoot, compatible, allResults, counts);
        }

        if (counts.attempts == 0) {
            die("After filtering, no benchmarks remain for adapter " + adapter.version()
                    + " (workloadFilter=" + parsed.workloadFilter
                    + " implFilter=" + parsed.implFilter
                    + " variantFilter=" + parsed.variantFilter + ")");
        }

        List<ResultWriter.FailureRecord> failures = extractFailures(allResults);

        // Synthesize failure records for fork-death cases: a Runner.run() invocation
        // that returned no results at all (happens when the fork System.exits via
        // the per-iteration watchdog) leaves no NaN in `allResults` to surface.
        for (Counts.DeadFork df : counts.deadForks) {
            failures.add(new ResultWriter.FailureRecord(
                    "(unknown — fork died before reporting)",
                    df.axisName + "=" + df.axisValue,
                    "ForkExit",
                    "Fork exited without producing results — likely watchdog fired ("
                            + Watchdog.TIMEOUT_SECONDS + "s) on a hung iteration"));
        }

        ResultWriter.writeText(System.out, adapter.version(), allResults, failures);

        if (parsed.record) {
            persist(adapter, caps, replaysRoot, requested, allResults, failures);
        }

        if (!failures.isEmpty()) {
            System.err.println("Bench completed with " + failures.size() + " failed cell(s).");
            System.exit(2);
        }
    }

    /** Aggregator passed through the matrix expansion. */
    private static final class Counts {
        int attempts;
        record DeadFork(String benchClass, String axisName, String axisValue) {}
        final List<DeadFork> deadForks = new ArrayList<>();
    }

    // ---------------------------------------------------------------------
    // Matrix expansion
    // ---------------------------------------------------------------------

    /**
     * Runs ParseBench: one JMH invocation per declared impl (filtered by
     * {@code --impl} if present), each restricted to that impl's applicable
     * engines intersected with {@code compatible}. If the adapter declares
     * no impls, runs once with {@code impl=DEFAULT} against all compatible.
     */
    private static void runParseMatrix(
            BenchAdapter adapter,
            Capabilities caps,
            Args args,
            Path replaysRoot,
            List<Manifest.Entry> compatible,
            List<RunResult> sink,
            Counts counts) throws Exception {

        if (caps.entityStateImpls().isEmpty()) {
            String[] replays = compatible.stream().map(Manifest.Entry::relativePath).toArray(String[]::new);
            runOne(ParseBench.class, replays, "impl", "DEFAULT", replaysRoot, sink, counts);
            return;
        }

        for (var entry : caps.entityStateImpls().entrySet()) {
            String impl = entry.getKey();
            Set<String> applicableEngines = entry.getValue();
            if (!args.implFilter.isEmpty() && !args.implFilter.contains(impl)) continue;

            String[] replays = compatible.stream()
                    .filter(e -> applicableEngines.contains(e.engine()))
                    .map(Manifest.Entry::relativePath)
                    .toArray(String[]::new);
            if (replays.length == 0) {
                System.out.println("ParseBench impl=" + impl + ": no compatible replays after filtering — skipping");
                continue;
            }
            runOne(ParseBench.class, replays, "impl", impl, replaysRoot, sink, counts);
        }
    }

    /**
     * Runs a variant-axis benchmark class (Dispatch or PropertyChange). One
     * JMH invocation per declared variant (filtered by {@code --variant}),
     * each restricted to the variant's applicable engines.
     */
    private static void runVariantMatrix(
            Class<?> benchClass,
            BenchAdapter adapter,
            Map<String, Set<String>> variantMap,
            Set<String> variantFilter,
            Args args,
            Path replaysRoot,
            List<Manifest.Entry> compatible,
            List<RunResult> sink,
            Counts counts) throws Exception {

        if (variantMap.isEmpty()) {
            // Adapter does not expose this workload.
            return;
        }

        for (var entry : variantMap.entrySet()) {
            String variant = entry.getKey();
            Set<String> applicableEngines = entry.getValue();
            if (!variantFilter.isEmpty() && !variantFilter.contains(variant)) continue;

            String[] replays = compatible.stream()
                    .filter(e -> applicableEngines.contains(e.engine()))
                    .map(Manifest.Entry::relativePath)
                    .toArray(String[]::new);
            if (replays.length == 0) {
                System.out.println(benchClass.getSimpleName() + " variant=" + variant
                        + ": no compatible replays after filtering — skipping");
                continue;
            }
            runOne(benchClass, replays, "variant", variant, replaysRoot, sink, counts);
        }
    }

    /**
     * One JMH invocation: a single benchmark class, a fixed value for one
     * non-replay axis, a set of replays. Forks per the benchmark class's
     * {@code @Fork} annotation.
     */
    private static void runOne(
            Class<?> benchClass,
            String[] replays,
            String axisName,
            String axisValue,
            Path replaysRoot,
            List<RunResult> sink,
            Counts counts) throws Exception {

        OptionsBuilder ob = new OptionsBuilder();
        ob.include(benchClass.getName());
        ob.shouldFailOnError(false);
        ob.param("replay", replays);
        ob.param(axisName, axisValue);
        ob.jvmArgsAppend("-D" + ParseBench.REPLAY_ROOT_PROP + "=" + replaysRoot);
        ob.addProfiler(org.openjdk.jmh.profile.GCProfiler.class);
        Options opts = ob.build();

        counts.attempts++;
        try {
            Collection<RunResult> r = new Runner(opts).run();
            if (r.isEmpty()) {
                // Fork died before reporting (likely Watchdog hard-exit).
                counts.deadForks.add(new Counts.DeadFork(benchClass.getSimpleName(), axisName, axisValue));
            } else {
                sink.addAll(r);
            }
        } catch (NoBenchmarksException e) {
            die("JMH found no benchmarks for " + benchClass.getSimpleName()
                    + ". Is harness build output on the classpath?");
        } catch (RunnerException e) {
            die("JMH refused to run " + benchClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean workloadActive(Args args, String workload) {
        return args.workloadFilter.isEmpty() || args.workloadFilter.contains(workload);
    }

    // ---------------------------------------------------------------------
    // List-replays mode
    // ---------------------------------------------------------------------

    private static void runListReplays(BenchAdapter adapter, Manifest manifest, Path replaysRoot) {
        System.out.println("# detector: " + adapter.version());
        System.out.printf(Locale.ROOT, "%-60s %-12s %-12s%n", "replay", "detected", "manifest");
        for (Manifest.Entry e : manifest.entries().values()) {
            Path file = replaysRoot.resolve(e.relativePath());
            String detected;
            if (!Files.isRegularFile(file)) {
                detected = "<missing>";
            } else {
                try {
                    String d = adapter.detectEngine(file);
                    detected = d == null ? "<unknown>" : d;
                } catch (IOException ioe) {
                    detected = "<error: " + ioe.getMessage() + ">";
                }
            }
            String marker = detected.equals(e.engine()) ? "" : "  *";
            System.out.printf(Locale.ROOT, "%-60s %-12s %-12s%s%n",
                    e.relativePath(), detected, e.engine(), marker);
        }
    }

    // ---------------------------------------------------------------------
    // Filter validation, capability validation, summary
    // ---------------------------------------------------------------------

    private static void validateFilterNames(Args args) {
        for (String w : args.workloadFilter) {
            if (!Workloads.ALL.contains(w)) {
                die("Unknown --workload value '" + w + "'. Valid: " + sorted(Workloads.ALL));
            }
        }
        for (String impl : args.implFilter) {
            if (!Workloads.KNOWN_IMPLS.contains(impl)) {
                die("Unknown --impl value '" + impl + "'. Valid: " + sorted(Workloads.KNOWN_IMPLS));
            }
        }
        for (String v : args.variantFilter) {
            if (!Workloads.KNOWN_VARIANTS.contains(v)) {
                die("Unknown --variant value '" + v + "'. Valid: " + sorted(Workloads.KNOWN_VARIANTS));
            }
        }
    }

    private static Set<String> sorted(Set<String> in) {
        return new TreeSet<>(in);
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
        System.out.println("adapter:                  " + adapter.version());
        System.out.println("supportedEngines:         " + caps.supportedEngines());
        printApplicabilityMap("entityStateImpls:         ", caps.entityStateImpls(),
                "{} (will run once with version default)");
        printApplicabilityMap("dispatchVariants:         ", caps.dispatchVariants(),
                "{} (DispatchBench will be skipped)");
        printApplicabilityMap("propertyChangeVariants:   ", caps.propertyChangeVariants(),
                "{} (PropertyChangeBench will be skipped)");
        System.out.println();
    }

    private static void printApplicabilityMap(String label, Map<String, Set<String>> map, String emptyHint) {
        if (map.isEmpty()) {
            System.out.println(label + emptyHint);
            return;
        }
        System.out.println(label);
        for (var e : map.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }
    }

    // ---------------------------------------------------------------------
    // Manifest engine-tag verification (detector vs manifest)
    // ---------------------------------------------------------------------

    private static void verifyEngineTags(BenchAdapter adapter, Path replaysRoot, List<Manifest.Entry> entries) {
        List<String> mismatches = new ArrayList<>();
        for (Manifest.Entry e : entries) {
            Path file = replaysRoot.resolve(e.relativePath());
            String detected;
            try {
                detected = adapter.detectEngine(file);
            } catch (IOException ioe) {
                mismatches.add("  " + e.relativePath() + ": detector failed (" + ioe.getMessage() + ")");
                continue;
            }
            if (detected == null) {
                // Adapter doesn't recognize this engine (e.g. older release on a newer game).
                // Trust the manifest tag in that case — log only.
                System.out.println("Note: " + adapter.version() + " adapter cannot classify "
                        + e.relativePath() + "; trusting manifest tag '" + e.engine() + "'");
                continue;
            }
            if (!detected.equals(e.engine())) {
                mismatches.add("  " + e.relativePath() + ": manifest='" + e.engine()
                        + "' detected='" + detected + "'");
            }
        }
        if (!mismatches.isEmpty()) {
            System.err.println("ERROR: manifest engine tag(s) disagree with detector for "
                    + mismatches.size() + " replay(s):");
            for (String m : mismatches) System.err.println(m);
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------------
    // Plumbing carried over from prior version
    // ---------------------------------------------------------------------

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
                String impl = r.getParams().getParam("impl");
                String variant = r.getParams().getParam("variant");
                String axisLabel = impl != null ? "impl=" + impl
                        : variant != null ? "variant=" + variant : "(no axis)";
                out.add(new ResultWriter.FailureRecord(
                        r.getParams().getParam("replay"),
                        axisLabel,
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
        String safeVersion = adapter.version().replaceAll("[^A-Za-z0-9._-]", "_");
        Path textFile = runDir.resolve(safeVersion + ".txt");
        if (Files.exists(textFile)) {
            die("Refusing to overwrite existing recorded result: " + textFile);
        }

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
            String provenance = adapter.implSelectionProvenance();
            if (provenance != null && !provenance.isEmpty()) {
                p.println("impl selection:  " + provenance);
            }
        }

        try (PrintStream p = new PrintStream(Files.newOutputStream(textFile))) {
            ResultWriter.writeText(p, adapter.version(), results, failures);
        }
        ResultWriter.writeJson(runDir.resolve(safeVersion + ".json"), adapter.version(), results, failures);

        System.out.println();
        System.out.println("Recorded to: " + runDir);
    }

    private static Path locateResultsRoot() {
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
    public record Args(
            Path replaysRoot,
            boolean record,
            boolean listReplays,
            List<String> replayFilter,
            Set<String> implFilter,
            Set<String> workloadFilter,
            Set<String> variantFilter
    ) {
        public static Args parse(String[] argv) {
            Path root = null;
            boolean rec = false;
            boolean list = false;
            List<String> replayFilter = new ArrayList<>();
            Set<String> implFilter = new LinkedHashSet<>();
            Set<String> workloadFilter = new LinkedHashSet<>();
            Set<String> variantFilter = new LinkedHashSet<>();
            for (int i = 0; i < argv.length; i++) {
                String a = argv[i];
                switch (a) {
                    case "--replays-root" -> root = Path.of(req(argv, ++i, "--replays-root"));
                    case "--record" -> rec = true;
                    case "--list-replays" -> list = true;
                    case "--replay" -> replayFilter.add(req(argv, ++i, "--replay"));
                    case "--impl" -> implFilter.add(req(argv, ++i, "--impl"));
                    case "--workload" -> workloadFilter.add(req(argv, ++i, "--workload"));
                    case "--variant" -> variantFilter.add(req(argv, ++i, "--variant"));
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
            return new Args(root, rec, list, replayFilter, implFilter, workloadFilter, variantFilter);
        }

        private static String req(String[] argv, int i, String flag) {
            if (i >= argv.length) {
                System.err.println("Flag " + flag + " requires a value");
                System.exit(1);
            }
            return argv[i];
        }

        private static void usage() {
            System.err.println("usage: BenchMain --replays-root <path>");
            System.err.println("                 [--record]");
            System.err.println("                 [--list-replays]");
            System.err.println("                 [--replay <relative-path>]...");
            System.err.println("                 [--workload parse|dispatch|propchange]...");
            System.err.println("                 [--impl <impl-name>]...");
            System.err.println("                 [--variant <variant-name>]...");
        }
    }

    private BenchMain() {}
}
