# Building and testing

## Requirements

* JDK 17 or newer. Everything is compiled with `javac`; there is no build tool to install.
* No network access is required at build or run time.

Check your JDK:

```bash
javac -version
```

## Build

### Windows

```cmd
build.cmd
```

### Linux / macOS

```bash
chmod +x build.sh
./build.sh
```

Both scripts do the same three things:

1. compile every file under `src/` into `build/classes`,
2. write a manifest with `Main-Class: j2pc.Main`,
3. package `dist/j2pc.jar`.

### Manual build

If you prefer to do it by hand:

```bash
mkdir -p build/classes dist
find src -name '*.java' > build/sources.txt
javac -encoding UTF-8 -d build/classes @build/sources.txt
printf 'Main-Class: j2pc.Main\n' > build/manifest.txt
jar cfm dist/j2pc.jar build/manifest.txt -C build/classes .
```

`-encoding UTF-8` matters: several sources contain Cyrillic string literals.

## Running

```bash
java -jar dist/j2pc.jar --help
java -jar dist/j2pc.jar -o build/out samples/Tetris.jar
```

## Testing

### Sample corpus

`test.cmd` recompiles every JAR in `samples/`, compiles the generated Java against the J2PC
runtime and reports pass/fail:

```cmd
test.cmd
test.cmd D:\path\to\a\folder\of\jars
```

Output per application:

```
=== 2048 ===
  [ OK ] recompiled and compiled

=== Tetris ===
  [ OK ] recompiled and compiled

Passed: 2  Failed: 0
```

Failures point at `build/test/javac.log`, which contains the full `javac` output.

### Manual round trip

```bash
# 1. recompile
java -jar dist/j2pc.jar -o build/out-2048 samples/2048.jar

# 2. compile the generated sources
mkdir -p build/gen-2048
find build/out-2048 -name '*.java' > build/gen.txt
javac -encoding UTF-8 -cp dist/j2pc.jar -d build/gen-2048 @build/gen.txt

# 3. run it
java -cp "dist/j2pc.jar:build/gen-2048" J2PCLauncher
# on Windows use ; instead of : in the class path
```

Or skip steps 2 and 3 and let the tool do it:

```bash
java -jar dist/j2pc.jar --build -o build/out-2048 samples/2048.jar
java -jar build/out-2048/2048.jar
```

### Packaging check

`tools/selftest.cmd` (and `.sh`) ends with a packaging step: it recompiles the
synthetic suite, builds the JAR, and asserts that the archive contains the
launcher, the MIDP runtime and `j2pc.rt`. A packaged JAR that is missing any of
those is not self-contained, and the check catches it before anyone tries to run
it on a machine without the tool.

`--exe` is not part of CI: `jpackage` takes a minute per application and needs
platform tooling. Run it by hand when touching `ExePackager`:

```bash
java -jar dist/j2pc.jar --exe -o build/out-Tetris samples/Tetris.jar
build/out-Tetris/image/Tetris/Tetris.exe
```

### Interfaces

The TUI and the GUI are smoke-tested by hand; both are thin wrappers over the
same engine, so a regression there shows up as a build failure, not as a wrong
translation.

```bash
java -jar dist/j2pc.jar --tui      # quit with 4
java -jar dist/j2pc.jar --gui      # prints the URL; close the window to stop
```

The launcher reads `assets/` next to the generated sources, so run it from the project root or
pass an explicit class path that includes the output directory.

### Where the assets are found

`J2PCLauncher` registers the asset root by a **relative** name (`"assets"`), never an absolute
path, so the generated project stays portable. At run time `j2pc.rt.Assets` resolves it against:

1. the working directory,
2. the directory the launcher class was loaded from, and its parents,
3. as a fallback, a sibling directory of any of those.

That means all of these work without editing anything:

```bash
# classes compiled next to the sources (assets/ sits beside them)
javac -d build/out-2048 build/out-2048/**/*.java
java -cp "dist/j2pc.jar;build/out-2048" J2PCLauncher

# classes compiled into a separate directory
javac -d build/gen-2048 build/out-2048/**/*.java
java -cp "dist/j2pc.jar;build/gen-2048" J2PCLauncher

# classes inside a JAR, next to the output directory
jar cf app.jar -C build/gen-2048 .
java -cp "dist/j2pc.jar;app.jar" J2PCLauncher
```

### Inspecting a class

Two helpers are useful when something looks wrong:

```bash
# disassemble one class or one method
java -cp dist/j2pc.jar j2pc.tools.Disassembler path/to/Foo.class methodName

# show the analysis view: leaders, instructions, frames
java -cp dist/j2pc.jar j2pc.tools.Trace path/to/Foo.class methodName
```

Compare the output with `javap -c -p` from the JDK. If the two disagree, the bug is in the
decoder; if they agree but the generated Java misbehaves, the bug is in the emitter.

## Continuous integration

The intended CI job is:

```bash
./build.sh
./test.cmd            # or the bash equivalent
```

A non-zero exit status means at least one sample failed to recompile or the generated code did
not compile.

## Troubleshooting

| Symptom | Cause |
|---------|-------|
| `illegal character: '\ufeff'` | a source file was saved with a BOM; re-save as UTF-8 without BOM |
| `unknown opcode N` | the decoder met bytecode it does not know; report it with the class name |
| `invalid pc N` at run time | a dispatch target that is not a block start; report it |
| Blank window when running a game | the game draws only after its animation timer fires; check the console for exceptions |
| `resource not found` | the `assets/` directory is not next to the generated classes; run from the output directory |

## Style

* Four spaces, no tabs, 100 column soft limit.
* `final` on locals only when it clarifies.
* Every public class has a one-line purpose comment; tricky logic gets a comment that explains
  *why*, not *what*.
* No external dependencies. If a feature needs a library, it needs a very good reason.
