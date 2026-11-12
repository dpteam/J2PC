package j2pc.tui;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Terminal helpers: ANSI colour, screen clearing, and input.
 *
 * <p>Colour is disabled automatically when the stream is redirected or when the
 * {@code NO_COLOR} environment variable is set, so piping the output to a file
 * produces clean text.</p>
 */
final class Term {

    private static final boolean COLOR = detectColor();

    private Term() {
    }

    private static boolean detectColor() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String term = System.getenv("TERM");
        if (term != null && term.equals("dumb")) {
            return false;
        }
        // Windows terminals handle ANSI since Windows 10; the check is conservative.
        return System.console() != null;
    }

    static boolean colorEnabled() {
        return COLOR;
    }

    static String bold(String s) {
        return COLOR ? "\u001b[1m" + s + "\u001b[0m" : s;
    }

    static String dim(String s) {
        return COLOR ? "\u001b[2m" + s + "\u001b[0m" : s;
    }

    static String accent(String s) {
        return COLOR ? "\u001b[36m" + s + "\u001b[0m" : s;
    }

    static String ok(String s) {
        return COLOR ? "\u001b[32m" + s + "\u001b[0m" : s;
    }

    static String warn(String s) {
        return COLOR ? "\u001b[33m" + s + "\u001b[0m" : s;
    }

    static String error(String s) {
        return COLOR ? "\u001b[31m" + s + "\u001b[0m" : s;
    }

    /** Clears the screen and moves the cursor home. */
    static void clear(PrintStream out) {
        if (COLOR) {
            out.print("\u001b[2J\u001b[H");
        }
        out.flush();
    }

    /** Draws a horizontal rule of the given width. */
    static void rule(PrintStream out, int width) {
        out.println(dim("-".repeat(Math.max(4, width))));
    }

    /** Pads or truncates text to exactly {@code width} characters. */
    static String fit(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() > width) {
            return text.substring(0, Math.max(0, width - 2)) + "..";
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Numbered menu; returns the index chosen, or -1 for back/quit. */
    static int choose(PrintStream out, java.io.BufferedReader in, String title,
                      List<String> items, boolean allowBack) throws IOException {
        out.println();
        out.println(bold(title));
        for (int i = 0; i < items.size(); i++) {
            out.printf("  %s %s%n", accent(String.format("%2d)", i + 1)), items.get(i));
        }
        if (allowBack) {
            out.printf("  %s %s%n", accent(" 0)"), "back");
        }
        out.print(accent("> "));
        out.flush();
        String line = in.readLine();
        if (line == null) {
            return -1;
        }
        line = line.trim();
        if (line.isEmpty()) {
            return -1;
        }
        try {
            int n = Integer.parseInt(line);
            if (n == 0) {
                return -1;
            }
            if (n >= 1 && n <= items.size()) {
                return n - 1;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the error below
        }
        out.println(error("  not a valid choice"));
        return -2;
    }

    /** Asks a yes/no question with a default. */
    static boolean confirm(PrintStream out, java.io.BufferedReader in, String question,
                           boolean fallback) throws IOException {
        out.print(question + (fallback ? " [Y/n] " : " [y/N] "));
        out.flush();
        String line = in.readLine();
        if (line == null || line.trim().isEmpty()) {
            return fallback;
        }
        String v = line.trim().toLowerCase();
        return v.startsWith("y") || v.startsWith("д");
    }

    /** Asks for a line of text with a default value. */
    static String ask(PrintStream out, java.io.BufferedReader in, String question,
                      String fallback) throws IOException {
        out.print(question + (fallback == null || fallback.isEmpty() ? ": " : " [" + fallback + "]: "));
        out.flush();
        String line = in.readLine();
        if (line == null || line.trim().isEmpty()) {
            return fallback;
        }
        return line.trim();
    }
}
