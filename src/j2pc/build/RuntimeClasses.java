package j2pc.build;

import j2pc.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Locates the J2PC runtime classes so they can be compiled against and packaged.
 *
 * <p>The tool is normally run from its own JAR, but during development it runs
 * from a directory of class files. Both cases are handled, and the answer is
 * cached because it never changes within a run.</p>
 */
public final class RuntimeClasses {

    /**
     * Packages that make up the runtime and must be bundled into a standalone
     * application JAR. Application classes are packaged separately.
     */
    private static final String[] RUNTIME_PREFIXES = {
            "javax/microedition/",
            "com/nokia/",
            "javax/bluetooth/",
            "javax/wireless/",
            "j2pc/rt/",
    };

    private static Path codeSource;
    private static String classpath;

    private RuntimeClasses() {
    }

    /** The JAR or directory the tool was loaded from. */
    public static synchronized Path codeSource() {
        if (codeSource == null) {
            try {
                java.security.CodeSource cs = RuntimeClasses.class.getProtectionDomain()
                        .getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    codeSource = Path.of(cs.getLocation().toURI());
                }
            } catch (URISyntaxException | RuntimeException e) {
                codeSource = null;
            }
        }
        return codeSource;
    }

    /** Class path entry that gives access to the runtime classes. */
    public static synchronized String classpath() {
        if (classpath == null) {
            Path cs = codeSource();
            classpath = cs == null
                    ? System.getProperty("java.class.path", "")
                    : cs.toString();
        }
        return classpath;
    }

    /** True when the runtime was loaded from a JAR rather than a directory. */
    public static boolean isArchive() {
        Path cs = codeSource();
        return cs != null && Files.isRegularFile(cs);
    }

    /**
     * Copies the runtime classes into {@code target} (a directory).
     *
     * <p>Only the packages an application can reference are copied: the compiler,
     * the CLI and the TUI are tooling, not runtime.</p>
     */
    public static int copyInto(Path target) throws IOException {
        Path cs = codeSource();
        if (cs == null) {
            Log.warn("cannot locate the runtime classes; the output will not be standalone");
            return 0;
        }
        Files.createDirectories(target);
        if (Files.isRegularFile(cs)) {
            return copyFromArchive(cs, target);
        }
        return copyFromDirectory(cs, target);
    }

    private static int copyFromArchive(Path archive, Path target) throws IOException {
        int copied = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !wanted(name)) {
                    continue;
                }
                Path out = target.resolve(name);
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        return copied;
    }

    private static int copyFromDirectory(Path root, Path target) throws IOException {
        int copied = 0;
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            if (!wanted(name)) {
                continue;
            }
            Path out = target.resolve(name);
            Files.createDirectories(out.getParent());
            Files.copy(file, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        return copied;
    }

    private static boolean wanted(String entryName) {
        if (!entryName.endsWith(".class")) {
            return false;
        }
        for (String prefix : RUNTIME_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
