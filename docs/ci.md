# Continuous integration

Three workflows live in `.github/workflows`.

## `ci.yml` — every push and pull request

| Job | What it does |
|-----|--------------|
| **build** | Compiles on Ubuntu, Windows and macOS with JDK 17 and 21 (six combinations). Uploads `j2pc.jar` as an artifact per combination. |
| **self-test** | Builds the synthetic MIDlet suite from `src/test`, recompiles it with J2PC, compiles the generated Java, then diffs behaviour between the original and the recompiled classes. Runs on Linux and Windows. |
| **corpus** | Recompiles a local corpus of real J2ME applications. Skipped unless the repository variable `J2PC_CORPUS` is `true`, because the JARs are third-party and not part of the repository. |
| **status** | Aggregates the results so a single check can gate merges. |

### Why the self test matters

The synthetic suite is the part of CI that actually proves the translator works. It covers:

| File | Exercises |
|------|-----------|
| `Arithmetic.java` | every arithmetic, conversion, comparison and wide opcode |
| `ControlFlow.java` | `tableswitch`, `lookupswitch`, nested loops, labelled breaks, ternaries |
| `Exceptions.java` | typed catches, `finally`, rethrow, nested handlers, `synchronized` |
| `Objects.java` | arrays of all kinds, `multianewarray`, `dup`/`swap`, collections |
| `Modern.java` | `invokedynamic`: lambdas, method references, string concatenation |
| `Hierarchy.java` | inheritance, inner and anonymous classes, enums, static initialisers |
| `SyntheticMidlet.java` | the full MIDP lifecycle, painting, keys, record stores |

`tools/selftest.sh` (or `.cmd`) checks that everything compiles, and ends by packaging the
result into a JAR and asserting that the archive contains the launcher, the MIDP runtime and
`j2pc.rt` — a packaged application that is missing any of those is not self-contained.
`tools/verify.sh` (or `.cmd`) goes further: it runs `j2pc.tools.Verifier` against the original
classes and against the recompiled ones, and diffs the output. Any behavioural difference fails
the job.

Two details of the harness are load-bearing, and both were learned the hard way:

* **Class path order.** The test classes must come *before* the runtime JAR. An older build of
  the JAR contained a copy of the synthetic classes, which silently shadowed both versions and
  made the comparison compare the same stale code twice.
* **Sanity checks on the result set.** A comparison of two empty outputs passes trivially, so the
  script requires a minimum number of results and fails when a class could not be loaded.

### Corpus job

Set the repository variable to enable it:

```
Settings → Secrets and variables → Actions → Variables → New variable
  J2PC_CORPUS = true
```

The job expects the JARs to be available in `samples/`. Restore them from a cache or a private
artifact, since they cannot be committed.

### Working on the corpus

The corpus tools are built around one idea: **fix causes, not applications**. A single
application failing tells you almost nothing; the same message appearing in twenty tells you
exactly where the translator is weak.

```powershell
# 1. recompile and compile everything, writing one merged report
powershell -File tools/batch.ps1 -Compile `
    -Source 'D:\j2me\DPT','D:\j2me\GoodJ2ME','D:\j2me\jAR-HIVE'

# 2. consolidate: which kinds of failure are most common, in how many apps
powershell -File tools\errors.ps1 -Batch build\batch -Top 25
```

`errors.ps1` normalises each javac message (file names, line numbers and identifiers become
placeholders), so `cannot find symbol` variants collapse into one row, and sorts by **number of
applications affected** rather than raw error count. One row means one fix worth doing.

It also writes `build/batch/errors-summary.csv`, which is the file to diff between runs:

```powershell
# before a change
powershell -File tools\errors.ps1
Copy-Item build\batch\errors-summary.csv build\before.csv

# ... make the change, re-run batch.ps1 ...

# after
powershell -File tools\errors.ps1
Compare-Object (Import-Csv build\before.csv) (Import-Csv build\batch\errors-summary.csv) -Property Kind
```

`batch.ps1` accepts several folders at once and keeps the per-application output separate, so
adding a new collection never invalidates the previous measurements.

## `release.yml` — on a `v*` tag

1. builds and self-tests,
2. stages `j2pc-<tag>.jar`, a source zip, `SHA256SUMS.txt`, `README.md`, `LICENSE`,
3. publishes a GitHub release with generated notes,
4. keeps the staged files as a workflow artifact.

Trigger it by pushing a tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Or run it manually with an explicit version.

## Artifacts

| Artifact | Contents | Retention |
|----------|----------|-----------|
| `j2pc-jar-jdk<N>-<OS>` | the built JAR | 14 days |
| `self-test-<OS>` | generated sources, the packaged application JAR and the behaviour diff inputs | 14 days |
| `corpus-report-<OS>` | `report.csv` and `javac.log` | 14 days |
| `release-<tag>` | everything published in the release | 30 days |

## Local equivalents

Every CI step has a local script, so a failure can be reproduced without pushing:

```bash
./build.sh              # or build.cmd
./tools/selftest.sh     # or tools\selftest.cmd
./tools/verify.sh       # or tools\verify.cmd
./test.sh               # or test.cmd      (sample corpus)
powershell -File tools/batch.ps1 -Compile    # full corpus with a report
powershell -File tools/errors.ps1            # error taxonomy from the report
```
