# Deobfuscation

Design notes for the optional deobfuscation pass. Written before implementation, to keep the
scope honest.

## The question

> Should J2PC have a built-in deobfuscator? And how would it even tell whether an application
> is obfuscated?

Short answer: **yes, but only for structural and naming *hints* — not for semantic renaming.**
And yes, detection is very tractable: obfuscation leaves fingerprints that are cheap to measure.

## Why semantic renaming is out of scope

Recovering meaningful names (`a` → `applyGravity`) requires understanding what the code *does*.
That is an AI-complete problem and, more importantly, it is unfalsifiable: nobody can prove the
name is right. A wrong name is worse than no name, because it lies to the reader.

So the goal is not "recover the original source". The goal is **make the output reviewable**.

## What is worth doing

Three tiers, in increasing risk:

| Tier | What | Risk | Value |
|------|------|------|-------|
| 1 | **Detection** — measure and report obfuscation | none | high: tells the user what they got, and drives the rest |
| 2 | **Structural rewrites** — recover idioms that were compiled, not obfuscated | low: pattern is provable | high: `synchronized`, string concat, `for` loops |
| 3 | **Naming hints** — rename only when the *shape* of the code proves the role | low (cosmetic) | medium: `Canvas` subclass → `GameCanvasImpl` |
| 4 | **String decryption** — run the decryptor at recompile time | medium | high when present |
| 5 | **Semantic renaming** | — | **not doing it** |

## Tier 1: detecting obfuscation

Obfuscators are tools with fixed defaults. Their output is statistically distinctive, and every
signal below is cheap to compute from the `ClassFile` model we already have.

### Signals

Each signal yields a score in `[0, 1]`; the report shows the raw measurement next to it.

#### Name shape

| Signal | Measurement | Obfuscated when |
|--------|-------------|-----------------|
| Mean identifier length | `avg(len(name))` over classes + fields + methods | `< 4` |
| Short-name ratio | share of names with `len <= 2` | `> 0.5` |
| ProGuard alphabet | share matching `^[a-z]{1,2}[0-9]*$` | `> 0.4` |
| Package depth | mean dots in class names | `< 0.3` |
| Default package share | classes with no package | `> 0.3` |

ProGuard's default alphabet is `a..z`, then `aa..zz`, then `a1..z9`. A suite where 70% of
classes are called `a`, `b`, `c` is not subtly obfuscated, it is obviously obfuscated.

#### Debug information

| Signal | Measurement | Obfuscated when |
|--------|-------------|-----------------|
| `SourceFile` present | share of classes with the attribute | `< 0.2` |
| `LineNumberTable` present | share of methods with the attribute | `< 0.2` |
| `LocalVariableTable` present | share of methods with the attribute | `< 0.2` |

This is the single most reliable signal. Compilers emit debug info by default; obfuscators strip
it by default. A suite with 200 classes and zero `SourceFile` attributes is obfuscated, full stop.

It also has an immediate practical consequence: without `LocalVariableTable` there are no
argument names to use, so the emitter should not waste effort looking for them.

#### Structure

| Signal | Measurement | Obfuscated when |
|--------|-------------|-----------------|
| Synthetic accessor density | share of methods named `access$NNN` | `> 0.05` |
| Name/descriptor collisions | fields with the same name and different descriptors | `> 0` |
| Class file age | major version | `<= 45` with modern patterns |

Name collisions are a ProGuard artifact: the JVM allows `a:I` and `a:Ljava/lang/String;` in one
class, Java does not. Seeing them means the class went through an obfuscator. J2PC already
handles them (see `NameRegistry`), but their presence is a diagnostic.

#### Strings

| Signal | Measurement | Obfuscated when |
|--------|-------------|-----------------|
| Encrypted-string share | share of `ldc` strings that are not valid text | `> 0.3` |
| Decryptor presence | a static method taking `String`/`byte[]` and returning `String`, called from many classes | `> 0` |
| Constant-array density | large `static final byte[]`/`int[]` initialised in `<clinit>` | `> 0` |

"Not valid text" is measurable: the string is either not decodable, or its character
distribution is far from the language of the app, or its length is a multiple of 4 and it is
base64-alphabet only.

#### Control flow

| Signal | Measurement | Obfuscated when |
|--------|-------------|-----------------|
| Flattening | share of methods whose entry block is `while(true) { switch (state) }` | `> 0.3` |
| `goto` density | `goto` count per 100 instructions | `> 20` |
| Opaque predicates | branches on a constant (`iconst_1; ifeq`) | `> 0.1` |
| Unreachable code | share of instructions with no frame after analysis | `> 0.05` |

**A note on irony.** J2PC's own dispatch-loop output *is* control-flow flattening. That is why
the detector must run on the **input** class files, never on the generated sources. It also means
that a suite which was flattened by an obfuscator recompiles into something structurally similar
to what J2PC produces anyway — so for those suites the readability cost of the dispatch loop is
zero, and structuring would gain nothing. Detection tells us that, which is useful.

### Composite score

```java
score = 0.25 * nameScore
      + 0.30 * debugScore
      + 0.15 * structureScore
      + 0.15 * stringScore
      + 0.15 * flowScore
```

Debug info is weighted highest because it is the most reliable. The report prints both the
composite and the per-signal breakdown, so a false positive is explainable.

| Score | Verdict |
|-------|---------|
| `< 0.25` | not obfuscated |
| `0.25 – 0.55` | lightly obfuscated (names only) |
| `> 0.55` | heavily obfuscated |

### What detection buys us

Detection is not a goal in itself; it is a **policy switch**:

| Detected | Behaviour |
|----------|-----------|
| debug info stripped | skip debug-name lookups entirely; use `arg0`, `v3` |
| names obfuscated | offer `--rename-hints`, enable the naming strategy |
| strings encrypted | enable the string inliner (tier 4) |
| control flow flattened | do not attempt structuring; the dispatch loop is already optimal |
| nothing detected | emit cleaner code: prefer `for`/`while`, keep original names |

So the flag is not `--deobfuscate`; it is `--auto`, and the detector decides.

## Tier 2: structural rewrites

These are *not* deobfuscation. They recover idioms that were compiled away, and they are safe
because the pattern is matched structurally and the rewrite is provably equivalent.

Each is a small rewriter over `(Instruction[], Analysis)`:

| Rewrite | Pattern | Output |
|---------|---------|--------|
| `synchronized` | `Monitor.enter(x)` in a `try` whose only exit is `Monitor.exit(x)` | `synchronized (x) { … }` |
| String concatenation | `new StringBuffer(); … .append(…)` chain ending in `toString()` | `a + b + c` |
| `for` loop | `i = 0; while (i < n) { … i++; }` | `for (int i = 0; i < n; i++)` |
| `switch` over strings | `hashCode()` + `equals()` chain (Java 6 pattern) | `switch (s)` |
| Ternary | diamond where one branch assigns and rejoins | `x = c ? a : b` |
| `foreach` | `Iterator` + `hasNext()`/`next()` loop | `for (T x : coll)` |

The last two are the riskiest and belong behind `--structured`, which is already on the roadmap.

Ordering matters: run rewrites to a fixed point, cheapest first, and never let one rewrite
consume the output of another in the same pass without re-analysing.

## Tier 3: naming hints

Rename only when the *shape* of the class proves the role. The rule is: the new name must be
derivable from the class hierarchy and the method signatures alone, with no guessing about
behaviour.

| Evidence | Rename to |
|----------|-----------|
| `extends javax.microedition.midlet.MIDlet` | `<JadName>MIDlet` or `AppMIDlet` |
| `extends Canvas`/`GameCanvas` and has `paint` | `XxxCanvas` (numbered: `Canvas1`) |
| `extends java.lang.Thread` | `Worker` |
| implements `Runnable` and has `run` | `Task` |
| `implements CommandListener` | `CommandHandler` |
| fields only, all with getters/setters | keep, but mark `/* data */` |
| `$1`, `$2` suffix | keep the `$` (anonymous classes are real) |

Names are suffixed with a counter to stay unique. Every rename is listed in the report, so the
reader can see what was invented and what was original.

This is cosmetic and reversible: `--rename-hints=off` gives the raw names back.

## Tier 4: string decryption

Many commercial suites encrypt their strings and decrypt them lazily through a generated
helper. The pattern is recognisable:

1. a `static` method taking `String` (or `byte[]`) and returning `String`,
2. called from `<clinit>` or from many different classes,
3. whose body is a short loop over `char[]` with `^`, `+`, or a `char` shift,
4. with a constant key field.

Since J2PC runs on a JVM, it can **execute** that decryptor:

1. load the decrypted helper class in an isolated class loader,
2. call it for every encrypted literal,
3. replace the `ldc` with the plaintext.

Risks, and how to contain them:

* the helper might have side effects — only run methods whose bytecode is a pure loop
  (no field writes outside its own class, no calls except `charAt`/`length`/array ops),
* the helper might hang — run it on a watchdog thread with a timeout,
* the helper might depend on other classes — run it in a sandbox class loader with only the
  suite on the path.

When it works, it is the single biggest readability win: `a_m3("Q2hhbmdl")` becomes
`"Change"`. When it does not, nothing is lost.

This one is deliberately last: it is the only tier that executes code from the input.

## Architecture

```
j2pc.deob
  ObfuscationDetector     → ObfuscationReport (per-signal scores + verdict)
  ObfuscationReport       → text and JSON rendering
  NamingStrategy          (None | Heuristic | Custom)
  Rewriter                interface: canApply(...) / apply(...)
  rewrite/
    SynchronizedRewriter
    ConcatRewriter
    ForLoopRewriter
    StringSwitchRewriter
    TernaryRewriter
  StringDecryptor         sandboxed execution of the decrypt helper
```

The detector runs once, on the parsed input, before naming. The rewriters run per method,
between analysis and emission, and each one re-runs the analysis when it changes the
instruction stream.

## How we know we did not break anything

This is the part that makes the whole idea safe: **the batch corpus**.

`tools/batch.ps1` recompiles 151 real applications and compiles the result. Every rewriter is
validated the same way:

1. recompile the corpus with the rewriter off, record a hash of every generated method,
2. recompile with the rewriter on,
3. diff: only methods that *matched the pattern* may change,
4. compile everything: no new compile errors,
5. for a handful of apps, run them and compare screenshots frame by frame.

Any rewrite that changes a method it did not match is a bug, by definition.

## Report format

```
Obfuscation: heavy (score 0.71)
  names      0.85  mean length 2.1, 78% short names, 64% ProGuard alphabet
  debug      1.00  0/214 classes have SourceFile, 0/3102 methods have LineNumberTable
  structure  0.40  12 fields collide by name, 0.08 synthetic accessors
  strings    0.60  41% of literals are not text, decryptor found: bd.a(String)String
  flow       0.55  47% of methods start with a dispatch switch
Policy: dispatch loop kept, naming hints on, string inlining attempted (38/41 decrypted)
```

## Decision

Implement in this order:

1. **Detector + report** — no risk, immediate value, drives everything else. *Do this first.*
2. **Concat and `synchronized` rewrites** — biggest readability gain per unit of risk.
3. **Naming hints** — cosmetic, cheap, easy to switch off.
4. **String decryption** — highest value, highest risk; only after the corpus harness can prove
   it changes nothing it should not.

Tier 5 stays out.
