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
- There is no source code.
- There is no `deps.edn`.
- There are no tasks.

## Next

- Spike on a Valhalla early-access JDK. Verify the assumptions in
  `ramora/defvaluetype-design/risks-and-evolution.md`.
- Then make munera tasks from the design.

## Related

- `ramora/INDEX.md`
- `munera/plan.md`
