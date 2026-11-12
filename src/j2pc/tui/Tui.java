package j2pc.tui;

import j2pc.Options;
import j2pc.Recompiler;
import j2pc.build.AppBuilder;
import j2pc.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Terminal user interface.
 *
 * <p>Line-oriented rather than raw-mode: it works in every terminal, over SSH, and
 * inside an IDE console, with no native library and no {@code stty} fiddling. The
 * screen is redrawn with ANSI escapes when the terminal supports it, and plain
 * text otherwise.</p>
 */
public final class Tui {

    private final PrintStream out = System.out;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private final Options opts;

    private Path folder = defaultFolder();

    private Tui(Options opts) {
        this.opts = opts;
    }

    public static int run(Options opts) {
        return new Tui(opts).loop();
    }

    private int loop() {
        while (true) {
            Term.clear(out);
            header();
            List<Path> jars = findJars();
            out.printf("  folder: %s%n", Term.accent(folder.toString()));
            out.printf("  found : %d application(s)%n", jars.size());
            out.printf("  output: %s  %s%n%n", Term.dim(opts.outputDir.toString()),
                    opts.buildJar ? Term.ok("package JAR") : Term.dim("sources only"));

            List<String> items = new ArrayList<>();
            items.add("choose a folder");
            items.add("recompile the whole folder");
            items.add("options");
            items.add("quit");
            for (int i = 0; i < Math.min(20, jars.size()); i++) {
                Path p = jars.get(i);
                items.add(Term.fit(p.getFileName().toString(), 48)
                        + " " + Term.dim(size(p)));
            }
            if (jars.size() > 20) {
                items.add(Term.dim("... " + (jars.size() - 20) + " more; recompile the folder"));
            }

            int choice;
            try {
                choice = Term.choose(out, in, "J2PC", items, false);
            } catch (IOException e) {
                return 1;
            }
            if (choice == -1) {
                continue;
            }
            if (choice < 0) {
                continue;
            }
            switch (choice) {
                case 0:
                    askFolder();
                    break;
                case 1:
                    recompileAll(jars);
                    break;
                case 2:
                    options();
                    break;
                case 3:
                    out.println(Term.dim("  bye"));
                    return 0;
                default: {
                    int index = choice - 4;
                    if (index >= 0 && index < jars.size()) {
                        recompileOne(jars.get(index));
                    }
                    break;
                }
            }
        }
    }

    private void header() {
        // Plain ASCII: a terminal in a legacy code page would otherwise show
        // replacement characters where the dashes should be.
        out.println(Term.bold("J2PC " + opts.version + " - J2ME static recompiler"));
        out.println(Term.dim("  recompiles a MIDlet suite into plain Java"));
        Term.rule(out, 72);
    }

    private void askFolder() {
        try {
            String value = Term.ask(out, in, "folder containing .jar files", folder.toString());
            if (value == null || value.isEmpty()) {
                return;
            }
            Path candidate = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isDirectory(candidate)) {
                out.println(Term.error("  not a directory: " + candidate));
                pause();
                return;
            }
            folder = candidate;
        } catch (IOException e) {
            out.println(Term.error("  " + e.getMessage()));
        }
    }

    private void options() {
        while (true) {
            Term.clear(out);
            header();
            List<String> items = new ArrayList<>();
            items.add("output folder      : " + opts.outputDir);
            items.add("compile and package: " + (opts.buildJar ? Term.ok("yes") : "no"));
            items.add("native launcher    : " + (opts.buildNativeImage ? Term.ok("yes") : "no"));
            items.add("stub missing APIs  : " + (opts.generateStubs ? "yes" : "no"));
            items.add("line comments      : " + (opts.lineComments ? "yes" : "no"));
            items.add("verbose logging    : " + (opts.verbose ? "yes" : "no"));
            items.add("back");

            int choice;
            try {
                choice = Term.choose(out, in, "options", items, false);
            } catch (IOException e) {
                return;
            }
            if (choice < 0) {
                continue;
            }
            try {
                switch (choice) {
                    case 0:
                        opts.outputDir = Path.of(Term.ask(out, in, "output folder",
                                opts.outputDir.toString()));
                        break;
                    case 1:
                        opts.buildJar = !opts.buildJar;
                        break;
                    case 2:
                        opts.buildNativeImage = !opts.buildNativeImage;
                        if (opts.buildNativeImage) {
                            opts.buildJar = true;
                        }
                        break;
                    case 3:
                        opts.generateStubs = !opts.generateStubs;
                        break;
                    case 4:
                        opts.lineComments = !opts.lineComments;
                        break;
                    case 5:
                        opts.verbose = !opts.verbose;
                        Log.setLevel(opts.verbose ? Log.DEBUG : Log.INFO);
                        break;
                    default:
                        return;
                }
            } catch (IOException e) {
                return;
            }
        }
    }

    private void recompileAll(List<Path> jars) {
        if (jars.isEmpty()) {
            out.println(Term.warn("  no .jar files in this folder"));
            pause();
            return;
        }
        try {
            if (!Term.confirm(out, in, "recompile " + jars.size() + " application(s)?", true)) {
                return;
            }
        } catch (IOException e) {
            return;
        }
        int ok = 0;
        int failed = 0;
        for (int i = 0; i < jars.size(); i++) {
            Path jar = jars.get(i);
            out.printf("%n%s [%d/%d] %s%n", Term.accent(">>>"), i + 1, jars.size(),
                    jar.getFileName());
            if (run(jar)) {
                ok++;
            } else {
                failed++;
            }
        }
        out.printf("%n%s %d ok, %s %d failed%n",
                Term.ok("summary:"), ok, failed == 0 ? Term.dim("") : Term.error(""), failed);
        pause();
    }

    private void recompileOne(Path jar) {
        out.printf("%n%s %s%n", Term.accent(">>>"), jar.getFileName());
        run(jar);
        pause();
    }

    private boolean run(Path jar) {
        Path jad = siblingJad(jar);
        // Every application gets its own folder: two suites almost always ship a
        // class with the same name, and the second would overwrite the first.
        Path base = opts.outputDir;
        opts.outputDir = base.resolve(j2pc.util.Names.folderForJar(jar));
        try {
            return runIn(jar, jad);
        } finally {
            opts.outputDir = base;
        }
    }

    private boolean runIn(Path jar, Path jad) {
        try {
            Recompiler.Result result = new Recompiler(opts).recompile(jar, jad);
            out.printf("    %s %d classes, %d methods%s%n",
                    Term.ok("recompiled"), result.classes, result.methods,
                    result.failedMethods > 0 ? Term.warn(" (" + result.failedMethods + " stubbed)")
                            : "");
            if (!opts.buildJar) {
                out.printf("    sources: %s%n", opts.outputDir);
                return true;
            }
            AppBuilder.BuildResult build = new AppBuilder(opts).build(opts.outputDir,
                    opts.outputDir.resolve("assets"),
                    new AppBuilder.AppInfo(result.appName, result.appVendor, result.appVersion));
            if (!build.ok) {
                out.printf("    %s %d compile error(s)%n", Term.error("failed:"), build.compileErrors);
                return false;
            }
            out.printf("    %s %s%n", Term.ok("jar:"), build.jar);
            if (build.image != null) {
                out.printf("    %s %s%n", Term.ok("image:"), build.image);
            }
            return true;
        } catch (IOException e) {
            out.printf("    %s %s%n", Term.error("error:"), e.getMessage());
            return false;
        }
    }

    private void pause() {
        out.print(Term.dim("  press Enter to continue "));
        out.flush();
        try {
            in.readLine();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private List<Path> findJars() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> list = Files.list(folder)) {
            list.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            // an unreadable folder simply yields an empty list
        }
        return out;
    }

    private static Path siblingJad(Path jar) {
        String name = jar.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        Path candidate = jar.resolveSibling(name.substring(0, dot) + ".jad");
        return Files.exists(candidate) ? candidate : null;
    }

    /**
     * Starting folder. The working directory is the honest default, but when it
     * holds no archives and a {@code samples} directory exists, that is almost
     * certainly what the user wants to look at.
     */
    private static Path defaultFolder() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        if (hasJars(cwd)) {
            return cwd;
        }
        Path samples = cwd.resolve("samples");
        if (Files.isDirectory(samples) && hasJars(samples)) {
            return samples;
        }
        return cwd;
    }

    private static boolean hasJars(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.anyMatch(p -> p.toString().toLowerCase().endsWith(".jar"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String size(Path p) {
        try {
            long bytes = Files.size(p);
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024) + " KiB";
            }
            return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "?";
        }
    }
}
