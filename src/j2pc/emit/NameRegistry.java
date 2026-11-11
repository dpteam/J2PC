package j2pc.emit;

import j2pc.classfile.ClassFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Application-wide registry of Java identifiers.
 *
 * <p>Names have to be assigned once per <em>declaring</em> class, not per
 * reference: otherwise a method referenced from two different classes could
 * receive two different numbers and the generated code would not link. Every
 * class of the application is registered before any source is emitted.</p>
 *
 * <p>Classes outside the application (the JDK and the MIDP emulation layer) are
 * never renamed: their members keep the names the platform actually uses.</p>
 */
public final class NameRegistry {

    private final Map<String, ClassNames> classes = new HashMap<>();
    private final Set<String> typeNames = new HashSet<>();
    private boolean sealed;

    /**
     * Assigns the final Java names for every member of every class.
     *
     * <p>Two passes are required: all type names are collected first, because a
     * field may never be named like a type ({@code d.foo()} would otherwise
     * resolve to the field instead of the class).</p>
     */
    public void registerAll(java.util.Collection<ClassFile> all) {
        for (ClassFile cf : all) {
            typeNames.add(Names.simpleName(cf.name()));
        }
        typeNames.addAll(PLATFORM_TYPES);
        for (ClassFile cf : all) {
            register(cf);
        }
        sealed = true;
    }

    /** Assigns the final Java names for every member of {@code cf}. */
    public void register(ClassFile cf) {
        ClassNames names = new ClassNames();
        names.usedFields.addAll(typeNames);
        names.usedMethods.addAll(typeNames);
        for (ClassFile.Member f : cf.fields) {
            names.field.put(f.name() + ":" + f.desc(), names.uniqueField(f.name()));
        }
        for (ClassFile.Member m : cf.methods) {
            names.method.put(m.name() + m.desc(), names.uniqueMethod(m.name()));
        }
        classes.put(cf.name(), names);
        typeNames.add(Names.simpleName(cf.name()));
    }

    /**
     * True when {@code name} would shadow a type that the generated code refers
     * to by its simple name. Local variables must avoid those names, otherwise
     * {@code d.foo()} would resolve to the variable instead of the class.
     */
    public boolean isTypeName(String name) {
        return typeNames.contains(name);
    }

    private static final Set<String> PLATFORM_TYPES = new HashSet<>(java.util.Arrays.asList(
            "String", "Object", "Integer", "Long", "Float", "Double", "Boolean", "Byte",
            "Character", "Short", "Math", "System", "Thread", "Exception", "Throwable",
            "RuntimeException", "Error", "Class", "Vector", "Hashtable", "Random",
            "StringBuffer", "StringBuilder", "InputStream", "OutputStream", "DataInputStream",
            "DataOutputStream", "Date", "Calendar", "TimeZone", "Number"));

    public String field(String owner, String name, String desc) {
        ClassNames names = classes.get(owner);
        if (names == null) {
            return Names.external(name);
        }
        String mapped = names.field.get(name + ":" + desc);
        return mapped == null ? Names.external(name) : mapped;
    }

    public String method(String owner, String name, String desc) {
        ClassNames names = classes.get(owner);
        if (names == null) {
            return Names.external(name);
        }
        String mapped = names.method.get(name + desc);
        return mapped == null ? Names.external(name) : mapped;
    }

    /** Simple Java name for a type, used in casts and constructor calls. */
    public static String simpleType(String internalName) {
        return Names.simpleName(internalName);
    }

    private static final class ClassNames {
        final Map<String, String> field = new HashMap<>();
        final Map<String, String> method = new HashMap<>();
        final Set<String> usedFields = new HashSet<>();
        final Set<String> usedMethods = new HashSet<>();

        String uniqueField(String raw) {
            return unique(usedFields, Names.identifier(raw), "_f");
        }

        String uniqueMethod(String raw) {
            return unique(usedMethods, Names.identifier(raw), "_m");
        }

        private static String unique(Set<String> used, String base, String suffix) {
            String candidate = base;
            int n = 2;
            while (!used.add(candidate)) {
                candidate = base + suffix + n++;
            }
            return candidate;
        }
    }
}
