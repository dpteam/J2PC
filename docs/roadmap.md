# Roadmap

Planned work, roughly in order of value. Nothing here is committed; the list is a map, not a
promise.

## Next

### 1. Control-flow structuring (optional output mode)

Turn the dispatch loop back into `if`/`while`/`for` where it is provably safe:

* detect natural loops (back edges whose target dominates the source),
* detect `if`/`else` diamonds (a block with two successors that reconverge),
* keep the dispatch loop for anything irreducible or exception-driven,
* make it opt-in: `--structured`, because correctness beats beauty.

This is the single biggest readability win. The infrastructure is already there — the analysis
provides the block graph, dominance and frames.

### 2. Deobfuscation hints

See [deobfuscation.md](deobfuscation.md) for the full design. In short, four tiers:

1. **detection** — measure name shape, debug info, structure, strings and control flow; report a
   per-signal score and a verdict,
2. **structural rewrites** — recover `synchronized`, string concatenation, `for` loops,
3. **naming hints** — rename only when the class hierarchy proves the role,
4. **string decryption** — execute the suite's own decryptor in a sandbox at recompile time.

Not renaming (that needs semantics), but *labelling*:

* detect synthetic accessors (`access$000`) and mark them,
* detect `StringBuffer` concatenation and emit `+`,
* detect the `synchronized` block pattern (`Monitor.enter` + `try`/`finally`) and emit
  `synchronized (x) { }`,
* detect switch-over-string patterns and emit `switch (s)`,
* fold constant `new X()` + `<init>` chains.

Each of these is a local rewrite on the instruction stream, safe because the pattern is
recognised structurally.

### 3. Vendor extension shims

Real games use vendor APIs heavily. In rough order of frequency:

1. `com.nokia.mid.ui.FullCanvas`, `DirectGraphics`, `DirectUtils`
2. `com.nokia.mid.sound.Sound`
3. `com.siemens.mp.game.*`
4. `com.motorola.*`
5. `javax.microedition.lcdui.game` extensions (`Sprite` extras)

Each is a small adapter over the existing emulation layer.

## Later

### 4. Smarter MIDP emulation

* **Fonts.** Ship a bitmap font that matches handset metrics, so layouts stop shifting.
* **Audio.** Decode MIDI (via `javax.sound.midi`), WAV and AMR; route `Player` to it.
* **Timing.** A frame clock that can be throttled to the original speed.
* **Screens.** Render `Form`/`List` with proper focus, scrolling and soft keys.

### 5. Verification

* **Round-trip tests.** Compile a set of Java sources, recompile them, and compare behaviour on
  a corpus of inputs. *(Started: `tools/verify.sh` and `j2pc.tools.Verifier` already do this for
  the synthetic suite.)*
* **Differential testing.** Run the original class and the recompiled class side by side on
  random inputs and compare results.
* **Bytecode-level checks.** Assert that every instruction of the input maps to at least one
  statement of the output.

### 6. Quality of life

* `--report html` — the report as a browsable page with per-method warnings,
* `--diff` — show what changed between two recompilations,
* `--stats json` — machine readable statistics,
* caching so that unchanged classes are not re-emitted,
* a `j2pc` wrapper script that recompiles, packages and runs in one step,
* **run from the GUI** — the interface can recompile and package, but not launch yet.

## Done

* **TUI** — `--tui`, line oriented, works in any terminal and over SSH.
* **GUI** — `--gui`, local HTTP server plus a plain HTML/JS front end; opens in the system
  browser next to a control window, and embeds in a JavaFX `WebView` when one is present.
* **Packaging** — `--build` compiles and writes a self-contained JAR, `--exe` wraps it into a
  native launcher with `jpackage`. See [packaging.md](packaging.md).
* **CI** — build, self test, verifier and corpus run, with artifacts and checksums.
  See [ci.md](ci.md).

## Non-goals

* **Decompiling to the original sources.** The output is equivalent Java, not the author's.
* **Running MIDlets without recompilation.** J2PC is a source generator, not a bytecode
  interpreter. (An interpreter would be a different, much easier project.)
* **Supporting every vendor API.** The common ones, yes; the long tail, no.
