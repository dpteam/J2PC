package j2pc.emit;

import j2pc.Options;
import j2pc.analyze.Analysis;
import j2pc.analyze.Analyzer;
import j2pc.analyze.Frame;
import j2pc.analyze.SlotType;
import j2pc.bytecode.Instruction;
import j2pc.bytecode.O;
import j2pc.classfile.ClassFile;
import j2pc.classfile.ConstantPool;
import j2pc.classfile.Descriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates one method body into Java source.
 *
 * <p>This is a <em>static recompiler</em>: it never guesses the original
 * control flow. Every basic block of the bytecode becomes a {@code case} of a
 * dispatch loop, so the generated method keeps the exact behaviour of the
 * original even for obfuscated or hand written bytecode.</p>
 *
 * <p>Methods without branches and without exception handlers are emitted as
 * plain straight-line Java instead.</p>
 */
public final class MethodEmitter {

    /** Type families used to give a JVM local slot one Java variable per type. */
    private static final int K_INT = 0;
    private static final int K_FLOAT = 1;
    private static final int K_LONG = 2;
    private static final int K_DOUBLE = 3;
    private static final int K_REF = 4;

    private static final String[] KIND_SUFFIX = {"", "f", "l", "d", "r"};

    private static final String PC = "__pc";

    private final ClassFile cf;
    private final ClassFile.Member method;
    private final ClassFile.Code code;
    private final ConstantPool cp;
    private final Options opts;
    private final NameRegistry names;
    private final TypeMapper types;

    private final SourceWriter out = new SourceWriter("    ");

    private Instruction[] insns;
    private Map<Integer, Integer> indexOf;
    private Analysis analysis;
    private boolean flat;

    private final List<Val> stack = new ArrayList<>();
    private final Map<String, String> varNames = new HashMap<>();
    /** Local slots that currently alias an uninitialised {@code new} temporary. */
    private final Map<Integer, Val> freshLocals = new HashMap<>();    private final Set<String> declared = new HashSet<>();
    private final Map<Integer, Set<Integer>> localKinds = new HashMap<>();
    private final List<String> declarations = new ArrayList<>();

    private boolean prologueNeedsThrows;
    private boolean thisIsSlotZero;
    private boolean slotZeroStored;
    /** Set for {@code <clinit>}: {@code return} leaves a labelled block, not a method. */
    private boolean staticInitializer;
    private boolean declarationsInline;
    private boolean prologueMode;

    private int tempSeq;
    private int condSeq;
    private Map<Integer, Integer> lineAt;

    private MethodEmitter(ClassFile cf, ClassFile.Member method, Options opts, NameRegistry names,
                          TypeMapper types) {
        this.cf = cf;
        this.method = method;
        this.code = method.code();
        this.cp = cf.cp;
        this.opts = opts;
        this.names = names;
        this.types = types;
    }

    public static String emit(ClassFile cf, ClassFile.Member method, Options opts,
                                     NameRegistry names, TypeMapper types) {
        return new MethodEmitter(cf, method, opts, names, types).run();
    }

    /** Emits a {@code <clinit>} body, where {@code return} has to leave a block. */
    public static String emitStaticInitializer(ClassFile cf, ClassFile.Member clinit, Options opts,
                                               NameRegistry names, TypeMapper types) {
        MethodEmitter emitter = new MethodEmitter(cf, clinit, opts, names, types);
        emitter.staticInitializer = true;
        return emitter.run();
    }

    // ------------------------------------------------------------------ entry

    private String run() {
        if (code == null) {
            boolean iface = cf.isInterface() || (method.isAbstract() && !cf.isInterface());
            if (iface || method.isAbstract() || method.isNative()) {
                return signature() + ";";
            }
            return signature() + " {\n    throw new UnsupportedOperationException(\"no code\");\n}";
        }

        analysis = Analyzer.analyze(cf, method, code);
        if (!analysis.ok) {
            return stub("analysis: " + analysis.failure);
        }

        insns = analysis.insns;
        indexOf = new HashMap<>();
        for (int i = 0; i < insns.length; i++) {
            indexOf.put(insns[i].pc, i);
        }

        collectLocalKinds();
        flat = analysis.blockStarts.length <= 1 && code.handlers.length == 0 && !hasJsr();

        int initPc = "<init>".equals(method.name()) ? findConstructorCall() : -1;
        int loopStart = 0;
        String prologue = "";
        if (initPc >= 0) {
            int after = nextPcOf(initPc);
            loopStart = after < 0 ? initPc : after;
            // Java requires super()/this() to be the first statement of a
            // constructor, so the mandatory chain is emitted before anything
            // else, including the local declarations.
            prologueMode = true;
            java.util.List<String> prologueLines = new ArrayList<>();
            out.captureTo(prologueLines);
            for (Instruction ins : insns) {
                if (ins.pc >= loopStart) {
                    break;
                }
                if (ins.isInvoke() && !"<init>".equals(cp.refName(ins.operand(0)))) {
                    prologueNeedsThrows = true;
                }
                emitLineComment(ins.pc);
                exec(ins);
            }
            out.stopCapture();
            prologueMode = false;
            prologue = reorderSuperFirst(prologueLines);
        }

        if (flat) {
            emitFlat(loopStart);
        } else {
            emitDispatch(loopStart);
        }
        String body = out.drain();

        StringBuilder sb = new StringBuilder(signature());
        sb.append(" {\n");
        sb.append(prologue);
        for (String d : declarations) {
            sb.append(d).append('\n');
        }
        if (!declarations.isEmpty()) {
            sb.append('\n');
        }
        // Bytecode has no checked exceptions: any API call may throw one without
        // a throws clause. The wrapper keeps the generated source compilable
        // while preserving the original exception object.
        sb.append("try {\n");
        sb.append(body);
        sb.append("} catch (java.lang.Throwable __unchecked) {\n");
        sb.append("throw j2pc.rt.Sneaky.sneak(__unchecked);\n");
        sb.append("}\n");
        sb.append('}');
        return sb.toString();
    }

    /**
     * Java requires {@code super(...)} or {@code this(...)} to be the first
     * statement of a constructor, but the JVM allows work before it — inner
     * classes assign their synthetic {@code this$0} field first, for example.
     *
     * <p>The call is therefore moved to the front. That is safe because nothing
     * can observe the object before {@code super()} returns: every instance method
     * needs the object to be constructed first.</p>
     */
    private static String reorderSuperFirst(java.util.List<String> lines) {
        int callIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("super(") || t.startsWith("this(")) {
                callIndex = i;
                break;
            }
        }
        if (callIndex > 0) {
            String call = lines.remove(callIndex);
            lines.add(0, call);
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String stub(String reason) {
        StringBuilder sb = new StringBuilder(signature());
        sb.append(" {\n");
        sb.append("    // J2PC could not recompile this method: ").append(reason).append('\n');
        sb.append("    throw new UnsupportedOperationException(\"unrecompilable method: ")
                .append(escape(reason)).append("\");\n");
        sb.append('}');
        return sb.toString();
    }

    private boolean hasJsr() {
        for (Instruction i : insns) {
            if (i.op == O.JSR || i.op == O.JSR_W || i.op == O.RET) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- signature

    private String signature() {
        StringBuilder sb = new StringBuilder();
        String mods = Modifiers.forMethod(method.access, cf.isInterface(), opts);
        if (!mods.isEmpty()) {
            sb.append(mods).append(' ');
        }
        String name = method.name();
        if ("<clinit>".equals(name)) {
            return "static";
        }
        if ("<init>".equals(name)) {
            sb.append(types.simpleName(cf.name())).append('(');
        } else {
            sb.append(types.ref(Descriptor.returnType(method.desc())))
                    .append(' ').append(names.method(cf.name(), name, method.desc())).append('(');
        }
        String[] args = Descriptor.args(method.desc());
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types.ref(args[i]))
                    .append(' ').append(argName(i, args[i]));        }
        sb.append(')');
        if ("<init>".equals(method.name()) && prologueNeedsThrows) {
            // Only when the mandatory super(...) chain itself performs calls that
            // may throw checked exceptions; otherwise the constructor stays clean
            // so that generated classes remain easy to instantiate.
            sb.append(" throws java.lang.Throwable");
        }
        return sb.toString();
    }

    /** Argument names come from the debug table when it survived obfuscation. */
    private String[] argNameCache;

    private String argName(int index, String desc) {
        if (argNameCache == null) {
            String[] args = Descriptor.args(method.desc());
            argNameCache = new String[args.length];
            String[] dbg = code == null ? null : code.localNames(cp);
            int slot = method.isStatic() ? 0 : 1;
            for (int i = 0; i < args.length; i++) {
                String candidate = dbg != null && slot < dbg.length ? dbg[slot] : null;
                String nm = usable(candidate) ? Names.identifier(candidate) : "arg" + i;
                if (names.isTypeName(nm)) {
                    nm = nm + "_a";
                }
                argNameCache[i] = nm;
                slot += Descriptor.slots(args[i]);
            }
        }
        return argNameCache[index];
    }

    private static boolean usable(String name) {
        return name != null && !name.isEmpty() && !Names.isKeyword(name)
                && name.chars().allMatch(c -> Character.isJavaIdentifierPart(c) || c == '$');
    }

    // ----------------------------------------------------------- local naming

    private void collectLocalKinds() {
        thisIsSlotZero = !method.isStatic();
        slotZeroStored = false;
        for (Instruction ins : insns) {
            int kind = -1;
            int slot = -1;
            switch (ins.op) {
                case O.ILOAD: case O.ISTORE: case O.IINC:
                    kind = K_INT; slot = ins.operand(0); break;
                case O.LLOAD: case O.LSTORE:
                    kind = K_LONG; slot = ins.operand(0); break;
                case O.FLOAD: case O.FSTORE:
                    kind = K_FLOAT; slot = ins.operand(0); break;
                case O.DLOAD: case O.DSTORE:
                    kind = K_DOUBLE; slot = ins.operand(0); break;
                case O.ALOAD: case O.ASTORE:
                    kind = K_REF; slot = ins.operand(0); break;
                case O.ILOAD_0: case O.ILOAD_1: case O.ILOAD_2: case O.ILOAD_3:
                    kind = K_INT; slot = ins.op - O.ILOAD_0; break;
                case O.LLOAD_0: case O.LLOAD_1: case O.LLOAD_2: case O.LLOAD_3:
                    kind = K_LONG; slot = ins.op - O.LLOAD_0; break;
                case O.FLOAD_0: case O.FLOAD_1: case O.FLOAD_2: case O.FLOAD_3:
                    kind = K_FLOAT; slot = ins.op - O.FLOAD_0; break;
                case O.DLOAD_0: case O.DLOAD_1: case O.DLOAD_2: case O.DLOAD_3:
                    kind = K_DOUBLE; slot = ins.op - O.DLOAD_0; break;
                case O.ALOAD_0: case O.ALOAD_1: case O.ALOAD_2: case O.ALOAD_3:
                    kind = K_REF; slot = ins.op - O.ALOAD_0; break;
                case O.ISTORE_0: case O.ISTORE_1: case O.ISTORE_2: case O.ISTORE_3:
                    kind = K_INT; slot = ins.op - O.ISTORE_0; break;
                case O.LSTORE_0: case O.LSTORE_1: case O.LSTORE_2: case O.LSTORE_3:
                    kind = K_LONG; slot = ins.op - O.LSTORE_0; break;
                case O.FSTORE_0: case O.FSTORE_1: case O.FSTORE_2: case O.FSTORE_3:
                    kind = K_FLOAT; slot = ins.op - O.FSTORE_0; break;
                case O.DSTORE_0: case O.DSTORE_1: case O.DSTORE_2: case O.DSTORE_3:
                    kind = K_DOUBLE; slot = ins.op - O.DSTORE_0; break;
                case O.ASTORE_0: case O.ASTORE_1: case O.ASTORE_2: case O.ASTORE_3:
                    kind = K_REF; slot = ins.op - O.ASTORE_0; break;
                case O.RET:
                    kind = K_INT; slot = ins.operand(0); break;
                default:
                    break;
            }
            if (kind < 0) {
                continue;
            }
            localKinds.computeIfAbsent(slot, k -> new HashSet<>()).add(kind);
            boolean isAstore = ins.op == O.ASTORE
                    || (ins.op >= O.ASTORE_0 && ins.op <= O.ASTORE_3);
            if (slot == 0 && kind == K_REF && thisIsSlotZero && isAstore) {
                slotZeroStored = true;
            }
        }
    }

    /**
     * Name of the Java variable that backs local slot {@code slot} for a value of
     * the given precise type.
     *
     * <p>Obfuscated code reuses one slot for unrelated values, so a slot may need
     * several Java variables. The argument keeps its own name; every other type
     * that appears in the same slot gets a numbered variant.</p>
     */
    private String localName(int slot, int kind, SlotType precise) {
        String key = slot + "|" + typeKey(kind, precise);
        String cached = varNames.get(key);
        if (cached != null) {
            return cached;
        }

        String base;
        if (slot == 0 && thisIsSlotZero && kind == K_REF && !slotZeroStored) {
            base = "this";
        } else if (isArgSlot(slot) && kindOf(SlotType.ofDescriptor(argDescOf(slot))) == kind
                && sameRefShape(precise, SlotType.ofDescriptor(argDescOf(slot)))) {
            base = argNameForSlot(slot);
        } else {
            base = "v" + slot;
        }

        String name = base;
        if (!usedLocalNames.add(name) && !"this".equals(base)) {
            int n = 2;
            String tag = KIND_SUFFIX[kind];
            name = base + "_" + tag + n;
            while (!usedLocalNames.add(name)) {
                name = base + "_" + tag + (++n);
            }
        }
        if (names.isTypeName(name)) {
            name = name + "_v";
            usedLocalNames.add(name);
        }
        varNames.put(key, name);
        return name;
    }

    private static String typeKey(int kind, SlotType t) {
        switch (kind) {
            case K_REF:
                return "r:" + (t == null || t.desc == null ? "?" : t.desc);
            case K_INT:
                return "i";
            case K_FLOAT:
                return "f";
            case K_LONG:
                return "j";
            default:
                return "d";
        }
    }

    private static boolean sameRefShape(SlotType a, SlotType b) {
        if (a == null || b == null) {
            return true;
        }
        if (a.desc == null || b.desc == null) {
            return true;
        }
        return a.desc.equals(b.desc);
    }

    private final Set<String> usedLocalNames = new HashSet<>();

    private final Map<Integer, Integer> argSlotToIndex = new HashMap<>();

    private String argDescOf(int slot) {
        buildArgSlots();
        Integer idx = argSlotToIndex.get(slot);
        if (idx == null) {
            return "Ljava/lang/Object;";
        }
        String[] args = Descriptor.args(method.desc());
        return idx < args.length ? args[idx] : "Ljava/lang/Object;";
    }

    private boolean isArgSlot(int slot) {
        buildArgSlots();
        return argSlotToIndex.containsKey(slot);
    }

    private String argNameForSlot(int slot) {
        buildArgSlots();
        Integer idx = argSlotToIndex.get(slot);
        if (idx == null) {
            return "v" + slot;
        }
        String[] args = Descriptor.args(method.desc());
        return argName(idx, args[idx]);
    }

    private void buildArgSlots() {
        if (!argSlotToIndex.isEmpty()) {
            return;
        }
        int slot = method.isStatic() ? 0 : 1;
        String[] args = Descriptor.args(method.desc());
        for (int i = 0; i < args.length; i++) {
            argSlotToIndex.put(slot, i);
            slot += Descriptor.slots(args[i]);
        }
    }

    private void declare(String name, String type, String init) {
        if (!declared.add(name)) {
            return;
        }
        declarations.add(type + " " + name + " = " + init + ";");
    }

    /**
     * Declares a temporary at the current position instead of at the method top.
     *
     * <p>Temporaries created inside a constructor prologue cannot be declared with
     * the other locals: those come after {@code super(...)}, while the temporary is
     * used before it. Emitting the declaration where it is first needed keeps the
     * mandatory-call-first rule intact.</p>
     */
    private void declareInline(String name, String type, String init) {
        if (!declared.add(name)) {
            return;
        }
        out.line(type + " " + name + " = " + init + ";");
    }

    // ------------------------------------------------------------ dispatch mode

    private void emitDispatch(int loopStart) {
        out.line("int " + PC + " = " + loopStart + ";");

        // The dispatch target after the constructor prologue is an arbitrary pc,
        // not necessarily a basic block start, so it is added explicitly.
        java.util.TreeSet<Integer> starts = new java.util.TreeSet<>();
        for (int l : analysis.blockStarts) {
            if (l >= loopStart) {
                starts.add(l);
            }
        }
        starts.add(loopStart);
        int[] leaders = starts.stream().mapToInt(Integer::intValue).toArray();

        out.line((staticInitializer ? "__clinit: " : "") + "while (true) {");
        out.indent();
        out.line("switch (" + PC + ") {");
        out.indent();

        for (int bi = 0; bi < leaders.length; bi++) {
            int start = leaders[bi];
            int end = bi + 1 < leaders.length ? leaders[bi + 1] : code.code.length;
            Frame frame = analysis.frameAt(start);
            if (frame == null) {
                continue;
            }
            out.line("case " + start + ":");
            out.indent();
            emitBlock(start, end, frame);
            out.outdent();
        }

        out.line("default:");
        out.indent();
        out.line("throw new IllegalStateException(\"invalid pc \" + " + PC + ");");
        out.outdent();
        out.outdent();
        out.line("}");
        out.outdent();
        out.line("}");
    }

    /**
     * Finds the {@code super(...)}/{@code this(...)} call of a constructor.
     * Returns -1 when the constructor has no explicit call (Java inserts one).
     */
    private int findConstructorCall() {
        for (Instruction ins : insns) {
            if (ins.op == O.INVOKESPECIAL && "<init>".equals(cp.refName(ins.operand(0)))) {
                return ins.pc;
            }
            if (ins.isBranch() || ins.op == O.ATHROW || ins.isReturn()) {
                return -1;
            }
        }
        return -1;
    }

    private int nextPcOf(int pc) {
        Integer idx = indexOf.get(pc);
        if (idx == null) {
            return -1;
        }
        return idx + 1 < insns.length ? insns[idx + 1].pc : code.code.length;
    }

    private void emitBlock(int start, int end, Frame frame) {
        currentFrame = frame;
        localNow = new SlotType[analysis.maxLocals];
        for (int i = 0; i < localNow.length; i++) {
            localNow[i] = i < frame.locals.length ? frame.locals[i] : SlotType.UNSET_T;
        }
        stack.clear();
        for (int i = 0; i < frame.sp; i++) {
            SlotType declaredType = analysis.stackDecl[i];
            declare("s" + i, javaType(declaredType), declaredType.defaultLiteral());
            // The variable holds the merged type, but the block knows a more
            // precise one; coerce() inserts the cast where it is needed.
            SlotType precise = frame.stack[i] == null ? declaredType : frame.stack[i];
            stack.add(new Val("s" + i, declaredType, precise, null));
        }

        List<ClassFile.Handler> wrapping = handlersFor(start, end);
        int opened = 0;
        for (int i = wrapping.size() - 1; i >= 0; i--) {
            out.line("try {");
            out.indent();
            opened++;
        }

        Integer idx = indexOf.get(start);
        if (idx == null) {
            idx = 0;
        }
        boolean terminated = false;
        int last = start;
        while (idx < insns.length && insns[idx].pc < end) {
            Instruction ins = insns[idx];
            if (opts.lineComments) {
                emitLineComment(ins.pc);
            }
            last = ins.pc;
            terminated = exec(ins);
            idx++;
            if (terminated) {
                break;
            }
        }

        if (!terminated) {
            int next = nextPcOf(last);
            if (next >= code.code.length) {
                spill();
                out.line("throw new IllegalStateException(\"fell off the end of the method\");");
            } else {
                jump(next);
            }
        }

        // The handlers were opened from the last to the first, so the first one
        // in the table is the innermost `try`. Closing therefore has to run in
        // table order: pairing the other way round would attach each catch to the
        // wrong try and change which exception is caught first.
        for (int i = 0; i < opened; i++) {
            ClassFile.Handler h = wrapping.get(i);
            out.outdent();
            String exType = h.catchType == 0 ? "java.lang.Throwable"
                    : types.refInternal(cp.className(h.catchType));
            SlotType st = analysis.stackDecl.length > 0 ? analysis.stackDecl[0] : SlotType.THROWABLE_T;
            declare("s0", javaType(st), st.defaultLiteral());
            out.line("} catch (java.lang.Throwable __ex) {");
            out.indent();
            if (h.catchType != 0) {
                // The bytecode exception table matches by type; anything else
                // keeps propagating.
                out.line("if (!(__ex instanceof " + exType + ")) {");
                out.indent();
                out.line("throw j2pc.rt.Sneaky.sneak(__ex);");
                out.outdent();
                out.line("}");
            }
            out.line("s0 = " + exceptionAssignment(st) + ";");
            out.line(PC + " = " + h.handlerPc + ";");
            out.line("continue;");
            out.outdent();
            out.line("}");
        }
    }

    /**
     * Assignment of the caught exception to the stack slot.
     *
     * <p>The catch clause always declares {@code java.lang.Throwable __ex} and then
     * re-checks the type from the exception table, so the variable may have a
     * narrower type and needs a cast.</p>
     */
    private String exceptionAssignment(SlotType slotType) {
        String declared = javaType(slotType);
        if ("java.lang.Throwable".equals(declared) || "java.lang.Object".equals(declared)) {
            return "__ex";
        }
        return "(" + declared + ")(__ex)";
    }

    private List<ClassFile.Handler> handlersFor(int start, int end) {
        List<ClassFile.Handler> out = new ArrayList<>();
        for (ClassFile.Handler h : code.handlers) {
            if (start >= h.startPc && end <= h.endPc) {
                out.add(h);
            }
        }
        return out;
    }

    private void emitLineComment(int pc) {
        if (!opts.lineComments) {
            return;
        }
        if (lineAt == null) {
            lineAt = new HashMap<>();
            int[] table = code.lineNumbers();
            for (int i = 0; i < table.length; i += 2) {
                lineAt.put(table[i], table[i + 1]);
            }
        }
        Integer ln = lineAt.get(pc);
        if (ln != null) {
            out.line("// line " + ln);
        }
    }

    // --------------------------------------------------------------- flat mode

    private void emitFlat(int startPc) {
        currentFrame = analysis.frameAt(startPc < code.code.length ? startPc : 0);
        localNow = new SlotType[analysis.maxLocals];
        for (int i = 0; i < localNow.length; i++) {
            localNow[i] = currentFrame != null && i < currentFrame.locals.length
                    ? currentFrame.locals[i] : SlotType.UNSET_T;
        }
        stack.clear();
        for (Instruction ins : insns) {
            if (ins.pc < startPc) {
                continue;
            }
            emitLineComment(ins.pc);
            exec(ins);
        }
    }

    // ------------------------------------------------------------ value model

    private static final class Val {
        final String code;
        /** Static Java type of the expression, i.e. what javac will infer. */
        final SlotType type;
        /** More precise type known from the data-flow analysis, if any. */
        final SlotType sem;
        /** Non-null when this value is an uninitialised {@code new} expression. */
        final String freshNew;
        /** True when evaluating the expression has an observable side effect. */
        final boolean effect;
        /** Statement form of an effectful expression, when it differs from {@link #code}. */
        final String raw;

        Val(String code, SlotType type, String freshNew) {
            this(code, type, type, freshNew, false, null);
        }

        Val(String code, SlotType type, SlotType sem, String freshNew) {
            this(code, type, sem, freshNew, false, null);
        }

        Val(String code, SlotType type, SlotType sem, String freshNew, boolean effect) {
            this(code, type, sem, freshNew, effect, null);
        }

        Val(String code, SlotType type, SlotType sem, String freshNew, boolean effect, String raw) {
            this.code = code;
            this.type = type;
            this.sem = sem == null ? type : sem;
            this.freshNew = freshNew;
            this.effect = effect;
            this.raw = raw;
        }

        Val withEffect() {
            return new Val(code, type, sem, freshNew, true, raw);
        }
    }

    private void push(Val v) {
        stack.add(v);
    }

    /**
     * Forces a value into a temporary when it is a compound expression that is
     * about to be duplicated: {@code dup} of {@code new int[5]} must not emit
     * {@code new int[5][0] = ...}.
     */
    /**
     * Evaluates pending stack values that mention {@code token} before a store
     * changes it.
     *
     * <p>{@code counter++} compiles to {@code getfield}, {@code dup_x1},
     * {@code iadd}, {@code putfield}: the old value stays on the stack as the
     * <em>expression</em> {@code this.counter}, and after the store that
     * expression would read the new value. Copying it into a temporary first is
     * what keeps the post-increment semantics.</p>
     */
    private void preservePending(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?<![\\w$])" + java.util.regex.Pattern.quote(token) + "(?![\\w$])");
        for (int i = 0; i < stack.size(); i++) {
            Val v = stack.get(i);
            if (v.code == null || !p.matcher(v.code).find()) {
                continue;
            }
            String nm = "__p" + (tempSeq++);
            if (prologueMode) {
                declareInline(nm, javaType(v.type), v.code);
            } else {
                declare(nm, javaType(v.type), v.type.defaultLiteral());
                out.line(nm + " = " + v.code + ";");
            }
            stack.set(i, new Val(nm, v.type, v.sem, null));
        }
    }

    private Val materialize(Val v) {
        if (isSimple(v.code)) {
            return v;
        }
        String nm = "__a" + (tempSeq++);
        if (prologueMode) {
            declareInline(nm, javaType(v.type), v.code);
        } else {
            declare(nm, javaType(v.type), v.type.defaultLiteral());
            out.line(nm + " = " + v.code + ";");
        }
        return new Val(nm, v.type, v.freshNew);
    }

    private static boolean isSimple(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        if ("null".equals(code) || "true".equals(code) || "false".equals(code)) {
            return true;
        }
        char first = code.charAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!(Character.isJavaIdentifierPart(c) || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private Val pop() {
        if (stack.isEmpty()) {
            return new Val("null", SlotType.OBJECT_T, null);
        }
        return stack.remove(stack.size() - 1);
    }

    /**
     * Drops a value that the bytecode discards.
     *
     * <p>An expression such as {@code obj.createNumber()} must still be
     * evaluated: the call has side effects even though its result is thrown
     * away. Plain values and variables simply vanish.</p>
     */
    private void discard(Val v) {
        if (v.effect) {
            out.line((v.raw != null ? v.raw : v.code) + ";");
        }
    }

    private void spill() {
        for (int i = 0; i < stack.size(); i++) {
            Val v = stack.get(i);
            SlotType decl = analysis.stackDecl[i];
            declare("s" + i, javaType(decl), decl.defaultLiteral());
            String value = coerce(v, decl);
            if (!value.equals("s" + i)) {
                out.line("s" + i + " = " + value + ";");
            }
            // The expression has been evaluated once; from now on the variable
            // holds it. Keeping the expression would re-evaluate it on the next
            // spill, which duplicates side effects such as append().
            // freshNew survives the spill: the object is still uninitialised and
            // the pending <init> call must still turn into `s = new X(...)`.
            stack.set(i, new Val("s" + i, decl, decl, v.freshNew));
        }
    }

    private void jump(int target) {
        spill();
        out.line(PC + " = " + target + ";");
        out.line("continue;");
    }

    // --------------------------------------------------------------- coercion

    private String coerce(Val v, SlotType target) {
        String c = v.code;
        if (target.kind == SlotType.UNSET) {
            return c;
        }
        if (v.type.equals(target)) {
            return c;
        }
        // A slot whose uses disagree ends up as Object; box and unbox through
        // the runtime helpers so the generated code still type checks.
        if (target.kind == SlotType.CONFLICT) {
            switch (v.type.kind) {
                case SlotType.INT:
                    return "j2pc.rt.Box.of(" + c + ")";
                case SlotType.LONG:
                    return "j2pc.rt.Box.of(" + c + ")";
                case SlotType.FLOAT:
                    return "j2pc.rt.Box.of(" + c + ")";
                case SlotType.DOUBLE:
                    return "j2pc.rt.Box.of(" + c + ")";
                default:
                    return c;
            }
        }
        if (v.type.kind == SlotType.CONFLICT) {
            switch (target.kind) {
                case SlotType.INT:
                    return "j2pc.rt.Box.toInt(" + c + ")";
                case SlotType.LONG:
                    return "j2pc.rt.Box.toLong(" + c + ")";
                case SlotType.FLOAT:
                    return "j2pc.rt.Box.toFloat(" + c + ")";
                case SlotType.DOUBLE:
                    return "j2pc.rt.Box.toDouble(" + c + ")";
                case SlotType.REF:
                    return "(" + javaType(target) + ")(" + c + ")";
                default:
                    return c;
            }
        }
        switch (target.kind) {
            case SlotType.INT:
                switch (v.type.kind) {
                    case SlotType.FLOAT:
                    case SlotType.LONG:
                    case SlotType.DOUBLE:
                        return "(int)(" + c + ")";
                    case SlotType.REF:
                        return "j2pc.rt.Box.toInt(" + c + ")";
                    default:
                        return c;
                }
            case SlotType.LONG:
                switch (v.type.kind) {
                    case SlotType.INT:
                    case SlotType.FLOAT:
                    case SlotType.DOUBLE:
                        return "(long)(" + c + ")";
                    case SlotType.REF:
                        return "j2pc.rt.Box.toLong(" + c + ")";
                    default:
                        return c;
                }
            case SlotType.FLOAT:
                switch (v.type.kind) {
                    case SlotType.INT:
                    case SlotType.LONG:
                    case SlotType.DOUBLE:
                        return "(float)(" + c + ")";
                    case SlotType.REF:
                        return "j2pc.rt.Box.toFloat(" + c + ")";
                    default:
                        return c;
                }
            case SlotType.DOUBLE:
                switch (v.type.kind) {
                    case SlotType.INT:
                    case SlotType.LONG:
                    case SlotType.FLOAT:
                        return "(double)(" + c + ")";
                    case SlotType.REF:
                        return "j2pc.rt.Box.toDouble(" + c + ")";
                    default:
                        return c;
                }
            case SlotType.REF:
                if (v.type.kind == SlotType.REF) {
                    if (target.desc == null) {
                        return c;
                    }
                    if (v.type.desc == null) {
                        // A null literal still needs the explicit type when the
                        // target is more specific than Object.
                        return "java.lang.Object".equals(javaType(target))
                                ? c : "((" + javaType(target) + ")(" + c + "))";
                    }
                    if (!target.desc.equals(v.type.desc)) {
                        return "((" + javaType(target) + ")(" + c + "))";
                    }
                    return c;
                }
                return "j2pc.rt.Box.of(" + c + ")";
            default:
                return c;
        }
    }

    /** Converts a value to a value of the given JVM descriptor, honouring Z/B/C/S. */
    private String coerceTo(String code, SlotType actual, String desc) {
        char d = desc.charAt(0);
        switch (d) {
            case 'Z':
                if (actual.kind == SlotType.REF || actual.kind == SlotType.CONFLICT) {
                    return "j2pc.rt.Box.toInt(" + code + ") != 0";
                }
                return "(" + code + ") != 0";
            case 'B':
                return "(byte)(" + coerce(new Val(code, actual, null), SlotType.INT_T) + ")";
            case 'C':
                return "(char)(" + coerce(new Val(code, actual, null), SlotType.INT_T) + ")";
            case 'S':
                return "(short)(" + coerce(new Val(code, actual, null), SlotType.INT_T) + ")";
            default:
                return coerce(new Val(code, actual, null), SlotType.ofDescriptor(desc));
        }
    }

    /** Converts a value coming back from a method with the given descriptor. */
    private Val fromCall(String code, String desc) {
        Val v;
        if (desc.charAt(0) == 'Z') {
            v = new Val("(" + code + " ? 1 : 0)", SlotType.INT_T, null);
        } else {
            v = new Val(code, SlotType.ofDescriptor(desc), null);
        }
        // `code` is the raw call: usable as a statement if the value is dropped.
        return new Val(v.code, v.type, v.sem, v.freshNew, true, code);
    }

    // ------------------------------------------------------------- instruction

    private boolean exec(Instruction ins) {
        switch (ins.op) {
            case O.NOP:
                return false;

            case O.ACONST_NULL:
                push(new Val("null", SlotType.NULL_T, null));
                return false;
            case O.ICONST_M1: case O.ICONST_0: case O.ICONST_1:
            case O.ICONST_2: case O.ICONST_3: case O.ICONST_4: case O.ICONST_5:
                push(new Val(Integer.toString(ins.op - O.ICONST_0), SlotType.INT_T, null));
                return false;
            case O.LCONST_0: case O.LCONST_1:
                push(new Val((ins.op - O.LCONST_0) + "L", SlotType.LONG_T, null));
                return false;
            case O.FCONST_0: case O.FCONST_1: case O.FCONST_2:
                push(new Val((ins.op - O.FCONST_0) + "f", SlotType.FLOAT_T, null));
                return false;
            case O.DCONST_0: case O.DCONST_1:
                push(new Val((ins.op - O.DCONST_0) + "d", SlotType.DOUBLE_T, null));
                return false;
            case O.BIPUSH: case O.SIPUSH:
                push(new Val(Integer.toString(ins.operand(0)), SlotType.INT_T, null));
                return false;

            case O.LDC: case O.LDC_W: case O.LDC2_W:
                push(constant(ins.operand(0)));
                return false;

            case O.ILOAD: case O.LLOAD: case O.FLOAD: case O.DLOAD: case O.ALOAD:
                return loadLocal(ins.operand(0), kindOfLoad(ins.op));
            case O.ILOAD_0: case O.ILOAD_1: case O.ILOAD_2: case O.ILOAD_3:
                return loadLocal(ins.op - O.ILOAD_0, K_INT);
            case O.LLOAD_0: case O.LLOAD_1: case O.LLOAD_2: case O.LLOAD_3:
                return loadLocal(ins.op - O.LLOAD_0, K_LONG);
            case O.FLOAD_0: case O.FLOAD_1: case O.FLOAD_2: case O.FLOAD_3:
                return loadLocal(ins.op - O.FLOAD_0, K_FLOAT);
            case O.DLOAD_0: case O.DLOAD_1: case O.DLOAD_2: case O.DLOAD_3:
                return loadLocal(ins.op - O.DLOAD_0, K_DOUBLE);
            case O.ALOAD_0: case O.ALOAD_1: case O.ALOAD_2: case O.ALOAD_3:
                return loadLocal(ins.op - O.ALOAD_0, K_REF);

            case O.ISTORE: case O.LSTORE: case O.FSTORE: case O.DSTORE: case O.ASTORE:
                return storeLocal(ins.operand(0), kindOfStore(ins.op));
            case O.ISTORE_0: case O.ISTORE_1: case O.ISTORE_2: case O.ISTORE_3:
                return storeLocal(ins.op - O.ISTORE_0, K_INT);
            case O.LSTORE_0: case O.LSTORE_1: case O.LSTORE_2: case O.LSTORE_3:
                return storeLocal(ins.op - O.LSTORE_0, K_LONG);
            case O.FSTORE_0: case O.FSTORE_1: case O.FSTORE_2: case O.FSTORE_3:
                return storeLocal(ins.op - O.FSTORE_0, K_FLOAT);
            case O.DSTORE_0: case O.DSTORE_1: case O.DSTORE_2: case O.DSTORE_3:
                return storeLocal(ins.op - O.DSTORE_0, K_DOUBLE);
            case O.ASTORE_0: case O.ASTORE_1: case O.ASTORE_2: case O.ASTORE_3:
                return storeLocal(ins.op - O.ASTORE_0, K_REF);

            case O.IINC: {
                int slot = ins.operand(0);
                int delta = ins.operand(1);
                SlotType precise = frameLocal(slot, K_INT);
                String nm = localName(slot, K_INT, precise);
                declareLocal(slot, K_INT, precise);
                if (isBooleanSlot(slot, nm)) {
                    // The bytecode increments a boolean slot as if it were an int;
                    // the Java variable is a real boolean, so convert both ways.
                    out.line(nm + " = (((" + nm + " ? 1 : 0) + " + delta + ") != 0);");
                } else if (delta == 1) {
                    out.line(nm + "++;");
                } else if (delta == -1) {
                    out.line(nm + "--;");
                } else {
                    out.line(nm + " += " + delta + ";");
                }
                return false;
            }

            case O.IALOAD: {
                Val index = pop();
                Val arr = pop();
                push(new Val(arrayExpr(arr, "int[]") + "[" + coerce(index, SlotType.INT_T) + "]", SlotType.INT_T, null));
                return false;
            }
            case O.LALOAD: {
                Val index = pop();
                Val arr = pop();
                push(new Val(arrayExpr(arr, "long[]") + "[" + coerce(index, SlotType.INT_T) + "]", SlotType.LONG_T, null));
                return false;
            }
            case O.FALOAD: {
                Val index = pop();
                Val arr = pop();
                push(new Val(arrayExpr(arr, "float[]") + "[" + coerce(index, SlotType.INT_T) + "]", SlotType.FLOAT_T, null));
                return false;
            }
            case O.DALOAD: {
                Val index = pop();
                Val arr = pop();
                push(new Val(arrayExpr(arr, "double[]") + "[" + coerce(index, SlotType.INT_T) + "]", SlotType.DOUBLE_T, null));
                return false;
            }
            case O.BALOAD: case O.CALOAD: case O.SALOAD: {
                Val index = pop();
                Val arr = pop();
                String desc = arrayDescOf(arr);
                if ("[Z".equals(desc)) {
                    // boolean[] elements are ints on the operand stack
                    push(new Val("(" + arrayExpr(arr, "boolean[]") + "["
                            + coerce(index, SlotType.INT_T) + "] ? 1 : 0)", SlotType.INT_T, null));
                } else {
                    push(new Val(arrayExpr(arr, "byte[]") + "[" + coerce(index, SlotType.INT_T) + "]",
                            SlotType.INT_T, null));
                }
                return false;
            }
            case O.AALOAD: {
                Val index = pop();
                Val arr = pop();
                String arrDesc = arrayDescOf(arr);
                if (arrDesc != null) {
                    SlotType et = elemType(arr.sem);
                    push(new Val("((" + javaType(et) + ")((" + arrayExpr(arr, null)
                            + ")[" + coerce(index, SlotType.INT_T) + "]))", et, null));
                } else {
                    // Unknown element type: read reflectively and let the caller
                    // cast the result.
                    push(new Val("j2pc.rt.Arrays.get(" + arr.code + ", "
                            + coerce(index, SlotType.INT_T) + ")", SlotType.OBJECT_T, null));
                }
                return false;
            }

            case O.IASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "int[]") + "[" + coerce(index, SlotType.INT_T) + "] = "
                        + coerce(value, SlotType.INT_T) + ";");
                return false;
            }
            case O.LASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "long[]") + "[" + coerce(index, SlotType.INT_T) + "] = "
                        + coerce(value, SlotType.LONG_T) + ";");
                return false;
            }
            case O.FASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "float[]") + "[" + coerce(index, SlotType.INT_T) + "] = "
                        + coerce(value, SlotType.FLOAT_T) + ";");
                return false;
            }
            case O.DASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "double[]") + "[" + coerce(index, SlotType.INT_T) + "] = "
                        + coerce(value, SlotType.DOUBLE_T) + ";");
                return false;
            }
            case O.BASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = pop();
                String desc = arrayDescOf(arr);
                if ("[Z".equals(desc)) {
                    out.line(arrayExpr(arr, "boolean[]") + "[" + coerce(index, SlotType.INT_T)
                            + "] = (" + coerce(value, SlotType.INT_T) + ") != 0;");
                } else {
                    out.line(arrayExpr(arr, "byte[]") + "[" + coerce(index, SlotType.INT_T) + "] = (byte)("
                            + coerce(value, SlotType.INT_T) + ");");
                }
                return false;
            }
            case O.CASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "char[]") + "[" + coerce(index, SlotType.INT_T) + "] = (char)("
                        + coerce(value, SlotType.INT_T) + ");");
                return false;
            }
            case O.SASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = materialize(pop());
                out.line(arrayExpr(arr, "short[]") + "[" + coerce(index, SlotType.INT_T) + "] = (short)("
                        + coerce(value, SlotType.INT_T) + ");");
                return false;
            }
            case O.AASTORE: {
                Val value = pop();
                Val index = pop();
                Val arr = pop();
                String arrDesc = arrayDescOf(arr);
                if (arrDesc != null && arrDesc.startsWith("[L")) {
                    SlotType et = elemType(arr.sem);
                    out.line(arrayExpr(arr, null) + "[" + coerce(index, SlotType.INT_T) + "] = "
                            + coerce(value, et) + ";");
                } else if (arrDesc != null && arrDesc.startsWith("[[")) {
                    SlotType et = elemType(arr.sem);
                    out.line(arrayExpr(arr, null) + "[" + coerce(index, SlotType.INT_T) + "] = "
                            + coerce(value, et) + ";");
                } else {
                    // The element type is unknown; a wrong cast would fail at run
                    // time, so store through reflection instead.
                    out.line("j2pc.rt.Arrays.set(" + arr.code + ", "
                            + coerce(index, SlotType.INT_T) + ", " + value.code + ");");
                }
                return false;
            }

            case O.POP:
                discard(pop());
                return false;
            case O.POP2: {
                Val a = pop();
                discard(a);
                if (!a.type.isCategory2()) {
                    discard(pop());
                }
                return false;
            }
            case O.DUP: {
                Val a = materialize(pop());
                push(a);
                push(a);
                return false;
            }
            case O.DUP_X1: {
                Val a = materialize(pop());
                Val b = pop();
                push(a);
                push(b);
                push(a);
                return false;
            }
            case O.DUP_X2: {
                Val a = materialize(pop());
                Val b = pop();
                if (b.type.isCategory2()) {
                    push(a);
                    push(b);
                    push(a);
                } else {
                    Val c = pop();
                    push(a);
                    push(c);
                    push(b);
                    push(a);
                }
                return false;
            }
            case O.DUP2: {
                Val a = pop();
                if (a.type.isCategory2()) {
                    a = materialize(a);
                    push(a);
                    push(a);
                } else {
                    Val b = pop();
                    push(b);
                    push(a);
                    push(b);
                    push(a);
                }
                return false;
            }
            case O.DUP2_X1: {
                Val a = pop();
                if (a.type.isCategory2()) {
                    Val b = pop();
                    push(a);
                    push(b);
                    push(a);
                } else {
                    Val b = pop();
                    Val c = pop();
                    push(b);
                    push(a);
                    push(c);
                    push(b);
                    push(a);
                }
                return false;
            }
            case O.DUP2_X2: {
                Val a = pop();
                if (a.type.isCategory2()) {
                    Val b = pop();
                    if (b.type.isCategory2()) {
                        push(a);
                        push(b);
                        push(a);
                    } else {
                        Val c = pop();
                        push(a);
                        push(c);
                        push(b);
                        push(a);
                    }
                } else {
                    Val b = pop();
                    Val c = pop();
                    if (c.type.isCategory2()) {
                        push(b);
                        push(a);
                        push(c);
                        push(b);
                        push(a);
                    } else {
                        Val d = pop();
                        push(b);
                        push(a);
                        push(d);
                        push(c);
                        push(b);
                        push(a);
                    }
                }
                return false;
            }
            case O.SWAP: {
                Val a = pop();
                Val b = pop();
                push(a);
                push(b);
                return false;
            }

            case O.IADD: case O.LADD: case O.FADD: case O.DADD:
            case O.ISUB: case O.LSUB: case O.FSUB: case O.DSUB:
            case O.IMUL: case O.LMUL: case O.FMUL: case O.DMUL:
            case O.IDIV: case O.LDIV: case O.FDIV: case O.DDIV:
            case O.IREM: case O.LREM: case O.FREM: case O.DREM:
            case O.IAND: case O.LAND: case O.IOR: case O.LOR: case O.IXOR: case O.LXOR: {
                String op = binaryOperator(ins.op);
                Val b = pop();
                Val a = pop();
                SlotType t = arithResultType(ins.op);
                push(new Val("(" + coerce(a, t) + " " + op + " " + coerce(b, t) + ")", t, null));
                return false;
            }
            case O.ISHL: case O.ISHR: case O.IUSHR: {
                String op = binaryOperator(ins.op);
                Val b = pop();
                Val a = pop();
                push(new Val("(" + coerce(a, SlotType.INT_T) + " " + op + " "
                        + coerce(b, SlotType.INT_T) + ")", SlotType.INT_T, null));
                return false;
            }
            case O.LSHL: case O.LSHR: case O.LUSHR: {
                String op = binaryOperator(ins.op);
                Val b = pop();
                Val a = pop();
                push(new Val("(" + coerce(a, SlotType.LONG_T) + " " + op + " "
                        + coerce(b, SlotType.INT_T) + ")", SlotType.LONG_T, null));
                return false;
            }

            case O.INEG: case O.LNEG: case O.FNEG: case O.DNEG: {
                Val a = pop();
                push(new Val("(-" + a.code + ")", a.type, null));
                return false;
            }

            case O.I2L: push(new Val("(long)(" + pop().code + ")", SlotType.LONG_T, null)); return false;
            case O.I2F: push(new Val("(float)(" + pop().code + ")", SlotType.FLOAT_T, null)); return false;
            case O.I2D: push(new Val("(double)(" + pop().code + ")", SlotType.DOUBLE_T, null)); return false;
            case O.L2I: push(new Val("(int)(" + pop().code + ")", SlotType.INT_T, null)); return false;
            case O.L2F: push(new Val("(float)(" + pop().code + ")", SlotType.FLOAT_T, null)); return false;
            case O.L2D: push(new Val("(double)(" + pop().code + ")", SlotType.DOUBLE_T, null)); return false;
            case O.F2I: push(new Val("(int)(" + pop().code + ")", SlotType.INT_T, null)); return false;
            case O.F2L: push(new Val("(long)(" + pop().code + ")", SlotType.LONG_T, null)); return false;
            case O.F2D: push(new Val("(double)(" + pop().code + ")", SlotType.DOUBLE_T, null)); return false;
            case O.D2I: push(new Val("(int)(" + pop().code + ")", SlotType.INT_T, null)); return false;
            case O.D2L: push(new Val("(long)(" + pop().code + ")", SlotType.LONG_T, null)); return false;
            case O.D2F: push(new Val("(float)(" + pop().code + ")", SlotType.FLOAT_T, null)); return false;
            case O.I2B: push(new Val("(byte)(" + pop().code + ")", SlotType.INT_T, null)); return false;
            case O.I2C: push(new Val("(char)(" + pop().code + ")", SlotType.INT_T, null)); return false;
            case O.I2S: push(new Val("(short)(" + pop().code + ")", SlotType.INT_T, null)); return false;

            case O.LCMP: {
                Val b = pop();
                Val a = pop();
                push(new Val("j2pc.rt.Cmp.lcmp(" + coerce(a, SlotType.LONG_T) + ", "
                        + coerce(b, SlotType.LONG_T) + ")", SlotType.INT_T, null));
                return false;
            }
            case O.FCMPL: case O.FCMPG: {
                Val b = pop();
                Val a = pop();
                String fn = ins.op == O.FCMPL ? "j2pc.rt.Cmp.fcmpl" : "j2pc.rt.Cmp.fcmpg";
                push(new Val(fn + "(" + coerce(a, SlotType.FLOAT_T) + ", "
                        + coerce(b, SlotType.FLOAT_T) + ")", SlotType.INT_T, null));
                return false;
            }
            case O.DCMPL: case O.DCMPG: {
                Val b = pop();
                Val a = pop();
                String fn = ins.op == O.DCMPL ? "j2pc.rt.Cmp.dcmpl" : "j2pc.rt.Cmp.dcmpg";
                push(new Val(fn + "(" + coerce(a, SlotType.DOUBLE_T) + ", "
                        + coerce(b, SlotType.DOUBLE_T) + ")", SlotType.INT_T, null));
                return false;
            }

            case O.IFEQ: case O.IFNE: case O.IFLT: case O.IFGE: case O.IFGT: case O.IFLE: {
                Val v = pop();
                return emitCondition("(" + coerce(v, SlotType.INT_T) + " "
                        + intCompareOp(ins.op) + " 0)", ins.branchTarget());
            }
            case O.IF_ICMPEQ: case O.IF_ICMPNE: case O.IF_ICMPLT:
            case O.IF_ICMPGE: case O.IF_ICMPGT: case O.IF_ICMPLE: {
                Val b = pop();
                Val a = pop();
                return emitCondition("(" + coerce(a, SlotType.INT_T) + " " + refCompareOp(ins.op)
                        + " " + coerce(b, SlotType.INT_T) + ")", ins.branchTarget());
            }
            case O.IF_ACMPEQ: case O.IF_ACMPNE: {
                Val b = pop();
                Val a = pop();
                return emitCondition("(" + a.code + " " + refCompareOp(ins.op) + " " + b.code + ")",
                        ins.branchTarget());
            }
            case O.IFNULL: case O.IFNONNULL: {
                Val v = pop();
                String op = ins.op == O.IFNULL ? "==" : "!=";
                return emitCondition("(" + v.code + " " + op + " null)", ins.branchTarget());
            }

            case O.GOTO: case O.GOTO_W:
                jump(ins.branchTarget());
                return true;

            case O.JSR: case O.JSR_W: {
                int ret = nextPcOf(ins.pc);
                push(new Val(Integer.toString(ret), SlotType.INT_T, null));
                jump(ins.branchTarget());
                return true;
            }
            case O.RET: {
                spill();
                out.line(PC + " = " + localName(ins.operand(0), K_INT, SlotType.INT_T) + ";");
                out.line("continue;");
                return true;
            }

            case O.TABLESWITCH: case O.LOOKUPSWITCH:
                return emitSwitch(ins);

            case O.IRETURN: case O.LRETURN: case O.FRETURN: case O.DRETURN: case O.ARETURN: {
                Val v = pop();
                String ret = Descriptor.returnType(method.desc());
                out.line("return " + coerceTo(v.code, v.type, ret) + ";");
                return true;
            }
            case O.RETURN:
                if (staticInitializer) {
                    // Inside <clinit> the body lives in a dispatch loop, so the
                    // JVM `return` becomes a break out of that loop. A flat body
                    // needs nothing at all.
                    if (!flat) {
                        spill();
                        out.line("break __clinit;");
                    }
                } else {
                    out.line("return;");
                }
                return true;

            case O.INVOKEDYNAMIC:
                return emitInvokeDynamic(ins);

            case O.GETSTATIC: {
                int idx = ins.operand(0);
                String owner = cp.refOwner(idx);
                String desc = cp.refType(idx);
                String access = types.refInternal(owner) + "." + names.field(owner, cp.refName(idx), cp.refType(idx));
                if (desc.charAt(0) == 'Z') {
                    push(new Val("(" + access + " ? 1 : 0)", SlotType.INT_T, null));
                } else {
                    push(new Val(access, SlotType.ofDescriptor(desc), null));
                }
                return false;
            }
            case O.PUTSTATIC: {
                int idx = ins.operand(0);
                String owner = cp.refOwner(idx);
                String desc = cp.refType(idx);
                String fname = names.field(owner, cp.refName(idx), cp.refType(idx));
                Val v = pop();
                preservePending(fname);
                out.line(types.refInternal(owner) + "." + fname + " = "
                        + coerceTo(v.code, v.type, desc) + ";");
                return false;
            }
            case O.GETFIELD: {
                int idx = ins.operand(0);
                String owner = cp.refOwner(idx);
                String desc = cp.refType(idx);
                Val recv = pop();
                String access = cast(recv, owner) + "." + names.field(owner, cp.refName(idx), cp.refType(idx));
                if (desc.charAt(0) == 'Z') {
                    push(new Val("(" + access + " ? 1 : 0)", SlotType.INT_T, null));
                } else {
                    push(new Val(access, SlotType.ofDescriptor(desc), null));
                }
                return false;
            }
            case O.PUTFIELD: {
                int idx = ins.operand(0);
                String owner = cp.refOwner(idx);
                String desc = cp.refType(idx);
                String fname = names.field(owner, cp.refName(idx), cp.refType(idx));
                Val v = pop();
                Val recv = pop();
                preservePending(fname);
                out.line(cast(recv, owner) + "." + fname + " = "
                        + coerceTo(v.code, v.type, desc) + ";");
                return false;
            }

            case O.INVOKEVIRTUAL: case O.INVOKESPECIAL:
            case O.INVOKESTATIC: case O.INVOKEINTERFACE:
                return emitInvoke(ins);

            case O.NEW: {
                String owner = cp.className(ins.operand(0));
                String nm = "__n" + (tempSeq++);
                if (prologueMode) {
                    declareInline(nm, types.refInternal(owner), "null");
                } else {
                    declarations.add(types.refInternal(owner) + " " + nm + " = null;");
                    declared.add(nm);
                }
                push(new Val(nm, SlotType.ref(owner), owner));
                return false;
            }
            case O.NEWARRAY: {
                Val count = pop();
                String desc = arrayDesc(ins.operand(0));
                String base = types.ref(desc.substring(1));
                push(new Val("new " + base + "[" + count.code + "]", SlotType.ref(desc), null));
                return false;
            }
            case O.ANEWARRAY: {
                Val count = pop();
                String elem = cp.className(ins.operand(0));
                String desc = elem.startsWith("[") ? "[" + elem : "[L" + elem + ";";
                push(new Val(arrayCreation(elem, count.code), SlotType.ref(desc), null));
                return false;
            }
            case O.MULTIANEWARRAY: {
                String desc = cp.className(ins.operand(0));
                int dims = ins.operand(1);
                List<Val> sizes = new ArrayList<>();
                for (int i = 0; i < dims; i++) {
                    sizes.add(0, pop());
                }
                int totalDims = 0;
                while (totalDims < desc.length() && desc.charAt(totalDims) == '[') {
                    totalDims++;
                }
                String base = types.ref(desc.substring(totalDims));
                StringBuilder sb = new StringBuilder("new ").append(base);
                for (int i = 0; i < dims; i++) {
                    sb.append('[').append(sizes.get(i).code).append(']');
                }
                for (int i = dims; i < totalDims; i++) {
                    sb.append("[]");
                }
                push(new Val(sb.toString(), SlotType.ref(desc), null));
                return false;
            }
            case O.ARRAYLENGTH: {
                Val a = pop();
                boolean staticallyArray = a.type.kind == SlotType.REF
                        && a.type.desc != null && a.type.desc.startsWith("[");
                boolean knownArray = a.sem.kind == SlotType.REF
                        && a.sem.desc != null && a.sem.desc.startsWith("[");
                String code;
                if (staticallyArray || knownArray) {
                    code = arrayExpr(a, null) + ".length";
                } else {
                    // The element type is unknown, and a wrong cast would throw:
                    // ask the JVM for the length instead.
                    code = "j2pc.rt.Arrays.length(" + a.code + ")";
                }
                push(new Val(code, SlotType.INT_T, null));
                return false;
            }
            case O.ATHROW: {
                Val e = pop();
                out.line("throw (java.lang.Throwable)(" + e.code + ");");
                return true;
            }
            case O.CHECKCAST: {
                Val v = pop();
                String target = cp.className(ins.operand(0));
                String type = target.startsWith("[") ? types.ref(target)
                        : types.refInternal(target);
                push(new Val("((" + type + ")(" + v.code + "))", SlotType.ref(target), null));
                return false;
            }
            case O.INSTANCEOF: {
                Val v = pop();
                String target = cp.className(ins.operand(0));
                String type = target.startsWith("[") ? types.ref(target)
                        : types.refInternal(target);
                push(new Val("(" + v.code + " instanceof " + type + " ? 1 : 0)", SlotType.INT_T, null));
                return false;
            }
            case O.MONITORENTER: {
                Val v = pop();
                out.line("j2pc.rt.Monitor.enter(" + v.code + ");");
                return false;
            }
            case O.MONITOREXIT: {
                Val v = pop();
                out.line("j2pc.rt.Monitor.exit(" + v.code + ");");
                return false;
            }

            default:
                out.line("throw new UnsupportedOperationException(\"" + ins.mnemonic() + " at " + ins.pc + "\");");
                return true;
        }
    }

    // ----------------------------------------------------------- load / store

    private boolean loadLocal(int slot, int kind) {
        Val fresh = freshLocals.get(slot);
        if (fresh != null) {
            push(fresh);
            return false;
        }
        // Before the mandatory super(...)/this(...) call, slot 0 of a constructor
        // is the uninitialised instance. Java only allows it as the receiver of
        // that call, which is exactly how it is used here; naming it after a
        // local variable would produce a call to a method that does not exist.
        if (prologueMode && slot == 0 && kind == K_REF && thisIsSlotZero) {
            push(new Val("this", SlotType.ref(cf.name()), null));
            return false;
        }
        SlotType precise = frameLocal(slot, kind);
        declareLocal(slot, kind, precise);
        push(new Val(localRead(slot, kind, precise), localType(slot, kind, precise), null));
        return false;
    }

    /**
     * Reads a local. A {@code boolean} parameter is stored as a Java boolean but
     * used as an int by the bytecode, so it has to be widened on load.
     */
    private String localRead(int slot, int kind, SlotType precise) {
        String nm = localName(slot, kind, precise);
        if (kind == K_INT && "Z".equals(argDesc(slot)) && nm.equals(argNameForSlot(slot))) {
            return "(" + nm + " ? 1 : 0)";
        }
        return nm;
    }

    private String argDesc(int slot) {
        buildArgSlots();
        Integer idx = argSlotToIndex.get(slot);
        if (idx == null) {
            return null;
        }
        String[] args = Descriptor.args(method.desc());
        return idx < args.length ? args[idx] : null;
    }

    /** True when this slot holds a {@code boolean} parameter stored as a boolean. */
    private boolean isBooleanSlot(int slot, String varName) {
        return "Z".equals(argDesc(slot)) && varName.equals(argNameForSlot(slot));
    }

    /**
     * Casts a value to the narrow primitive type of a parameter.
     *
     * <p>Bytecode keeps {@code boolean}, {@code byte}, {@code char} and
     * {@code short} on the stack as plain ints, but the generated parameter keeps
     * its real Java type, so an explicit cast is needed on every store.</p>
     */
    private String narrowToArgType(int slot, String value) {
        String desc = argDesc(slot);
        if (desc == null) {
            return null;
        }
        switch (desc.charAt(0)) {
            case 'Z':
                return "(" + value + ") != 0";
            case 'B':
                return "(byte)(" + value + ")";
            case 'C':
                return "(char)(" + value + ")";
            case 'S':
                return "(short)(" + value + ")";
            default:
                return null;
        }
    }

    private boolean storeLocal(int slot, int kind) {
        Val v = pop();
        if (v.freshNew != null) {
            // An object created by `new` is stored into a local before its
            // constructor runs (`new`/`dup`/`astore`/.../`aload`/`invokespecial`).
            // The local is remembered as an alias of the temporary so the later
            // <init> call can still be turned into `temp = new X(...)`.
            freshLocals.put(slot, new Val(v.code, v.type, v.freshNew));
            return false;
        }
        freshLocals.remove(slot);
        SlotType precise = storeType(slot, kind, v);
        if (localNow != null && slot < localNow.length) {
            localNow[slot] = precise;
        }
        declareLocal(slot, kind, precise);
        SlotType t = localType(slot, kind, precise);
        String nm = localName(slot, kind, precise);
        if (kind == K_INT && "Z".equals(argDesc(slot)) && nm.equals(argNameForSlot(slot))) {
            out.line(nm + " = (" + coerce(v, SlotType.INT_T) + ") != 0;");
            return false;
        }
        String value = coerce(v, t);
        String narrow = nm.equals(argNameForSlot(slot)) ? narrowToArgType(slot, value) : null;
        if (narrow != null) {
            out.line(nm + " = " + narrow + ";");
            return false;
        }
        if (value.equals(nm + " + 1")) {
            out.line(nm + "++;");
        } else if (value.equals(nm + " - 1")) {
            out.line(nm + "--;");
        } else if (!value.equals(nm)) {
            out.line(nm + " = " + value + ";");
        }
        return false;
    }

    /** Type a store gives the slot: the value's precise type when usable. */
    private SlotType storeType(int slot, int kind, Val v) {
        if (v.sem != null && v.sem.kind != SlotType.UNSET && v.sem.kind != SlotType.CONFLICT
                && kindOf(v.sem) == kind) {
            return v.sem;
        }
        return frameLocal(slot, kind);
    }

    /** Type the analysis knows for this slot at the current program point. */
    private SlotType frameLocal(int slot, int kind) {
        if (localNow != null && slot < localNow.length) {
            SlotType t = localNow[slot];
            if (t != null && t.kind != SlotType.UNSET && t.kind != SlotType.CONFLICT && kindOf(t) == kind) {
                return t;
            }
        }
        if (currentFrame != null && slot < currentFrame.locals.length) {
            SlotType t = currentFrame.locals[slot];
            if (t != null && t.kind != SlotType.UNSET && t.kind != SlotType.CONFLICT && kindOf(t) == kind) {
                return t;
            }
        }
        if (slot < analysis.localDecl.length) {
            SlotType t = analysis.localDecl[slot];
            if (t.kind != SlotType.UNSET && t.kind != SlotType.CONFLICT && kindOf(t) == kind) {
                return t;
            }
        }
        return typeOfKind(kind);
    }

    /** Running type of every local inside the block currently being emitted. */
    private SlotType[] localNow;

    private Frame currentFrame;

    private void declareLocal(int slot, int kind, SlotType precise) {
        String nm = localName(slot, kind, precise);
        if ("this".equals(nm)) {
            return;
        }
        buildArgSlots();
        if (argSlotToIndex.containsKey(slot) && nm.equals(argNameForSlot(slot))) {
            return;
        }
        SlotType t = localType(slot, kind, precise);
        declare(nm, javaType(t), t.defaultLiteral());
    }

    /**
     * Best known Java type for a local slot. Uses the declared argument types,
     * {@code this}, and the merged data-flow type from the analyzer, so the
     * generated code avoids needless casts and boxing.
     */
    private SlotType localType(int slot, int kind, SlotType precise) {
        if (slot == 0 && thisIsSlotZero && kind == K_REF && !slotZeroStored) {
            return SlotType.ref(cf.name());
        }
        buildArgSlots();
        Integer argIndex = argSlotToIndex.get(slot);
        if (argIndex != null) {
            String[] args = Descriptor.args(method.desc());
            SlotType t = SlotType.ofDescriptor(args[argIndex]);
            if (kindOf(t) == kind && sameRefShape(t, precise)) {
                return t;
            }
        }
        if (precise != null && precise.kind != SlotType.UNSET && precise.kind != SlotType.CONFLICT
                && kindOf(precise) == kind) {
            return precise;
        }
        SlotType merged = slot < analysis.localDecl.length ? analysis.localDecl[slot] : SlotType.UNSET_T;
        if (merged.kind != SlotType.UNSET && merged.kind != SlotType.CONFLICT && kindOf(merged) == kind) {
            return merged;
        }
        return typeOfKind(kind);
    }

    private static int kindOf(SlotType t) {
        switch (t.kind) {
            case SlotType.FLOAT:
                return K_FLOAT;
            case SlotType.LONG:
                return K_LONG;
            case SlotType.DOUBLE:
                return K_DOUBLE;
            case SlotType.REF:
                return K_REF;
            default:
                return K_INT;
        }
    }

    private static SlotType typeOfKind(int kind) {
        switch (kind) {
            case K_FLOAT:
                return SlotType.FLOAT_T;
            case K_LONG:
                return SlotType.LONG_T;
            case K_DOUBLE:
                return SlotType.DOUBLE_T;
            case K_REF:
                return SlotType.OBJECT_T;
            default:
                return SlotType.INT_T;
        }
    }

    private static int kindOfLoad(int op) {
        switch (op) {
            case O.LLOAD: return K_LONG;
            case O.FLOAD: return K_FLOAT;
            case O.DLOAD: return K_DOUBLE;
            case O.ALOAD: return K_REF;
            default: return K_INT;
        }
    }

    private static int kindOfStore(int op) {
        switch (op) {
            case O.LSTORE: return K_LONG;
            case O.FSTORE: return K_FLOAT;
            case O.DSTORE: return K_DOUBLE;
            case O.ASTORE: return K_REF;
            default: return K_INT;
        }
    }

    // --------------------------------------------------------------- helpers

    private Val constant(int idx) {
        switch (cp.tag(idx)) {
            case ConstantPool.INTEGER:
                return new Val(Integer.toString(cp.intValue(idx)), SlotType.INT_T, null);
            case ConstantPool.FLOAT: {
                float f = cp.floatValue(idx);
                if (Float.isNaN(f)) {
                    return new Val("java.lang.Float.NaN", SlotType.FLOAT_T, null);
                }
                if (Float.isInfinite(f)) {
                    return new Val(f > 0 ? "java.lang.Float.POSITIVE_INFINITY"
                            : "java.lang.Float.NEGATIVE_INFINITY", SlotType.FLOAT_T, null);
                }
                return new Val(Float.toString(f) + "f", SlotType.FLOAT_T, null);
            }
            case ConstantPool.LONG:
                return new Val(cp.longValue(idx) + "L", SlotType.LONG_T, null);
            case ConstantPool.DOUBLE: {
                double d = cp.doubleValue(idx);
                if (Double.isNaN(d)) {
                    return new Val("java.lang.Double.NaN", SlotType.DOUBLE_T, null);
                }
                if (Double.isInfinite(d)) {
                    return new Val(d > 0 ? "java.lang.Double.POSITIVE_INFINITY"
                            : "java.lang.Double.NEGATIVE_INFINITY", SlotType.DOUBLE_T, null);
                }
                return new Val(Double.toString(d) + "d", SlotType.DOUBLE_T, null);
            }
            case ConstantPool.STRING:
                return new Val(quote(cp.stringValue(idx)), SlotType.STRING_T, null);
            case ConstantPool.CLASS: {
                String name = cp.className(idx);
                String type = name.startsWith("[") ? types.ref(name)
                        : types.refInternal(name);
                return new Val(type + ".class", SlotType.ref("java/lang/Class"), null);
            }
            default:
                return new Val("null", SlotType.NULL_T, null);
        }
    }

    private boolean emitCondition(String cond, int target) {
        if (flat) {
            return false;
        }
        String flag = "__c" + (condSeq++);
        out.line("final boolean " + flag + " = " + cond + ";");
        spill();
        out.line("if (" + flag + ") {");
        out.indent();
        out.line(PC + " = " + target + ";");
        out.line("continue;");
        out.outdent();
        out.line("}");
        return false;
    }

    private boolean emitSwitch(Instruction ins) {
        Val key = pop();
        String sw = "__sw" + (condSeq++);
        out.line("final int " + sw + " = " + coerce(key, SlotType.INT_T) + ";");
        spill();
        out.line("switch (" + sw + ") {");
        out.indent();
        for (int i = 0; i < ins.switchKeys.length; i++) {
            out.line("case " + ins.switchKeys[i] + ": " + PC + " = " + ins.switchTargets[i] + "; continue;");
        }
        out.line("default: " + PC + " = " + ins.defaultTarget + "; continue;");
        out.outdent();
        out.line("}");
        return true;
    }

    /**
     * {@code invokedynamic}: the call site is described by a bootstrap method.
     *
     * <p>Two bootstrap targets matter in practice, and both are translated to
     * plain Java:</p>
     *
     * <ul>
     *   <li>{@code StringConcatFactory} — compiled string concatenation, emitted as
     *       {@code a + b + c} using the recipe carried in the bootstrap arguments;</li>
     *   <li>{@code LambdaMetafactory} — a lambda, emitted as an anonymous class
     *       implementing the functional interface.</li>
     * </ul>
     *
     * <p>Anything else becomes a stub that compiles and throws when reached, so a
     * single exotic call site cannot break the whole class.</p>
     */
    private boolean emitInvokeDynamic(Instruction ins) {
        int idx = ins.operand(0);
        String desc = cp.refType(idx);
        if (desc == null) {
            out.line("throw new IllegalStateException(\"bad invokedynamic at " + ins.pc + "\");");
            return true;
        }
        String[] args = Descriptor.args(desc);
        String ret = Descriptor.returnType(desc);

        ClassFile.BootstrapMethod[] bootstraps = cf.bootstrapMethods();
        int bootstrapIndex = cp.bootstrapIndex(idx);
        ClassFile.BootstrapMethod bm = bootstrapIndex >= 0 && bootstrapIndex < bootstraps.length
                ? bootstraps[bootstrapIndex] : null;

        String bsmOwner = null;
        String bsmName = null;
        if (bm != null) {
            // The bootstrap entry points at a MethodHandle; its owner and name
            // live on the referenced member, not on the handle itself.
            int target = cp.methodHandleIndex(bm.methodRef);
            bsmOwner = cp.refOwner(target);
            bsmName = cp.refName(target);
        }

        if (bm != null && "java/lang/invoke/StringConcatFactory".equals(bsmOwner)) {
            return emitStringConcat(ins, bm, args, "makeConcatWithConstants".equals(bsmName));
        }
        if (bm != null && "java/lang/invoke/LambdaMetafactory".equals(bsmOwner)) {
            return emitLambda(ins, bm, args, ret);
        }

        // Unknown bootstrap: consume the arguments so the stack stays consistent.
        for (int i = 0; i < args.length; i++) {
            pop();
        }
        out.line("throw new UnsupportedOperationException(\"invokedynamic " + bsmOwner
                + "." + bsmName + " at " + ins.pc + "\");");
        if (!"V".equals(ret)) {
            return true;
        }
        return false;
    }

    /** String concatenation: rebuild the {@code +} expression from the recipe. */
    private boolean emitStringConcat(Instruction ins, ClassFile.BootstrapMethod bm,
                                     String[] args, boolean withConstants) {
        List<String> parts = new ArrayList<>();
        List<SlotType> types = new ArrayList<>();
        for (int i = args.length - 1; i >= 0; i--) {
            Val v = pop();
            parts.add(0, v.code);
            types.add(0, v.type);
        }

        if (!withConstants || bm.args.length == 0) {
            StringBuilder sb = new StringBuilder("(\"\"");
            for (String p : parts) {
                sb.append(" + ").append(p);
            }
            sb.append(')');
            push(new Val(sb.toString(), SlotType.STRING_T, null));
            return false;
        }

        String recipe = cp.stringValue(bm.args[0]);
        if (recipe == null) {
            recipe = "";
        }

        StringBuilder sb = new StringBuilder("(\"\"");
        int argIndex = 0;
        int constIndex = 1;
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == '\u0001') {
                flushLiteral(sb, literal);
                if (argIndex < parts.size()) {
                    sb.append(" + ").append(parts.get(argIndex++));
                }
            } else if (c == '\u0002') {
                flushLiteral(sb, literal);
                if (constIndex < bm.args.length) {
                    String constant = cp.stringValue(bm.args[constIndex++]);
                    sb.append(" + ").append(quote(constant == null ? "" : constant));
                }
            } else {
                literal.append(c);
            }
        }
        flushLiteral(sb, literal);
        sb.append(')');
        push(new Val(sb.toString(), SlotType.STRING_T, null));
        return false;
    }

    private static void flushLiteral(StringBuilder sb, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        sb.append(" + ").append(quote(literal.toString()));
        literal.setLength(0);
    }

    /** Lambda: an anonymous class implementing the functional interface. */
    private boolean emitLambda(Instruction ins, ClassFile.BootstrapMethod bm,
                               String[] args, String interfaceDesc) {
        if (bm.args.length < 2) {
            for (int i = 0; i < args.length; i++) {
                pop();
            }
            out.line("throw new UnsupportedOperationException(\"lambda at " + ins.pc + "\");");
            return true;
        }

        int handleIndex = bm.args[1];
        int targetIndex = cp.methodHandleIndex(handleIndex);
        String implOwner = cp.refOwner(targetIndex);
        String implName = cp.refName(targetIndex);
        String implDesc = cp.refType(targetIndex);
        int handleKind = cp.methodHandleKind(handleIndex);
        String[] implArgs = Descriptor.args(implDesc == null ? "()V" : implDesc);
        String implRet = Descriptor.returnType(implDesc == null ? "()V" : implDesc);

        // Captured values, in order. Each one is copied into a final local: the
        // anonymous class may only capture effectively final variables, and a
        // recompiled method stores its locals many times.
        List<Val> captured = new ArrayList<>();
        for (int i = args.length - 1; i >= 0; i--) {
            captured.add(0, materializeForCapture(pop()));
        }

        String interfaceType = types.ref(interfaceDesc);
        String samDesc = bm.args.length > 0 ? cp.methodTypeDescriptor(bm.args[0]) : "()V";
        if (samDesc == null) {
            samDesc = "()V";
        }
        String samName = samMethodName(interfaceDesc);
        String[] samArgs = Descriptor.args(samDesc);
        String samRet = Descriptor.returnType(samDesc);

        StringBuilder sb = new StringBuilder("new ").append(interfaceType).append("() {\n");
        sb.append("public ").append(types.ref(samRet))
                .append(' ').append(samName).append('(');
        for (int i = 0; i < samArgs.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types.ref(samArgs[i])).append(" a").append(i);
        }
        sb.append(") {\n");

        // Everything the implementation method receives, in order: captured
        // values first, then the interface method's own arguments.
        List<String> actual = new ArrayList<>();
        List<SlotType> actualTypes = new ArrayList<>();
        for (Val v : captured) {
            actual.add(v.code);
            actualTypes.add(v.type);
        }
        for (int i = 0; i < samArgs.length; i++) {
            actual.add("a" + i);
            actualTypes.add(SlotType.ofDescriptor(samArgs[i]));
        }

        String ownerType = types.refInternal(implOwner);
        StringBuilder call = new StringBuilder();
        int implArgStart = 0;

        if (handleKind == 8) {
            // REF_newInvokeSpecial: the handle is a constructor.
            call.append("new ").append(ownerType).append('(');
        } else if (handleKind == 6) {
            call.append(ownerType).append('.').append(Names.external(implName)).append('(');
        } else {
            // An instance handle receives the receiver as its first argument. It is
            // either a captured value or, for an unbound reference such as
            // String::length, the interface method's first argument.
            String receiver = actual.isEmpty() ? "null" : actual.remove(0);
            actualTypes.remove(0);
            call.append("((").append(ownerType).append(")(").append(receiver).append("))")
                    .append('.').append(Names.external(implName)).append('(');
            implArgStart = 1;
        }

        List<String> callArgs = new ArrayList<>();
        for (int i = 0; i < actual.size(); i++) {
            String target = argAt(implArgs, implArgStart + i);
            callArgs.add(castTo(actual.get(i), actualTypes.get(i), target));
        }
        for (int i = 0; i < callArgs.size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append(callArgs.get(i));
        }
        call.append(')');

        if ("V".equals(samRet)) {
            sb.append(call).append(";\n");
        } else {
            String value = castTo(call.toString(), SlotType.ofDescriptor(implRet), samRet);
            sb.append("return ").append(value).append(";\n");
        }
        sb.append("}\n}");

        push(new Val(sb.toString(), SlotType.ref(Descriptor.internalName(interfaceDesc)), null));
        return false;
    }

    private static String argAt(String[] args, int index) {
        return index < args.length ? args[index] : "Ljava/lang/Object;";
    }

    /**
     * Copies a value into a {@code final} local so that a lambda can capture it.
     * Literals and {@code this} are already effectively final and stay as they are.
     */
    private Val materializeForCapture(Val v) {
        String code = v.code == null ? "null" : v.code.trim();
        if (code.equals("this") || code.equals("null") || code.equals("true") || code.equals("false")
                || code.matches("-?\\d+[lLfFdD]?") || code.startsWith("\"")) {
            return v;
        }
        String name = "__cap" + (tempSeq++);
        String type = javaType(v.type);
        out.line("final " + type + " " + name + " = " + coerce(v, v.type) + ";");
        return new Val(name, v.type, null);
    }

    /** Casts an expression to a parameter type, tolerating unknown descriptors. */
    private String castTo(String code, SlotType actual, String desc) {
        if (desc == null || desc.isEmpty()) {
            return code;
        }
        SlotType target = SlotType.ofDescriptor(desc);
        String coerced = coerce(new Val(code, actual, null), target);
        return coerced;
    }

    /** Name of the single abstract method of a functional interface. */
    private String samMethodName(String interfaceDesc) {
        String internal = Descriptor.internalName(interfaceDesc);
        // The interface is usually not the current class, so reflection is the
        // most reliable source; fall back to the conventional names otherwise.
        try {
            Class<?> c = Class.forName(internal.replace('/', '.'));
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) {
                    return m.getName();
                }
            }
        } catch (Throwable ignored) {
            // not on the class path
        }
        if ("java/util/Comparator".equals(internal)) {
            return "compare";
        }
        if ("java/lang/Runnable".equals(internal)) {
            return "run";
        }
        return "apply";
    }

    private boolean emitInvoke(Instruction ins) {
        int idx = ins.operand(0);
        String owner = cp.refOwner(idx);
        String name = cp.refName(idx);
        String desc = cp.refType(idx);
        if (owner == null || name == null || desc == null) {
            out.line("throw new IllegalStateException(\"bad invoke at " + ins.pc + "\");");
            return true;
        }
        String[] args = Descriptor.args(desc);
        String ret = Descriptor.returnType(desc);

        List<String> argCodes = new ArrayList<>();
        for (int i = args.length - 1; i >= 0; i--) {
            Val v = pop();
            argCodes.add(0, coerceTo(v.code, v.type, args[i]));
        }

        // Resources of a recompiled suite are files, not JAR entries, so the
        // class loader lookup is redirected to the asset resolver.
        if ("java/lang/Class".equals(owner) && "getResourceAsStream".equals(name)) {
            pop();
            String call = "j2pc.rt.Assets.open(" + argCodes.get(0) + ", \""
                    + Names.packageName(cf.name()) + "\")";
            if ("V".equals(ret)) {
                out.line(call + ";");
            } else {
                push(new Val(call, SlotType.ofDescriptor(ret), null));
            }
            return false;
        }

        boolean isInit = "<init>".equals(name);
        StringBuilder call = new StringBuilder();

        if (ins.op == O.INVOKESTATIC) {
            call.append(types.refInternal(owner)).append('.').append(names.method(owner, name, desc)).append('(');
        } else {
            Val recv = pop();
            if (ins.op == O.INVOKESPECIAL && isInit) {
                if (recv.freshNew != null) {
                    String expr = "new " + types.refInternal(owner) + "(";
                    StringBuilder inner = new StringBuilder(expr);
                    appendArgs(inner, argCodes);
                    inner.append(')');
                    if (prologueMode) {
                        // Inside a constructor prologue the object must be used
                        // inline, because declarations may only appear after the
                        // mandatory super() call.
                        for (int i = 0; i < stack.size(); i++) {
                            Val v = stack.get(i);
                            if (v.code.equals(recv.code) && v.freshNew != null) {
                                stack.set(i, new Val(inner.toString(), SlotType.ref(owner), null));
                            }
                        }
                        return false;
                    }
                    out.line(recv.code + " = " + inner + ";");
                    // <init> consumes the receiver and produces nothing: the
                    // reference that `dup` left on the stack is the same object.
                    for (int i = 0; i < stack.size(); i++) {
                        Val v = stack.get(i);
                        if (v.code.equals(recv.code) && v.freshNew != null) {
                            stack.set(i, new Val(v.code, SlotType.ref(owner), null));
                        }
                    }
                    return false;
                }
                // A constructor call on an already constructed object is either
                // the mandatory this(...) or super(...) chain. Only the receiver
                // `this` can appear here; anything else would be a call on a
                // different instance, which Java cannot express.
                if ("this".equals(recv.code) && owner.equals(cf.name())) {
                    call.append("this(");
                } else if ("this".equals(recv.code)) {
                    call.append("super(");
                } else {
                    // Valid bytecode can only reach <init> on an object that is
                    // still uninitialised. The freshNew marker is lost when the
                    // reference crosses a basic block, so the call is rebuilt as
                    // an assignment: `v = new Owner(args)`.
                    String expr = "new " + types.refInternal(owner) + "(";
                    StringBuilder inner = new StringBuilder(expr);
                    appendArgs(inner, argCodes);
                    inner.append(')');
                    out.line(recv.code + " = " + inner + ";");
                    for (int i = 0; i < stack.size(); i++) {
                        Val v = stack.get(i);
                        if (v.code.equals(recv.code)) {
                            stack.set(i, new Val(v.code, SlotType.ref(owner), null));
                        }
                    }
                    return false;
                }
            } else if (ins.op == O.INVOKESPECIAL && "this".equals(recv.code)
                    && owner.equals(cf.superName())) {
                call.append("super.").append(names.method(owner, name, desc)).append('(');
            } else {
                call.append(cast(recv, owner)).append('.').append(names.method(owner, name, desc)).append('(');
            }
        }
        appendArgs(call, argCodes);
        call.append(')');

        if (isInit || "V".equals(ret)) {
            out.line(call + ";");
            return false;
        }
        // The call may be discarded by a following `pop`, but its side effects
        // must survive: mark it so `discard` still emits the statement.
        push(fromCall(call.toString(), ret).withEffect());
        return false;
    }

    private void appendArgs(StringBuilder sb, List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String arg = args.get(i);
            // `append(null)` is ambiguous between the String and char[] overloads,
            // so a bare null literal has to be typed explicitly.
            sb.append("null".equals(arg) ? "(java.lang.Object) null" : arg);
        }
    }

    private String cast(Val recv, String owner) {
        String want = types.refInternal(owner);
        if (recv.type.kind == SlotType.REF) {
            if (recv.type.desc != null) {
                String have = recv.type.desc.startsWith("[") ? types.ref(recv.type.desc)
                        : types.refInternal(recv.type.desc);
                if (have.equals(want)) {
                    return recv.code;
                }
            } else {
                return "((" + want + ")(" + recv.code + "))";
            }
        }
        return "((" + want + ")(" + recv.code + "))";
    }

    private String typeRef(String internalName) {
        if (internalName == null) {
            return "java.lang.Object";
        }
        return types.refInternal(internalName);
    }

    /**
     * Java type of an analysed value, mapped through the type mapper so that a
     * relocated class is referenced by its new package.
     */
    private String javaType(SlotType t) {
        if (t == null) {
            return "java.lang.Object";
        }
        if (t.kind == SlotType.REF && t.desc != null) {
            return types.refInternal(t.desc);
        }
        // Arrays of relocated classes also need mapping.
        String raw = t.javaType();
        if (t.kind == SlotType.REF && raw.endsWith("[]")) {
            return javaType(SlotType.ofDescriptor(t.descriptor())) + "[]";
        }
        return raw;
    }

    /**
     * Array descriptor of a value ({@code [Z}, {@code [Ljava/lang/String;}) when
     * the analysis knows it, otherwise {@code null}.
     */
    private static String arrayDescOf(Val v) {
        if (v.sem != null && v.sem.kind == SlotType.REF && v.sem.desc != null
                && v.sem.desc.startsWith("[")) {
            return v.sem.desc;
        }
        if (v.type != null && v.type.kind == SlotType.REF && v.type.desc != null
                && v.type.desc.startsWith("[")) {
            return v.type.desc;
        }
        return null;
    }

    /**
     * Expression to index into {@code arr}. When the variable holding the array
     * has a merged static type ({@code Object}) but the analysis knows the real
     * type, a cast is inserted. If even the analysis lost the type, the array
     * kind implied by the opcode is used as a last resort.
     */
    private String arrayExpr(Val arrRaw, String fallbackArrayType) {
        // A compound receiver (`new int[2]`, `a.b()`) must be evaluated once into a
        // temporary: `new int[2][0] = 7` is not a statement.
        Val arr = materialize(arrRaw);
        boolean staticallyArray = arr.type.kind == SlotType.REF
                && arr.type.desc != null && arr.type.desc.startsWith("[");
        if (staticallyArray) {
            return arr.code;
        }
        boolean knownArray = arr.sem.kind == SlotType.REF
                && arr.sem.desc != null && arr.sem.desc.startsWith("[");
        if (knownArray) {
            return "((" + javaType(arr.sem) + ")(" + arr.code + "))";
        }
        if (fallbackArrayType == null) {
            return arr.code;
        }
        return "((" + fallbackArrayType + ")(" + arr.code + "))";
    }

    private static SlotType elemType(SlotType arrayType) {
        if (arrayType.kind != SlotType.REF || arrayType.desc == null || !arrayType.desc.startsWith("[")) {
            return SlotType.OBJECT_T;
        }
        String e = arrayType.desc.substring(1);
        if (e.startsWith("L")) {
            return SlotType.ref(e.substring(1, e.length() - 1));
        }
        if (e.startsWith("[")) {
            return SlotType.ref(e);
        }
        return SlotType.INT_T;
    }

    /**
     * Builds {@code new T[n][]...} for {@code anewarray}. The component type may
     * itself be an array ({@code anewarray [I} creates {@code int[n][]}).
     *
     * <p>Note that a single-letter class name such as {@code n} is <em>not</em> a
     * primitive descriptor, even though it has the same length — obfuscators rely
     * on exactly that confusion.</p>
     */
    private String arrayCreation(String componentInternalName, String size) {
        String desc = componentInternalName;
        int dims = 0;
        if (desc.startsWith("[")) {
            while (dims < desc.length() && desc.charAt(dims) == '[') {
                dims++;
            }
            desc = desc.substring(dims);
        }
        String base;
        if (desc.startsWith("L") && desc.endsWith(";")) {
            base = types.ref(desc);
        } else if (desc.length() == 1 && Descriptor.isPrimitive(desc)) {
            base = Descriptor.javaType(desc);
        } else {
            base = types.refInternal(desc);
        }
        StringBuilder sb = new StringBuilder("new ").append(base).append('[').append(size).append(']');
        for (int i = 0; i < dims; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    private static String arrayDesc(int atype) {
        switch (atype) {
            case 4: return "[Z";
            case 5: return "[C";
            case 6: return "[F";
            case 7: return "[D";
            case 8: return "[B";
            case 9: return "[S";
            case 10: return "[I";
            case 11: return "[J";
            default: return "[I";
        }
    }

    private static String binaryOperator(int op) {
        switch (op) {
            case O.IADD: case O.LADD: case O.FADD: case O.DADD: return "+";
            case O.ISUB: case O.LSUB: case O.FSUB: case O.DSUB: return "-";
            case O.IMUL: case O.LMUL: case O.FMUL: case O.DMUL: return "*";
            case O.IDIV: case O.LDIV: case O.FDIV: case O.DDIV: return "/";
            case O.IREM: case O.LREM: case O.FREM: case O.DREM: return "%";
            case O.ISHL: case O.LSHL: return "<<";
            case O.ISHR: case O.LSHR: return ">>";
            case O.IUSHR: case O.LUSHR: return ">>>";
            case O.IAND: case O.LAND: return "&";
            case O.IOR: case O.LOR: return "|";
            case O.IXOR: case O.LXOR: return "^";
            default: return "+";
        }
    }

    private static SlotType arithResultType(int op) {
        if (op == O.LADD || op == O.LSUB || op == O.LMUL || op == O.LDIV || op == O.LREM
                || op == O.LAND || op == O.LOR || op == O.LXOR) {
            return SlotType.LONG_T;
        }
        if (op == O.FADD || op == O.FSUB || op == O.FMUL || op == O.FDIV || op == O.FREM) {
            return SlotType.FLOAT_T;
        }
        if (op == O.DADD || op == O.DSUB || op == O.DMUL || op == O.DDIV || op == O.DREM) {
            return SlotType.DOUBLE_T;
        }
        return SlotType.INT_T;
    }

    private static String intCompareOp(int op) {
        switch (op) {
            case O.IFEQ: return "==";
            case O.IFNE: return "!=";
            case O.IFLT: return "<";
            case O.IFGE: return ">=";
            case O.IFGT: return ">";
            case O.IFLE: return "<=";
            default: return "==";
        }
    }

    private static String refCompareOp(int op) {
        switch (op) {
            case O.IF_ICMPEQ: case O.IF_ACMPEQ: return "==";
            case O.IF_ICMPNE: case O.IF_ACMPNE: return "!=";
            case O.IF_ICMPLT: return "<";
            case O.IF_ICMPGE: return ">=";
            case O.IF_ICMPGT: return ">";
            case O.IF_ICMPLE: return "<=";
            default: return "==";
        }
    }

    static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
