package j2pc.analyze;

/** Operand-stack and local-variable frame used by the analyzer. */
public final class Frame {

    public final SlotType[] locals;
    public final SlotType[] stack;
    public int sp;

    public Frame(int maxLocals, int maxStack) {
        this.locals = new SlotType[Math.max(1, maxLocals)];
        this.stack = new SlotType[Math.max(1, maxStack)];
        java.util.Arrays.fill(this.locals, SlotType.UNSET_T);
        java.util.Arrays.fill(this.stack, SlotType.UNSET_T);
    }

    public Frame copy() {
        Frame f = new Frame(locals.length, stack.length);
        System.arraycopy(locals, 0, f.locals, 0, locals.length);
        System.arraycopy(stack, 0, f.stack, 0, stack.length);
        f.sp = sp;
        return f;
    }

    public void push(SlotType t) {
        if (sp >= stack.length) {
            throw new IllegalStateException("stack overflow in analysis (maxStack=" + stack.length + ")");
        }
        stack[sp++] = t;
    }

    public SlotType pop() {
        if (sp <= 0) {
            throw new IllegalStateException("stack underflow in analysis");
        }
        SlotType t = stack[--sp];
        stack[sp] = SlotType.UNSET_T;
        return t == null ? SlotType.UNSET_T : t;
    }

    public SlotType peek(int depth) {
        int i = sp - 1 - depth;
        if (i < 0) {
            throw new IllegalStateException("stack underflow in analysis");
        }
        return stack[i];
    }

    /** Merges {@code other} into this frame; returns true when something changed. */
    public boolean mergeWith(Frame other) {
        boolean changed = false;
        for (int i = 0; i < locals.length; i++) {
            SlotType m = locals[i].merge(other.locals[i]);
            if (!m.equals(locals[i])) {
                locals[i] = m;
                changed = true;
            }
        }
        for (int i = 0; i < stack.length; i++) {
            SlotType m = stack[i].merge(other.stack[i]);
            if (!m.equals(stack[i])) {
                stack[i] = m;
                changed = true;
            }
        }
        if (sp != other.sp) {
            sp = Math.max(sp, other.sp);
            changed = true;
        }
        return changed;
    }
}
