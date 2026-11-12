package j2pc.emit;

import j2pc.classfile.Descriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps internal class names to the names used in the generated source.
 *
 * <p>Java has a restriction the JVM does not: a class in a named package cannot
 * reference a class in the unnamed package. J2ME suites break that rule all the
 * time — a packaged MIDlet next to a pile of obfuscated default-package helper
 * classes. Such classes are therefore moved into a synthetic package, and every
 * reference is rewritten through this mapper.</p>
 */
public final class TypeMapper {

    /** Package that receives the default-package classes of a mixed suite. */
    public static final String UNPACKAGED = "unpackaged";

    private final Map<String, String> renamed = new HashMap<>();
    private final Set<String> stubs = new HashSet<>();

    /** Registers that {@code from} is emitted as {@code to} (both internal names). */
    public void rename(String from, String to) {
        renamed.put(from, to);
    }

    /** Records a type that exists only as a generated stub. */
    public void markStub(String internalName) {
        stubs.add(internalName);
    }

    /**
     * True when the type is a generated stub.
     *
     * <p>The real relationship between two stubbed types is unknown — an m3g
     * {@code TriangleStripArray} is an {@code IndexBuffer}, but nothing in the
     * archive says so — and Java rejects a cast between unrelated classes. Casts
     * that involve a stub therefore go through {@code Object}, which always
     * compiles.</p>
     */
    public boolean isStub(String internalName) {
        return stubs.contains(internalName);
    }

    public boolean isEmpty() {
        return renamed.isEmpty();
    }

    public int renamedCount() {
        return renamed.size();
    }

    /** Internal name used in the output for the given input class. */
    public String internal(String internalName) {
        if (internalName == null) {
            return null;
        }
        String mapped = renamed.get(internalName);
        return mapped == null ? internalName : mapped;
    }

    /** Package the class is emitted into. */
    public String packageOf(String internalName) {
        return Names.packageName(internal(internalName));
    }

    /** Simple class name, as written in the class declaration. */
    public String simpleName(String internalName) {
        return sanitize(Names.simpleName(internal(internalName)));
    }

    /**
     * Makes a simple name legal Java. Obfuscators happily emit classes called
     * {@code short} or {@code int}, which are keywords.
     */
    private static String sanitize(String simple) {
        return Names.isKeyword(simple) ? simple + "_" : simple;
    }

    /** Source-level type for a JVM descriptor. */
    public String ref(String typeDesc) {
        if (typeDesc == null || typeDesc.isEmpty()) {
            return "java.lang.Object";
        }
        if (typeDesc.charAt(0) == '[') {
            return ref(typeDesc.substring(1)) + "[]";
        }
        if (typeDesc.charAt(0) != 'L') {
            return Descriptor.javaType(typeDesc);
        }
        String internal = typeDesc.substring(1, typeDesc.length() - 1);
        return refInternal(internal);
    }
    /**
     * Source-level type for an internal name ({@code a/b/C}, {@code [I} or {@code java/lang/String}).
     *
     * <p>An internal name is never a primitive descriptor, so a single-letter class
     * name such as {@code B} or {@code I} must be treated as a class. Obfuscators
     * name classes exactly like that, and mistaking {@code B} for {@code byte}
     * produces code that does not compile.</p>
     */
    public String refInternal(String internalName) {
        if (internalName == null) {
            return "java.lang.Object";
        }
        if (internalName.startsWith("[")) {
            // An array descriptor: the component may be a primitive descriptor
            // (`[I`), another array (`[[I`) or a class (`[Lfoo/Bar;`). Only inside
            // an array is a single letter unambiguously a primitive.
            String component = internalName.substring(1);
            if (component.length() == 1 && Descriptor.isPrimitive(component)) {
                return Descriptor.javaType(component) + "[]";
            }
            return refInternal(component) + "[]";
        }
        // Tolerate a full descriptor, which some call sites pass through.
        if (internalName.length() > 1 && internalName.startsWith("L")
                && internalName.endsWith(";")) {
            return refInternal(internalName.substring(1, internalName.length() - 1));
        }
        String mapped = internal(internalName);
        String dotted = Descriptor.platformType(mapped);
        int dot = dotted.lastIndexOf('.');
        if (dot < 0) {
            return sanitize(dotted);
        }
        return dotted.substring(0, dot + 1) + sanitize(dotted.substring(dot + 1));
    }

    /** Fully qualified name of the emitted class. */
    public String qualified(String internalName) {
        String pkg = packageOf(internalName);
        String simple = simpleName(internalName);
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }
}
