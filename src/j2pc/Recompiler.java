package j2pc;

import j2pc.classfile.ClassFile;
import j2pc.classfile.ClassReader;
import j2pc.emit.ClassEmitter;
import j2pc.emit.NameRegistry;
import j2pc.emit.Names;
import j2pc.emit.TypeMapper;
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
                    if (bytes == null || bytes.length < 4 || !hasClassMagic(bytes)) {
                        // Some suites carry decoy .class entries with no class
                        // data at all; they are not errors, just noise.
                        Log.debug("skipping non-class entry %s", entry);
                        continue;
                    }
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

        // Java forbids a packaged class from referring to a class in the unnamed
        // package, which J2ME suites do all the time. Such classes move into a
        // synthetic package and every reference is rewritten.
        TypeMapper types = buildTypeMapper(classes.keySet());

        for (ClassFile cf : classes.values()) {
            String internal = cf.name();
            if (opts.onlyPrefix != null && !internal.startsWith(opts.onlyPrefix)) {
                continue;
            }
            String pkg = opts.packagePrefix.isEmpty() ? types.packageOf(internal) : opts.packagePrefix;
            Path dir = pkg.isEmpty() ? outRoot : outRoot.resolve(pkg.replace('.', '/'));
            Files.createDirectories(dir);
            Path file = dir.resolve(types.simpleName(internal) + ".java");
            try {
                String src = new ClassEmitter(cf, opts, registry, types).emit();
                Files.write(file, src.getBytes(StandardCharsets.UTF_8));
                Stat st = Stat.of(cf);
                stats.add(st);
                result.classes++;
                result.methods += st.methods;
                result.failedMethods += st.failed;
                result.lines += st.lines;
            } catch (RuntimeException e) {
                Log.error("cannot emit %s: %s", internal, e);
                if (Log.getLevel() >= Log.DEBUG) {
                    e.printStackTrace();
                }
                result.failedClasses++;
            }
        }

        Path launcher = writeLauncher(outRoot, jad, classes, types);
        result.launcher = launcher;

        Path manifest = outRoot.resolve("j2pc-report.txt");
        Files.write(manifest, report(jad, classes).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    private static boolean hasClassMagic(byte[] b) {
        return (b[0] & 0xFF) == 0xCA && (b[1] & 0xFF) == 0xFE
                && (b[2] & 0xFF) == 0xBA && (b[3] & 0xFF) == 0xBE;
    }

    /**
     * Decides which classes have to be moved into a synthetic package.
     *
     * <p>Java source cannot mix the two worlds: a class in package {@code p} may
     * not refer to a class in the unnamed package. As soon as a suite contains
     * both kinds — a packaged MIDlet next to obfuscated helper classes — the
     * default-package classes are relocated so the output can compile.</p>
     */
    private static TypeMapper buildTypeMapper(java.util.Collection<String> classNames) {
        TypeMapper mapper = new TypeMapper();
        boolean hasPackaged = false;
        boolean hasDefault = false;
        for (String name : classNames) {
            if (Names.packageName(name).isEmpty()) {
                hasDefault = true;
            } else {
                hasPackaged = true;
            }
        }
        if (!hasPackaged || !hasDefault) {
            return mapper;
        }
        for (String name : classNames) {
            if (Names.packageName(name).isEmpty()) {
                mapper.rename(name, TypeMapper.UNPACKAGED + "/" + Names.simpleName(name));
            }
        }
        Log.info("moving %d default-package classes into %s",
                mapper.renamedCount(), TypeMapper.UNPACKAGED);
        return mapper;
    }

    private Path writeLauncher(Path outRoot, JadFile jad, Map<String, ClassFile> classes,
                               TypeMapper types) throws IOException {
        String midletClass = null;
        String name = "J2ME Application";
        String vendor = "unknown";
        String version = "1.0";
        if (jad != null) {
            name = jad.midletName();
            vendor = jad.vendor();
            version = jad.version();
            List<JadFile.MidletEntry> entries = jad.midlets();
            if (!entries.isEmpty() && entries.get(0).className != null) {
                // The JAD names the class as it was in the suite; a relocated
                // class has to be referenced by its new name.
                midletClass = types.qualified(entries.get(0).className.replace('.', '/'));
            }
        }
        if (midletClass == null) {
            midletClass = guessMidlet(classes, types);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by J2PC ").append(opts.version).append('\n');
        sb.append("import javax.microedition.midlet.MIDlet;\n\n");
        sb.append("/** Desktop entry point for \"").append(name).append("\". */\n");
        sb.append("public final class J2PCLauncher {\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        sb.append("        // Resources of the original suite live in a plain directory. The path is\n");
        sb.append("        // relative so that the output stays portable: it is resolved against the\n");
        sb.append("        // working directory, the location of this class, and their parents.\n");
        sb.append("        j2pc.rt.Assets.addRoot(\"assets\");\n");
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

    private String guessMidlet(Map<String, ClassFile> classes, TypeMapper types) {
        String best = null;
        for (ClassFile cf : classes.values()) {
            String sup = cf.superName();
            if (sup != null && sup.equals("javax/microedition/midlet/MIDlet")) {
                if (best == null || cf.name().length() < best.length()) {
                    best = types.qualified(cf.name());
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
