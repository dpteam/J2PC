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
    private final Map<String, String> superOf = new HashMap<>();
    private final Set<String> typeNames = new HashSet<>();
    /** Every class of the archive, by internal name; used to walk hierarchies. */
    private final Map<String, ClassFile> byName = new HashMap<>();

    /** Assigns the final Java names for every member of every class. */
    public void registerAll(java.util.Collection<ClassFile> all) {
        for (ClassFile cf : all) {
            byName.put(cf.name(), cf);
            typeNames.add(Names.simpleName(cf.name()));
            if (cf.superName() != null) {
                superOf.put(cf.name(), cf.superName());
            }
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
            registerWithAncestors(cf, new HashSet<>());
        }
    }

    /** Registers {@code cf} after its superclass and interfaces. */
    private void registerWithAncestors(ClassFile cf, Set<String> path) {
        if (classes.containsKey(cf.name()) || !path.add(cf.name())) {
            return;
        }
        String parent = cf.superName();
        ClassFile parentFile = parent == null ? null : byName.get(parent);
        if (parentFile != null) {
            registerWithAncestors(parentFile, path);
        }
        // Interfaces must be registered first for the same reason as superclasses:
        // an implementing class has to see the name the interface chose for the
        // method it implements.
        for (String itf : cf.interfaceNames()) {
            ClassFile itfFile = byName.get(itf);
            if (itfFile != null) {
                registerWithAncestors(itfFile, path);
            }
        }
        register(cf);
    }

    /**
     * Allocates a name for a member that the archive record of {@code owner} is
     * missing.
     *
     * <p>The name has to go through the registry like any other, or it can collide
     * with a real member whose raw name was suffixed. Recording it here also makes
     * every reference to that member resolve to the same name.</p>
     */
    public void declareMissingMember(String owner, String name, String desc, boolean isMethod,
                                     boolean isStatic) {
        ClassNames names = classes.get(owner);
        if (names == null) {
            return;
        }
        if (isMethod) {
            String key = methodKey(name, desc, isStatic);
            if (!names.methodNames.containsKey(key)) {
                String base = methodNames.get(key);
                names.methodNames.put(key, names.uniqueMethod(
                        base == null ? Names.external(name) : base, paramSignature(desc)));
            }
        } else {
            String key = name + ":" + desc;
            if (!names.field.containsKey(key)) {
                names.field.put(key, names.uniqueField(name));
            }
        }
    }

    /** Assigns the final Java names for every member of {@code cf}. */
    public void register(ClassFile cf) {
        ClassNames names = new ClassNames();
        names.usedFields.addAll(typeNames);
        // Field names must be unique across the whole hierarchy: bytecode tells
        // `b:I` and `b:Ld;` apart, Java resolves `this.b` to the most derived
        // declaration and would silently read the wrong field.
        java.util.Set<String> inherited = inheritedFieldNames(cf);
        names.usedFields.addAll(inherited);
        for (ClassFile.Member f : cf.fields) {
            if (f.name() == null || f.desc() == null) {
                continue;
            }
            names.field.put(f.name() + ":" + f.desc(), names.uniqueField(f.name()));
        }

        // A method that overrides or implements an inherited one must keep the
        // inherited name, otherwise javac reports "does not override". Names are
        // therefore reserved for the whole inherited set first, and only genuinely
        // new signatures are disambiguated.
        Map<String, String> inheritedMethods = inheritedMethodNames(cf);
        for (Map.Entry<String, String> e : inheritedMethods.entrySet()) {
            // Reserve the signature, not the bare name: a different parameter list
            // is a different method and may reuse the name.
            names.usedMethods.add(e.getValue() + paramSigOfKey(e.getKey()));
        }
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (ClassFile.Member m : cf.methods) {
            if (m.name() != null && m.desc() != null) {
                keys.add(methodKey(m));
            }
        }
        java.util.Collections.sort(keys);
        for (String key : keys) {
            if (names.methodNames.containsKey(key)) {
                continue;
            }
            String inheritedName = inheritedMethods.get(key);
            if (inheritedName != null) {
                names.methodNames.put(key, inheritedName);
                continue;
            }
            String base = methodNames.get(key);
            if (base != null) {
                names.methodNames.put(key, names.uniqueMethod(base, paramSigOfKey(key)));
            }
        }
        classes.put(cf.name(), names);
        typeNames.add(Names.simpleName(cf.name()));
    }

    /** Method names visible through the superclass and interface chain. */
    private Map<String, String> inheritedMethodNames(ClassFile cf) {
        Map<String, String> out = new HashMap<>();
        collectInherited(cf.superName(), out, 0);
        for (String itf : cf.interfaceNames()) {
            collectInherited(itf, out, 0);
        }
        return out;
    }

    private void collectInherited(String internal, Map<String, String> out, int depth) {
        if (internal == null || depth > 32) {
            return;
        }
        ClassNames names = classes.get(internal);
        if (names == null) {
            return;
        }
        for (Map.Entry<String, String> e : names.methodNames.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
        }
        String parent = superOf.get(internal);
        if (parent != null) {
            collectInherited(parent, out, depth + 1);
        }
    }

    /**
     * The class file of {@code internal}, when it is part of the archive.
     *
     * <p>Lets a caller walk a hierarchy it does not own: the emitter needs it to
     * know whether a superclass constructor forces a {@code throws} clause.</p>
     */
    public ClassFile classFile(String internal) {
        return internal == null ? null : byName.get(internal);
    }

    /**
     * Name of a method as declared in {@code owner}.
     *
     * <p>Two different signatures can still land on one base name when their raw
     * names sanitise to the same identifier, so the declaring class resolves the
     * residual clash. That resolution is per class and therefore never changes the
     * name a supertype or interface chose for the same signature.</p>
     *
     * <p>Methods of classes outside the application keep their original name: the
     * platform is never renamed, and a suffix would break the call.</p>
     */
    public String method(String owner, String name, String desc) {
        if (owner == null || !classes.containsKey(owner)) {
            return Names.external(name);
        }
        // Staticness is part of the key, and the call site may name a subclass
        // while the method is declared further up. Both variants are tried before
        // giving up, so an inherited static method keeps its name.
        String[] keys = {
                methodKey(name, desc, isStaticMethod(owner, name, desc)),
                methodKey(name, desc, true),
                methodKey(name, desc, false),
        };
        for (String key : keys) {
            String found = lookupInHierarchy(owner, key);
            if (found != null) {
                return found;
            }
        }
        return Names.external(name);
    }

    /** Finds the assigned name of {@code key} in {@code owner} or its ancestors. */
    private String lookupInHierarchy(String owner, String key) {
        String current = owner;
        int guard = 0;
        while (current != null && guard++ < 64) {
            ClassNames names = classes.get(current);
            if (names == null) {
                return null;
            }
            String resolved = names.methodNames.get(key);
            if (resolved != null) {
                return resolved;
            }
            current = superOf.get(current);
        }
        return null;
    }

    /**
     * Field names visible through the superclass chain and the interfaces.
     *
     * <p>Both matter: an interface field is inherited just like a superclass
     * field, and a class that declares its own field with the same name shadows
     * two unrelated ones — which Java reports as an ambiguous reference.</p>
     */
    private java.util.Set<String> inheritedFieldNames(ClassFile cf) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String current = superOf.get(cf.name());
        int guard = 0;
        while (current != null && guard++ < 64) {
            ClassNames names = classes.get(current);
            if (names == null) {
                break;
            }
            out.addAll(names.field.values());
            current = superOf.get(current);
        }
        collectInterfaceFieldNames(cf, out, 0);
        return out;
    }

    /** Adds the field names of every implemented interface, recursively. */
    private void collectInterfaceFieldNames(ClassFile cf, java.util.Set<String> out, int depth) {
        if (cf == null || depth > 16) {
            return;
        }
        for (String itf : cf.interfaceNames()) {
            ClassNames names = classes.get(itf);
            if (names != null) {
                out.addAll(names.field.values());
            }
            ClassFile itfFile = byName.get(itf);
            if (itfFile != null) {
                collectInterfaceFieldNames(itfFile, out, depth + 1);
            }
        }
        String parent = cf.superName();
        ClassFile parentFile = parent == null ? null : byName.get(parent);
        if (parentFile != null) {
            collectInterfaceFieldNames(parentFile, out, depth + 1);
        }
    }

    /**
     * Field names that a class inherits from both a superclass and an interface.
     *
     * <p>Java resolves {@code this.x} by name, and when two unrelated ancestors
     * contribute an {@code x} the reference is ambiguous — even though the bytecode
     * said exactly which one it meant. The emitter asks this to qualify the
     * receiver with a cast in that case.</p>
     */
    public boolean ambiguousField(String className, String fieldName) {
        ClassNames names = classes.get(className);
        if (names == null) {
            return false;
        }
        if (names.ambiguousFields == null) {
            ClassFile cf = byName.get(className);
            names.ambiguousFields = cf == null
                    ? java.util.Set.of()
                    : computeAmbiguousFields(cf);
        }
        return names.ambiguousFields.contains(fieldName);
    }

    private java.util.Set<String> computeAmbiguousFields(ClassFile cf) {
        java.util.Set<String> fromSuper = new java.util.HashSet<>();
        String current = superOf.get(cf.name());
        int guard = 0;
        while (current != null && guard++ < 64) {
            ClassNames names = classes.get(current);
            if (names == null) {
                break;
            }
            fromSuper.addAll(names.field.values());
            current = superOf.get(current);
        }
        java.util.Set<String> fromInterfaces = new java.util.HashSet<>();
        collectInterfaceFieldNames(cf, fromInterfaces, 0);
        fromSuper.retainAll(fromInterfaces);
        return fromSuper;
    }

    private static String methodKey(ClassFile.Member m) {        boolean isStatic = (m.access & ClassFile.ACC_STATIC) != 0;
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

    /** Parameter part of a {@link #methodKey} key. */
    private static String paramSigOfKey(String key) {
        String[] parts = key.split("\u0000", -1);
        return parts.length > 1 ? parts[1] : "";
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
        String key = name + ":" + desc;
        // Field resolution walks up the hierarchy exactly like the JVM: bytecode may
        // reference a field through a subclass even though the declaring class is a
        // superclass, and only the declaring class knows the final name.
        String current = owner;
        int guard = 0;
        while (current != null && guard++ < 64) {
            ClassNames names = classes.get(current);
            if (names == null) {
                // The chain left the application: nothing above was renamed.
                return Names.external(name);
            }
            String mapped = names.field.get(key);
            if (mapped != null) {
                return mapped;
            }
            current = superOf.get(current);
        }
        return Names.external(name);
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
        /**
         * Taken method signatures, as {@code name(paramSig)}. Only methods with the
         * same parameter list can clash: Java happily holds {@code read()} and
         * {@code read(byte[],int,int)} in one hierarchy, and reserving the bare
         * name would rename one of them for no reason — which breaks overriding a
         * platform method such as {@code InputStream.read()}.
         */
        final Set<String> usedMethods = new HashSet<>();
        /** Field names inherited from both a superclass and an interface. */
        Set<String> ambiguousFields;

        String uniqueField(String raw) {
            String base = Names.identifier(raw);
            String candidate = base;
            int n = 2;
            while (!usedFields.add(candidate)) {
                candidate = base + "_f" + n++;
            }
            return candidate;
        }

        String uniqueMethod(String base, String paramSig) {
            String candidate = base;
            int n = 2;
            while (!usedMethods.add(candidate + paramSig)) {
                candidate = base + "_m" + n++;
            }
            return candidate;
        }
    }
}
