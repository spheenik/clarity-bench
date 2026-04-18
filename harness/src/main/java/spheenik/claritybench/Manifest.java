package spheenik.claritybench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replay manifest: each line is {@code <sha256> <size_bytes> <engine> <relative_path>}.
 * Comment lines start with {@code #}. Append-only by policy (not enforced
 * mechanically; reviewer discipline).
 */
public final class Manifest {

    public record Entry(String sha256, long sizeBytes, String engine, String relativePath) {}

    private final Map<String, Entry> byPath;

    private Manifest(Map<String, Entry> byPath) {
        this.byPath = byPath;
    }

    public static Manifest load(Path manifestFile) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(manifestFile);
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+", 4);
            if (parts.length != 4) {
                throw new IOException(manifestFile + ":" + (i + 1)
                        + ": expected '<sha256> <size> <engine> <relative_path>', got: " + raw);
            }
            String sha = parts[0].toLowerCase(Locale.ROOT);
            long size;
            try {
                size = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException(manifestFile + ":" + (i + 1) + ": bad size: " + parts[1]);
            }
            String engine = parts[2];
            if (!Engines.ALL.contains(engine)) {
                throw new IOException(manifestFile + ":" + (i + 1)
                        + ": unknown engine '" + engine + "', expected one of " + Engines.ALL);
            }
            String path = parts[3];
            entries.put(path, new Entry(sha, size, engine, path));
        }
        return new Manifest(entries);
    }

    public Map<String, Entry> entries() {
        return byPath;
    }

    public Entry require(String relativePath) {
        Entry e = byPath.get(relativePath);
        if (e == null) {
            throw new IllegalStateException(
                    "Replay '" + relativePath + "' is not in MANIFEST.sha256. Append it before benching.");
        }
        return e;
    }

    /**
     * Verify a file's actual sha256 and size against this manifest entry.
     * Returns null if OK; returns a descriptive error message otherwise.
     */
    public static String verify(Path file, Entry expected) throws IOException {
        long actualSize = Files.size(file);
        if (actualSize != expected.sizeBytes()) {
            return "size mismatch on " + file + ": expected " + expected.sizeBytes()
                    + " bytes, got " + actualSize;
        }
        String actualSha = sha256Hex(file);
        if (!actualSha.equalsIgnoreCase(expected.sha256())) {
            return "sha256 mismatch on " + file + ": expected " + expected.sha256()
                    + ", got " + actualSha;
        }
        return null;
    }

    public static String sha256Hex(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private Manifest() { this.byPath = Map.of(); }
}
