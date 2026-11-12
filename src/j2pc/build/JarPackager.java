package j2pc.build;

import j2pc.Options;
import j2pc.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Packages a compiled application into a runnable JAR.
 *
 * <p>The result is self-contained: the application classes, the runtime packages
 * the generated code refers to, and the original resources all go into one
 * archive with a {@code Main-Class} manifest. It runs with a plain
 * {@code java -jar}.</p>
 */
public final class JarPackager {

    private final Options opts;

    public JarPackager(Options opts) {
        this.opts = opts;
    }

    /**
     * Writes {@code targetJar} from the compiled classes and the resources.
     *
     * @param classesDir  compiled application and runtime classes
     * @param assetsDir   original resources, may be null
     * @param mainClass   entry point
     * @return number of entries written
     */
    public int packageJar(Path classesDir, Path assetsDir, String mainClass, Path targetJar)
            throws IOException {
        Files.createDirectories(targetJar.toAbsolutePath().getParent());

        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.put(Attributes.Name.MAIN_CLASS, mainClass);
        main.putValue("Created-By", "J2PC " + opts.version);
        main.putValue("Implementation-Title", "J2PC recompiled application");
        // The recompiled code is large; a small stack hint avoids surprises on
        // applications with deeply nested expressions.
        main.putValue("X-J2PC-Generated", "true");

        int count = 0;
        try (OutputStream out = Files.newOutputStream(targetJar);
             JarOutputStream jar = new JarOutputStream(out, manifest)) {

            List<Path> files = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(classesDir)) {
                walk.filter(Files::isRegularFile).forEach(files::add);
            }
            for (Path file : files) {
                String name = classesDir.relativize(file).toString().replace('\\', '/');
                writeEntry(jar, name, Files.readAllBytes(file));
                count++;
            }

            if (assetsDir != null && Files.isDirectory(assetsDir)) {
                List<Path> assets = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(assetsDir)) {
                    walk.filter(Files::isRegularFile).forEach(assets::add);
                }
                for (Path file : assets) {
                    String name = "assets/" + assetsDir.relativize(file).toString().replace('\\', '/');
                    writeEntry(jar, name, Files.readAllBytes(file));
                    count++;
                }
            }
        }
        Log.info("packaged %s (%d entries)", targetJar, count);
        return count;
    }

    private static void writeEntry(JarOutputStream jar, String name, byte[] data)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        // A fixed timestamp keeps the archive reproducible.
        entry.setTime(0L);
        jar.putNextEntry(entry);
        jar.write(data);
        jar.closeEntry();
    }

    /** Manifest text for inspection, written next to the archive. */
    public void writeManifestText(Path targetJar, String mainClass, String name, String vendor,
                                  String version) throws IOException {
        Path sidecar = targetJar.resolveSibling(targetJar.getFileName() + ".manifest.txt");
        String text = "Main-Class: " + mainClass + "\n"
                + "MIDlet-Name: " + name + "\n"
                + "MIDlet-Vendor: " + vendor + "\n"
                + "MIDlet-Version: " + version + "\n";
        Files.write(sidecar, text.getBytes(StandardCharsets.UTF_8));
    }
}
