package j2pc.emit;

import j2pc.Options;
import j2pc.classfile.ClassFile;
import j2pc.classfile.Descriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders an enum class as a real Java {@code enum}.
 *
 * <p>Bytecode represents an enum as an ordinary class extending
 * {@code java.lang.Enum} with a {@code $VALUES} array, a {@code <clinit>} that
 * instantiates every constant and synthetic {@code values()}/{@code valueOf()}
 * methods. Java source forbids extending {@code Enum} directly, so that shape has
 * to be translated back into an {@code enum} declaration.</p>
 *
 * <p>Constants are recovered from the {@code ACC_ENUM} fields, which the compiler
 * emits in declaration order. Constructors are left to javac, and the synthetic
 * members are dropped.</p>
 */
final class EnumEmitter {

    private final ClassFile cf;
    private final Options opts;
    private final SourceWriter out;
    private final NameRegistry names;
    private final TypeMapper types;

    EnumEmitter(ClassFile cf, Options opts, SourceWriter out, NameRegistry names,
                TypeMapper types) {
        this.cf = cf;
        this.opts = opts;
        this.out = out;
        this.names = names;
        this.types = types;
    }

    /** True when the class is an enum as the JVM represents it. */
    static boolean isEnum(ClassFile cf) {
        return (cf.access & ClassFile.ACC_ENUM) != 0
                && "java/lang/Enum".equals(cf.superName());
    }

    void emit() {
        List<ClassFile.Member> constants = new ArrayList<>();
        for (ClassFile.Member f : cf.fields) {
            if ((f.access & ClassFile.ACC_ENUM) != 0 && f.name() != null) {
                constants.add(f);
            }
        }

        String simple = types.simpleName(cf.name());
        StringBuilder decl = new StringBuilder("public enum ").append(simple);
        String[] itfs = cf.interfaceNames();
        if (itfs.length > 0) {
            decl.append(" implements ");
            for (int i = 0; i < itfs.length; i++) {
                if (i > 0) {
                    decl.append(", ");
                }
                decl.append(typeRef(itfs[i]));
            }
        }
        out.line(decl.append(" {").toString());
        out.indent();

        if (constants.isEmpty()) {
            out.line("// no constants recovered");
            out.line(";");
        } else {
            for (int i = 0; i < constants.size(); i++) {
                String suffix = i + 1 < constants.size() ? "," : ";";
                out.line(names.field(cf.name(), constants.get(i).name(), constants.get(i).desc())
                        + suffix);
            }
        }

        for (ClassFile.Member m : cf.methods) {
            if (isSyntheticEnumMember(m)) {
                continue;
            }
            if (m.name() == null || m.desc() == null) {
                continue;
            }
            if (opts.includeSynthetic && m.isSynthetic()) {
                // synthetic bridge methods are still skipped: they cannot be written in source
                continue;
            }
            out.blank();
            String code = MethodEmitter.emit(cf, m, opts, names, types);
            for (String line : code.split("\n", -1)) {
                if (line.isEmpty()) {
                    out.blank();
                } else {
                    out.line(line);
                }
            }
        }

        out.outdent();
        out.line("}");
    }

    /**
     * Members that javac generates itself, or that cannot be expressed in an
     * {@code enum} declaration.
     */
    private static boolean isSyntheticEnumMember(ClassFile.Member m) {
        String name = m.name();
        if (name == null) {
            return true;
        }
        if ("<clinit>".equals(name) || "<init>".equals(name)) {
            return true;
        }
        if ("values".equals(name) && m.desc().startsWith("()[L")) {
            return true;
        }
        if ("valueOf".equals(name) && m.desc().startsWith("(Ljava/lang/String;)")) {
            return true;
        }
        return "$VALUES".equals(name) || m.isSynthetic();
    }

    private String typeRef(String internalName) {
        return types.refInternal(internalName);
    }
}
