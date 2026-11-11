# Known limitations

J2PC is a young project. This page lists what it does not do, so that you can tell a bug from
a missing feature.

## Unsupported bytecode

| Feature | Effect |
|---------|--------|
| `invokedynamic` | the method gets a stub that throws `UnsupportedOperationException` and a warning is logged |
| `jsr`/`ret` (old `finally` encoding) | handled, but such methods are forced into dispatch mode and read poorly |
| Methods without `Code` | abstract/native methods are emitted as declarations, others as throwing stubs |

`invokedynamic` is practically absent from J2ME suites (CLDC 1.0/1.1 predates Java 7), so this
rarely matters in practice. If it appears, the class still compiles; only that one method
throws.

## Readability

The dispatch-loop output is correct but not pretty. A method becomes a `switch` over program
counters with `continue` between blocks. There is no structuring pass yet, so no `if`,
`while` or `for` is recovered.

Two options soften this:

* `--no-lines` removes the `// line N` comments,
* `--no-comments` removes per-instruction comments.

A structuring pass is on the roadmap; it will be an optional output mode, because structuring
is exactly where decompilers start changing behaviour.

## Type inference

The analysis is a standard forward abstract interpretation over the operand stack. It does not:

* propagate types backwards,
* infer generic types (irrelevant for CLDC, which has no generics),
* prove nullness,
* prove array element types across method calls.

When a slot's type cannot be proven, the emitter falls back to `Object` plus boxing helpers, or
to a reflective `arraylength`. This is correct but produces extra casts and boxes.

## Platform coverage

Implemented and reasonably tested:

* `javax.microedition.lcdui` — `Display`, `Canvas`, `GameCanvas`, `Graphics`, `Image`, `Font`,
  `Form`, `List`, `Alert`, `TextBox`, `ChoiceGroup`, `Gauge`, `DateField`, `Spacer`,
  `CustomItem`, `Command`, `Ticker`
* `javax.microedition.lcdui.game` — `Layer`, `Sprite`, `TiledLayer`, `LayerManager`
* `javax.microedition.rms` — `RecordStore` and the full exception set
* `javax.microedition.io` — `Connector` with `http`, `https`, `file`
* `javax.microedition.media` — `Manager`, `Player`, tone playback

Missing or stubbed:

| API | State |
|-----|-------|
| `javax.microedition.media` audio decoding | only tones; `Player` for MIDI/AMR/WAV is silent |
| `javax.microedition.lcdui.Canvas` pointer events | delivered, but `CustomItem` traversal is minimal |
| `javax.microedition.io` sockets (`socket://`, `datagram://`) | not implemented |
| `javax.microedition.location`, `pki`, `bluetooth`, `wap` | not implemented |
| `com.nokia.*`, `com.siemens.*`, vendor extensions | not implemented |

Vendor extensions are the biggest practical gap: many games call
`com.nokia.mid.ui.FullCanvas` or `DirectGraphics`. Adding them is straightforward — they are
ordinary Java classes on top of the same primitives — but each vendor needs its own shim.

## Rendering fidelity

`Graphics` implements MIDP semantics on top of `Graphics2D`, but a few details can differ:

* **Font metrics.** MIDP fonts are device fonts; the emulation picks AWT fonts of a comparable
  size, so `stringWidth` and `getHeight` return slightly different numbers. Layouts that
  depend on exact pixel widths may shift.
* **Anti-aliasing.** Text is anti-aliased, shapes are not, matching the "crisp" look of a
  handset while keeping text readable.
* **`drawRegion` transforms.** Implemented with an `AffineTransform`; sub-pixel differences
  from a real handset are possible.
* **Alpha blending.** `Image` is always ARGB, which is what most devices did anyway.

## Runtime behaviour

* **Timing.** Games that rely on exact frame timing will run at a different speed; the
  emulation does not throttle the game loop.
* **Sound.** Only `Manager.playTone` and tone sequences produce audio.
* **`RecordStore`.** Stored under `~/.j2pc/rms`. Two suites with the same record store name
  share data, which differs from a handset where stores are per-suite.
* **Threads.** Java threads are used directly. `Thread.yield()` behaves differently from a
  single-core handset, so busy-wait loops may spin hotter.
* **`System.currentTimeMillis()`** returns real time, not handset time.

## Scale

The recompiler has been exercised on suites up to a few hundred classes. Very large suites
(thousands of classes) should work but have not been profiled; memory use is dominated by
holding all `ClassFile` objects at once.

## What is *not* a limitation

Some things that look like limitations are deliberate:

* **No deobfuscation.** Class, field and method names stay as they are (`a`, `b_m3`). Renaming
  would require semantic understanding of the code, which this tool does not attempt.
* **No reconstruction of the original sources.** The output is equivalent Java, not the
  author's Java. Comments, formatting and structure are gone.
* **The dispatch loop.** It looks unusual, but it is the reason the output is exact.
