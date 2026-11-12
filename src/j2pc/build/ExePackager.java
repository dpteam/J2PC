package j2pc.build;

import j2pc.Options;
import j2pc.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds a native launcher with the JDK's {@code jpackage}.
 *
 * <p>{@code jpackage} wraps a JAR plus a trimmed runtime into a platform-native
 * artefact: an {@code .exe} on Windows, a {@code .app} bundle on macOS, a
 * {@code .deb}/{@code .rpm} or an app-image elsewhere. The output is not a true
 * native binary — a JVM is still inside — but it starts with a double click and
 * needs no Java installation on the target machine.</p>
 */
public final class ExePackager {

    private final Options opts;

    public ExePackager(Options opts) {
        this.opts = opts;
    }

    /** True when {@code jpackage} is available on this JDK. */
    public static boolean available() {
        Path tool = jpackage();
        return tool != null;
    }

    private static Path jpackage() {
        String home = System.getProperty("java.home");
        if (home == null) {
            return null;
        }
        Path bin = Path.of(home, "bin");
        for (String name : new String[]{"jpackage", "jpackage.exe"}) {
            Path candidate = bin.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Runs {@code jpackage} to produce a native image.
     *
     * @param appJar  the self-contained JAR produced by {@link JarPackager}
     * @param outDir  directory that receives the image
     * @param name    application name, also used for the file name
     * @return path of the produced image, or null on failure
     */
    public Path buildImage(Path appJar, Path outDir, String name) throws IOException {
        Path tool = jpackage();
        if (tool == null) {
            Log.warn("jpackage not found; install a full JDK to build a native launcher");
            return null;
        }
        Files.createDirectories(outDir);
        String safeName = j2pc.util.Names.safe(name);

        // jpackage refuses to write into a destination that already holds an
        // image of the same name, and deleting an existing image means removing
        // tens of thousands of runtime files. So the image is built into a fresh
        // staging directory and moved into place afterwards: a directory rename
        // on the same volume is immediate.
        Path staging = outDir.resolve(".staging-" + System.nanoTime());

        List<String> cmd = new ArrayList<>();
        cmd.add(tool.toString());
        cmd.add("--type");
        cmd.add(defaultType());
        cmd.add("--input");
        cmd.add(appJar.toAbsolutePath().getParent().toString());
        cmd.add("--main-jar");
        cmd.add(appJar.getFileName().toString());
        cmd.add("--main-class");
        cmd.add("J2PCLauncher");
        cmd.add("--name");
        cmd.add(safeName);
        cmd.add("--dest");
        cmd.add(staging.toAbsolutePath().toString());
        cmd.add("--app-version");
        cmd.add(normalizeVersion(opts.version));
        cmd.add("--vendor");
        cmd.add("J2PC");
        cmd.add("--description");
        cmd.add("Recompiled J2ME application (J2PC " + opts.version + ")");

        Log.info("running jpackage (%s); this builds a runtime and takes a minute ...",
                defaultType());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (java.io.InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                Log.error("jpackage timed out");
                deleteRecursively(staging);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteRecursively(staging);
            return null;
        }
        if (process.exitValue() != 0) {
            Log.error("jpackage failed (exit %d):", process.exitValue());
            for (String line : output.split("\n")) {
                Log.error("  %s", line);
            }
            deleteRecursively(staging);
            return null;
        }

        Path produced = findImage(staging, safeName);
        if (produced == null) {
            Log.error("jpackage reported success but produced no image in %s", staging);
            deleteRecursively(staging);
            return null;
        }
        Path target = outDir.resolve(safeName);
        if (Files.exists(target)) {
            deleteRecursively(target);
        }
        try {
            Files.move(produced, target);
        } catch (IOException e) {
            // A move can fail across volumes; copying is slower but always works.
            Log.debug("cannot move the image (%s); copying instead", e.getMessage());
            copyTree(produced, target);
        }
        deleteRecursively(staging);
        Log.info("native image: %s", target);
        return target;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            walk.forEach(files::add);
        }
        for (Path source : files) {
            Path target = to.resolve(from.relativize(source).toString());
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * {@code app-image} is the portable choice: a directory that runs anywhere
     * without installation. Installers are opt-in because they need platform
     * tooling (WiX on Windows, dpkg on Linux).
     */
    private static String defaultType() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "app-image";
        }
        if (os.contains("mac")) {
            return "app-image";
        }
        return "app-image";
    }

    /** jpackage insists on a numeric version without a leading zero segment. */
    private static String normalizeVersion(String version) {
        String v = version.replaceAll("[^0-9.]", "");
        if (v.isEmpty() || v.startsWith(".") || v.endsWith(".")) {
            return "1.0.0";
        }
        String[] parts = v.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts[i].isEmpty() ? "0" : parts[i]);
        }
        while (sb.toString().split("\\.").length < 3) {
            sb.append(".0");
        }
        return sb.toString();
    }

    /** Locates the produced image inside the destination directory. */
    private static Path findImage(Path outDir, String name) throws IOException {
        Path expected = outDir.resolve(name);
        if (Files.isDirectory(expected)) {
            return expected;
        }
        try (java.util.stream.Stream<Path> children = Files.list(outDir)) {
            return children.filter(Files::isDirectory).findFirst().orElse(null);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.forEach(paths::add);
        }
        // Children first: a directory cannot be removed while it is non-empty.
        for (int i = paths.size() - 1; i >= 0; i--) {
            try {
                Files.deleteIfExists(paths.get(i));
            } catch (IOException e) {
                Log.debug("cannot delete %s: %s", paths.get(i), e.getMessage());
            }
        }
    }
}
