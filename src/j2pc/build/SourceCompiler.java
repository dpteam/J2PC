package j2pc.build;

import j2pc.Options;
import j2pc.util.Log;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compiles the generated sources with the JDK's own compiler.
 *
 * <p>Uses {@link ToolProvider} rather than spawning {@code javac}: no process
 * management, no quoting rules per platform, and the diagnostics come back
 * structured. The runtime classes are on the class path, so the generated code can
 * refer to {@code javax.microedition.*} and {@code j2pc.rt.*}.</p>
 */
public final class SourceCompiler {

    private final Options opts;

    public SourceCompiler(Options opts) {
        this.opts = opts;
    }

    /**
     * Compiles every {@code .java} file under {@code sourceRoot} into {@code outputDir}.
     *
     * @return number of errors, zero on success
     */
    public int compile(Path sourceRoot, Path outputDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("no Java compiler available; run J2PC on a JDK, not a JRE");
        }
        Files.createDirectories(outputDir);

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        if (sources.isEmpty()) {
            Log.warn("nothing to compile under %s", sourceRoot);
            return 0;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {

            files.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
            files.setLocation(StandardLocation.SOURCE_PATH, List.of(sourceRoot.toFile()));

            List<java.io.File> sourceFiles = new ArrayList<>();
            for (Path p : sources) {
                sourceFiles.add(p.toFile());
            }

            List<String> args = new ArrayList<>();
            args.add("-encoding");
            args.add("UTF-8");
            args.add("-nowarn");
            args.add("-classpath");
            args.add(RuntimeClasses.classpath());
            // The generated code is verbose by nature; suppressing the notes keeps
            // the log readable without hiding real problems.
            args.add("-Xlint:none");

            StringWriter out = new StringWriter();
            boolean ok = compiler.getTask(out, files, diagnostics, args, null,
                    files.getJavaFileObjectsFromFiles(sourceFiles)).call();

            int errors = 0;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) {
                    continue;
                }
                errors++;
                if (errors <= 20) {
                    String where = d.getSource() == null ? ""
                            : Path.of(d.getSource().toUri()).getFileName() + ":" + d.getLineNumber();
                    Log.error("%s %s", where, d.getMessage(null));
                }
            }
            if (errors > 20) {
                Log.error("... and %d more errors", errors - 20);
            }
            if (!ok && errors == 0) {
                Log.error("compilation failed: %s", out.toString().trim());
                return 1;
            }
            return errors;
        }
    }
}
