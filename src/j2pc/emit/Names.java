package j2pc.emit;

import java.util.HashSet;
import java.util.Set;

/** Java identifier helpers. */
public final class Names {

    /**
     * Words that can never be used as an identifier.
     *
     * <p>Context-sensitive words ({@code var}, {@code yield}, {@code record},
     * {@code sealed}, {@code permits}) are deliberately absent: they are legal
     * identifiers, and {@code Thread.yield()} must stay a call to a platform
     * method rather than becoming {@code yield_()}.</p>
     */
    private static final Set<String> KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null"));

    /** Context-sensitive words: usable as identifiers, but awkward in declarations. */
    private static final Set<String> CONTEXTUAL = new HashSet<>(java.util.Arrays.asList(
            "var", "record", "yield", "sealed", "permits"));

    private Names() {
    }

    public static boolean isKeyword(String s) {
        return KEYWORDS.contains(s);
    }

    /** True for words that are legal identifiers but best avoided when declaring. */
    public static boolean isContextual(String s) {
        return CONTEXTUAL.contains(s);
    }

    public static String identifier(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "unnamed";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 1);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = Character.isJavaIdentifierPart(c) || c == '$';
            if (i == 0) {
                ok = Character.isJavaIdentifierStart(c) || c == '$' || c == '_';
            }
            sb.append(ok ? c : '_');
        }
        String out = sb.toString();
        if (isKeyword(out)) {
            out = out + "_";
        }
        return out;
    }

    /**
     * Name of a member of a class outside the application.
     *
     * <p>Platform names are already legal Java identifiers, so they are kept
     * verbatim: renaming {@code Thread.yield} would break the call. A name that is
     * nonetheless a keyword ({@code do}) or contains illegal characters cannot come
     * from the platform and has to be sanitised.</p>
     */
    public static String external(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "unnamed";
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = i == 0
                    ? (Character.isJavaIdentifierStart(c) || c == '$' || c == '_')
                    : (Character.isJavaIdentifierPart(c) || c == '$');
            if (!ok) {
                return identifier(raw);
            }
        }
        return isKeyword(raw) ? raw + "_" : raw;
    }

    /** Simple class name of an internal name ({@code a/b/C$D -> C$D}). */
    public static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    public static String packageName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.');
    }
}
