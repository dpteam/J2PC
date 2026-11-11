package j2pc.tools;

import j2pc.analyze.Analysis;
import j2pc.analyze.Analyzer;
import j2pc.bytecode.Instruction;
import j2pc.classfile.ClassFile;
import j2pc.classfile.ClassReader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Debug helper: prints the analysis view of one method. */
public final class Trace {

    public static void main(String[] args) throws Exception {
        ClassFile cf = ClassReader.read(Files.readAllBytes(Path.of(args[0])));
        String methodName = args[1];
        String methodDesc = args.length > 2 ? args[2] : null;
        for (ClassFile.Member m : cf.methods) {
            if (!m.name().equals(methodName)) {
                continue;
            }
            if (methodDesc != null && !m.desc().equals(methodDesc)) {
                continue;
            }
            ClassFile.Code code = m.code();
            Analysis a = Analyzer.analyze(cf, m, code);
            System.out.println("method " + m.name() + m.desc() + " ok=" + a.ok
                    + " failure=" + a.failure);
            System.out.println("leaders: " + java.util.Arrays.toString(a.blockStarts));
            for (Instruction i : a.insns) {
                System.out.printf("  %4d %-16s %s  frame=%s%n", i.pc, i.mnemonic(),
                        java.util.Arrays.toString(i.operands),
                        a.frameAt(i.pc) == null ? "-" : "sp=" + a.frameAt(i.pc).sp);
            }
        }
    }
}
