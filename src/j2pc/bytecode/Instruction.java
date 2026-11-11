package j2pc.bytecode;

/** One decoded bytecode instruction. */
public final class Instruction {

    public final int pc;
    public final int op;

    /** Raw operand bytes widened to int (see {@link Decoder} for the layout). */
    public final int[] operands;

    /** For tableswitch / lookupswitch: default target (absolute pc). */
    public int defaultTarget;

    /** For tableswitch: low key; lookupswitch: unused. */
    public int switchLow;

    /** Case keys (tableswitch: implicit range). */
    public int[] switchKeys = new int[0];

    /** Absolute jump targets matching {@link #switchKeys}. */
    public int[] switchTargets = new int[0];

    /** Set when the opcode was encoded through the {@code wide} prefix. */
    public boolean wide;

    public Instruction(int pc, int op, int[] operands) {
        this.pc = pc;
        this.op = op;
        this.operands = operands;
    }

    public int operand(int i) {
        return i < operands.length ? operands[i] : 0;
    }

    public String mnemonic() {
        return O.name(op);
    }

    /** Absolute branch target for single-target branches. */
    public int branchTarget() {
        return pc + operand(0);
    }

    public boolean isBranch() {
        return (op >= O.IFEQ && op <= O.JSR) || op == O.IFNULL || op == O.IFNONNULL
                || op == O.GOTO_W || op == O.JSR_W;
    }

    public boolean isUnconditionalTransfer() {
        return op == O.GOTO || op == O.GOTO_W || op == O.JSR || op == O.JSR_W || op == O.RET
                || isReturn() || op == O.ATHROW || op == O.TABLESWITCH || op == O.LOOKUPSWITCH;
    }

    public boolean isReturn() {
        return op >= O.IRETURN && op <= O.RETURN;
    }

    public boolean isInvoke() {
        return op >= O.INVOKEVIRTUAL && op <= O.INVOKEINTERFACE;
    }

    @Override
    public String toString() {
        return pc + ": " + mnemonic();
    }
}
