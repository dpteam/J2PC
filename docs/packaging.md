# Packaging

Recompilation produces Java sources. Packaging turns those sources into
something a person can double click. Both steps live behind flags, and both use
only what the JDK already ships — no Maven, no Gradle, no bundler.

```
archive  ──recompile──▶  sources  ──--build──▶  JAR  ──--exe──▶  native image
```

## `--build`: sources to JAR

```bash
java -jar dist/j2pc.jar --build -o build/out-Tetris samples/Tetris.jar
```

Three things happen, in order.

### 1. Compile

`j2pc.build.SourceCompiler` calls `ToolProvider.getSystemJavaCompiler()` instead
of spawning `javac`. The advantages are practical:

* no process management and no per-platform quoting;
* diagnostics arrive as objects with file, line and message, so they can be
  logged consistently and counted;
* the runtime JAR is put on the class path programmatically, which is exactly
  what a spawned `javac` would need `-cp` for.

The compiler needs a JDK. On a JRE `ToolProvider` returns `null` and the tool
says so instead of failing later with a confusing error.

### 2. Collect the runtime

`j2pc.build.RuntimeClasses` finds the classes the tool itself was loaded from —
a JAR when installed, a directory during development — and copies out only the
packages generated code can reference:

```
javax/microedition/   MIDP, RMS, media, lcdui.game, bluetooth, wireless
com/nokia/            Nokia UI API
javax/bluetooth/      JSR-82
javax/wireless/       JSR-120
j2pc/rt/              comparison helpers, monitors, asset lookup
```

The compiler, the CLI, the TUI and the GUI are tooling and stay out. That is
what makes the result self-contained: 138 runtime class files for a small suite,
against a 3 MB tool JAR.

### 3. Package

`j2pc.build.JarPackager` writes one archive containing

* the compiled application classes,
* the runtime classes,
* the original resources under `assets/`,
* a manifest with `Main-Class: J2PCLauncher`.

Entry timestamps are fixed at zero, so the same input produces the same bytes —
useful when a build is compared across machines.

The application name, vendor and version come from the JAD when one is present.
Without a JAD the archive name is used, because "J2ME Application" would make
every output collide.

```bash
java -jar build/out-Tetris/Tetris.jar
```

Resources resolve through `j2pc.rt.Assets`, which looks at the class path first,
so `Image.createImage("/icon.png")` finds `assets/icon.png` inside the archive
without any extra configuration.

## `--exe`: JAR to native image

```bash
java -jar dist/j2pc.jar --exe -o build/out-Tetris samples/Tetris.jar
```

`j2pc.build.ExePackager` wraps `jpackage` with `--type app-image`:

```
build/out-Tetris/image/Tetris/Tetris.exe
```

An app image is a directory with a trimmed runtime and a launcher, so the target
machine needs no Java installed. It is a launcher, not a compiled binary: a JVM
is inside.

Three details are worth knowing.

**The JAR gets its own input directory.** `jpackage --input` copies everything
in the directory into the image. Pointing it at the output directory would sweep
in the sources, the asset copies and the compiler work area. `AppBuilder` copies
the JAR into `build/image-input/` first.

**Versions are normalised.** `jpackage` wants a numeric `major.minor.patch`
without a leading zero segment; `1.0.0-beta` becomes `1.0.0`.

**The image is built aside and moved into place.** `jpackage` refuses to write
into a destination that already holds an image of the same name, and deleting an
old image means removing tens of thousands of runtime files — on Windows that
takes longer than the build. So the image is created in a staging directory and
renamed afterwards, which is immediate on the same volume.

`jpackage` is part of the JDK from 14 onwards. When it is missing, the tool logs
a warning and leaves the JAR in place — the build is still usable.

## Output layout

With the CLI, everything lands in the output directory you pass with `-o`:

```
build/out-Tetris/
  game2048/*.java              generated sources
  assets/**                    resources copied out of the archive
  J2PCLauncher.java            desktop entry point
  j2pc-report.txt              per class summary
  Tetris.jar                   self-contained application
  Tetris.jar.manifest.txt      manifest in readable form
  build/
    classes/                   compiled application classes
    runtime/                   runtime classes, copied
    staging/                   classes + runtime, pre-packaging
    image-input/               the JAR, alone, for jpackage
  image/
    Tetris/                    app image
```

The TUI and the GUI add one level: `out/Tetris/…`, `out/2048/…`. Two suites in
one batch almost always ship a class with the same name, so a shared directory
would let the second run overwrite the first. The GUI also writes the launched
application's console output to `console.log` next to the archive — a window has
no console, and that file is the first place to look when a game refuses to
start.

## Why not a fat JAR by default

Because the sources are the point of the project. A JAR that hides the
translation removes the ability to read, diff and learn from it. `--build` is
opt-in for the same reason: the default output stays a directory of `.java`
files, and packaging is a step you take when you want to run the thing.
