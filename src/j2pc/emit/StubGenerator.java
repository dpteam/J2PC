package j2pc.emit;

import j2pc.Options;
import j2pc.bytecode.Decoder;
import j2pc.bytecode.Instruction;
import j2pc.bytecode.O;
import j2pc.classfile.ClassFile;
import j2pc.classfile.ConstantPool;
import j2pc.classfile.Descriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
/**
 * Generates stubs for the types an application references but does not ship.
 *
 * <p>Many suites are compiled against vendor or optional APIs and then packaged
 * without them — a 3D engine that is only used by one game mode, a handset
 * specific sound API, a location provider. The recompiler cannot invent those
 * implementations, but it can emit a <em>signature-compatible</em> stand-in, which
 * separates "the translation is wrong" from "this API is missing" and lets the
 * rest of the application be compiled and inspected.</p>
 *
 * <p>A stub is derived from the references themselves: exactly the fields,
 * methods and constructors that the application actually touches, with the same
 * descriptors. Every body throws {@link UnsupportedOperationException}, so a game
 * that reaches one fails loudly at that point instead of failing to build.</p>
 *
 * <p>Interfaces are emitted with {@code default} methods, so an implementing class
 * does not have to provide them.</p>
 */
public final class StubGenerator {

    /** Package the generated stubs live in when their original name is unknown. */
    public static final String STUB_PACKAGE = "j2pc.stubs";

    private final Options opts;
    private final TypeMapper types;
    private final Set<String> known;

    /** Missing types, in discovery order. */
    private final Map<String, StubType> stubs = new LinkedHashMap<>();

    /**
     * Members referenced on classes that <em>are</em> present but do not declare
     * them. Old packagers occasionally drop a member record, and the reference
     * survives; a stub member keeps the class usable.
     */
    private final Map<String, StubType> memberStubs = new LinkedHashMap<>();

    private Map<String, ClassFile> appByName = new HashMap<>();

    private StubGenerator(Options opts, TypeMapper types, Set<String> known) {
        this.opts = opts;
        this.types = types;
        this.known = known;
    }

    /**
     * Scans the application and returns one source file per missing type.
     *
     * @param app    the classes of the application
     * @param known  internal names that are available at compile time
     */
    public static Map<String, String> generate(java.util.Collection<ClassFile> app,
                                               TypeMapper types, Options opts, Set<String> known) {
        return plan(app, types, opts, known).emitAll();
    }

    /**
     * Scans without emitting, so the caller can adjust the type mapper first —
     * a stub for a default-package type has to be relocated exactly like the
     * application's own classes, and that decision affects the emitted source.
     */
    public static StubGenerator plan(java.util.Collection<ClassFile> app, TypeMapper types,
                                     Options opts, Set<String> known) {
        StubGenerator gen = new StubGenerator(opts, types, known);
        for (ClassFile cf : app) {
            gen.appByName.put(cf.name(), cf);
        }
        gen.scan(app);
        return gen;
    }

    /** Members that a present class does not declare, keyed by owner. */
    public Map<String, StubType> memberStubs() {
        return memberStubs;
    }

    /**
     * Ready-to-write declarations for the missing members of each class.
     *
     * <p>Rendered here so the caller does not need to know how a stub member is
     * spelled. Names come from the registry, exactly like a declared member, so a
     * stub can never collide with a real one.</p>
     */
    public Map<String, List<String>> memberDeclarations(NameRegistry registry) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, StubType> e : memberStubs.entrySet()) {
            String owner = e.getKey();
            List<String> lines = new ArrayList<>();
            for (StubMember f : e.getValue().fields.values()) {
                lines.add((f.isStatic ? "public static " : "public ")
                        + fieldType(f.desc) + " " + registry.field(owner, f.name, f.desc) + ";");
            }
            Set<String> emitted = new LinkedHashSet<>();
            for (StubMember m : e.getValue().methods.values()) {
                String sig = methodSignature(m, null, registry.method(owner, m.name, m.desc));
                if (!emitted.add(eraseKey(m))) {
                    continue;
                }
                lines.add(sig + " {");
                lines.add("    throw new UnsupportedOperationException("
                        + "\"J2PC stub: member missing from the archive\");");
                lines.add("}");
            }
            out.put(owner, lines);
        }
        return out;
    }

    /** Internal names of the types that need a stub. */
    public Set<String> stubNames() {
        return new LinkedHashSet<>(stubs.keySet());
    }

    /** Cache of "is this class available at compile time" answers. */
    private final Map<String, Boolean> available = new HashMap<>();

    /**
     * True when the generated source can refer to this type.
     *
     * <p>An application class is available because it is emitted; anything else is
     * available when the class loader can find it, which covers the JDK and the
     * emulation layer. Using the loader instead of a hand-written list means a
     * class is never stubbed just because the list forgot it.</p>
     */
    private boolean isAvailable(String internalName) {
        if (known.contains(internalName)) {
            return true;
        }
        Boolean cached = available.get(internalName);
        if (cached != null) {
            return cached;
        }
        boolean found;
        try {
            Class.forName(internalName.replace('/', '.'), false,
                    StubGenerator.class.getClassLoader());
            found = true;
        } catch (Throwable t) {
            found = false;
        }
        available.put(internalName, found);
        return found;
    }

    // ------------------------------------------------------------------- scan

    private void scan(java.util.Collection<ClassFile> app) {
        for (ClassFile cf : app) {
            // Supertypes and interfaces tell us whether a missing type is a class
            // or an interface, which changes the emitted declaration.
            noteType(cf.superName());
            for (String itf : cf.interfaceNames()) {
                StubType t = noteType(itf);
                if (t != null) {
                    t.isInterface = true;
                }
            }

            ConstantPool cp = cf.cp;
            for (ClassFile.Member f : cf.fields) {
                noteDescriptor(f.desc());
            }
            for (ClassFile.Member m : cf.methods) {
                noteDescriptor(m.desc());
            }
            for (int i = 1; i < cp.size(); i++) {
                switch (cp.tag(i)) {
                    case ConstantPool.CLASS:
                        noteType(cp.className(i));
                        break;
                    case ConstantPool.FIELDREF:
                        noteDescriptor(cp.refType(i));
                        noteFieldDeclared(cp.refOwner(i), cp.refName(i), cp.refType(i));
                        break;
                    case ConstantPool.METHODREF:
                        noteDescriptor(cp.refType(i));
                        noteMethodDeclared(cp.refOwner(i), cp.refName(i), cp.refType(i));
                        break;
                    case ConstantPool.INTERFACE_METHODREF: {
                        noteDescriptor(cp.refType(i));
                        StubType t = noteType(cp.refOwner(i));
                        if (t != null) {
                            t.isInterface = true;
                        }
                        noteMethodDeclared(cp.refOwner(i), cp.refName(i), cp.refType(i));
                        break;
                    }
                    default:
                        break;
                }
            }

            // Instructions carry the staticness and the exception handler types,
            // neither of which the constant pool records.
            for (ClassFile.Member m : cf.methods) {
                ClassFile.Code code = m.code();
                if (code != null) {
                    scanCode(cp, code);
                }
            }
        }
    }

    private void scanCode(ConstantPool cp, ClassFile.Code code) {
        for (ClassFile.Handler h : code.handlers) {
            if (h.catchType != 0) {
                StubType t = noteType(cp.className(h.catchType));
                if (t != null) {
                    t.isThrowable = true;
                }
            }
        }
        Instruction[] insns;
        try {
            insns = Decoder.decode(code.code);
        } catch (Exception e) {
            return;
        }
        for (Instruction ins : insns) {
            switch (ins.op) {
                case O.GETSTATIC:
                case O.PUTSTATIC:
                    noteField(cp.refOwner(ins.operand(0)), cp.refName(ins.operand(0)),
                            cp.refType(ins.operand(0)), true);
                    break;
                case O.GETFIELD:
                case O.PUTFIELD:
                    noteField(cp.refOwner(ins.operand(0)), cp.refName(ins.operand(0)),
                            cp.refType(ins.operand(0)), false);
                    break;
                case O.INVOKESTATIC:
                    noteMethod(cp.refOwner(ins.operand(0)), cp.refName(ins.operand(0)),
                            cp.refType(ins.operand(0)), true, false);
                    break;
                case O.INVOKEVIRTUAL:
                case O.INVOKESPECIAL:
                    noteMethod(cp.refOwner(ins.operand(0)), cp.refName(ins.operand(0)),
                            cp.refType(ins.operand(0)), false, false);
                    break;
                case O.INVOKEINTERFACE: {
                    StubType t = noteType(cp.refOwner(ins.operand(0)));
                    if (t != null) {
                        t.isInterface = true;
                    }
                    noteMethod(cp.refOwner(ins.operand(0)), cp.refName(ins.operand(0)),
                            cp.refType(ins.operand(0)), false, true);
                    break;
                }
                case O.NEW:
                    noteType(cp.className(ins.operand(0)));
                    break;
                case O.CHECKCAST:
                case O.INSTANCEOF:
                case O.ANEWARRAY:
                case O.MULTIANEWARRAY:
                    noteType(cp.className(ins.operand(0)));
                    break;
                default:
                    break;
            }
        }
    }

    /** Records every type mentioned by a field or method descriptor. */
    private void noteDescriptor(String desc) {
        if (desc == null) {
            return;
        }
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) {
                    return;
                }
                noteType(desc.substring(i + 1, end));
                i = end + 1;
            } else if (c == '[') {
                i++;
            } else {
                i++;
            }
        }
    }

    /** Records a referenced type, returning its stub when it is missing. */
    private StubType noteType(String internalName) {
        if (internalName == null || internalName.isEmpty() || internalName.startsWith("[")) {
            return null;
        }
        if (isAvailable(internalName)) {
            return null;
        }
        // The JDK's own packages belong to a module; a source file cannot add to
        // them, so those references stay unresolved rather than producing a
        // duplicate that javac rejects.
        if (internalName.startsWith("java/") || internalName.startsWith("jdk/")
                || internalName.startsWith("sun/") || internalName.startsWith("javax/swing/")
                || internalName.startsWith("javax/imageio/")) {
            return null;
        }
        StubType t = stubs.get(internalName);
        if (t == null) {
            t = new StubType(internalName);
            stubs.put(internalName, t);
        }
        return t;
    }
    private void noteField(String owner, String name, String desc, boolean isStatic) {
        if (owner == null || name == null || desc == null) {
            return;
        }
        if (appByName.containsKey(owner)) {
            noteMissingMember(owner, name, desc, isStatic, false);
            return;
        }
        StubType t = noteType(owner);
        if (t == null) {
            return;
        }
        StubMember m = t.fields.get(name + ":" + desc);
        if (m == null) {
            m = new StubMember(name, desc, isStatic);
            t.fields.put(name + ":" + desc, m);
        }
        if (isStatic) {
            m.seenStatic = true;
        } else {
            m.seenInstance = true;
        }
        m.isStatic = m.seenStatic && !m.seenInstance;
    }

    /** Records a member that a present class does not declare. */
    private void noteMissingMember(String owner, String name, String desc, boolean isStatic,
                                   boolean isMethod) {
        // The member may be declared anywhere up the chain, including a platform
        // superclass. Emitting a stub for an inherited method would turn it into an
        // override — and overriding a final platform method does not compile.
        if (declaresInChain(owner, name, desc, isMethod)) {
            return;
        }
        StubType t = memberStubs.get(owner);
        if (t == null) {
            t = new StubType(owner);
            memberStubs.put(owner, t);
        }
        Map<String, StubMember> target = isMethod ? t.methods : t.fields;
        String key = isMethod ? name + desc : name + ":" + desc;
        StubMember m = target.get(key);
        if (m == null) {
            m = new StubMember(name, desc, isStatic);
            target.put(key, m);
        }
        if (isStatic) {
            m.seenStatic = true;
        } else {
            m.seenInstance = true;
        }
        m.isStatic = m.seenStatic && !m.seenInstance;
    }

    /** True when the member is declared in {@code owner} or any of its supertypes. */
    private boolean declaresInChain(String owner, String name, String desc, boolean isMethod) {
        String current = owner;
        int guard = 0;
        while (current != null && guard++ < 64) {
            ClassFile cf = appByName.get(current);
            if (cf == null) {
                return platformDeclares(current, name, desc, isMethod);
            }
            if (isMethod) {
                for (ClassFile.Member m : cf.methods) {
                    if (name.equals(m.name()) && desc.equals(m.desc())) {
                        return true;
                    }
                }
            } else {
                for (ClassFile.Member f : cf.fields) {
                    if (name.equals(f.name()) && desc.equals(f.desc())) {
                        return true;
                    }
                }
            }
            current = cf.superName();
            if (current == null) {
                return false;
            }
        }
        return false;
    }

    /** Asks reflection whether a platform class or its ancestors declare the member. */
    private boolean platformDeclares(String internal, String name, String desc, boolean isMethod) {
        Class<?> c;
        try {
            c = Class.forName(internal.replace('/', '.'), false,
                    StubGenerator.class.getClassLoader());
        } catch (Throwable t) {
            return false;
        }
        while (c != null) {
            if (isMethod) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && methodDescriptor(m).equals(desc)) {
                        return true;
                    }
                }
            } else {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(name) && fieldDescriptor(f.getType()).equals(desc)) {
                        return true;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static String methodDescriptor(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(fieldDescriptor(p));
        }
        sb.append(')').append(fieldDescriptor(m.getReturnType()));
        return sb.toString();
    }

    private static String fieldDescriptor(Class<?> type) {
        if (type == void.class) {
            return "V";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type == byte.class) {
            return "B";
        }
        if (type == char.class) {
            return "C";
        }
        if (type == short.class) {
            return "S";
        }
        if (type == int.class) {
            return "I";
        }
        if (type == long.class) {
            return "J";
        }
        if (type == float.class) {
            return "F";
        }
        if (type == double.class) {
            return "D";
        }
        if (type.isArray()) {
            return "[" + fieldDescriptor(type.getComponentType());
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    /**
     * Records a member discovered through the constant pool, where staticness is
     * not encoded. The instruction scan decides that later; this only makes sure
     * the member exists.
     */
    private void noteFieldDeclared(String owner, String name, String desc) {
        StubType t = noteType(owner);
        if (t == null || name == null || desc == null) {
            return;
        }
        t.fields.putIfAbsent(name + ":" + desc, StubMember.discovered(name, desc));
    }

    private void noteMethodDeclared(String owner, String name, String desc) {
        StubType t = noteType(owner);
        if (t == null || name == null || desc == null) {
            return;
        }
        t.methods.putIfAbsent(name + desc, StubMember.discovered(name, desc));
    }

    private void noteMethod(String owner, String name, String desc, boolean isStatic,
                            boolean ifaceCall) {
        if (owner == null || name == null || desc == null) {
            return;
        }
        if (appByName.containsKey(owner)) {
            noteMissingMember(owner, name, desc, isStatic, true);
            return;
        }
        StubType t = noteType(owner);
        if (t == null) {
            return;
        }
        if (ifaceCall) {
            t.isInterface = true;
        }
        StubMember m = t.methods.get(name + desc);
        if (m == null) {
            m = new StubMember(name, desc, isStatic);
            t.methods.put(name + desc, m);
        }
        // An interface call or a non-static call proves it is an instance method;
        // otherwise a static call site makes it static. Recording both facts keeps
        // the inference stable regardless of the order the call sites are seen in.
        if (ifaceCall || !isStatic) {
            m.seenInstance = true;
        } else {
            m.seenStatic = true;
        }
        m.isStatic = m.seenStatic && !m.seenInstance;
    }

    // ------------------------------------------------------------------- emit

    public Map<String, String> emitAll() {        Map<String, String> out = new LinkedHashMap<>();
        for (StubType t : stubs.values()) {
            out.put(t.internalName, emit(t));
        }
        return out;
    }

    private String emit(StubType t) {
        SourceWriter out = new SourceWriter(opts.indentUnit);
        // The stub has to land on the name the application actually refers to, which
        // may have been relocated or renamed by the type mapper.
        String emittedName = types.internal(t.internalName);
        String pkg = Names.packageName(emittedName);
        String simple = Names.simpleName(emittedName);

        out.line("// Generated by J2PC: signature-compatible stub.");
        out.line("// " + t.internalName + " is referenced by the application but is not part of");
        out.line("// the archive and not provided by the platform. Only the members the");
        out.line("// application actually uses are declared; every body throws.");
        out.blank();
        if (!pkg.isEmpty()) {
            out.line("package " + pkg + ";");
            out.blank();
        }

        if (t.isInterface) {
            out.line("public interface " + simple + " {");
            out.indent();
            Set<String> emitted = new LinkedHashSet<>();
            for (StubMember f : t.fields.values()) {
                out.line("int " + fieldName(f.name) + " = 0;");
            }
            for (StubMember m : t.methods.values()) {
                String sig = methodSignature(m, simple);
                if (!emitted.add(eraseKey(m))) {
                    continue;
                }
                out.line(sig + " {");
                out.indent();
                out.line(body(Descriptor.returnType(m.desc)));
                out.outdent();
                out.line("}");
            }
            out.outdent();
            out.line("}");
            return out.toString();
        }

        String parent = t.isThrowable ? "java.lang.Exception" : "java.lang.Object";
        out.line("public class " + simple + " extends " + parent + " {");
        out.indent();
        for (StubMember f : t.fields.values()) {
            out.line((f.isStatic ? "public static " : "public ")
                    + fieldType(f.desc) + " " + fieldName(f.name) + ";");
        }
        Set<String> emitted = new LinkedHashSet<>();
        for (StubMember m : t.methods.values()) {
            if ("<init>".equals(m.name)) {
                StringBuilder sig = new StringBuilder("public ").append(simple).append('(');
                appendParams(sig, m.desc);
                sig.append(") {");
                if (!emitted.add(sig.toString())) {
                    continue;
                }
                out.line(sig.toString());
                out.indent();
                out.line("throw new UnsupportedOperationException(\"J2PC stub: "
                        + t.internalName + " is not implemented\");");
                out.outdent();
                out.line("}");
                continue;
            }
            String sig = methodSignature(m, null);
            if (!emitted.add(eraseKey(m))) {
                continue;
            }
            out.line(sig + " {");
            out.indent();
            out.line(body(Descriptor.returnType(m.desc)));
            out.outdent();
            out.line("}");
        }
        if (t.methods.isEmpty() && t.fields.isEmpty()) {
            out.line("// no members referenced");
        }
        out.outdent();
        out.line("}");
        return out.toString();
    }

    /**
     * Deduplication key for a stub method: name plus parameter list.
     *
     * <p>Java has no return-type overloading, and every stub body throws, so one
     * method per parameter list is enough — and the only thing that compiles.</p>
     */
    private static String eraseKey(StubMember m) {
        StringBuilder sb = new StringBuilder(m.name).append('(');
        for (String a : Descriptor.args(m.desc)) {
            if (Descriptor.isPrimitive(a)) {
                sb.append(a);
            } else if (a.startsWith("[")) {
                // Array types stay distinct: byte[] and short[] are different
                // parameters and must remain separate overloads.
                sb.append(a);
            } else {
                sb.append('L');
            }
        }
        return sb.append(')').toString();
    }

    /** Full declaration of a stub method, without the body. */
    private String methodSignature(StubMember m, String constructorOf) {
        return methodSignature(m, constructorOf, methodName(m.name));
    }

    private String methodSignature(StubMember m, String constructorOf, String name) {
        String ret = Descriptor.returnType(m.desc);
        StringBuilder sig = new StringBuilder("public ");
        if (constructorOf != null) {
            sig.append("default ");
        } else if (m.isStatic) {
            sig.append("static ");
        }
        sig.append(returnType(ret)).append(' ').append(name).append('(');
        appendParams(sig, m.desc);
        sig.append(')');
        return sig.toString();
    }

    /**
     * Type used for a stub parameter.
     *
     * <p>A parameter whose type is itself missing becomes {@code Object}: the real
     * relationship between two missing types is unknowable (an m3g
     * {@code TriangleStripArray} is an {@code IndexBuffer}, but nothing in the
     * archive says so), and widening makes every call site compile. Primitive and
     * available types keep their exact type.</p>
     */
    private String paramType(String desc) {
        if (desc.length() == 1 && Descriptor.isPrimitive(desc)) {
            return Descriptor.javaType(desc);
        }
        if (desc.startsWith("L") && desc.endsWith(";")) {
            String internal = desc.substring(1, desc.length() - 1);
            return stubs.containsKey(internal) ? "java.lang.Object" : types.ref(desc);
        }
        return types.ref(desc);
    }

    /** Return types stay exact: call sites rely on the declared type. */
    private String returnType(String desc) {
        if ("V".equals(desc)) {
            return "void";
        }
        return types.ref(desc);
    }

    /** Field types follow the same rule as parameters. */
    private String fieldType(String desc) {
        if (desc.length() == 1 && Descriptor.isPrimitive(desc)) {
            return Descriptor.javaType(desc);
        }
        if (desc.startsWith("L") && desc.endsWith(";")) {
            String internal = desc.substring(1, desc.length() - 1);
            return stubs.containsKey(internal) ? "java.lang.Object" : types.ref(desc);
        }
        if (desc.startsWith("[")) {
            String component = desc.substring(1);
            if (component.startsWith("L") && component.endsWith(";")) {
                String internal = component.substring(1, component.length() - 1);
                if (stubs.containsKey(internal)) {
                    return "java.lang.Object[]";
                }
            }
        }
        return types.ref(desc);
    }

    private void appendParams(StringBuilder sb, String desc) {
        String[] args = Descriptor.args(desc);
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(paramType(args[i])).append(" arg").append(i);
        }
    }

    private static String body(String ret) {
        return "throw new UnsupportedOperationException(\"J2PC stub: not implemented\");";
    }

    private static String fieldName(String raw) {
        return Names.identifier(raw);
    }

    private static String methodName(String raw) {
        return Names.identifier(raw);
    }

    // ------------------------------------------------------------------ model

    /** Missing type or member, exposed so callers can allocate names for it. */
    public static final class StubType {
        final String internalName;
        final Map<String, StubMember> fields = new TreeMap<>();
        final Map<String, StubMember> methods = new TreeMap<>();
        boolean isInterface;
        boolean isThrowable;

        StubType(String internalName) {
            this.internalName = internalName;
        }

        /** Fields referenced on this type. */
        public java.util.Collection<StubMember> fieldList() {
            return fields.values();
        }

        /** Methods referenced on this type. */
        public java.util.Collection<StubMember> methodList() {
            return methods.values();
        }
    }

    /** One referenced member. */
    public static final class StubMember {
        public final String name;
        public final String desc;
        boolean isStatic;
        boolean seenStatic;
        boolean seenInstance;

        /** Member proven static or instance by a call site. */
        StubMember(String name, String desc, boolean isStatic) {
            this.name = name;
            this.desc = desc;
            this.isStatic = isStatic;
            this.seenStatic = isStatic;
            this.seenInstance = !isStatic;
        }

        /**
         * Member seen only in the constant pool, where staticness is not encoded.
         * Both flags stay clear until an instruction proves one of them.
         */
        static StubMember discovered(String name, String desc) {
            StubMember m = new StubMember(name, desc, false);
            m.seenStatic = false;
            m.seenInstance = false;
            return m;
        }

        /** True when a static call site proves this is a static member. */
        public boolean isStatic() {
            return isStatic;
        }
    }
}
