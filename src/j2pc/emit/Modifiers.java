package j2pc.emit;

import j2pc.Options;
import j2pc.classfile.ClassFile;

/** Java modifier rendering from access flags. */
public final class Modifiers {

    private Modifiers() {
    }

    public static String forClass(int access, Options opts) {
        StringBuilder sb = new StringBuilder();
        if ((access & ClassFile.ACC_PUBLIC) != 0 || opts.sourceLevel >= 8) {
            sb.append("public ");
        }
        if ((access & ClassFile.ACC_ABSTRACT) != 0 && (access & ClassFile.ACC_INTERFACE) == 0) {
            sb.append("abstract ");
        }
        if ((access & ClassFile.ACC_FINAL) != 0 && (access & ClassFile.ACC_ENUM) == 0) {
            sb.append("final ");
        }
        return sb.toString().trim();
    }

    /**
     * Fields are always emitted {@code public}.
     *
     * <p>Java 11+ lets a nest access private members of its host directly, and
     * javac relies on that for inner classes. Bytecode therefore contains plain
     * {@code getfield}/{@code putfield} on private fields of another class. Once
     * the code is recompiled, that nest relationship is gone, so the visibility
     * has to be widened — otherwise the generated source would not compile.</p>
     */
    public static String forField(int access, Options opts) {
        StringBuilder sb = new StringBuilder("public ");
        if ((access & ClassFile.ACC_STATIC) != 0) {
            sb.append("static ");
        }
        if ((access & ClassFile.ACC_VOLATILE) != 0) {
            sb.append("volatile ");
        }
        if ((access & ClassFile.ACC_TRANSIENT) != 0) {
            sb.append("transient ");
        }
        return sb.toString().trim();
    }

    /**
     * Fields are never {@code final} in the output.
     *
     * <p>Bytecode assigns {@code static final} fields from {@code <clinit>}, and
     * instance finals from constructors. Since both bodies are recompiled as
     * dispatch loops, javac can no longer prove the single-assignment rule and
     * rejects the class. Dropping the modifier keeps the behaviour identical —
     * nothing outside the class could observe finality anyway.</p>
     */
    public static String fieldFinal(int access, boolean hasConstantValue) {
        return "";
    }

    public static String forMethod(int access, boolean inInterface, Options opts) {
        StringBuilder sb = new StringBuilder();
        if (inInterface) {
            boolean isStatic = (access & ClassFile.ACC_STATIC) != 0;
            boolean isAbstract = (access & ClassFile.ACC_ABSTRACT) != 0;
            if (isStatic) {
                // A static method in an interface is neither default nor abstract.
                sb.append("static ");
            } else if (!isAbstract) {
                // A concrete instance method in an interface must be declared default.
                sb.append("default ");
            }
        } else {
            // Every method is emitted public. Bytecode happily narrows access in a
            // subclass or widens it in a superclass, and it reaches private members
            // through nest access, neither of which survives a round trip through
            // source. Widening is invisible to behaviour and keeps overrides legal.
            sb.append("public ");
        }
        if ((access & ClassFile.ACC_STATIC) != 0 && !inInterface) {
            sb.append("static ");
        }
        if ((access & ClassFile.ACC_ABSTRACT) != 0 && !inInterface) {
            sb.append("abstract ");
        }
        // `final` is dropped: a final static method may not be hidden by a
        // subclass, and a final instance method may not be overridden, both of
        // which happen in bytecode that was compiled without those constraints.
        if ((access & ClassFile.ACC_SYNCHRONIZED) != 0) {
            sb.append("synchronized ");
        }
        if ((access & ClassFile.ACC_NATIVE) != 0) {
            sb.append("native ");
        }
        if ((access & ClassFile.ACC_STRICT) != 0) {
            sb.append("strictfp ");
        }
        return sb.toString().trim();
    }
}
