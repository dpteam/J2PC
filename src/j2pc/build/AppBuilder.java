package j2pc.build;

import j2pc.Options;
import j2pc.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Turns the generated sources into something that runs.
 *
 * <p>Three steps, each optional but meant to be used together:</p>
 * <ol>
 *   <li>compile the generated sources against the runtime,</li>
 *   <li>package a self-contained JAR (application + runtime + resources),</li>
 *   <li>optionally wrap that JAR into a native launcher with {@code jpackage}.</li>
 * </ol>
 */
public final class AppBuilder {

    private final Options opts;

    public AppBuilder(Options opts) {
        this.opts = opts;
    }

    /** Outcome of a build. */
    public static final class BuildResult {
        public int compiledClasses;
        public int compileErrors;
        public Path jar;
        public Path image;
        public boolean ok;
    }

    /** Identity of the application, used for the manifest and the file names. */
    public record AppInfo(String name, String vendor, String version) {
        public static final AppInfo UNKNOWN = new AppInfo("J2ME Application", "unknown", "1.0");

        public AppInfo {
            if (name == null || name.isBlank()) {
                name = "J2ME Application";
            }
            if (vendor == null || vendor.isBlank()) {
                vendor = "unknown";
            }
            if (version == null || version.isBlank()) {
                version = "1.0";
            }
        }
    }

    /**
     * Compiles and packages.
     *
     * @param sourceRoot directory holding the generated sources
     * @param assetsDir  original resources, may be null
     * @param info       application identity, used for names and the manifest
     */
    public BuildResult build(Path sourceRoot, Path assetsDir, AppInfo info) throws IOException {
        if (info == null) {
            info = AppInfo.UNKNOWN;
        }
        BuildResult result = new BuildResult();
        Path work = opts.outputDir.resolve("build");
        Path classesDir = work.resolve("classes");
        Path runtimeDir = work.resolve("runtime");
        Files.createDirectories(classesDir);

        Log.info("compiling generated sources ...");
        int errors = new SourceCompiler(opts).compile(sourceRoot, classesDir);
        result.compileErrors = errors;
        if (errors > 0) {
            Log.error("compilation finished with %d error(s); not packaging", errors);
            return result;
        }
        result.compiledClasses = countClassFiles(classesDir);
        Log.info("compiled %d class file(s)", result.compiledClasses);

        Log.info("collecting runtime classes ...");
        int runtimeCount = RuntimeClasses.copyInto(runtimeDir);
        Log.info("collected %d runtime class file(s)", runtimeCount);

        Path staging = work.resolve("staging");
        copyTree(classesDir, staging);
        copyTree(runtimeDir, staging);

        String appName = info.name();
        String vendor = info.vendor();
        String version = info.version();

        Path jar = opts.outputDir.resolve(safeName(appName) + ".jar");
        new JarPackager(opts).packageJar(staging, assetsDir, "J2PCLauncher", jar);
        new JarPackager(opts).writeManifestText(jar, "J2PCLauncher", appName, vendor, version);
        result.jar = jar;

        if (opts.buildNativeImage) {
            Log.info("building native launcher ...");
            // jpackage copies everything in --input into the image, so the JAR
            // gets a directory of its own. Pointing --input at the output
            // directory would sweep in the sources, the asset copies and the
            // compiler work area as well.
            Path input = work.resolve("image-input");
            Files.createDirectories(input);
            Path inputJar = input.resolve(jar.getFileName().toString());
            Files.copy(jar, inputJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Path imageDir = opts.outputDir.resolve("image");
            result.image = new ExePackager(opts).buildImage(inputJar, imageDir, appName);
            if (result.image == null) {
                Log.warn("native launcher not produced; the JAR is still usable");
            }
        }
        result.ok = true;
        return result;
    }

    private static int countClassFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(p -> p.toString().endsWith(".class")).count();
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(source)) {
                    continue;
                }
                Path target = to.resolve(from.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String safeName(String name) {
        return j2pc.util.Names.safe(name);
    }
}
