package j2pc;

import j2pc.classfile.ClassFile;
import j2pc.classfile.ClassReader;
import j2pc.emit.ClassEmitter;
import j2pc.emit.NameRegistry;
import j2pc.emit.Names;
import j2pc.jar.JadFile;
import j2pc.jar.JarReader;
import j2pc.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Orchestrates a whole JAR recompilation. */
public final class Recompiler {

    private final Options opts;
    private final List<Stat> stats = new ArrayList<>();

    public Recompiler(Options opts) {
        this.opts = opts;
    }

    public Result recompile(Path jarPath, Path jadPath) throws IOException {
        Result result = new Result();
        Path outRoot = opts.outputDir;
        Files.createDirectories(outRoot);

        JadFile jad = null;
        if (jadPath != null && Files.exists(jadPath)) {
            jad = JadFile.parse(jadPath);
            Log.info("JAD: %s (%s %s)", jad.midletName(), jad.vendor(), jad.version());
        }

        Map<String, ClassFile> classes = new LinkedHashMap<>();
        List<String> resources = new ArrayList<>();

        try (JarReader jar = new JarReader(jarPath)) {
            for (String entry : jar.allEntries()) {
                if (entry.endsWith("/")) {
                    continue;
                }
                if (entry.endsWith(".class") && !entry.startsWith("META-INF/")) {
                    byte[] bytes = jar.read(entry);
                    try {
                        ClassFile cf = ClassReader.read(bytes);
                        classes.put(cf.name(), cf);
                    } catch (IOException e) {
                        Log.warn("cannot read %s: %s", entry, e.getMessage());
                        result.failedClasses++;
                    }
                } else if (!entry.startsWith("META-INF/")) {
                    resources.add(entry);
                }
            }
            result.jarEntryCount = jar.allEntries().size();

            for (String res : resources) {
                if (res.endsWith(".class")) {
                    continue;
                }
                byte[] bytes = jar.read(res);
                Path target = outRoot.resolve("assets").resolve(res);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            }
        }

        Log.info("classes: %d, resources: %d", classes.size(), resources.size());

        // Names must be assigned before any source is produced: a method
        // referenced from another class has to receive the same Java name it
        // was declared with.
        NameRegistry registry = new NameRegistry();
        registry.registerAll(classes.values());

        for (ClassFile cf : classes.values()) {
            String internal = cf.name();
            if (opts.onlyPrefix != null && !internal.startsWith(opts.onlyPrefix)) {
                continue;
            }
            String pkg = opts.packagePrefix.isEmpty() ? Names.packageName(internal) : opts.packagePrefix;
            Path dir = pkg.isEmpty() ? outRoot : outRoot.resolve(pkg.replace('.', '/'));
            Files.createDirectories(dir);
            Path file = dir.resolve(Names.simpleName(internal) + ".java");
            try {
                String src = new ClassEmitter(cf, opts, registry).emit();
                Files.write(file, src.getBytes(StandardCharsets.UTF_8));
                Stat st = Stat.of(cf);
                stats.add(st);
                result.classes++;
                result.methods += st.methods;
                result.failedMethods += st.failed;
                result.lines += st.lines;
            } catch (RuntimeException e) {
                Log.error("cannot emit %s: %s", internal, e);
                result.failedClasses++;
            }
        }

        Path launcher = writeLauncher(outRoot, jad, classes);
        result.launcher = launcher;

        Path manifest = outRoot.resolve("j2pc-report.txt");
        Files.write(manifest, report(jad, classes).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    private Path writeLauncher(Path outRoot, JadFile jad, Map<String, ClassFile> classes) throws IOException {
        String midletClass = null;
        String name = "J2ME Application";
        String vendor = "unknown";
        String version = "1.0";
        Path assets = outRoot.resolve("assets").toAbsolutePath();
        String assetsDir = assets.toString().replace('\\', '/');
        if (jad != null) {
            name = jad.midletName();
            vendor = jad.vendor();
            version = jad.version();
            List<JadFile.MidletEntry> entries = jad.midlets();
            if (!entries.isEmpty()) {
                midletClass = entries.get(0).className;
            }
        }
        if (midletClass == null) {
            midletClass = guessMidlet(classes);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by J2PC ").append(opts.version).append('\n');
        sb.append("import javax.microedition.midlet.MIDlet;\n\n");
        sb.append("/** Desktop entry point for \"").append(name).append("\". */\n");
        sb.append("public final class J2PCLauncher {\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        sb.append("        // Resources of the original suite live in a plain directory.\n");
        sb.append("        j2pc.rt.Assets.addRoot(java.nio.file.Path.of(")
                .append(assetsDir == null ? "\"assets\"" : "\"" + escape(assetsDir) + "\"").append("));\n");
        sb.append("        String cls = args.length > 0 ? args[0] : ")
                .append(midletClass == null ? "null" : "\"" + midletClass + "\"").append(";\n");
        sb.append("        if (cls == null) {\n");
        sb.append("            throw new IllegalStateException(\"no MIDlet class found; pass one as the first argument\");\n");
        sb.append("        }\n");
        sb.append("        MIDlet midlet = (MIDlet) Class.forName(cls).getDeclaredConstructor().newInstance();\n");
        sb.append("        javax.microedition.midlet.MIDletHost.run(midlet, ")
                .append("\"").append(escape(name)).append("\", ")
                .append("\"").append(escape(vendor)).append("\", ")
                .append("\"").append(escape(version)).append("\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        Path p = outRoot.resolve("J2PCLauncher.java");
        Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8));
        return p;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String guessMidlet(Map<String, ClassFile> classes) {
        String best = null;
        for (ClassFile cf : classes.values()) {
            String sup = cf.superName();
            if (sup != null && sup.equals("javax/microedition/midlet/MIDlet")) {
                if (best == null || cf.name().length() < best.length()) {
                    best = cf.name().replace('/', '.');
                }
            }
        }
        return best;
    }

    private String report(JadFile jad, Map<String, ClassFile> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("J2PC recompilation report\n");
        sb.append("=========================\n\n");
        if (jad != null) {
            sb.append("MIDlet      : ").append(jad.midletName()).append('\n');
            sb.append("Vendor      : ").append(jad.vendor()).append('\n');
            sb.append("Version     : ").append(jad.version()).append('\n');
            sb.append("Profile     : ").append(jad.profile()).append('\n');
            sb.append("Configuration: ").append(jad.configuration()).append('\n');
            sb.append('\n');
        }
        sb.append("Classes     : ").append(classes.size()).append('\n');
        int methods = 0;
        int failed = 0;
        for (Stat s : stats) {
            methods += s.methods;
            failed += s.failed;
        }
        sb.append("Methods     : ").append(methods).append('\n');
        sb.append("Unrecompiled: ").append(failed).append('\n');
        sb.append('\n');
        sb.append("Per class:\n");
        for (Stat s : stats) {
            sb.append(String.format("  %-60s %5d methods %5d lines%s%n",
                    s.name, s.methods, s.lines, s.failed > 0 ? "  (" + s.failed + " failed)" : ""));
        }
        return sb.toString();
    }

    public static final class Result {
        public int classes;
        public int methods;
        public int failedMethods;
        public int failedClasses;
        public int lines;
        public int jarEntryCount;
        public Path launcher;
    }

    private static final class Stat {
        String name;
        int methods;
        int failed;
        int lines;

        static Stat of(ClassFile cf) {
            Stat s = new Stat();
            s.name = cf.name();
            s.methods = cf.methods.length;
            for (ClassFile.Member m : cf.methods) {
                if (m.code() == null) {
                    continue;
                }
                s.lines += m.code().code.length / 8 + 1;
            }
            return s;
        }
    }
}
