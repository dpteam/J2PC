package j2pc.emit;

import j2pc.classfile.ClassFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Application-wide registry of Java identifiers.
 *
 * <p>Names have to be assigned once per <em>declaring</em> class, not per
 * reference: otherwise a method referenced from another class could receive two
 * different numbers and the generated code would not link. Every class of the
 * application is registered before any source is emitted.</p>
 *
 * <p>Classes outside the application (the JDK and the MIDP emulation layer) are
 * never renamed: their members keep the names the platform actually uses.</p>
 *
 * <h2>Method names must be hierarchy-wide</h2>
 *
 * <p>A method's Java name is derived only from its own signature — raw name,
 * parameter list, return type and staticness — and never from its class. That is
 * what makes an override keep the name of the method it overrides, and what makes
 * an implementation keep the name of the interface method it implements.</p>
 *
 * <p>Two things force a suffix:</p>
 *
 * <ul>
 *   <li>Java has no return-type overloading, so a signature that appears with
 *       different return types anywhere in the application gets one;</li>
 *   <li>a static method may not hide an instance method, so staticness is part of
 *       the suffix.</li>
 * </ul>
 *
 * <p>Suffixing is computed over the whole application precisely so that a class
 * and its supertype always agree.</p>
 */
public final class NameRegistry {

    private final Map<String, ClassNames> classes = new HashMap<>();
    private final Map<String, String> methodNames = new HashMap<>();
    private final Set<String> typeNames = new HashSet<>();

    /** Assigns the final Java names for every member of every class. */
    public void registerAll(java.util.Collection<ClassFile> all) {
        for (ClassFile cf : all) {
            typeNames.add(Names.simpleName(cf.name()));
        }
        typeNames.addAll(PLATFORM_TYPES);

        // A signature that carries more than one return type cannot be expressed
        // in Java without a suffix.
        Map<String, Set<String>> returnsBySignature = new HashMap<>();
        for (ClassFile cf : all) {
            for (ClassFile.Member m : cf.methods) {
                if (m.name() == null || m.desc() == null) {
                    continue;
                }
                String sig = m.name() + paramSignature(m.desc());
                returnsBySignature.computeIfAbsent(sig, k -> new HashSet<>())
                        .add(returnType(m.desc()));
            }
        }

        // Build the global method table in a stable order so the result does not
        // depend on iteration order.
        Map<String, String> keys = new TreeMap<>();
        for (ClassFile cf : all) {
            for (ClassFile.Member m : cf.methods) {
                if (m.name() == null || m.desc() == null) {
                    continue;
                }
                keys.put(methodKey(m), methodKey(m));
                staticFlags.put(cf.name() + '\u0000' + m.name() + m.desc(), m.isStatic());
            }
        }
        // The base name is a pure function of the signature, so a class and its
        // supertype always agree. Uniqueness inside one class is handled later,
        // where the other members of that class are known.
        for (String key : keys.keySet()) {
            String[] parts = key.split("\\u0000", -1);
            String raw = parts[0];
            String paramSig = parts[1];
            String returnType = parts[2];
            boolean isStatic = "S".equals(parts[3]);

            String base = Names.identifier(raw);
            if (returnsBySignature.getOrDefault(raw + paramSig, Set.of()).size() > 1) {
                base = base + (isStatic ? "_S" : "_I") + returnTag(returnType);
            }
            methodNames.put(key, base);
        }

        for (ClassFile cf : all) {
            register(cf);
        }
    }

    /** Assigns the final Java names for every member of {@code cf}. */
    public void register(ClassFile cf) {
        ClassNames names = new ClassNames();
        names.usedFields.addAll(typeNames);
        for (ClassFile.Member f : cf.fields) {
            if (f.name() == null || f.desc() == null) {
                continue;
            }
            names.field.put(f.name() + ":" + f.desc(), names.uniqueField(f.name()));
        }
        // Assign the declared methods in a stable order, so that a residual clash
        // is resolved the same way no matter which class is emitted first.
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (ClassFile.Member m : cf.methods) {
            if (m.name() != null && m.desc() != null) {
                keys.add(methodKey(m));
            }
        }
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String base = methodNames.get(key);
            if (base != null && !names.methodNames.containsKey(key)) {
                names.methodNames.put(key, names.uniqueMethod(base));
            }
        }
        classes.put(cf.name(), names);
        typeNames.add(Names.simpleName(cf.name()));
    }

    /**
     * Name of a method as declared in {@code owner}.
     *
     * <p>Two different signatures can still land on one base name when their raw
     * names sanitise to the same identifier, so the declaring class resolves the
     * residual clash. That resolution is per class and therefore never changes the
     * name a supertype or interface chose for the same signature.</p>
     */
    public String method(String owner, String name, String desc) {
        boolean isStatic = isStaticMethod(owner, name, desc);
        String key = methodKey(name, desc, isStatic);
        String base = methodNames.get(key);
        if (base == null) {
            return Names.external(name);
        }
        ClassNames names = classes.get(owner);
        if (names == null) {
            return base;
        }
        String resolved = names.methodNames.get(key);
        if (resolved == null) {
            resolved = names.uniqueMethod(base);
            names.methodNames.put(key, resolved);
        }
        return resolved;
    }

    private static String methodKey(ClassFile.Member m) {
        boolean isStatic = (m.access & ClassFile.ACC_STATIC) != 0;
        return m.name() + '\u0000' + paramSignature(m.desc()) + '\u0000'
                + returnType(m.desc()) + '\u0000' + (isStatic ? "S" : "I");
    }

    private static String methodKey(String name, String desc, boolean isStatic) {
        return name + '\u0000' + paramSignature(desc) + '\u0000'
                + returnType(desc) + '\u0000' + (isStatic ? "S" : "I");
    }

    /** {@code (I)V -> (I)}: the part Java overloads on. */
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

    /**
     * Staticness is part of the method key, so it has to be known when looking a
     * name up. It is recorded during {@link #registerAll} for application classes.
     */
    private final Map<String, Boolean> staticFlags = new HashMap<>();

    private boolean isStaticMethod(String owner, String name, String desc) {
        Boolean known = staticFlags.get(owner + '\u0000' + name + desc);
        if (known != null) {
            return known;
        }
        // Unknown owner: an instance method is the common case, and a wrong guess
        // only matters for return-type-overloaded signatures.
        return false;
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
        final Map<String, String> methodNames = new HashMap<>();
        final Set<String> usedFields = new HashSet<>();
        final Set<String> usedMethods = new HashSet<>();

        String uniqueField(String raw) {
            String base = Names.identifier(raw);
            String candidate = base;
            int n = 2;
            while (!usedFields.add(candidate)) {
                candidate = base + "_f" + n++;
            }
            return candidate;
        }

        String uniqueMethod(String base) {
            String candidate = base;
            int n = 2;
            while (!usedMethods.add(candidate)) {
                candidate = base + "_m" + n++;
            }
            return candidate;
        }
    }
}
