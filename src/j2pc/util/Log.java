package j2pc.util;

/**
 * Minimal, dependency-free logger. Everything goes to stderr so that
 * machine-readable output on stdout stays clean.
 */
public final class Log {

    public static final int QUIET = 0;
    public static final int ERROR = 1;
    public static final int WARN = 2;
    public static final int INFO = 3;
    public static final int DEBUG = 4;

    private static int level = INFO;
    private static int warnings;
    private static int errors;
    /** Optional second destination, used by the GUI to show the log live. */
    private static java.util.function.Consumer<String> sink;

    private Log() {
    }

    /** Redirects a copy of every line to {@code consumer}; null stops it. */
    public static void setSink(java.util.function.Consumer<String> consumer) {
        sink = consumer;
    }

    public static void setLevel(int l) {
        level = l;
    }

    public static int getLevel() {
        return level;
    }

    public static int warnings() {
        return warnings;
    }

    public static int errors() {
        return errors;
    }

    public static void error(String fmt, Object... args) {
        errors++;
        print("error", fmt, args);
    }

    public static void warn(String fmt, Object... args) {
        warnings++;
        print("warn", fmt, args);
    }

    public static void info(String fmt, Object... args) {
        if (level >= INFO) {
            print("info", fmt, args);
        }
    }

    public static void debug(String fmt, Object... args) {
        if (level >= DEBUG) {
            print("debug", fmt, args);
        }
    }

    private static void print(String tag, String fmt, Object... args) {
        String msg;
        try {
            msg = args.length == 0 ? fmt : String.format(fmt, args);
        } catch (RuntimeException e) {
            msg = fmt;
        }
        String line = "[" + tag + "] " + msg;
        System.err.println(line);
        java.util.function.Consumer<String> s = sink;
        if (s != null) {
            try {
                s.accept(line);
            } catch (RuntimeException ignored) {
                // a broken sink must never break logging
            }
        }
    }
}
