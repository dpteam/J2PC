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

    public static String forField(int access, Options opts) {
        StringBuilder sb = new StringBuilder();
        int vis = access & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED);
        if (vis == ClassFile.ACC_PRIVATE) {
            sb.append("private ");
        } else if (vis == ClassFile.ACC_PROTECTED) {
            sb.append("protected ");
        } else {
            sb.append("public ");
        }
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
     * {@code final} is only kept for static constants: an instance field that a
     * constructor assigns cannot stay final once the body is expressed as a
     * dispatch loop.
     */
    public static String fieldFinal(int access, boolean hasConstantValue) {
        return ((access & ClassFile.ACC_STATIC) != 0 && hasConstantValue) ? "final " : "";
    }

    public static String forMethod(int access, boolean inInterface, Options opts) {
        StringBuilder sb = new StringBuilder();
        int vis = access & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED);
        if (inInterface) {
            if ((access & ClassFile.ACC_STATIC) != 0 || (access & ClassFile.ACC_ABSTRACT) == 0) {
                sb.append("default ");
            }
        } else {
            if (vis == 0) {
                sb.append("public ");
            } else if (vis == ClassFile.ACC_PUBLIC) {
                sb.append("public ");
            } else if (vis == ClassFile.ACC_PRIVATE) {
                sb.append("private ");
            } else if (vis == ClassFile.ACC_PROTECTED) {
                sb.append("protected ");
            } else {
                sb.append("public ");
            }
        }
        if ((access & ClassFile.ACC_STATIC) != 0) {
            sb.append("static ");
        }
        if ((access & ClassFile.ACC_ABSTRACT) != 0 && !inInterface) {
            sb.append("abstract ");
        }
        if ((access & ClassFile.ACC_FINAL) != 0) {
            sb.append("final ");
        }
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
