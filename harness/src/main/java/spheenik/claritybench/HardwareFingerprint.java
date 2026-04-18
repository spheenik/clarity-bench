package spheenik.claritybench;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Captures enough about the host to make a recorded run reproducible, plus
 * a short {@link #tag()} suitable for use in a directory name.
 */
public record HardwareFingerprint(
        String cpuModel,
        int physicalCores,
        int logicalCores,
        long ramBytes,
        String osName,
        String osVersion,
        String tag
) {

    public static HardwareFingerprint capture() {
        String cpuModel = readCpuModel();
        int logical = Runtime.getRuntime().availableProcessors();
        int physical = readPhysicalCores(logical);
        long ram = readRam();
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String tag = deriveTag(cpuModel);
        return new HardwareFingerprint(cpuModel, physical, logical, ram, osName, osVersion, tag);
    }

    private static String readCpuModel() {
        Path cpuinfo = Path.of("/proc/cpuinfo");
        if (!Files.isReadable(cpuinfo)) return "unknown";
        try {
            for (String line : Files.readAllLines(cpuinfo)) {
                if (line.startsWith("model name")) {
                    int colon = line.indexOf(':');
                    if (colon >= 0) return line.substring(colon + 1).strip();
                }
            }
        } catch (IOException e) {
            return "unknown";
        }
        return "unknown";
    }

    private static int readPhysicalCores(int fallback) {
        Path cpuinfo = Path.of("/proc/cpuinfo");
        if (!Files.isReadable(cpuinfo)) return fallback;
        try {
            for (String line : Files.readAllLines(cpuinfo)) {
                if (line.startsWith("cpu cores")) {
                    int colon = line.indexOf(':');
                    if (colon >= 0) {
                        return Integer.parseInt(line.substring(colon + 1).strip());
                    }
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static long readRam() {
        Path meminfo = Path.of("/proc/meminfo");
        if (!Files.isReadable(meminfo)) return -1L;
        try {
            for (String line : Files.readAllLines(meminfo)) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) return Long.parseLong(parts[1]) * 1024L;
                }
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private static String deriveTag(String cpuModel) {
        String lower = cpuModel.toLowerCase(Locale.ROOT);
        if (lower.contains("ryzen")) {
            for (String token : lower.split("\\s+")) {
                if (token.matches("\\d{4,}x?")) {
                    return "ryzen" + token;
                }
            }
            return "ryzen";
        }
        if (lower.contains("intel")) {
            for (String token : lower.split("\\s+")) {
                if (token.matches("i[3579]-\\d+\\w*")) return "intel-" + token;
            }
            return "intel";
        }
        return "unknown-cpu";
    }

    public List<String> jvmInputArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
}
