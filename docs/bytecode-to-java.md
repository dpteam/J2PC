# Bytecode to Java

Every opcode J2PC understands and how it becomes Java. The generated code assumes two
conventions:

* `s0`, `s1`, … hold the operand stack at a block boundary,
* `__pc` holds the dispatch address.

## Constants

| Bytecode | Java |
|----------|------|
| `iconst_m1` … `iconst_5` | `-1` … `5` |
| `bipush`, `sipush` | the literal |
| `lconst_0/1` | `0L`, `1L` |
| `fconst_0/1/2` | `0f`, `1f`, `2f` |
| `dconst_0/1` | `0d`, `1d` |
| `aconst_null` | `null` |
| `ldc`/`ldc_w` `String` | escaped string literal |
| `ldc`/`ldc_w` `Class` | `Foo.class` |
| `ldc2_w` | `long` or `double` literal |
| `NaN`, `Infinity` | `java.lang.Float.NaN`, `java.lang.Float.POSITIVE_INFINITY`, … |

Strings are escaped to ASCII: every character outside printable ASCII becomes `\uXXXX`, so
generated files survive any encoding.

## Locals

| Bytecode | Java |
|----------|------|
| `iload`, `istore` | `int` variable |
| `lload`, `lstore` | `long` variable |
| `fload`, `fstore` | `float` variable |
| `dload`, `dstore` | `double` variable |
| `aload`, `astore` | reference variable |
| `iinc i n` | `v++;`, `v--;` or `v += n;` |

`slot 0` of an instance method becomes `this` unless the method stores into it, in which case
a real variable is declared. A slot used with several types gets one Java variable per type,
named `v3`, `v3_r2`, `v3_i2`, … — obfuscated code does this constantly.

Arguments keep their debug names when the class still has a `LocalVariableTable`, otherwise
they become `arg0`, `arg1`, …

## Arithmetic

| Bytecode | Java |
|----------|------|
| `iadd`/`isub`/`imul`/`idiv`/`irem` | `(a + b)` … |
| `ladd`, `fadd`, `dadd` … | same, with the matching Java type |
| `ishl`/`ishr`/`iushr` | `(a << b)`, `(a >> b)`, `(a >>> b)` |
| `iand`/`ior`/`ixor` | `(a & b)`, `(a | b)`, `(a ^ b)` |
| `ineg` … `dneg` | `(-a)` |
| `i2l`, `i2f`, … | `(long)(a)`, `(float)(a)`, … |
| `i2b`/`i2c`/`i2s` | `(byte)(a)`, `(char)(a)`, `(short)(a)` |
| `lcmp` | `j2pc.rt.Cmp.lcmp(a, b)` |
| `fcmpl`/`fcmpg` | `j2pc.rt.Cmp.fcmpl(a, b)` / `fcmpg(a, b)` |
| `dcmpl`/`dcmpg` | `j2pc.rt.Cmp.dcmpl(a, b)` / `dcmpg(a, b)` |

`Cmp` exists because `fcmpg` and `fcmpl` differ only in their NaN result, which plain Java
operators cannot express.

## Arrays

| Bytecode | Java |
|----------|------|
| `iaload` | `arr[i]` |
| `laload`/`faload`/`daload` | `arr[i]` |
| `baload` | `arr[i]`, or `(arr[i] ? 1 : 0)` when the element type is `boolean` |
| `aaload` | `((T)(arr[i]))` |
| `iastore` … `aastore` | `arr[i] = value;` |
| `bastore` | `arr[i] = (byte)v;`, or `arr[i] = v != 0;` for `boolean[]` |
| `castore`/`sastore` | `arr[i] = (char)v;` / `(short)v` |
| `arraylength` | `arr.length`, or `j2pc.rt.Arrays.length(arr)` when the type is unknown |
| `newarray T` | `new T[n]` |
| `anewarray C` | `new C[n]`, `new int[n][]` when `C` is itself an array class |
| `multianewarray` | `new T[n][m]` |

When the analysis lost the array type, the opcode itself implies a fallback cast
(`((int[])(x))[i]`), which is what the original bytecode would have required anyway.

## Objects

| Bytecode | Java |
|----------|------|
| `new C` | `C __n = null;` then `__n = new C(...)` at `<init>` |
| `getfield C.f:T` | `((C)(recv)).f` |
| `putfield C.f:T` | `((C)(recv)).f = value;` |
| `getstatic`/`putstatic` | `C.f` |
| `checkcast C` | `((C)(x))` |
| `instanceof C` | `(x instanceof C ? 1 : 0)` |
| `invokevirtual` | `((C)(recv)).m(args)` |
| `invokespecial` | `super.m(args)` or `((C)(recv)).m(args)` |
| `invokestatic` | `C.m(args)` |
| `invokeinterface` | `((C)(recv)).m(args)` |
| `invokedynamic` | **unsupported** — a stub with a warning |
| `monitorenter` | `j2pc.rt.Monitor.enter(x);` |
| `monitorexit` | `j2pc.rt.Monitor.exit(x);` |
| `athrow` | `throw (java.lang.Throwable)(x);` |

`boolean` fields and methods are special: bytecode uses `int` for them on the stack, so reads
emit `(f ? 1 : 0)` and writes emit `x = (v != 0);`.

## Control flow

| Bytecode | Java |
|----------|------|
| `ifeq` … `ifle` | `final boolean __cN = (a OP 0);` + dispatch |
| `if_icmpeq` … `if_icmple` | `final boolean __cN = (a OP b);` + dispatch |
| `if_acmpeq`/`if_acmpne` | `final boolean __cN = (a == b);` + dispatch |
| `ifnull`/`ifnonnull` | `final boolean __cN = (a == null);` + dispatch |
| `goto` | `__pc = T; continue;` |
| `tableswitch`/`lookupswitch` | `switch (key) { case K: __pc = T; continue; … }` |
| `jsr`/`ret` | return address pushed as `int`, `ret` sets `__pc` |
| `ireturn` … `areturn` | `return value;` |
| `return` | `return;` |

The condition is always materialised into a `final boolean` before spilling the stack, so that
a spilled value cannot change the outcome of the comparison.

## The stack

Bytecode manipulates an operand stack; Java does not. The emitter keeps the stack symbolically
inside a basic block and materialises it into variables (`s0`, `s1`, …) whenever control leaves
the block or a call needs to happen in a defined order.

| Bytecode | Handling |
|----------|----------|
| `pop` | discard, but keep side effects |
| `pop2` | discard one or two slots depending on category |
| `dup` | duplicate; compound expressions are first materialised into a temp |
| `dup_x1`, `dup_x2`, `dup2`, `dup2_x1`, `dup2_x2` | stack shuffles |
| `swap` | swap two slots |

`pop` deserves a note: if the discarded value is a method call, the call is still emitted as a
statement. Without this, `obj.createNumber();` style code silently does nothing — a bug that
is invisible in the source and lethal at run time.

## Exceptions

A `Code` attribute's exception table becomes nested `try`/`catch` blocks. Each handler catches
`Throwable`, checks the type from the table and continues the dispatch loop at the handler
address:

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

`finally` blocks appear as duplicated handler code in the bytecode, so they come out as
ordinary handlers — no special casing needed.

## Checked exceptions

Java requires a `throws` clause or a `try` for every checked exception; bytecode has no such
concept. Rather than trying to infer `throws` clauses (impossible in general), every generated
body is wrapped:

```java
try {
    ... body ...
} catch (java.lang.Throwable __unchecked) {
    throw j2pc.rt.Sneaky.sneak(__unchecked);
}
```

`Sneaky.sneak` rethrows the original exception object unchanged, so the exception tables of
the generated code keep working. Constructors get an extra `throws java.lang.Throwable` when
their mandatory `super(...)` chain can itself throw.
