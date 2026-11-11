package j2pc.analyze;

import j2pc.bytecode.Instruction;

/** Result of the abstract interpretation of one method body. */
public final class Analysis {

    public final Instruction[] insns;
    public final java.util.Map<Integer, Frame> frames;
    public final int[] blockStarts;
    public final SlotType[] localDecl;
    public final SlotType[] stackDecl;
    public final int maxLocals;
    public final int maxStack;
    public final boolean ok;
    public final String failure;
    public final int codeLength;

    Analysis(Instruction[] insns, java.util.Map<Integer, Frame> frames, int[] blockStarts,
             SlotType[] localDecl, SlotType[] stackDecl, int maxLocals, int maxStack,
             boolean ok, String failure, int codeLength) {
        this.insns = insns;
        this.frames = frames;
        this.blockStarts = blockStarts;
        this.localDecl = localDecl;
        this.stackDecl = stackDecl;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
        this.ok = ok;
        this.failure = failure;
        this.codeLength = codeLength;
    }

    public Frame frameAt(int pc) {
        return frames.get(pc);
    }

    public Instruction instructionAt(int pc) {
        for (Instruction i : insns) {
            if (i.pc == pc) {
                return i;
            }
        }
        return null;
    }
}
