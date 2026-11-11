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

Not renaming (that needs semantics), but *labelling*:

* detect synthetic accessors (`access$000`) and mark them,
* detect `StringBuffer` concatenation and emit `+`,
* detect the `synchronized` block pattern (`Monitor.enter` + `try`/`finally`) and emit
  `synchronized (x) { }`,
* detect switch-over-string patterns and emit `switch (s)`,
* fold constant `new X()` + `<init>` chains.

Each of these is a local rewrite on the instruction stream, safe because the pattern is
recognised structurally.

### 3. TUI

A terminal UI for people who live in a terminal:

* file picker for JAR/JAD,
* progress and live statistics during recompilation,
* per-class list with method counts, warnings and failures,
* view the generated source with syntax highlighting,
* "recompile → compile → run" in one keystroke.

Plain Java, no dependencies: ANSI escapes plus `JLine`-style raw mode written by hand.

### 4. GUI

A desktop window with a web view — the "launcher" look:

* **Backend**: a tiny HTTP server (`com.sun.net.httpserver`, part of the JDK) that serves a
  static HTML/JS/CSS bundle and a small JSON API.
* **Front end**: drag and drop a JAR, choose options, watch a live log, browse generated
  files, press *Run*.
* **Shell**: a `JFrame` with a `JEditorPane`/`JWebView`-equivalent. On the JDK alone that means
  either `JEditorPane` (HTML 3.2, no JS) or bundling a WebView. The pragmatic path is
  JavaFX's `WebView` when available, falling back to opening the UI in the system browser.

The API is deliberately plain JSON over localhost, so the same front end can be used by:

* the desktop shell,
* a browser,
* CI (post a JAR, get a report).

### 5. Vendor extension shims

Real games use vendor APIs heavily. In rough order of frequency:

1. `com.nokia.mid.ui.FullCanvas`, `DirectGraphics`, `DirectUtils`
2. `com.nokia.mid.sound.Sound`
3. `com.siemens.mp.game.*`
4. `com.motorola.*`
5. `javax.microedition.lcdui.game` extensions (`Sprite` extras)

Each is a small adapter over the existing emulation layer.

## Later

### 6. Smarter MIDP emulation

* **Fonts.** Ship a bitmap font that matches handset metrics, so layouts stop shifting.
* **Audio.** Decode MIDI (via `javax.sound.midi`), WAV and AMR; route `Player` to it.
* **Timing.** A frame clock that can be throttled to the original speed.
* **Screens.** Render `Form`/`List` with proper focus, scrolling and soft keys.

### 7. Verification

* **Round-trip tests.** Compile a set of Java sources, recompile them, and compare behaviour on
  a corpus of inputs.
* **Differential testing.** Run the original class and the recompiled class side by side on
  random inputs and compare results.
* **Bytecode-level checks.** Assert that every instruction of the input maps to at least one
  statement of the output.

### 8. Packaging

* one-file executable via `jlink`/`jpackage`,
* a `j2pc` wrapper script that builds and runs in one step,
* GitHub Actions: build, run the sample corpus, publish the report.

### 9. Quality of life

* `--report html` — the report as a browsable page with per-method warnings,
* `--diff` — show what changed between two recompilations,
* `--stats json` — machine readable statistics,
* caching so that unchanged classes are not re-emitted.

## Non-goals

* **Decompiling to the original sources.** The output is equivalent Java, not the author's.
* **Running MIDlets without recompilation.** J2PC is a source generator, not a bytecode
  interpreter. (An interpreter would be a different, much easier project.)
* **Supporting every vendor API.** The common ones, yes; the long tail, no.
