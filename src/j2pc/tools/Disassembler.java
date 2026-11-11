package j2pc.tools;

import j2pc.bytecode.Decoder;
import j2pc.bytecode.Instruction;
import j2pc.classfile.ClassFile;
import j2pc.classfile.ClassReader;
import j2pc.classfile.ConstantPool;

/** Human readable bytecode dump, used by {@code j2pc --disasm}. */
public final class Disassembler {

    private final ClassFile cf;
    private final ConstantPool cp;

    public Disassembler(ClassFile cf) {
        this.cf = cf;
        this.cp = cf.cp;
    }

    public String dump(String methodFilter) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(cf.name())
                .append(" extends ").append(cf.superName())
                .append(" (v").append(cf.major).append('.').append(cf.minor).append(")\n");
        for (ClassFile.Member m : cf.methods) {
            if (methodFilter != null && !m.name().equals(methodFilter)) {
                continue;
            }
            sb.append('\n').append(m.name()).append(m.desc()).append(":\n");
            ClassFile.Code code = m.code();
            if (code == null) {
                sb.append("  <no code>\n");
                continue;
            }
            sb.append("  maxStack=").append(code.maxStack)
                    .append(" maxLocals=").append(code.maxLocals)
                    .append(" length=").append(code.code.length).append('\n');
            try {
                Instruction[] insns = Decoder.decode(code.code);
                for (Instruction i : insns) {
                    sb.append(String.format("  %5d: %-16s %s%n", i.pc, i.mnemonic(), operands(i)));
                }
            } catch (Exception e) {
                java.util.List<Instruction> partial = new java.util.ArrayList<>();
                try {
                    Decoder.decodeInto(code.code, partial);
                } catch (Exception ignored) {
                    // partial list is what we want
                }
                for (Instruction i : partial) {
                    sb.append(String.format("  %5d: %-16s %s%n", i.pc, i.mnemonic(), operands(i)));
                }
                sb.append("  !! ").append(e.getMessage()).append('\n');
                sb.append("  raw: ").append(hex(code.code)).append('\n');
            }
        }
        return sb.toString();
    }

    private String operands(Instruction i) {
        StringBuilder sb = new StringBuilder();
        switch (i.op) {
            case j2pc.bytecode.O.LDC:
            case j2pc.bytecode.O.LDC_W:
            case j2pc.bytecode.O.LDC2_W:
                sb.append('#').append(i.operand(0)).append("  // ").append(constant(i.operand(0)));
                break;
            case j2pc.bytecode.O.GETSTATIC:
            case j2pc.bytecode.O.PUTSTATIC:
            case j2pc.bytecode.O.GETFIELD:
            case j2pc.bytecode.O.PUTFIELD:
                sb.append('#').append(i.operand(0)).append("  // ")
                        .append(cp.refOwner(i.operand(0))).append('.')
                        .append(cp.refName(i.operand(0))).append(':').append(cp.refType(i.operand(0)));
                break;
            case j2pc.bytecode.O.INVOKEVIRTUAL:
            case j2pc.bytecode.O.INVOKESPECIAL:
            case j2pc.bytecode.O.INVOKESTATIC:
            case j2pc.bytecode.O.INVOKEINTERFACE:
                sb.append('#').append(i.operand(0)).append("  // ")
                        .append(cp.refOwner(i.operand(0))).append('.')
                        .append(cp.refName(i.operand(0))).append(cp.refType(i.operand(0)));
                break;
            case j2pc.bytecode.O.NEW:
            case j2pc.bytecode.O.ANEWARRAY:
            case j2pc.bytecode.O.CHECKCAST:
            case j2pc.bytecode.O.INSTANCEOF:
            case j2pc.bytecode.O.MULTIANEWARRAY:
                sb.append('#').append(i.operand(0)).append("  // ").append(cp.className(i.operand(0)));
                break;
            case j2pc.bytecode.O.TABLESWITCH:
                sb.append("default=").append(i.defaultTarget);
                for (int k = 0; k < i.switchKeys.length; k++) {
                    sb.append(' ').append(i.switchKeys[k]).append("->").append(i.switchTargets[k]);
                }
                break;
            case j2pc.bytecode.O.LOOKUPSWITCH:
                sb.append("default=").append(i.defaultTarget);
                for (int k = 0; k < i.switchKeys.length; k++) {
                    sb.append(' ').append(i.switchKeys[k]).append("->").append(i.switchTargets[k]);
                }
                break;
            default:
                for (int o : i.operands) {
                    sb.append(o).append(' ');
                }
                if (i.isBranch()) {
                    sb.append("-> ").append(i.branchTarget());
                }
                break;
        }
        return sb.toString().trim();
    }

    private String constant(int idx) {
        switch (cp.tag(idx)) {
            case ConstantPool.STRING:
                return '"' + cp.stringValue(idx) + '"';
            case ConstantPool.INTEGER:
                return Integer.toString(cp.intValue(idx));
            case ConstantPool.FLOAT:
                return cp.floatValue(idx) + "f";
            case ConstantPool.LONG:
                return cp.longValue(idx) + "L";
            case ConstantPool.DOUBLE:
                return cp.doubleValue(idx) + "d";
            case ConstantPool.CLASS:
                return "class " + cp.className(idx);
            default:
                return "?tag" + cp.tag(idx);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x ", x));
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        ClassFile cf = ClassReader.read(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0])));
        System.out.print(new Disassembler(cf).dump(args.length > 1 ? args[1] : null));
    }
}
