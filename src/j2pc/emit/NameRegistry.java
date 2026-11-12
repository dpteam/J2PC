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
 *
 * <h2>Inheritance</h2>
 *
 * <p>Two constraints pull in opposite directions:</p>
 *
 * <ul>
 *   <li>a method that <em>overrides</em> an inherited one must keep the inherited
 *       name, otherwise virtual dispatch breaks;</li>
 *   <li>a method that does <em>not</em> override must not accidentally take an
 *       inherited name, otherwise javac rejects the class as an illegal override
 *       or hide (a static method cannot hide an instance method, and a narrower
 *       access modifier cannot override a wider one).</li>
 * </ul>
 *
 * <p>Both are satisfied by registering superclasses first: genuine overrides
 * inherit the parent's Java name, and every other method avoids the names that
 * are already visible in the class.</p>
 */
public final class NameRegistry {

    private final Map<String, ClassNames> classes = new HashMap<>();
    private final Map<String, String> superOf = new HashMap<>();
    private final Set<String> typeNames = new HashSet<>();
    private final Set<String> ambiguous = new HashSet<>();

    /**
     * Assigns the final Java names for every member of every class, superclasses
     * before subclasses.
     */
    public void registerAll(java.util.Collection<ClassFile> all) {
        Map<String, ClassFile> byName = new HashMap<>();
        for (ClassFile cf : all) {
            byName.put(cf.name(), cf);
            typeNames.add(Names.simpleName(cf.name()));
            if (cf.superName() != null) {
                superOf.put(cf.name(), cf.superName());
            }
        }
        typeNames.addAll(PLATFORM_TYPES);

        // Return-type overloading is the one case Java cannot express. It is
        // detected over the whole application, not per class, so that a method
        // receives the same Java name in a superclass and in every subclass.
        Map<String, Set<String>> returnsBySig = new HashMap<>();
        for (ClassFile cf : all) {
            for (ClassFile.Member m : cf.methods) {
                String sig = m.name() + paramSignature(m.desc());
                returnsBySig.computeIfAbsent(sig, k -> new HashSet<>())
                        .add(returnType(m.desc()));
            }
        }
        for (Map.Entry<String, Set<String>> e : returnsBySig.entrySet()) {
            if (e.getValue().size() > 1) {
                ambiguous.add(e.getKey());
            }
        }

        for (ClassFile cf : all) {
            registerWithAncestors(cf, byName, new HashSet<>());
        }
    }

    /** Registers {@code cf} after its superclasses, so overrides see their parent. */
    private void registerWithAncestors(ClassFile cf, Map<String, ClassFile> byName, Set<String> path) {
        if (classes.containsKey(cf.name()) || !path.add(cf.name())) {
            return;
        }
        String parent = cf.superName();
        ClassFile parentFile = parent == null ? null : byName.get(parent);
        if (parentFile != null) {
            registerWithAncestors(parentFile, byName, path);
        }
        register(cf);
    }

    /** Assigns the final Java names for every member of {@code cf}. */
    public void register(ClassFile cf) {
        ClassNames names = new ClassNames();
        names.usedFields.addAll(typeNames);
        names.usedMethods.addAll(typeNames);

        // Reserve every raw identifier first, so a generated suffix (from the
        // return-type tag) can never land on a name that some other method
        // already uses literally.
        for (ClassFile.Member m : cf.methods) {
            names.usedMethods.add(Names.identifier(m.name()));
        }

        for (ClassFile.Member f : cf.fields) {
            names.field.put(f.name() + ":" + f.desc(), names.uniqueField(f.name()));
        }
        // Java overloads by parameter list, exactly like the JVM. Methods that
        // differ only in their return type are the single case Java cannot
        // express; those get a deterministic suffix derived from the return type,
        // computed application-wide so a subclass keeps the superclass name.
        java.util.List<ClassFile.Member> ordered = new java.util.ArrayList<>();
        for (ClassFile.Member m : cf.methods) {
            ordered.add(m);
        }
        ordered.sort(java.util.Comparator
                .comparing(ClassFile.Member::name)
                .thenComparing(ClassFile.Member::desc));

        for (ClassFile.Member m : ordered) {
            String raw = m.name();
            String sig = raw + paramSignature(m.desc());
            String base = Names.identifier(raw);
            if (ambiguous.contains(sig)) {
                // Include staticness: a static and an instance method may legally
                // share a name and signature in different classes of a hierarchy,
                // but Java forbids one hiding the other.
                boolean isStatic = (m.access & ClassFile.ACC_STATIC) != 0;
                base = base + (isStatic ? "_S" : "_I") + returnTag(returnType(m.desc()));
            }
            // The plain name is already reserved, so unique() only renames when a
            // suffix was needed or the sanitised name collides with another one.
            String assigned = base.equals(Names.identifier(raw)) && !ambiguous.contains(sig)
                    ? base : names.uniqueMethod(base);
            names.method.put(raw + m.desc(), assigned);
        }
        classes.put(cf.name(), names);
        typeNames.add(Names.simpleName(cf.name()));
    }

    /** {@code (I)V -> (I)}, so overloads keep one name and bridges collapse. */
    private static String paramSignature(String desc) {
        int close = desc.indexOf(')');
        return close < 0 ? desc : desc.substring(0, close + 1);
    }

    /** {@code (I)V -> V}. */
    private static String returnType(String desc) {
        int close = desc.indexOf(')');
        return close < 0 ? "V" : desc.substring(close + 1);
    }

    /** Short stable tag for a return type, used to break return-type overloads. */
    private static String returnTag(String ret) {
        if (ret.isEmpty()) {
            return "v";
        }
        switch (ret.charAt(0)) {
            case 'V': return "v";
            case 'Z': return "z";
            case 'B': return "b";
            case 'C': return "c";
            case 'S': return "s";
            case 'I': return "i";
            case 'J': return "j";
            case 'F': return "f";
            case 'D': return "d";
            case '[': return "a";
            case 'L': {
                String name = ret.substring(1, ret.length() - 1);
                int slash = name.lastIndexOf('/');
                return "r" + Names.identifier(slash < 0 ? name : name.substring(slash + 1));
            }
            default: return "x";
        }
    }

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

    private static final class ClassNames {
        final Map<String, String> field = new HashMap<>();
        final Map<String, String> method = new HashMap<>();
        final Map<String, String> byParamSig = new HashMap<>();
        final Map<String, Boolean> bridgeOnly = new HashMap<>();
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
