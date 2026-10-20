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

## Next

- Adjacent tasks (not yet created): nested `defvaluetype` in a method
  body (needs a loader change); run the Clojure `deftype` test forms
  without mutable fields; a method-call benchmark.

## Related

- `ramora/INDEX.md`
- `munera/plan.md`
