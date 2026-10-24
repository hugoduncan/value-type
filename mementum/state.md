# State

## Project

value-type: a Clojure library. It supplies the `defvaluetype` macro.
The macro creates JEP 401 value objects
(https://openjdk.org/jeps/401). The macro is as compatible with
`deftype` as possible.

## Status

- The repository is initialised with ramora, mementum and munera.
- The design is in `ramora/defvaluetype-design.md` (status: transform, loader and macro implemented and tested).
  Approach: expand to `deftype`, catch the bytes with a capture loader
  in the thread-bound `Compiler/LOADER`, change them into a value class
  with `java.lang.classfile`.
- The design assumptions are verified on Valhalla EA
  `openjdk-27-jep401ea3+1-1` (JDK at
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). Results:
  `ramora/defvaluetype-design/verification.md`. Working spike code:
  `spike/valhalla/`.
- The project scaffold is done (`munera/closed/001-scaffold-project`): `deps.edn`
  (aliases `:test`, `:nrepl`), environment test `value-type.environment-test`, README
  "Development" section. Select the JDK with `JAVA_CMD`.
- Tests: `clojure -M:test -m scry.cli`. REPL: `clojure -M:test:nrepl`.
- The transform is done (`munera/closed/002-value-class-transform`):
  `value-type.impl.transform/->value-class` (bytes → bytes, with the
  D6 constructor check), tests in `value-type.impl.transform-test`.
- The loader is done (`munera/closed/003-capture-loader`):
  `value-type.impl.loader/install!` (call at macroexpansion time),
  tests in `value-type.impl.loader-test`.
- The macro is done (`munera/closed/004-defvaluetype-macro`, JDK 25
  run done): `value-type.core/defvaluetype` and
  `check-value-class`, tests in `value-type.core-test`. Shared test
  helpers and the `vt-type` fixture: `value-type.test-support`. README has
  "Usage" and "Differences from `deftype`".
- The compatibility corpus is done (`munera/closed/005-deftype-compat-tests`,
  no `src/` change): `value-type.deftype-compat-test` (the `deftype`
  forms of the Clojure 1.12.6 test suite, with the AOT checks CLJ-979
  and CLJ-1208). No form is excluded. The new-JVM helper is
  `value-type.test-support/run-in-new-jvm` (`dir` on the test
  classpath; returns the EDN of the last `prn`).
- The method-call benchmark is done
  (`munera/closed/006-method-call-benchmark`, no `src/` change):
  `value-type.bench` in `bench/`, run with
  `clojure -M:bench -m value-type.bench`. Results in
  `ramora/defvaluetype-design/benchmark.md`: `interface` and
  `protocol` ratios 1.14 (slower) and 1.00 (equal), respectively;
  A-21 is `contested`, A-28 records the ratios.
  Traps: one captured value object lets the JIT fold the call
  (measure over many instances); criterium's
  `:stats :stats :elapsed-time :median` is per batch (divide by
  `:batch-size`).

- The object-size comparison is done
  (`munera/closed/007-object-size-comparison`). Criterium
  `0.5.251-ALPHA` uses the packaged native agent via `extract-agent!`
  and `-agentpath` on Valhalla/macOS aarch64. For 0–3 primitive `long`
  fields, shallow sizes are `[16 24 32 40]` bytes for `deftype` and
  `[16 32 40 48]` for `defvaluetype`. Evidence is preserved in
  `ramora/defvaluetype-design/object-size/`. These results apply only
  to the recorded VM setup; the cause of the +8-byte difference is
  not established. Implementation and all reviews are complete.
  Knowledge extraction updated `mementum/knowledge/benchmarking-value-types.md`
  in commit `faeb15b`.

- The JAR build is done (`munera/closed/008-jar-build`). Use
  `clojure -T:build jar` for a source-only `org.hugoduncan/value-type`
  artifact with version `0.1.<git commit count>` and EPL-2.0 metadata.
  Both `jar` and `clean` delete `target/`, including benchmark output.
  Value-class consumers need a supported Valhalla JDK with
  `--enable-preview`; task 009 adds ordinary-JVM fallback.
  Installation and publication are out of scope. All reviews and knowledge
  extraction are complete. Artifact-test lessons are in
  `mementum/knowledge/testing-library-artifacts.md` (commit `5282c0a`).

- Controlled fallback is complete (`munera/closed/009-controlled-deftype-fallback`).
  Startup property `value-type.fallback` selects `silent` (default), `warn`
  or `error`; it is validated once per loaded library runtime. Invalid
  values fail; later changes are unsupported. Capability detection and
  lazy dependencies allow fallback without optional JDK APIs.
- Fallback uses ordinary `deftype` semantics. Warn emits once per library
  runtime; strict attempts throw Java `Error`. Policy applies at expansion,
  compilation and evaluation. AOT representation is fixed: top-level forms
  enforce policy during initialization; forms inside functions enforce it
  on invocation. Direct class access bypasses these guards. Failed AOT
  initialization can produce `NoClassDefFoundError` on retries.
- Fallback adds no JDK minimum beyond supported Clojure. Verified coverage
  is Java 8 (`1.8.0_492-b09`) and Valhalla `27-jep401ea3+1-1` with preview
  off/on, not future JVM formats. Use
  `JAVA_CMD=<java> clojure [-J--enable-preview] -M:fallback-test`.
- Implementation review and knowledge extraction are complete. Lessons:
  `mementum/knowledge/controlled-fallback-boundaries.md` (7251bcd),
  `java-lang-classfile.md` (030aee5) and
  `testing-library-artifacts.md` (854e76d). Detailed decisions and evidence
  remain in task 009's implementation notes and the fallback architecture.

## Next

- No tasks are scheduled. The user selects the next task.
- Separate candidate: investigate the interface-call slowdown (ratio 1.14)
  on JDK `27-jep401ea3+1-1`, including measurement stability and generated code.
- Other adjacent task (not yet created): nested `defvaluetype` in a method
  body on the value-class path (needs a loader change).

## Related

- `ramora/INDEX.md`
- `munera/plan.md`
- `ramora/defvaluetype-design/fallback.md`
- `mementum/knowledge/controlled-fallback-boundaries.md`

## Latest verification

Task 009 is closed after implementation review and knowledge extraction.
Recorded full fallback matrix: 15 tests with 166/166/141 assertions on
Java 8 and Valhalla preview off/on. After AOT fixture extraction, the AOT
matrix passed 4 tests / 54 assertions on each configuration. Earlier review
also verified the value suite (64 tests / 332 assertions) and build suite
(6 tests / 63 assertions). Recorded exits were zero, with no Scry failures.
Final code-shape review found no new actionable issues (3fb99b1).
Closure changed records only; no runtime tests were rerun.
