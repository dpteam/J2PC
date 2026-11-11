package j2pc.bytecode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Linear sweep decoder. Produces one {@link Instruction} per opcode with
 * absolute branch targets resolved for switches.
 *
 * <p>Operand layout:</p>
 * <pre>
 *   bipush, ldc, iload..aload, istore..astore, ret, newarray, anewarray,
 *   checkcast, instanceof, new, getstatic..putfield, invokevirtual..invokestatic,
 *   invokeinterface, invokedynamic, multianewarray  -> { u1 | u2 }
 *   sipush, ldc_w, ldc2_w, iinc, goto_w, jsr_w      -> { s2 } / { u1, s1 }
 *   if*                                             -> { s2 offset }
 * </pre>
 */
public final class Decoder {

    private Decoder() {
    }

    /**
     * Decodes as many instructions as possible and reports the first failure
     * instead of throwing. Used by the disassembler and by the diagnostics in
     * the recompiler when it meets bytecode it cannot follow.
     */
    public static Result decodeLenient(byte[] code) {
        try {
            return new Result(decode(code), null, -1);
        } catch (IOException e) {
            return new Result(null, e.getMessage(), -1);
        }
    }

    public static final class Result {
        public final Instruction[] instructions;
        public final String error;

        Result(Instruction[] instructions, String error, int at) {
            this.instructions = instructions;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }
    }

    public static Instruction[] decode(byte[] code) throws IOException {
        List<Instruction> out = new ArrayList<>(code.length / 2);
        decodeInto(code, out);
        return out.toArray(new Instruction[0]);
    }

    /** Decodes into {@code out}; on failure the partially decoded prefix is kept. */
    public static void decodeInto(byte[] code, List<Instruction> out) throws IOException {
        int pc = 0;
        while (pc < code.length) {
            int op = code[pc] & 0xFF;
            int start = pc;
            pc++;
            switch (op) {
                case O.BIPUSH:
                case O.LDC:
                case O.ILOAD:
                case O.LLOAD:
                case O.FLOAD:
                case O.DLOAD:
                case O.ALOAD:
                case O.ISTORE:
                case O.LSTORE:
                case O.FSTORE:
                case O.DSTORE:
                case O.ASTORE:
                case O.RET:
                case O.NEWARRAY:
                    out.add(new Instruction(start, op, new int[]{code[pc] & 0xFF}));
                    pc += 1;
                    break;

                case O.ANEWARRAY:
                case O.CHECKCAST:
                case O.INSTANCEOF:
                case O.NEW:
                case O.GETSTATIC:
                case O.PUTSTATIC:
                case O.GETFIELD:
                case O.PUTFIELD:
                case O.INVOKEVIRTUAL:
                case O.INVOKESPECIAL:
                case O.INVOKESTATIC:
                    out.add(new Instruction(start, op, new int[]{u2(code, pc)}));
                    pc += 2;
                    break;

                case O.INVOKEINTERFACE: {
                    // u2 index, u1 count, u1 zero
                    int idx = u2(code, pc);
                    int count = code[pc + 2] & 0xFF;
                    out.add(new Instruction(start, op, new int[]{idx, count}));
                    pc += 4;
                    break;
                }

                case O.INVOKEDYNAMIC: {
                    // u2 bootstrap index, u2 zero
                    int idx = u2(code, pc);
                    out.add(new Instruction(start, op, new int[]{idx}));
                    pc += 4;
                    break;
                }

                case O.SIPUSH:
                case O.LDC_W:
                case O.LDC2_W:
                    out.add(new Instruction(start, op, new int[]{s2(code, pc)}));
                    pc += 2;
                    break;

                case O.IINC: {
                    int idx = code[pc] & 0xFF;
                    int cst = code[pc + 1];
                    out.add(new Instruction(start, op, new int[]{idx, cst}));
                    pc += 2;
                    break;
                }

                case O.MULTIANEWARRAY: {
                    int idx = u2(code, pc);
                    int dims = code[pc + 2] & 0xFF;
                    out.add(new Instruction(start, op, new int[]{idx, dims}));
                    pc += 3;
                    break;
                }

                case O.IFEQ: case O.IFNE: case O.IFLT: case O.IFGE: case O.IFGT: case O.IFLE:
                case O.IF_ICMPEQ: case O.IF_ICMPNE: case O.IF_ICMPLT: case O.IF_ICMPGE:
                case O.IF_ICMPGT: case O.IF_ICMPLE: case O.IF_ACMPEQ: case O.IF_ACMPNE:
                case O.GOTO: case O.JSR:
                case O.IFNULL: case O.IFNONNULL:
                    out.add(new Instruction(start, op, new int[]{s2(code, pc)}));
                    pc += 2;
                    break;

                case O.GOTO_W:
                case O.JSR_W:
                    out.add(new Instruction(start, op, new int[]{s4(code, pc)}));
                    pc += 4;
                    break;

                case O.TABLESWITCH: {
                    int base = start;
                    int p = (start + 4) & ~3;
                    int def = base + s4(code, p);
                    int low = s4(code, p + 4);
                    int high = s4(code, p + 8);
                    p += 12;
                    int count = high - low + 1;
                    if (count < 0 || count > 65536) {
                        throw new IOException("bad tableswitch at " + start);
                    }
                    int[] keys = new int[count];
                    int[] targets = new int[count];
                    for (int i = 0; i < count; i++) {
                        keys[i] = low + i;
                        targets[i] = base + s4(code, p);
                        p += 4;
                    }
                    Instruction ins = new Instruction(start, op, new int[0]);
                    ins.defaultTarget = def;
                    ins.switchLow = low;
                    ins.switchKeys = keys;
                    ins.switchTargets = targets;
                    out.add(ins);
                    pc = p;
                    break;
                }

                case O.LOOKUPSWITCH: {
                    int base = start;
                    int p = (start + 4) & ~3;
                    int def = base + s4(code, p);
                    int npairs = s4(code, p + 4);
                    p += 8;
                    if (npairs < 0 || npairs > 65536) {
                        throw new IOException("bad lookupswitch at " + start);
                    }
                    int[] keys = new int[npairs];
                    int[] targets = new int[npairs];
                    for (int i = 0; i < npairs; i++) {
                        keys[i] = s4(code, p);
                        targets[i] = base + s4(code, p + 4);
                        p += 8;
                    }
                    Instruction ins = new Instruction(start, op, new int[0]);
                    ins.defaultTarget = def;
                    ins.switchKeys = keys;
                    ins.switchTargets = targets;
                    out.add(ins);
                    pc = p;
                    break;
                }

                case O.WIDE: {
                    int wop = code[pc] & 0xFF;
                    pc++;
                    if (wop == O.IINC) {
                        int idx = u2(code, pc);
                        int cst = s2(code, pc + 2);
                        Instruction ins = new Instruction(start, O.IINC, new int[]{idx, cst});
                        ins.wide = true;
                        out.add(ins);
                        pc += 4;
                    } else {
                        int idx = u2(code, pc);
                        Instruction ins = new Instruction(start, wop, new int[]{idx});
                        ins.wide = true;
                        out.add(ins);
                        pc += 2;
                    }
                    break;
                }

                default:
                    if (op > 201) {
                        throw new IOException("unknown opcode " + op + " at " + start);
                    }
                    out.add(new Instruction(start, op, new int[0]));
                    break;
            }
        }
    }

    private static int u2(byte[] code, int p) {
        return ((code[p] & 0xFF) << 8) | (code[p + 1] & 0xFF);
    }

    private static int s2(byte[] code, int p) {
        return (short) u2(code, p);
    }

    private static int s4(byte[] code, int p) {
        return ((code[p] & 0xFF) << 24) | ((code[p + 1] & 0xFF) << 16)
                | ((code[p + 2] & 0xFF) << 8) | (code[p + 3] & 0xFF);
    }
}
