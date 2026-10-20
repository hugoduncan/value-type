# Plan

## Approach

Implement bottom-up in `value-type.core`, then tests, then docs.

1. `check-value-class` first. It is a plain function with no macro
   dependency, so it is testable alone against `deftype` classes and
   value classes. It holds the value-class predicate: use
   `Class::isValue` found by `.getMethod` (the pattern in
   `value-type.environment-test`), so no reflection warning and JDK 25
   works (no `isValue` → not a value class). `value-type.core` sets
   `(set! *warn-on-reflection* true)` after its `ns` form (as `loader`
   and `transform`), so that a reflective call prints a warning.
2. `defvaluetype` next. Macroexpansion order per design: validate
   mutable fields (D8) → `loader/install!` with the binary name
   `(str (namespace-munge *ns*) "." name)` → return the D1 expansion
   `(let [] (deftype ...) (check-value-class <class-sym> <installed>) <class-sym>)`.
   Arguments pass to `deftype` unchanged (same objects, metadata
   kept). `<installed>` is a boolean literal.
3. Tests in `value-type.core-test` follow the Verification list 1–8.
   Fixtures use `gensym` class names and bind `*ns*` to the test
   namespace (`mementum/knowledge/testing-clojure-generated-classes.md`).
   The AOT test reuses the `aot-compile` recipe from
   `value-type.impl.loader-test`; the new-JVM part is new code.
4. Docs and design-document alignment last, per the "Documentation"
   list in design.md.
5. Manual JDK 25 run (acceptance 3 JDK 25 part, acceptance 5);
   record results in implementation.md.

Key decisions (all fixed by design.md):

- No reserved-field check: `deftype` gives its own `AssertionError`.
- Validation checks only symbol elements of a vector `fields`; a
  non-vector `fields` goes to `deftype` unvalidated.
- Mutable means truthy `:volatile-mutable` or
  `:unsynchronized-mutable` metadata (the compiler rule), so
  `{:volatile-mutable false}` passes.
- Error messages branch on the presence of `Class::isValue`: without
  it, name only the missing value-class support.
- `value-type.core` uses only `loader/install!`; no `Compiler/LOADER`,
  `DynamicClassLoader`, `Compiler/writeClassFile` or
  `java.lang.classfile` (boundary invariant, acceptance 4).

## Risks

- `Class::isValue` can change in an early-access build (design risk
  assumption 5). The predicate is only in `core`, so a change is
  local.
- The new-JVM AOT test depends on `java.class.path` holding the full
  classpath under `clojure -M:test` and under nREPL. Not yet checked
  (implementation.md note). If not, build the classpath another way
  inside the test.
- The new-JVM test can hang; the design requires a wait with a
  timeout and stderr in the failure message.
- `:not-installed` test needs `macroexpand` on a thread with no
  `LOADER` binding. `future`, `bound-fn` and `binding-conveyor-fn`
  convey the `LOADER` binding of `eval` (a REPL run binds it), so
  `install!` gives `true` and the test gets `:not-value-class`. A raw
  `Thread` has no `LOADER` binding, but its `*ns*` is `clojure.core`,
  so the install name and the `deftype` class name differ. Recipe: a
  raw `Thread` (as `no-binding-test` in `value-type.impl.loader-test`)
  whose body binds only `*ns*` to the test namespace and calls
  `macroexpand`; deliver the expansion with a `promise`; `deref` it
  with a timeout; `eval` it on the test thread with `*ns*` bound to
  the test namespace. No `future`, `bound-fn` or
  `binding-conveyor-fn`. This works under both runners (REPL and
  `-m scry.cli`).
- Manual JDK 25 run needs a JDK 25 install; `JAVA_CMD` selects it.
- A reflection-warning check can pass with no check: a namespace
  that does not set `*warn-on-reflection*` prints no warning, and a
  `require` in a REPL that already loaded the namespace does not
  compile it again. Recipe (acceptance 3): load each namespace in a
  new JVM with
  `JAVA_CMD=<java> clojure -M:test -e "(require '<ns>)" 2>/tmp/vt-err.txt`;
  exit 0 and no `Reflection warning` line in `/tmp/vt-err.txt` (the compiler
  writes warnings to standard error; exit is 0 with warnings). Valhalla
  JDK: `<ns>` is `value-type.core-test` (it loads `value-type.core`).
  JDK 25: `<ns>` is `value-type.core` only.

## Slice order

1. Runtime check: `check-value-class` + its direct tests
   (Verification 7, `check-value-class` items).
2. Macro: `defvaluetype` + basic tests (Verification 1, 2).
3. Behaviour tests: specs, fields, options, redefinition
   (Verification 3–6).
4. Error tests: mutable fields, reserved name, `:not-installed`,
   `:not-value-class` paths (Verification 7, remaining items).
5. AOT new-JVM test (Verification 8).
6. Quality gates: full suite, REPL run, reflection-warning check in a
   new JVM (see Risks) (acceptance 1–3).
7. Documentation: README, design documents, state.md
   (acceptance 6, 7).
8. Manual JDK 25 run, with the reflection-warning check in a new JVM
   (see Risks); record in implementation.md (acceptance 3, 5).
