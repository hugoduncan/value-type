# State

## Project

value-type: a Clojure library. It supplies the `defvaluetype` macro.
The macro creates JEP 401 value objects
(https://openjdk.org/jeps/401). The macro is as compatible with
`deftype` as possible.

## Status

- The repository is initialised with ramora, mementum and munera.
- The design is in `ramora/defvaluetype-design.md` (status: design).
  Approach: expand to `deftype`, catch the bytes with a capture loader
  in the thread-bound `Compiler/LOADER`, change them into a value class
  with `java.lang.classfile`.
- The design assumptions are verified on Valhalla EA
  `openjdk-27-jep401ea3+1-1` (JDK at
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). Results:
  `ramora/defvaluetype-design/verification.md`. Working spike code:
  `spike/valhalla/`.
- The project scaffold is done (`munera/closed/001-scaffold-project`): `deps.edn`
  (aliases `:test`, `:nrepl`), `src/value_type/core.clj` (docstring
  only), environment test `value-type.environment-test`, README
  "Development" section. Select the JDK with `JAVA_CMD`.
- Tests: `clojure -M:test -m scry.cli`. REPL: `clojure -M:test:nrepl`.
- There is no library code.

## Next

- Make munera tasks for the implementation. Start from
  `spike/valhalla/src/spike/vt.clj`.

## Related

- `ramora/INDEX.md`
- `munera/plan.md`
