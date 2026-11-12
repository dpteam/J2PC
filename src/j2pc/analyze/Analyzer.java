package j2pc.analyze;

import j2pc.bytecode.Decoder;
import j2pc.bytecode.Instruction;
import j2pc.bytecode.O;
import j2pc.classfile.ClassFile;
import j2pc.classfile.ConstantPool;
import j2pc.classfile.Descriptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abstract interpretation over the operand stack.
 *
 * <p>Produces, for every reachable program point, the merged local-variable
 * and operand-stack types. Those types are what the source emitter uses to
 * declare variables, so the generated Java is fully typed instead of being
 * a boxed {@code Object[]} soup.</p>
 */
public final class Analyzer {

    private final ClassFile cf;
    private final ClassFile.Member method;
    private final ClassFile.Code code;
    private final ConstantPool cp;

    private Instruction[] insns;
    private Map<Integer, Instruction> byPc;
    private Map<Integer, Frame> frames;
    private final Set<Integer> leaders = new TreeSet<>();
    private final Set<Integer> retTargets = new HashSet<>();

    private Analyzer(ClassFile cf, ClassFile.Member method, ClassFile.Code code) {
        this.cf = cf;
        this.method = method;
        this.code = code;
        this.cp = cf.cp;
    }

    public static Analysis analyze(ClassFile cf, ClassFile.Member method, ClassFile.Code code) {
        return new Analyzer(cf, method, code).run();
    }

    private Analysis run() {
        try {
            insns = Decoder.decode(code.code);
        } catch (Exception e) {
            return failed("decode: " + e.getMessage());
        }
        if (insns.length == 0) {
            return failed("empty code");
        }
        byPc = new HashMap<>();
        for (int i = 0; i < insns.length; i++) {
            byPc.put(insns[i].pc, insns[i]);
        }
        computeLeaders();
        frames = new HashMap<>();
        try {
            propagate();
        } catch (RuntimeException e) {
            return failed("analysis: " + e.getMessage());
        }
        computeDecls();
        return new Analysis(insns, frames, leaders.stream().mapToInt(Integer::intValue).toArray(),
                declLocals, declStack, code.maxLocals, code.maxStack, true, null, code.code.length);
    }

    private SlotType[] declLocals;
    private SlotType[] declStack;

    private Analysis failed(String reason) {
        return new Analysis(insns == null ? new Instruction[0] : insns, new HashMap<>(),
                new int[0], new SlotType[code.maxLocals], new SlotType[code.maxStack],
                code.maxLocals, code.maxStack, false, reason, code.code.length);
    }

    // --------------------------------------------------------------- leaders

    private void computeLeaders() {
        leaders.add(0);
        for (int i = 0; i < insns.length; i++) {
            Instruction ins = insns[i];
            int next = i + 1 < insns.length ? insns[i + 1].pc : code.code.length;
            if (ins.isBranch()) {
                if (ins.op == O.TABLESWITCH || ins.op == O.LOOKUPSWITCH) {
                    leaders.add(ins.defaultTarget);
                    for (int t : ins.switchTargets) {
                        leaders.add(t);
                    }
                } else if (ins.op == O.JSR || ins.op == O.JSR_W) {
                    leaders.add(ins.branchTarget());
                    retTargets.add(next);
                } else {
                    leaders.add(ins.branchTarget());
                }
            }
            if (ins.isUnconditionalTransfer() && next < code.code.length) {
                leaders.add(next);
            }
        }
        for (ClassFile.Handler h : code.handlers) {
            leaders.add(h.startPc);
            leaders.add(h.endPc);
            leaders.add(h.handlerPc);
        }
        leaders.removeIf(pc -> !byPc.containsKey(pc) && pc != code.code.length);
    }

    // ----------------------------------------------------------- propagation

    private void propagate() {
        Frame start = new Frame(code.maxLocals, code.maxStack);
        initLocals(start);
        frames.put(0, start);
        Deque<Integer> work = new ArrayDeque<>();
        work.add(0);

        while (!work.isEmpty()) {
            int pc = work.poll();
            Instruction ins = byPc.get(pc);
            if (ins == null) {
                continue;
            }
            Frame f = frames.get(pc).copy();
            int[] succ = step(ins, f);

            for (int target : succ) {
                if (target < 0 || target > code.code.length) {
                    continue;
                }
                if (!byPc.containsKey(target)) {
                    throw new IllegalStateException("branch to " + target + " which is not an instruction");
                }
                Frame copy = f.copy();
                Frame existing = frames.get(target);
                if (existing == null) {
                    frames.put(target, copy);
                    work.add(target);
                } else if (existing.mergeWith(copy)) {
                    work.add(target);
                }
            }

            for (ClassFile.Handler h : code.handlers) {
                if (pc >= h.startPc && pc < h.endPc) {
                    Frame hf = f.copy();
                    hf.sp = 0;
                    Arrays.fill(hf.stack, SlotType.UNSET_T);
                    hf.push(h.catchType == 0 ? SlotType.THROWABLE_T
                            : SlotType.ref(cp.className(h.catchType)));
                    Frame existing = frames.get(h.handlerPc);
                    if (existing == null) {
                        frames.put(h.handlerPc, hf);
                        work.add(h.handlerPc);
                    } else if (existing.mergeWith(hf)) {
                        work.add(h.handlerPc);
                    }
                }
            }
        }
    }

    private void initLocals(Frame f) {
        int slot = 0;
        if (!method.isStatic()) {
            f.locals[slot++] = SlotType.ref(cf.name());
        }
        for (String arg : Descriptor.args(method.desc())) {
            f.locals[slot] = SlotType.ofDescriptor(arg);
            slot += Descriptor.slots(arg);
            if (slot > 0 && slot <= f.locals.length) {
                // category-2 values keep the trailing slot unusable
            }
        }
    }

    // ------------------------------------------------------------ simulation

    private int[] step(Instruction ins, Frame f) {
        int next = nextPc(ins);
        switch (ins.op) {
            case O.NOP:
                return flow(next);

            case O.ACONST_NULL:
                f.push(SlotType.NULL_T);
                return flow(next);
            case O.ICONST_M1:
            case O.ICONST_0:
            case O.ICONST_1:
            case O.ICONST_2:
            case O.ICONST_3:
            case O.ICONST_4:
            case O.ICONST_5:
            case O.BIPUSH:
            case O.SIPUSH:
                f.push(SlotType.INT_T);
                return flow(next);
            case O.LCONST_0:
            case O.LCONST_1:
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.FCONST_0:
            case O.FCONST_1:
            case O.FCONST_2:
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.DCONST_0:
            case O.DCONST_1:
                f.push(SlotType.DOUBLE_T);
                return flow(next);

            case O.LDC:
            case O.LDC_W: {
                int idx = ins.operand(0);
                f.push(constantType(idx));
                return flow(next);
            }
            case O.LDC2_W: {
                int idx = ins.operand(0);
                int tag = cp.tag(idx);
                f.push(tag == ConstantPool.LONG ? SlotType.LONG_T : SlotType.DOUBLE_T);
                return flow(next);
            }

            case O.ILOAD:
                f.push(load(f, ins.operand(0), SlotType.INT_T));
                return flow(next);
            case O.LLOAD:
                f.push(load(f, ins.operand(0), SlotType.LONG_T));
                return flow(next);
            case O.FLOAD:
                f.push(load(f, ins.operand(0), SlotType.FLOAT_T));
                return flow(next);
            case O.DLOAD:
                f.push(load(f, ins.operand(0), SlotType.DOUBLE_T));
                return flow(next);
            case O.ALOAD:
                f.push(load(f, ins.operand(0), null));
                return flow(next);
            case O.ILOAD_0: case O.ILOAD_1: case O.ILOAD_2: case O.ILOAD_3:
                f.push(load(f, ins.op - O.ILOAD_0, SlotType.INT_T));
                return flow(next);
            case O.LLOAD_0: case O.LLOAD_1: case O.LLOAD_2: case O.LLOAD_3:
                f.push(load(f, ins.op - O.LLOAD_0, SlotType.LONG_T));
                return flow(next);
            case O.FLOAD_0: case O.FLOAD_1: case O.FLOAD_2: case O.FLOAD_3:
                f.push(load(f, ins.op - O.FLOAD_0, SlotType.FLOAT_T));
                return flow(next);
            case O.DLOAD_0: case O.DLOAD_1: case O.DLOAD_2: case O.DLOAD_3:
                f.push(load(f, ins.op - O.DLOAD_0, SlotType.DOUBLE_T));
                return flow(next);
            case O.ALOAD_0: case O.ALOAD_1: case O.ALOAD_2: case O.ALOAD_3:
                f.push(load(f, ins.op - O.ALOAD_0, null));
                return flow(next);

            case O.ISTORE: case O.LSTORE: case O.FSTORE: case O.DSTORE: case O.ASTORE:
                store(f, ins.operand(0), f.pop());
                return flow(next);
            case O.ISTORE_0: case O.ISTORE_1: case O.ISTORE_2: case O.ISTORE_3:
                store(f, ins.op - O.ISTORE_0, f.pop());
                return flow(next);
            case O.LSTORE_0: case O.LSTORE_1: case O.LSTORE_2: case O.LSTORE_3:
                store(f, ins.op - O.LSTORE_0, f.pop());
                return flow(next);
            case O.FSTORE_0: case O.FSTORE_1: case O.FSTORE_2: case O.FSTORE_3:
                store(f, ins.op - O.FSTORE_0, f.pop());
                return flow(next);
            case O.DSTORE_0: case O.DSTORE_1: case O.DSTORE_2: case O.DSTORE_3:
                store(f, ins.op - O.DSTORE_0, f.pop());
                return flow(next);
            case O.ASTORE_0: case O.ASTORE_1: case O.ASTORE_2: case O.ASTORE_3:
                store(f, ins.op - O.ASTORE_0, f.pop());
                return flow(next);

            case O.IALOAD:
                arrayLoad(f, SlotType.INT_T);
                return flow(next);
            case O.LALOAD:
                arrayLoad(f, SlotType.LONG_T);
                return flow(next);
            case O.FALOAD:
                arrayLoad(f, SlotType.FLOAT_T);
                return flow(next);
            case O.DALOAD:
                arrayLoad(f, SlotType.DOUBLE_T);
                return flow(next);
            case O.AALOAD: {
                f.pop();
                SlotType arr = f.pop();
                f.push(arrayElement(arr));
                return flow(next);
            }
            case O.BALOAD: case O.CALOAD: case O.SALOAD:
                arrayLoad(f, SlotType.INT_T);
                return flow(next);

            case O.IASTORE: case O.LASTORE: case O.FASTORE: case O.DASTORE:
            case O.AASTORE: case O.BASTORE: case O.CASTORE: case O.SASTORE:
                f.pop();
                f.pop();
                f.pop();
                return flow(next);

            case O.POP:
                f.pop();
                return flow(next);
            case O.POP2: {
                SlotType a = f.pop();
                if (!a.isCategory2()) {
                    f.pop();
                }
                return flow(next);
            }
            case O.DUP: {
                SlotType a = f.pop();
                f.push(a);
                f.push(a);
                return flow(next);
            }
            case O.DUP_X1: {
                SlotType a = f.pop();
                SlotType b = f.pop();
                f.push(a);
                f.push(b);
                f.push(a);
                return flow(next);
            }
            case O.DUP_X2: {
                SlotType a = f.pop();
                SlotType b = f.pop();
                if (b.isCategory2()) {
                    f.push(a);
                    f.push(b);
                    f.push(a);
                } else {
                    SlotType c = f.pop();
                    f.push(a);
                    f.push(c);
                    f.push(b);
                    f.push(a);
                }
                return flow(next);
            }
            case O.DUP2: {
                SlotType a = f.pop();
                if (a.isCategory2()) {
                    f.push(a);
                    f.push(a);
                } else {
                    SlotType b = f.pop();
                    f.push(b);
                    f.push(a);
                    f.push(b);
                    f.push(a);
                }
                return flow(next);
            }
            case O.DUP2_X1: {
                SlotType a = f.pop();
                if (a.isCategory2()) {
                    SlotType b = f.pop();
                    f.push(a);
                    f.push(b);
                    f.push(a);
                } else {
                    SlotType b = f.pop();
                    SlotType c = f.pop();
                    f.push(b);
                    f.push(a);
                    f.push(c);
                    f.push(b);
                    f.push(a);
                }
                return flow(next);
            }
            case O.DUP2_X2: {
                SlotType a = f.pop();
                if (a.isCategory2()) {
                    SlotType b = f.pop();
                    if (b.isCategory2()) {
                        f.push(a);
                        f.push(b);
                        f.push(a);
                    } else {
                        SlotType c = f.pop();
                        f.push(a);
                        f.push(c);
                        f.push(b);
                        f.push(a);
                    }
                } else {
                    SlotType b = f.pop();
                    SlotType c = f.pop();
                    if (c.isCategory2()) {
                        f.push(b);
                        f.push(a);
                        f.push(c);
                        f.push(b);
                        f.push(a);
                    } else {
                        SlotType d = f.pop();
                        f.push(b);
                        f.push(a);
                        f.push(d);
                        f.push(c);
                        f.push(b);
                        f.push(a);
                    }
                }
                return flow(next);
            }
            case O.SWAP: {
                SlotType a = f.pop();
                SlotType b = f.pop();
                f.push(a);
                f.push(b);
                return flow(next);
            }

            case O.IADD: case O.ISUB: case O.IMUL: case O.IDIV: case O.IREM:
            case O.ISHL: case O.ISHR: case O.IUSHR: case O.IAND: case O.IOR: case O.IXOR:
                f.pop();
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.LADD: case O.LSUB: case O.LMUL: case O.LDIV: case O.LREM:
            case O.LAND: case O.LOR: case O.LXOR:
                f.pop();
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.LSHL: case O.LSHR: case O.LUSHR:
                f.pop();
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.FADD: case O.FSUB: case O.FMUL: case O.FDIV: case O.FREM:
                f.pop();
                f.pop();
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.DADD: case O.DSUB: case O.DMUL: case O.DDIV: case O.DREM:
                f.pop();
                f.pop();
                f.push(SlotType.DOUBLE_T);
                return flow(next);
            case O.INEG:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.LNEG:
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.FNEG:
                f.pop();
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.DNEG:
                f.pop();
                f.push(SlotType.DOUBLE_T);
                return flow(next);

            case O.IINC: {
                int slot = ins.operand(0);
                if (slot < f.locals.length) {
                    f.locals[slot] = f.locals[slot].merge(SlotType.INT_T);
                }
                return flow(next);
            }

            case O.I2L:
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.I2F:
                f.pop();
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.I2D:
                f.pop();
                f.push(SlotType.DOUBLE_T);
                return flow(next);
            case O.L2I:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.L2F:
                f.pop();
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.L2D:
                f.pop();
                f.push(SlotType.DOUBLE_T);
                return flow(next);
            case O.F2I:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.F2L:
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.F2D:
                f.pop();
                f.push(SlotType.DOUBLE_T);
                return flow(next);
            case O.D2I:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.D2L:
                f.pop();
                f.push(SlotType.LONG_T);
                return flow(next);
            case O.D2F:
                f.pop();
                f.push(SlotType.FLOAT_T);
                return flow(next);
            case O.I2B: case O.I2C: case O.I2S:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);

            case O.LCMP:
                f.pop();
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.FCMPL: case O.FCMPG:
                f.pop();
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.DCMPL: case O.DCMPG:
                f.pop();
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);

            case O.IFEQ: case O.IFNE: case O.IFLT: case O.IFGE: case O.IFGT: case O.IFLE:
            case O.IFNULL: case O.IFNONNULL:
                f.pop();
                return branch(next, ins.branchTarget());
            case O.IF_ICMPEQ: case O.IF_ICMPNE: case O.IF_ICMPLT: case O.IF_ICMPGE:
            case O.IF_ICMPGT: case O.IF_ICMPLE: case O.IF_ACMPEQ: case O.IF_ACMPNE:
                f.pop();
                f.pop();
                return branch(next, ins.branchTarget());
            case O.GOTO:
            case O.GOTO_W:
                return new int[]{ins.branchTarget()};
            case O.JSR:
            case O.JSR_W:
                f.push(SlotType.RET_T);
                return new int[]{ins.branchTarget()};
            case O.RET: {
                int[] targets = new int[retTargets.size()];
                int i = 0;
                for (int t : retTargets) {
                    targets[i++] = t;
                }
                return targets;
            }
            case O.TABLESWITCH:
            case O.LOOKUPSWITCH: {
                f.pop();
                int[] targets = new int[ins.switchTargets.length + 1];
                System.arraycopy(ins.switchTargets, 0, targets, 0, ins.switchTargets.length);
                targets[targets.length - 1] = ins.defaultTarget;
                return targets;
            }

            case O.IRETURN: case O.LRETURN: case O.FRETURN: case O.DRETURN: case O.ARETURN:
                f.pop();
                return new int[0];
            case O.RETURN:
                return new int[0];

            case O.GETSTATIC:
                f.push(descriptorType(cp.refType(ins.operand(0))));
                return flow(next);
            case O.PUTSTATIC:
                f.pop();
                return flow(next);
            case O.GETFIELD: {
                f.pop();
                f.push(descriptorType(cp.refType(ins.operand(0))));
                return flow(next);
            }
            case O.PUTFIELD:
                f.pop();
                f.pop();
                return flow(next);

            case O.INVOKEVIRTUAL:
            case O.INVOKESPECIAL:
            case O.INVOKESTATIC:
            case O.INVOKEINTERFACE: {
                String desc = cp.refType(ins.operand(0));
                if (desc == null) {
                    desc = "()V";
                }
                for (int i = 0; i < Descriptor.args(desc).length; i++) {
                    f.pop();
                }
                if (ins.op != O.INVOKESTATIC) {
                    f.pop();
                }
                String ret = Descriptor.returnType(desc);
                if (!"V".equals(ret)) {
                    f.push(descriptorType(ret));
                }
                return flow(next);
            }
            case O.INVOKEDYNAMIC: {
                String desc = cp.refType(ins.operand(0));
                if (desc == null) {
                    desc = "()V";
                }
                for (int i = 0; i < Descriptor.args(desc).length; i++) {
                    f.pop();
                }
                String ret = Descriptor.returnType(desc);
                if (!"V".equals(ret)) {
                    f.push(descriptorType(ret));
                }
                return flow(next);
            }

            case O.NEW:
                f.push(SlotType.ref(cp.className(ins.operand(0))));
                return flow(next);
            case O.NEWARRAY: {
                f.pop();
                f.push(SlotType.ref(arrayDescForNewArray(ins.operand(0))));
                return flow(next);
            }
            case O.ANEWARRAY: {
                f.pop();
                String name = cp.className(ins.operand(0));
                f.push(SlotType.ref(name.startsWith("[") ? "[" + name : "[L" + name + ";"));
                return flow(next);
            }
            case O.MULTIANEWARRAY: {
                int dims = ins.operand(1);
                for (int i = 0; i < dims; i++) {
                    f.pop();
                }
                f.push(SlotType.ref(cp.className(ins.operand(0))));
                return flow(next);
            }
            case O.ARRAYLENGTH:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.ATHROW:
                f.pop();
                return new int[0];
            case O.CHECKCAST: {
                f.pop();
                f.push(SlotType.ref(cp.className(ins.operand(0))));
                return flow(next);
            }
            case O.INSTANCEOF:
                f.pop();
                f.push(SlotType.INT_T);
                return flow(next);
            case O.MONITORENTER:
            case O.MONITOREXIT:
                f.pop();
                return flow(next);

            default:
                throw new IllegalStateException("unhandled opcode " + ins.mnemonic() + " at " + ins.pc);
        }
    }

    private static SlotType descriptorType(String desc) {
        if (desc == null || desc.isEmpty()) {
            return SlotType.OBJECT_T;
        }
        return SlotType.ofDescriptor(desc);
    }

    private SlotType constantType(int idx) {
        switch (cp.tag(idx)) {
            case ConstantPool.INTEGER:
                return SlotType.INT_T;
            case ConstantPool.FLOAT:
                return SlotType.FLOAT_T;
            case ConstantPool.STRING:
                return SlotType.STRING_T;
            case ConstantPool.CLASS:
                return SlotType.ref("java/lang/Class");
            case ConstantPool.METHOD_TYPE:
                return SlotType.ref("java/lang/invoke/MethodType");
            case ConstantPool.METHOD_HANDLE:
                return SlotType.ref("java/lang/invoke/MethodHandle");
            case ConstantPool.LONG:
                return SlotType.LONG_T;
            case ConstantPool.DOUBLE:
                return SlotType.DOUBLE_T;
            default:
                return SlotType.OBJECT_T;
        }
    }

    private SlotType load(Frame f, int slot, SlotType expected) {
        if (slot >= f.locals.length) {
            throw new IllegalStateException("local slot " + slot + " out of range");
        }
        SlotType actual = f.locals[slot];
        if (expected == null) {
            return actual == null || actual.kind == SlotType.UNSET ? SlotType.OBJECT_T : actual;
        }
        return actual == null || actual.kind == SlotType.UNSET ? expected : actual.merge(expected);
    }

    private void store(Frame f, int slot, SlotType value) {
        if (slot >= f.locals.length) {
            throw new IllegalStateException("local slot " + slot + " out of range");
        }
        f.locals[slot] = f.locals[slot].merge(value);
    }

    private void arrayLoad(Frame f, SlotType result) {
        f.pop();
        f.pop();
        f.push(result);
    }

    private SlotType arrayElement(SlotType arr) {
        if (arr == null || arr.kind != SlotType.REF || arr.desc == null || !arr.desc.startsWith("[")) {
            return SlotType.OBJECT_T;
        }
        String elem = arr.desc.substring(1);
        if (elem.startsWith("L")) {
            return SlotType.ref(elem.substring(1, elem.length() - 1));
        }
        if (elem.startsWith("[")) {
            return SlotType.ref(elem);
        }
        return SlotType.INT_T;
    }

    private static String arrayDescForNewArray(int atype) {
        switch (atype) {
            case 4:
                return "[Z";
            case 5:
                return "[C";
            case 6:
                return "[F";
            case 7:
                return "[D";
            case 8:
                return "[B";
            case 9:
                return "[S";
            case 10:
                return "[I";
            case 11:
                return "[J";
            default:
                return "[I";
        }
    }

    private int nextPc(Instruction ins) {
        for (int i = 0; i < insns.length; i++) {
            if (insns[i] == ins) {
                return i + 1 < insns.length ? insns[i + 1].pc : code.code.length;
            }
        }
        return code.code.length;
    }

    private static int[] flow(int next) {
        return new int[]{next};
    }

    private static int[] branch(int next, int target) {
        return new int[]{next, target};
    }

    // -------------------------------------------------------------- decls

    private void computeDecls() {
        declLocals = new SlotType[code.maxLocals];
        declStack = new SlotType[code.maxStack];
        Arrays.fill(declLocals, SlotType.UNSET_T);
        Arrays.fill(declStack, SlotType.UNSET_T);
        List<Frame> all = new ArrayList<>(frames.values());
        for (Frame f : all) {
            for (int i = 0; i < declLocals.length; i++) {
                declLocals[i] = declLocals[i].merge(f.locals[i]);
            }
            for (int i = 0; i < declStack.length; i++) {
                declStack[i] = declStack[i].merge(f.stack[i]);
            }
        }
        for (int i = 0; i < declLocals.length; i++) {
            if (declLocals[i].kind == SlotType.UNSET) {
                declLocals[i] = SlotType.CONFLICT_T;
            }
        }
        for (int i = 0; i < declStack.length; i++) {
            if (declStack[i].kind == SlotType.UNSET) {
                declStack[i] = SlotType.CONFLICT_T;
            }
        }
    }
}
