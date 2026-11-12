package lk.technocom.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Secure local document storage. Optional ClamAV scanning is enabled with TECHNOCOM_CLAMSCAN_PATH. */
public final class FileService {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("pdf","doc","docx","xls","xlsx","csv","jpg","jpeg","png","gif","bmp","txt","rtf","dwg","dxf","zip");
    private FileService() {}

    public static Path root() { return Path.of(System.getProperty("user.home"), "TechnocomPMS").toAbsolutePath().normalize(); }

    /** Stores a validated file and returns a root-relative path using forward slashes. */
    public static String store(File src, String folder) throws IOException {
        if (src == null || !src.isFile() || !Files.isReadable(src.toPath())) throw new IOException("Choose a readable file.");
        long size = Files.size(src.toPath());
        if (size <= 0 || size > MAX_BYTES) throw new IOException("File must be between 1 byte and 50 MB.");
        String ext = extension(src.getName());
        if (!ALLOWED.contains(ext)) throw new IOException("File type is not allowed: ." + ext);
        Path safeRoot = root().resolve("uploads").normalize();
        Path dir = safeRoot.resolve(safeFolder(folder)).normalize();
        if (!dir.startsWith(safeRoot)) throw new IOException("Invalid upload folder.");
        Files.createDirectories(dir);
        if (!dir.toRealPath().startsWith(safeRoot.toRealPath())) throw new IOException("Upload folder resolves outside storage root.");
        String base = src.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 120) base = base.substring(base.length() - 120);
        Path temp = Files.createTempFile(dir, ".upload-", ".part");
        try {
            Files.copy(src.toPath(), temp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temp) != size) throw new IOException("File changed while being uploaded.");
            scanIfConfigured(temp); // scan the exact staged bytes that will be committed
            Path dest = dir.resolve(UUID.randomUUID() + "_" + base).normalize();
            if (!dest.startsWith(safeRoot)) throw new IOException("Invalid destination.");
            try { Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, dest); }
            return root().relativize(dest).toString().replace('\\', '/');
        } finally { Files.deleteIfExists(temp); }
    }

    /** Opens only regular files contained inside the application storage root. */
    public static boolean open(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return false;
        try {
            Path base = root().toRealPath();
            Path candidate = base.resolve(relativePath).normalize();
            if (!candidate.startsWith(base) || !Files.isRegularFile(candidate) || Files.isSymbolicLink(candidate)) return false;
            Path real = candidate.toRealPath();
            if (!real.startsWith(base)) return false;
            if (!Desktop.isDesktopSupported()) return false;
            new Thread(() -> { try { Desktop.getDesktop().open(real.toFile()); } catch (IOException | RuntimeException ignored) {} }, "technocom-file-open").start();
            return true;
        } catch (IOException | RuntimeException e) { return false; }
    }

    private static void scanIfConfigured(Path file) throws IOException {
        String scanner = System.getenv("TECHNOCOM_CLAMSCAN_PATH");
        if (scanner == null || scanner.isBlank()) {
            if (Boolean.parseBoolean(System.getenv().getOrDefault("TECHNOCOM_REQUIRE_ANTIVIRUS", "false")))
                throw new IOException("Antivirus scanning is required but TECHNOCOM_CLAMSCAN_PATH is not configured; upload blocked.");
            return; // Optional mode: absence is not represented as a successful scan.
        }
        Process p;
        try { p = new ProcessBuilder(scanner, "--no-summary", file.toAbsolutePath().toString()).redirectErrorStream(true).start(); }
        catch (IOException e) { throw new IOException("Malware scanner is configured but could not start; upload blocked.", e); }
        try {
            if (!p.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS)) { p.destroyForcibly(); throw new IOException("Malware scan timed out; upload blocked."); }
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (p.exitValue() == 1) throw new IOException("Malware detected; upload blocked.");
            if (p.exitValue() != 0) throw new IOException("Malware scan failed; upload blocked. " + output.strip());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Malware scan interrupted; upload blocked.", e); }
    }
    private static String extension(String name) { int i=name.lastIndexOf('.'); return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT); }
    private static String safeFolder(String folder) throws IOException {
        if (folder == null || folder.isBlank()) return "general";
        String normalized = folder.replace('\\','/');
        for (String part : normalized.split("/")) if (part.isBlank() || part.equals(".") || part.equals("..") || !part.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("Invalid upload folder name.");
        return normalized;
    }
}
