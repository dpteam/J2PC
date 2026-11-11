package j2pc.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Reads a {@code .class} file into a {@link ClassFile}. */
public final class ClassReader {

    private ClassReader() {
    }

    public static ClassFile read(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("not a class file (magic 0x" + Integer.toHexString(magic) + ")");
        }
        // Class file layout: magic, minor, major, constant_pool, access, ...
        int minor = in.readUnsignedShort();
        int major = in.readUnsignedShort();
        ConstantPool cp = readConstantPool(in);
        ClassFile cf = new ClassFile(cp);
        cf.minor = minor;
        cf.major = major;
        cf.access = in.readUnsignedShort();
        cf.thisClass = in.readUnsignedShort();
        cf.superClass = in.readUnsignedShort();
        int nIf = in.readUnsignedShort();
        cf.interfaces = new int[nIf];
        for (int i = 0; i < nIf; i++) {
            cf.interfaces[i] = in.readUnsignedShort();
        }
        cf.fields = readMembers(in, cp);
        cf.methods = readMembers(in, cp);
        cf.attributes = ClassFile.readAttrs(in, cp);
        return cf;
    }

    private static ConstantPool readConstantPool(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        ConstantPool cp = new ConstantPool(count);
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case ConstantPool.UTF8:
                    cp.set(i, tag, in.readUTF());
                    break;
                case ConstantPool.INTEGER:
                    cp.set(i, tag, in.readInt());
                    break;
                case ConstantPool.FLOAT:
                    cp.set(i, tag, in.readFloat());
                    break;
                case ConstantPool.LONG:
                    cp.set(i, tag, in.readLong());
                    i++;
                    break;
                case ConstantPool.DOUBLE:
                    cp.set(i, tag, in.readDouble());
                    i++;
                    break;
                case ConstantPool.CLASS:
                case ConstantPool.STRING:
                case ConstantPool.METHOD_TYPE:
                case ConstantPool.MODULE:
                case ConstantPool.PACKAGE:
                    cp.set(i, tag, in.readUnsignedShort());
                    break;
                case ConstantPool.FIELDREF:
                case ConstantPool.METHODREF:
                case ConstantPool.INTERFACE_METHODREF:
                case ConstantPool.NAME_AND_TYPE:
                case ConstantPool.DYNAMIC:
                case ConstantPool.INVOKE_DYNAMIC:
                    cp.set(i, tag, new int[]{in.readUnsignedShort(), in.readUnsignedShort()});
                    break;
                case ConstantPool.METHOD_HANDLE:
                    cp.set(i, tag, new int[]{in.readUnsignedByte(), in.readUnsignedShort()});
                    break;
                default:
                    throw new IOException("unknown constant pool tag " + tag + " at index " + i);
            }
        }
        return cp;
    }

    private static ClassFile.Member[] readMembers(DataInputStream in, ConstantPool cp) throws IOException {
        int n = in.readUnsignedShort();
        ClassFile.Member[] out = new ClassFile.Member[n];
        for (int i = 0; i < n; i++) {
            int access = in.readUnsignedShort();
            int nameIdx = in.readUnsignedShort();
            int descIdx = in.readUnsignedShort();
            ClassFile.Attr[] attrs = ClassFile.readAttrs(in, cp);
            out[i] = new ClassFile.Member(cp, access, nameIdx, descIdx, attrs);
        }
        return out;
    }
}
