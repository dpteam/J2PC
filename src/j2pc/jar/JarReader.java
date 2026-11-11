package j2pc.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only view over a JAR (or any zip) archive. */
public final class JarReader implements AutoCloseable {

    private final Path path;
    private final ZipFile zip;

    public JarReader(Path path) throws IOException {
        this.path = path;
        this.zip = new ZipFile(path.toFile());
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
