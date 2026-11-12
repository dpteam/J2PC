package j2pc.tools;

import j2pc.classfile.ClassFile;
import j2pc.classfile.ClassReader;
import j2pc.classfile.ConstantPool;

import java.nio.file.Files;
import java.nio.file.Path;

/** Debug helper: lists members whose name or descriptor is not a valid UTF8 entry. */
public final class Inspect {

    public static void main(String[] args) throws Exception {
        ClassFile cf = ClassReader.read(Files.readAllBytes(Path.of(args[0])));
        System.out.println("class " + cf.name() + " v" + cf.major + "." + cf.minor);
        for (ClassFile.Member f : cf.fields) {
            if (f.name() == null || f.desc() == null) {
                System.out.println("  bad field  nameIdx=" + f.nameIndex + " descIdx=" + f.descIndex
                        + " nameTag=" + cf.cp.tag(f.nameIndex) + " descTag=" + cf.cp.tag(f.descIndex));
            }
        }
        for (ClassFile.Member m : cf.methods) {
            if (m.name() == null || m.desc() == null) {
                System.out.println("  bad method nameIdx=" + m.nameIndex + " descIdx=" + m.descIndex
                        + " nameTag=" + cf.cp.tag(m.nameIndex) + " descTag=" + cf.cp.tag(m.descIndex)
                        + " name=" + m.name() + " desc=" + m.desc());
            }
        }
        System.out.println("pool size " + cf.cp.size());
        for (int i = 1; i < cf.cp.size(); i++) {
            int tag = cf.cp.tag(i);
            if (tag == ConstantPool.UTF8) {
                String s = cf.cp.utf8(i);
                if (s == null) {
                    System.out.println("  utf8 entry " + i + " is null");
                }
            }
        }
    }
}
