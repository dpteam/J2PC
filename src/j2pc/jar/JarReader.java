package j2pc.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Read-only view over a JAR (or any zip) archive.
 *
 * <p>J2ME suites were packaged by tools from the early 2000s, and a fair number of
 * them declare entry names as UTF-8 while actually storing them in a legacy code
 * page. Modern JDKs reject such archives with {@code invalid CEN header}. The
 * reader therefore retries with the common legacy charsets before giving up.</p>
 */
public final class JarReader implements AutoCloseable {

    /** Charsets tried in order when the archive claims UTF-8 but is not. */
    private static final Charset[] LEGACY_CHARSETS = {
            StandardCharsets.ISO_8859_1,
            Charset.forName("Cp437"),
            Charset.forName("windows-1251"),
            Charset.forName("GBK"),
    };

    private final Path path;
    private final ZipFile zip;

    public JarReader(Path path) throws IOException {
        this.path = path;
        this.zip = open(path);
    }

    private static ZipFile open(Path path) throws IOException {
        ZipException first = null;
        try {
            return new ZipFile(path.toFile(), StandardCharsets.UTF_8);
        } catch (ZipException e) {
            first = e;
        }
        for (Charset cs : LEGACY_CHARSETS) {
            try {
                return new ZipFile(path.toFile(), cs);
            } catch (ZipException ignored) {
                // try the next code page
            }
        }
        throw first;
    }

    public Path path() {
        return path;
    }

    public List<String> classEntries() {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
    }

    public List<String> allEntries() {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            out.add(e.nextElement().getName());
        }
        Collections.sort(out);
        return out;
    }

    public byte[] read(String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    public boolean has(String entryName) {
        return zip.getEntry(entryName) != null;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
