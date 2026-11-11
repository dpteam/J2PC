package j2pc.rt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the resources that a recompiled application loads by name.
 *
 * <p>MIDP code asks for {@code Image.createImage("/icon.png")} and expects the
 * MIDlet suite's JAR to provide it. After recompilation the resources live in a
 * plain directory, so every lookup tries, in order: the class path, the asset
 * roots registered here, and finally the name as a file system path.</p>
 */
public final class Assets {

    private static final List<Path> ROOTS = new ArrayList<>();

    private Assets() {
    }

    public static synchronized void addRoot(Path root) {
        if (root != null && Files.isDirectory(root) && !ROOTS.contains(root)) {
            ROOTS.add(root);
        }
    }

    public static synchronized List<Path> roots() {
        return new ArrayList<>(ROOTS);
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
        synchronized (Assets.class) {
            for (Path root : ROOTS) {
                Path candidate = root.resolve(cleaned);
                if (Files.isRegularFile(candidate)) {
                    return Files.newInputStream(candidate);
                }
            }
        }
        Path direct = Path.of(cleaned);
        if (Files.isRegularFile(direct)) {
            return Files.newInputStream(direct);
        }
        return null;
    }
}
