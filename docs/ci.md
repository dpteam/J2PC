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

`tools/selftest.sh` (or `.cmd`) checks that everything compiles.
`tools/verify.sh` (or `.cmd`) goes further: it runs `j2pc.tools.Verifier` against the original
classes and against the recompiled ones, and diffs the output. Any behavioural difference fails
the job.

### Corpus job

Set the repository variable to enable it:

```
Settings → Secrets and variables → Actions → Variables → New variable
  J2PC_CORPUS = true
```

The job expects the JARs to be available in `samples/`. Restore them from a cache or a private
artifact, since they cannot be committed.

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
| `self-test-<OS>` | generated sources and the behaviour diff inputs | 14 days |
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
