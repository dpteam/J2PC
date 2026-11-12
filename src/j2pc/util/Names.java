package j2pc.util;

import java.nio.file.Path;

/**
 * Turns arbitrary application names into names that are safe to use for files,
 * directories and package metadata.
 *
 * <p>MIDlet names come from a JAD written by a 2005 toolchain: they contain
 * spaces, colons, Cyrillic, trademarks and occasionally a path separator. Every
 * consumer — the packager, {@code jpackage}, the per-application output folder —
 * needs the same sanitised form, so it lives in one place.</p>
 */
public final class Names {

    /** Used when nothing usable is left after sanitising. */
    public static final String FALLBACK = "J2PCApp";

    private Names() {
    }

    /**
     * Replaces every character that is not safe in a file name with {@code _}.
     * The result is never empty and never starts with a separator.
     */
    public static String safe(String name) {
        if (name == null) {
            return FALLBACK;
        }
        String safe = name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        // Leading dots make hidden files on Unix and confuse archive tools.
        while (safe.startsWith(".")) {
            safe = safe.substring(1);
        }
        if (safe.isEmpty()) {
            return FALLBACK;
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe;
    }

    /**
     * Folder name for one application: the archive name without its extension.
     *
     * <p>Batch runs put every application into its own folder, because two
     * suites almost always ship a class called {@code J2PCLauncher} — writing
     * them into one directory would make the second overwrite the first.</p>
     */
    public static String folderForJar(Path jar) {
        if (jar == null || jar.getFileName() == null) {
            return FALLBACK;
        }
        String file = jar.getFileName().toString();
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            file = file.substring(0, dot);
        }
        return safe(file);
    }
}
