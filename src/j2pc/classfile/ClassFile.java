package j2pc.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Parsed representation of a single {@code .class} file. */
public final class ClassFile {

    public static final int ACC_PUBLIC = 0x0001;
    public static final int ACC_PRIVATE = 0x0002;
    public static final int ACC_PROTECTED = 0x0004;
    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_FINAL = 0x0010;
    public static final int ACC_SYNCHRONIZED = 0x0020;
    public static final int ACC_VOLATILE = 0x0040;
    public static final int ACC_TRANSIENT = 0x0080;
    public static final int ACC_NATIVE = 0x0100;
    public static final int ACC_INTERFACE = 0x0200;
    public static final int ACC_ABSTRACT = 0x0400;
    public static final int ACC_STRICT = 0x0800;
    public static final int ACC_SYNTHETIC = 0x1000;
    public static final int ACC_ANNOTATION = 0x2000;
    public static final int ACC_ENUM = 0x4000;

    public final ConstantPool cp;
    public int minor;
    public int major;
    public int access;
    public int thisClass;
    public int superClass;
    public int[] interfaces = new int[0];
    public Member[] fields = new Member[0];
    public Member[] methods = new Member[0];
    public Attr[] attributes = new Attr[0];

    public ClassFile(ConstantPool cp) {
        this.cp = cp;
    }

    public String name() {
        return cp.className(thisClass);
    }

    public String superName() {
        return superClass == 0 ? null : cp.className(superClass);
    }

    public String[] interfaceNames() {
        String[] out = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            out[i] = cp.className(interfaces[i]);
        }
        return out;
    }

    public boolean isInterface() {
        return (access & ACC_INTERFACE) != 0;
    }

    public boolean isAbstract() {
        return (access & ACC_ABSTRACT) != 0;
    }

    public String sourceFile() {
        Attr a = attr(attributes, "SourceFile");
        if (a == null || a.data.length < 2) {
            return null;
        }
        return cp.utf8(u2(a.data, 0));
    }

    public Attr attr(String name) {
        return attr(attributes, name);
    }

    /**
     * Parsed {@code BootstrapMethods} attribute.
     *
     * <p>Needed for {@code invokedynamic}: the call site names a bootstrap method
     * by index, and the static arguments of that bootstrap method carry the data
     * the recompiler needs (a string concatenation recipe, a lambda target).</p>
     */
    public BootstrapMethod[] bootstrapMethods() {
        Attr a = attr(attributes, "BootstrapMethods");
        if (a == null) {
            return new BootstrapMethod[0];
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(a.data));
            int n = in.readUnsignedShort();
            BootstrapMethod[] out = new BootstrapMethod[n];
            for (int i = 0; i < n; i++) {
                BootstrapMethod bm = new BootstrapMethod();
                bm.methodRef = in.readUnsignedShort();
                int argc = in.readUnsignedShort();
                bm.args = new int[argc];
                for (int j = 0; j < argc; j++) {
                    bm.args[j] = in.readUnsignedShort();
                }
                out[i] = bm;
            }
            return out;
        } catch (IOException e) {
            return new BootstrapMethod[0];
        }
    }

    public static final class BootstrapMethod {
        /** Constant pool index of a {@code MethodHandle}. */
        public int methodRef;
        /** Constant pool indexes of the static arguments. */
        public int[] args = new int[0];
    }

    public static Attr attr(Attr[] attrs, String name) {
        for (Attr a : attrs) {
            if (a.name.equals(name)) {
                return a;
            }
        }
        return null;
    }

    static int u2(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static int u4(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    // ---------------------------------------------------------------- members

    public static final class Member {
        public final int access;
        public final int nameIndex;
        public final int descIndex;
        public final Attr[] attributes;
        public final ConstantPool cp;

        Member(ConstantPool cp, int access, int nameIndex, int descIndex, Attr[] attributes) {
            this.cp = cp;
            this.access = access;
            this.nameIndex = nameIndex;
            this.descIndex = descIndex;
            this.attributes = attributes;
        }

        public String name() {
            return cp.utf8(nameIndex);
        }

        public String desc() {
            return cp.utf8(descIndex);
        }

        public boolean isStatic() {
            return (access & ACC_STATIC) != 0;
        }

        public boolean isAbstract() {
            return (access & ACC_ABSTRACT) != 0;
        }

        public boolean isNative() {
            return (access & ACC_NATIVE) != 0;
        }

        public boolean isSynthetic() {
            return (access & ACC_SYNTHETIC) != 0;
        }

        public Code code() {
            Attr a = attr(attributes, "Code");
            if (a == null) {
                return null;
            }
            try {
                return Code.parse(a.data, cp);
            } catch (IOException e) {
                throw new IllegalStateException("cannot parse Code of " + name() + desc(), e);
            }
        }

        /** Constant value for static final fields, or null. */
        public Object constantValue() {
            Attr a = attr(attributes, "ConstantValue");
            if (a == null || a.data.length < 2) {
                return null;
            }
            int idx = u2(a.data, 0);
            switch (cp.tag(idx)) {
                case ConstantPool.INTEGER:
                    return cp.intValue(idx);
                case ConstantPool.FLOAT:
                    return cp.floatValue(idx);
                case ConstantPool.LONG:
                    return cp.longValue(idx);
                case ConstantPool.DOUBLE:
                    return cp.doubleValue(idx);
                case ConstantPool.STRING:
                    return cp.stringValue(idx);
                default:
                    return null;
            }
        }
    }

    // ------------------------------------------------------------- attributes

    public static final class Attr {
        public final String name;
        public final byte[] data;

        Attr(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    // -------------------------------------------------------------- code blob

    public static final class Code {
        public int maxStack;
        public int maxLocals;
        public byte[] code;
        public Handler[] handlers = new Handler[0];
        public Attr[] attributes = new Attr[0];

        static Code parse(byte[] data, ConstantPool cp) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            Code c = new Code();
            c.maxStack = in.readUnsignedShort();
            c.maxLocals = in.readUnsignedShort();
            int len = in.readInt();
            if (len < 0 || len > 0x1000000) {
                throw new IOException("implausible code length " + len);
            }
            c.code = new byte[len];
            in.readFully(c.code);
            int n = in.readUnsignedShort();
            c.handlers = new Handler[n];
            for (int i = 0; i < n; i++) {
                Handler h = new Handler();
                h.startPc = in.readUnsignedShort();
                h.endPc = in.readUnsignedShort();
                h.handlerPc = in.readUnsignedShort();
                h.catchType = in.readUnsignedShort();
                c.handlers[i] = h;
            }
            c.attributes = readAttrs(in, cp);
            return c;
        }

        public int[] lineNumbers() {
            Attr a = attr(attributes, "LineNumberTable");
            if (a == null) {
                return new int[0];
            }
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(a.data));
                int n = in.readUnsignedShort();
                int[] out = new int[n * 2];
                for (int i = 0; i < n; i++) {
                    out[i * 2] = in.readUnsignedShort();
                    out[i * 2 + 1] = in.readUnsignedShort();
                }
                return out;
            } catch (IOException e) {
                return new int[0];
            }
        }

        /**
         * Returns the debug names of the method arguments (index = local slot),
         * or null when the class was compiled without debug information.
         */
        public String[] localNames(ConstantPool cp) {
            Attr a = attr(attributes, "LocalVariableTable");
            if (a == null) {
                return null;
            }
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(a.data));
                int n = in.readUnsignedShort();
                String[] names = new String[maxLocals];
                for (int i = 0; i < n; i++) {
                    int start = in.readUnsignedShort();
                    int length = in.readUnsignedShort();
                    int nameIdx = in.readUnsignedShort();
                    in.readUnsignedShort();
                    int slot = in.readUnsignedShort();
                    if (start == 0 && length > 0 && slot < names.length && names[slot] == null) {
                        names[slot] = cp.utf8(nameIdx);
                    }
                }
                return names;
            } catch (IOException e) {
                return null;
            }
        }
    }

    public static final class Handler {
        public int startPc;
        public int endPc;
        public int handlerPc;
        public int catchType;
    }

    // ------------------------------------------------------------- low-level IO

    static Attr[] readAttrs(DataInputStream in, ConstantPool cp) throws IOException {
        int n = in.readUnsignedShort();
        Attr[] out = new Attr[n];
        for (int i = 0; i < n; i++) {
            int nameIdx = in.readUnsignedShort();
            int len = in.readInt();
            if (len < 0 || len > 0x4000000) {
                throw new IOException("implausible attribute length " + len);
            }
            byte[] data = new byte[len];
            in.readFully(data);
            out[i] = new Attr(cp.utf8(nameIdx), data);
        }
        return out;
    }
}
