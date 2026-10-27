# Allocation acceptance

Final acceptance passed. This record covers task 013 only. No production code,
dependency or contract changed during this pass.

## Environment and commands

See [environment](evidence/environment.txt). Runtime checks used Darwin arm64,
Clojure 1.12.6, Scry 0.1.61, Valhalla `27-jep401ea3+1-1` and Temurin
`1.8.0_492-b09`. Quality checks used Temurin `21.0.10+7-LTS` through mise.

Exact commands are retained as `evidence/<run>.command`, console summaries as
`.log`, and process status as `.exit`. For each Scry run, `.edn` records the
CLI summary and the empty failure-file set checked before the next run.
These are recorded CLI outcomes, not serialized in-process Scry results.

Runtime environment:

```sh
export VALUE_TYPE_VALHALLA_JAVA="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
export VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
export JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA"
```

Only `fallback-java8` overrides JAVA_CMD with VALUE_TYPE_BASELINE_JAVA.
Quality, Quickdoc and release runs unset JAVA_CMD and use `mise exec`.
The `test` and `build-test` aliases add `--enable-preview`; exports are explicit.
The fallback alias adds no preview flag. Child processes select their own flags.

## Results

All commands exited 0. Every Scry run had zero failures, zero errors and no
failure EDN files.

| Run | Tests | Assertions |
| --- | ---: | ---: |
| [library](evidence/library.edn) | 74 | 731 |
| [nonflat](evidence/nonflat.edn) | 10 | 399 |
| [matrix](evidence/matrix.edn) | 1 | 612 |
| [fallback-ea](evidence/fallback-ea.edn) | 17 | 806 |
| [fallback-preview](evidence/fallback-preview.edn) | 17 | 781 |
| [fallback-java8](evidence/fallback-java8.edn) | 17 | 806 |
| [build](evidence/build.edn) | 9 | 101 |
| [quickdoc](evidence/quickdoc.edn) | 11 | 182 |
| [release](evidence/release.edn) | 12 | 123 |

Formatting, clj-kondo (zero warnings/errors), Vale and API freshness passed.
Quickdoc and release tests use real files and processes. Markdown file targets
and whitespace were checked after updating these records.

## Design criteria

1. **Fixtures and lengths:** `value-type.array-test` in the library and nonflat
   runs covers zero-field, one-long, two-long and reference-field values at
   lengths 0, 1 and 3, checking all initial slots and runtime type.
2. **Native access:** the same tests cover Clojure and compiled Java selected-slot
   access. Null and wrong-type stores retain native exceptions and leave contents
   unchanged. Direct inspection checks restriction and atomicity separately.
3. **Validation:** allocator tests cover component, length and seed errors and
   their precedence. Pure length checks cover the integer upper bound without
   allocating near heap limits.
4. **Fresh JVMs:** matrix tests cover six configurations × three policies × two
   require orders (36 processes): Java 8; EA3 preview off with exports off/on;
   EA3 preview on without exports; preview and exports on; and that supported
   configuration with flattening disabled. Repeated failures, reasons, causes,
   warnings and independent allocations are checked. Nonflat repeats semantics.
5. **Fallback independence:** all policy rows pass; full fallback suites pass on
   Java 8 and EA3 with preview off/on. Existing class behavior remains covered.
6. **Packaging:** build tests check exact source-JAR membership and JAR-only
   consumers on supported EA3, Java 8 and preview-disabled EA3. Existing macro
   checks remain. No test fixture or compiled class enters the artifact.
7. **Documentation and quality:** usage, generated API, runtime guide, release
   notes and architecture distinguish allocation from tasks 014 and 015.
   Full library, fallback, build, Quickdoc, release and quality checks pass.

## Limits

This is the recorded EA3/macOS arm64 scope, not a claim of wider JDK support.
No heap-exhaustion experiment, concurrency stress, clone/copy guarantee or
eligibility claim for every untested value class is made. Flattening is optional.
Public diagnostics and sequence construction remain outside this task.
