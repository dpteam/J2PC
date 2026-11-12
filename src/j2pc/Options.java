package j2pc;

import java.nio.file.Path;

/** Command line options shared by the whole tool. */
public final class Options {

    public Path outputDir = Path.of("out");
    public Path jadFile;
    public boolean verbose;
    public boolean quiet;
    public boolean emitComments = true;
    public boolean lineComments = true;
    public boolean includeSynthetic = true;
    public boolean stats;
    public int sourceLevel = 17;
    public String version = "1.0.0";
    public String indentUnit = "    ";

    /** Optional package prefix applied to regenerated classes. */
    public String packagePrefix = "";

    /**
     * Emit signature-compatible stubs for types the application references but
     * does not ship, so the output can be compiled and inspected.
     */
    public boolean generateStubs = true;

    /** Compile the generated sources and package a runnable JAR. */
    public boolean buildJar;

    /** Wrap the JAR into a native launcher with jpackage. Implies {@link #buildJar}. */
    public boolean buildNativeImage;

    /** Start the terminal interface instead of recompiling from the command line. */
    public boolean tui;

    /** Start the desktop interface instead of recompiling from the command line. */
    public boolean gui;

    /** When set, only classes whose internal name starts with this prefix are emitted. */
    public String onlyPrefix;
}
