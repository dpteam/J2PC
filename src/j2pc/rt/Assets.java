package j2pc.rt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates the resources that a recompiled application loads by name.
 *
 * <p>MIDP code asks for {@code Image.createImage("/icon.png")} and expects the
 * MIDlet suite's JAR to provide it. After recompilation the resources live in a
 * plain directory, so every lookup tries, in order: the class path, the asset
 * roots registered here, and finally the name as a file system path.</p>
 *
 * <p>Roots are declared as plain relative names (usually just {@code "assets"})
 * and resolved lazily, so generated projects stay portable across machines:</p>
 *
 * <ol>
 *   <li>the working directory,</li>
 *   <li>the directory the calling classes were loaded from,</li>
 *   <li>its parents, a few levels up.</li>
 * </ol>
 */
public final class Assets {

    private static final List<Path> ROOTS = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Set<Path> SEEN = new LinkedHashSet<>();

    /** A root that was declared before its directory existed. */
    private record Pending(Class<?> anchor, String spec) {
    }

    /** How many parent directories are searched above the class location. */
    private static final int PARENT_LEVELS = 5;

    private Assets() {
    }

    /** Registers a root by relative or absolute path; missing paths are ignored. */
    public static void addRoot(String spec) {
        addRoot(callerClass(), spec);
    }

    /**
     * Registers a root resolved against the location of {@code anchor} and its
     * parents, in addition to the working directory.
     */
    public static void addRoot(Class<?> anchor, String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        List<Path> resolved = resolve(spec, anchor);
        synchronized (Assets.class) {
            if (resolved.isEmpty()) {
                Pending p = new Pending(anchor, spec);
                if (!PENDING.contains(p)) {
                    PENDING.add(p);
                }
                return;
            }
            for (Path p : resolved) {
                if (SEEN.add(p)) {
                    ROOTS.add(p);
                }
            }
        }
    }

    public static void addRoot(Path root) {
        if (root == null) {
            return;
        }
        synchronized (Assets.class) {
            Path normalized = root.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized) && SEEN.add(normalized)) {
                ROOTS.add(normalized);
            }
        }
    }

    public static List<Path> roots() {
        synchronized (Assets.class) {
            return new ArrayList<>(ROOTS);
        }
    }

    /**
     * Resolves a root specification into existing directories.
     *
     * <p>The name is tried against the working directory first, then against the
     * location of {@code anchor} (the class that asked for the root) and its
     * parents. That covers all the usual invocations: running from the project
     * root, running from the output directory, and running with a class path
     * that points somewhere else.</p>
     */
    public static List<Path> resolve(String spec, Class<?> anchor) {
        List<Path> out = new ArrayList<>();
        Path direct = Path.of(spec);
        if (direct.isAbsolute()) {
            if (Files.isDirectory(direct)) {
                out.add(direct.normalize());
            }
            return out;
        }
        Path cwd = direct.toAbsolutePath().normalize();
        if (Files.isDirectory(cwd)) {
            out.add(cwd);
        }
        Path base = codeLocation(anchor);
        if (base != null) {
            Path cur = base;
            for (int i = 0; i <= PARENT_LEVELS && cur != null; i++) {
                Path candidate = cur.resolve(spec).normalize();
                if (Files.isDirectory(candidate) && !out.contains(candidate)) {
                    out.add(candidate);
                }
                cur = cur.getParent();
            }
        }
        if (out.isEmpty()) {
            // Convenience for the common "sources in out/, classes in gen/" layout:
            // look one level below each ancestor for a directory of that name.
            scanSiblings(spec, base, out);
        }
        return out;
    }

    private static void scanSiblings(String spec, Path base, List<Path> out) {
        if (base == null || spec.contains("/") || spec.contains("\\")) {
            return;
        }
        Path cur = base;
        for (int i = 0; i <= PARENT_LEVELS && cur != null; i++) {
            try (java.util.stream.Stream<Path> children = Files.list(cur)) {
                children.filter(Files::isDirectory)
                        .map(d -> d.resolve(spec))
                        .filter(Files::isDirectory)
                        .sorted(java.util.Comparator.comparingInt(p -> p.getNameCount()))
                        .forEach(p -> {
                            Path normalized = p.normalize();
                            if (!out.contains(normalized)) {
                                out.add(normalized);
                            }
                        });
            } catch (IOException | RuntimeException ignored) {
                // unreadable directories are simply skipped
            }
            if (!out.isEmpty()) {
                return;
            }
            cur = cur.getParent();
        }
    }

    /** Directory (or archive) the given class was loaded from, or null when unknown. */
    private static Path codeLocation(Class<?> anchor) {
        try {
            Class<?> c = anchor == null ? Assets.class : anchor;
            java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            Path p = Path.of(cs.getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** The class that called {@link #addRoot(String)}; used as the path anchor. */
    private static Class<?> callerClass() {
        final Class<?>[] found = new Class<?>[1];
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> {
            frames.forEach(f -> {
                if (found[0] == null && f.getDeclaringClass() != Assets.class) {
                    found[0] = f.getDeclaringClass();
                }
            });
            return null;
        });
        return found[0];
    }

    /** Opens {@code name} from the class path or from one of the asset roots. */
    public static InputStream open(String name) throws IOException {
        return open(name, null);
    }

    /**
     * Opens a resource the way {@code Class.getResourceAsStream} does: a name that
     * does not start with {@code /} is resolved relative to {@code ownerPackage}.
     */
    public static InputStream open(String name, String ownerPackage) throws IOException {
        if (name == null) {
            throw new IOException("resource name is null");
        }
        retryPending();

        boolean absolute = name.startsWith("/");
        String cleaned = name.replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(cleaned);
        if (!absolute && ownerPackage != null && !ownerPackage.isEmpty()) {
            String pkg = ownerPackage.replace('.', '/');
            while (pkg.startsWith("/")) {
                pkg = pkg.substring(1);
            }
            candidates.add(0, pkg + "/" + cleaned);
        }

        for (String candidate : candidates) {
            InputStream in = tryOpen(candidate);
            if (in != null) {
                return in;
            }
        }
        throw new IOException("resource not found: " + name);
    }

    /** Re-resolves roots that were declared before their directory existed. */
    private static void retryPending() {
        List<Pending> pending;
        synchronized (Assets.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            pending = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Pending p : pending) {
            addRoot(p.anchor(), p.spec());
        }
    }

    private static InputStream tryOpen(String cleaned) throws IOException {
        InputStream in = Assets.class.getResourceAsStream("/" + cleaned);
        if (in != null) {
            return in;
        }
        ClassLoader loader = Assets.class.getClassLoader();
        if (loader != null) {
            in = loader.getResourceAsStream(cleaned);
            if (in != null) {
                return in;
            }
        }
        List<Path> roots;
        synchronized (Assets.class) {
            roots = new ArrayList<>(ROOTS);
        }
        for (Path root : roots) {
            Path candidate = root.resolve(cleaned);
            if (Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        }
        Path direct = Path.of(cleaned);
        if (Files.isRegularFile(direct)) {
            return Files.newInputStream(direct);
        }
        return null;
    }
}
