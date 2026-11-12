package j2pc;

import j2pc.jar.JadFile;
import j2pc.jar.JarReader;
import j2pc.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command line entry point. */
public final class Main {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Options opts = new Options();
        List<Path> inputs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h":
                case "--help":
                    printUsage();
                    return 0;
                case "-V":
                case "--version":
                    System.out.println("J2PC " + opts.version);
                    return 0;
                case "-o":
                case "--out":
                    opts.outputDir = Path.of(need(args, ++i, a));
                    break;
                case "-v":
                case "--verbose":
                    opts.verbose = true;
                    Log.setLevel(Log.DEBUG);
                    break;
                case "-q":
                case "--quiet":
                    opts.quiet = true;
                    Log.setLevel(Log.WARN);
                    break;
                case "--stats":
                    opts.stats = true;
                    break;
                case "--no-comments":
                    opts.emitComments = false;
                    opts.lineComments = false;
                    break;
                case "--no-lines":
                    opts.lineComments = false;
                    break;
                case "--package":
                    opts.packagePrefix = need(args, ++i, a);
                    break;
                case "--only":
                    opts.onlyPrefix = need(args, ++i, a);
                    break;
                case "--indent":
                    opts.indentUnit = need(args, ++i, a).replace("\\t", "\t");
                    break;
                case "--list":
                    return listJar(Path.of(need(args, ++i, a)));
                case "--build":
                    opts.buildJar = true;
                    break;
                case "--exe":
                case "--native":
                    opts.buildJar = true;
                    opts.buildNativeImage = true;
                    break;
                case "--tui":
                    opts.tui = true;
                    break;
                case "--gui":
                    opts.gui = true;
                    break;
                default:
                    if (a.startsWith("-")) {
                        System.err.println("unknown option: " + a);
                        printUsage();
                        return 2;
                    }
                    inputs.add(Path.of(a));
            }
        }

        if (opts.tui) {
            return j2pc.tui.Tui.run(opts);
        }
        if (opts.gui) {
            j2pc.gui.Gui.start(opts);
            return 0;
        }

        if (inputs.isEmpty()) {
            printUsage();
            return 2;
        }

        Path jar = null;
        Path jad = null;
        for (Path p : inputs) {
            String n = p.getFileName().toString().toLowerCase();
            if (n.endsWith(".jad")) {
                jad = p;
            } else if (n.endsWith(".jar") || n.endsWith(".zip")) {
                jar = p;
            }
        }
        if (jar == null) {
            Log.error("no .jar input given");
            return 2;
        }
        if (jad == null) {
            jad = siblingJad(jar);
        }

        long started = System.nanoTime();
        try {
            Recompiler r = new Recompiler(opts);
            Recompiler.Result result = r.recompile(jar, jad);
            double secs = (System.nanoTime() - started) / 1e9;
            Log.info("done in %.2fs: %d classes, %d methods, %d failed, %d warnings",
                    secs, result.classes, result.methods, result.failedMethods, Log.warnings());
            if (opts.stats) {
                System.out.println("classes=" + result.classes
                        + " methods=" + result.methods
                        + " failedMethods=" + result.failedMethods
                        + " failedClasses=" + result.failedClasses
                        + " launcher=" + result.launcher);
            }
            if (opts.buildJar) {
                j2pc.build.AppBuilder.AppInfo info = new j2pc.build.AppBuilder.AppInfo(
                        result.appName, result.appVendor, result.appVersion);
                java.nio.file.Path assets = opts.outputDir.resolve("assets");
                j2pc.build.AppBuilder.BuildResult build =
                        new j2pc.build.AppBuilder(opts).build(opts.outputDir, assets, info);
                if (!build.ok) {
                    return 1;
                }
                if (opts.stats) {
                    System.out.println("jar=" + build.jar
                            + " image=" + build.image
                            + " compiled=" + build.compiledClasses);
                }
            }
            return result.failedClasses == 0 ? 0 : 1;
        } catch (IOException e) {
            Log.error("I/O failure: %s", e);
            return 1;
        }
    }

    private static Path siblingJad(Path jar) {
        String n = jar.getFileName().toString();
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            Path candidate = jar.resolveSibling(n.substring(0, dot) + ".jad");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int listJar(Path jar) {
        try (JarReader r = new JarReader(jar)) {
            for (String e : r.allEntries()) {
                System.out.println(e);
            }
            return 0;
        } catch (IOException e) {
            Log.error("%s", e.getMessage());
            return 1;
        }
    }

    private static String need(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException("option " + opt + " needs a value");
        }
        return args[i];
    }

    private static void printUsage() {
        System.out.println("J2PC - J2ME static recompiler");
        System.out.println();
        System.out.println("Usage: j2pc [options] <application.jar> [application.jad]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o, --out <dir>     output directory (default: out)");
        System.out.println("  -v, --verbose       verbose logging");
        System.out.println("  -q, --quiet         only warnings and errors");
        System.out.println("      --stats         print a machine readable summary on stdout");
        System.out.println("      --no-comments   omit the per-instruction comments");
        System.out.println("      --no-lines      omit the // line N comments");
        System.out.println("      --package <p>   put every generated class into package <p>");
        System.out.println("      --only <prefix> recompile only classes with this internal name prefix");
        System.out.println("      --indent <str>  indentation unit (default: four spaces, \\t for a tab)");
        System.out.println("      --list <jar>    list archive entries and exit");
        System.out.println("      --build         compile the generated sources and package a runnable JAR");
        System.out.println("      --exe, --native also wrap the JAR into a native launcher (jpackage)");
        System.out.println("      --tui           start the terminal interface");
        System.out.println("      --gui           start the desktop interface (local web UI)");
        System.out.println("  -h, --help          show this help");
        System.out.println("  -V, --version       show the version");
    }

    private Main() {
    }
}
