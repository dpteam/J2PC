# Architecture

J2PC is a pipeline of small, independent stages. Each stage has one job and can be used on
its own — the disassembler and the analysis tracer in `j2pc.tools` are exactly that.

```
Main ──► Recompiler ──► JarReader ──► ClassReader ──► ClassFile
                                     (classfile)
                                          │
                                          ▼
                          NameRegistry ◄──┴──► ClassEmitter
                                                  │
                                                  ├──► MethodEmitter ──► Analyzer
                                                  │                        │
                                                  │                        ▼
                                                  │                    Analysis (frames)
                                                  ▼
                                          Java source files
```

## Stages

### `j2pc.jar`

`JarReader` is a thin `ZipFile` wrapper. `JadFile` parses the JAD descriptor: MIDlet name,
vendor, version, profile and the `MIDlet-N` entries. The JAD is optional; when it is missing
the launcher guesses the MIDlet by looking for the class that extends
`javax.microedition.midlet.MIDlet`.

### `j2pc.classfile`

A direct model of the class file format:

* `ClassReader` — the byte-level parser,
* `ClassFile` — the model (constant pool, fields, methods, attributes, `Code`),
* `ConstantPool` — typed accessors over the raw entries,
* `Descriptor` — descriptor to Java-type conversion.

Nothing is resolved lazily; a class is fully in memory after parsing, which keeps the later
stages simple.

### `j2pc.bytecode`

`Decoder` performs a linear sweep. It produces `Instruction` objects with absolute branch
targets for every conditional jump, `goto`, and both switch forms.

The decoder is intentionally strict: an unknown opcode is an error, not a silent skip,
because silently skipping shifts the whole method and produces nonsense later.

### `j2pc.analyze`

`Analyzer` is an abstract interpreter:

1. **Leaders.** Basic block starts are the method entry, every branch target, the instruction
   after a branch, and every exception table boundary.
2. **Propagation.** A worklist starts at pc 0 with the argument types in the locals. Each
   instruction is simulated on the abstract stack; successors get merged frames.
3. **Merge.** Two frames merge slot by slot. Same kind keeps the type; conflicting kinds
   become `CONFLICT`, which the emitter maps to `Object`.
4. **Declaration types.** After the fixpoint, every local and every stack depth gets one
   merged type — this is what the emitter declares.

`SlotType` is the lattice element: `int`, `float`, `long`, `double`, a reference with an
internal name, `UNSET`, or `CONFLICT`.

### `j2pc.emit`

`ClassEmitter` renders one compilation unit. `MethodEmitter` renders one method body and is
by far the most intricate part of the project.

`NameRegistry` assigns Java identifiers **before** any source is written, in two passes:

1. collect all type names of the application, so no member is ever named like a type
   (`d.foo()` would otherwise resolve to a field),
2. assign unique field and method names per declaring class.

This matters because obfuscated suites reuse the same short name for unrelated members across
classes; if names were assigned per reference site, a call could end up pointing at a method
that does not exist.

## Method emission

### Dispatch mode

Every method that has branches or exception handlers is emitted as:

```java
public void run() {
    int __pc = 0;
    while (true) {
        switch (__pc) {
        case 0:
            ...
            __pc = 14;
            continue;
        case 14:
            ...
        default:
            throw new IllegalStateException("invalid pc " + __pc);
        }
    }
}
```

Why a dispatch loop instead of structured `if`/`while`?

* It is **exact**. Structured output requires control-flow restructuring, which is where
  decompilers give up or, worse, silently produce different behaviour.
* It handles irreducible flow graphs, overlapping exception ranges and jumps into the middle
  of loops without special cases.
* It is easy to verify: the mapping from bytecode to Java is close to one-to-one.

The cost is readability. `--no-comments`/`--no-lines` and a future structuring pass
(see the roadmap) address that.

### Flat mode

A method with a single basic block and no handlers is emitted as straight-line Java — no
dispatch loop, no `__pc`. Constructors and simple accessors usually land here.

### Values

The emitter keeps a symbolic stack of `Val` objects. Each holds:

* `code` — the Java expression,
* `type` — the static type javac will infer,
* `sem` — the more precise type from the analysis,
* `freshNew` — set for `new X` results that have not been initialised yet,
* `effect` — whether evaluating the expression has side effects.

`effect` matters: `pop` after a call must still emit the call, otherwise side effects
disappear. `freshNew` matters because `new X` followed by `dup` and `invokespecial <init>`
has to become `x = new X(...)`, not `new X().<init>()`.

### Coercion

`coerce(value, targetType)` inserts the casts Java needs and the JVM does not: boxing when a
primitive crosses an `Object` slot, unboxing on the way back, `(int)`/`(long)` between numeric
kinds, and casts between reference types. `boolean` is special-cased: bytecode treats it as an
`int` on the stack, so loads widen it and stores narrow it.

### Constructors

Java requires `super(...)` or `this(...)` to be the first statement. The emitter therefore
emits the bytecode up to and including the constructor call as a *prologue* before the
dispatch loop, and everything after it inside the loop.

### Exceptions

A handler range becomes a `try` around the covered blocks:

```java
try {
    ...
} catch (java.lang.Throwable __ex) {
    if (!(__ex instanceof java.io.IOException)) {
        throw j2pc.rt.Sneaky.sneak(__ex);
    }
    s0 = __ex;
    __pc = 42;
    continue;
}
```

The type check reproduces the exception table, which matches by type; catching `Throwable`
and re-checking keeps nested handlers and `finally` blocks correct.

Every generated body is additionally wrapped so that an unexpected checked exception is
rethrown unchanged:

```java
try {
    ... body ...
} catch (java.lang.Throwable __unchecked) {
    throw j2pc.rt.Sneaky.sneak(__unchecked);
}
```

## Runtime

`j2pc.rt` is deliberately tiny. It exists only for things Java source cannot express:

| Class | Why |
|-------|-----|
| `Cmp` | `fcmpg`/`fcmpl`/`dcmpg`/`dcmpl` differ in NaN handling from Java operators |
| `Monitor` | `monitorenter`/`monitorexit` have no Java syntax |
| `Sneaky` | rethrow checked exceptions without wrapping |
| `Box` | primitive ↔ `Object` bridge for merged slots |
| `Assets` | resource lookup, because a recompiled suite has no JAR |
| `Arrays` | reflective `arraylength` when the array type is unknown |

`Monitor` uses identity-based weak buckets. A `HashMap` would be wrong: `Vector` and friends
define content-based `equals`, so a lock looked up after the collection changed would be a
different object and `monitorexit` would fail.

## MIDP emulation

`src/javax/microedition/**` implements the platform on top of AWT/Swing. The layout mirrors
the real API so that recompiled code needs no source changes:

* `Display` owns the single window and the current `Displayable`,
* `Canvas`/`GameCanvas` receive key and pointer events from a Swing panel,
* `Graphics` wraps `Graphics2D` and keeps MIDP semantics for translate, clip and anchors,
* `RecordStore` persists to `~/.j2pc/rms/<store>.rms`,
* `Connector` implements `http`, `https` and `file`.

## Design rules

1. **Never guess.** If the analysis cannot prove something, emit a helper call rather than a
   cast that may fail at run time.
2. **Never drop side effects.** Every instruction that can have an observable effect must
   appear in the output, even if its result is discarded.
3. **Fail loudly at build time, not at run time.** Unsupported bytecode produces a stub with a
   comment and a warning, never silently wrong code.
4. **Keep the runtime small.** Anything expressible in Java source belongs in the emitter, not
   in `j2pc.rt`.
