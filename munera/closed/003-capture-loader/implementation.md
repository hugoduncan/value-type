# Implementation

## Design review

- Architecture sources: `ramora/INDEX.md` → `ramora/defvaluetype-design.md` and its sections. `doc/architecture.md` and `ramora/META.md` do not exist.
- Architecture review (pass 1): no architectural review feedback. The move of the D9 define-error wrap from `core` to `loader` is a responsibility change. The design records it in "Documentation" (D9, components table, data flow), and the rationale holds: `core` cannot catch an error in analysis of its own expansion.
- Ambiguity review (pass 1): no ambiguity review feedback. Checked and not raised: a `LinkageError` with a nil message (`str` drops nil, so the message stays clean); the JDK 25 reflection check (`JAVA_CMD`, see `mementum/memories/valhalla-jdk-selection-java-cmd.md`); the arguments of the private define step (a plan choice, not a design choice).
- Inconsistency review (pass 1): added 2 design steps (the scope of the boundary invariant, the basis of the claim about default `ClassFile` options).
  - Checked with a Java probe on JDK `27-jep401ea3`: the design's `VerifyError` test approach works. The defaults throw at build. `DROP_STACK_MAPS` builds, and verification fails. A probe with `Lookup.defineClass` verified at define. That is not `DynamicClassLoader.defineClass`, so the design fact still applies.
  - The `VerifyError` test builds value-class bytes. Do not refer to `ClassFile/ACC_STRICT_INIT` or `ACC_IDENTITY` in the test namespace, because then it does not load on JDK 25 (acceptance 3). Use the private literal vars of `value-type.impl.transform` (`mementum/knowledge/java-lang-classfile.md`).
- For the design-steps slice:
  - Edit `design.md` in place, in simplified technical English. Add no revision markers. Do not change the scope or the "Documentation" list.
  - Step 1: change only the invariant text. `ramora/defvaluetype-design/embodiment.md` ("Boundaries") uses the same unqualified wording for library namespaces. Leave it as it is: the design's "Documentation" list does not include that section.
  - Step 2: the observed basis is in the inconsistency review note above. The Java probe was not kept. To run it again, use a single-file `java --enable-preview --source 27 Exp.java` with the Valhalla `java` (`~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`). Build a class with a `long` field that has the flags `ACC_FINAL|ACC_STRICT_INIT`, and a constructor that calls `Object.<init>` before `putfield`. Do not edit `mementum/knowledge/java-lang-classfile.md` now. The `extract-task-knowledge` step adds this fact to it.

## Design steps (pass 1, done)

- Both design steps done. No `SCOPE_QUESTION:` items.
- The boundary invariant now applies to `src/` only, and it agrees with acceptance 4. Test namespaces can use `Compiler/LOADER` and `DynamicClassLoader`.
- The `VerifyError` test basis is now the JDK probe result, not `java-lang-classfile.md`. That knowledge page still lacks the fact (for `extract-task-knowledge`).

## Design review (pass 2)

- Architecture review (pass 2): no architectural review feedback. Checked after the pass 1 edits: dependency direction `loader → transform` only; the `src/`-only boundary invariant agrees with embodiment "Boundaries" (library namespaces); D7 write order and the "AOT file equals defined bytes" invariant; the link step stays inside the `loader` boundary and serves the Correctness quality.
- Ambiguity review (pass 2): no ambiguity review feedback. Checked and not raised: where the Verification 3 fixture is defined (the `:test` alias always has `--enable-preview`, so a top-level fixture also loads on JDK 25 as an identity class); the ids of the new assertions in `assertions.md` (next free ids after A-24; a doc edit choice).
- Inconsistency review (pass 2): no inconsistency review feedback.

## Design review (pass 2, done)

- No new design steps. All items in `design-steps.md` are checked. The design is ready for `create-task-plan`.
- For the `VerifyError` test bytes: `test/value_type/impl/transform_test.clj` has private builders (`build-class`, `super-call!`, `write-v!`). They make identity-class bytes with `(ClassFile/of)` defaults, so they do not fit as is. The loader test needs: no `ACC_IDENTITY`, field flags `ACC_FINAL|ACC_STRICT_INIT` (`@#'transform/ACC_STRICT_INIT`), the preview version (major `ClassFile/latestMajorVersion`, minor 0xFFFF), and `StackMapsOption/DROP_STACK_MAPS`. Copy the builder pattern into the loader test. Do not share private test vars between test namespaces.

## Plan review

- Ambiguity review (plan pass 1): added 2 design steps (the `src` argument of the private define step; the binary name rule of the test macro and the `*ns*` binding in tests).
  - Basis of step 1: `DynamicClassLoader.defineClass(String, byte[], Object)` in Clojure 1.12 ignores `srcForm`. So the choice has no effect now. The step makes the signature explicit.
  - Not raised (note for the AOT test): the AOT fixture must implement no protocol that only `eval` defined. The `URLClassLoader` has the loader of `clojure.lang.IType` as parent, so it cannot find an interface that a `DynamicClassLoader` defined, and the load fails. Use a `deftype` with fields only, or an interface from the JDK or `clojure.lang`.
  - Checked and not raised: when `*compile-files*` is read (at define time, inside the proxy method: design scope 2, step 2); the 3-argument `ClassLoader.defineClass` overloads are final, so the `proxy` overrides only the target method; one `CompilerException` wrap (`analyzeSeq` does not wrap a `CompilerException` again).
- Inconsistency review (plan pass 1): added 1 design step (the field-value check of Verification 2 is missing from the `do`, `let` and redefinition items of Slice 3).
  - Checked and not raised: the plan slices agree with the steps; Slice 1 steps 3 to 5 agree with design scope 2; Slice 4 agrees with Verification 6 and 7; Slice 5 agrees with acceptance 1 to 3; Slice 6 agrees with "Documentation" and acceptance 7. Temp-dir cleanup: `deleteOnExit` (knowledge page) and a delete in the fixture both satisfy the design.
  - For Slice 1: steps.md does not say that the capture loader constructor is private. Design scope 1 allows one public function (`install!`), so use `defn-`. The spike makes it public.

## Plan review (done)

- For the slice that does the 3 plan-review design steps:
  - All 3 are plan-level. Edit `steps.md` (and `plan.md` only if it states the changed detail). Do not change `design.md`: the design already has each rule (scope 2, Verification 2, scope 1).
  - Edit in place, in simplified technical English. No revision markers. Keep the changed step items unchecked (no implementation exists yet).
  - Do not copy the full rationale into `steps.md`. Give the rule and, at most, a path. The rationale is in `design-steps.md` and the notes above.
  - `src` step: confirmed with `javap` on `clojure/lang/DynamicClassLoader.class` in `~/.m2/repository/org/clojure/clojure/1.12.6/clojure-1.12.6.jar`. `defineClass(String, byte[], Object)` never reads its third argument.
  - Binary-name step: the existing pattern is `compile-fixture` in `test/value_type/impl/transform_test.clj` (it binds `*ns*` to the test namespace around `eval`). The `deftype` rule is in `clojure/core_deftype.clj` (`namespace-munge *ns*`).

## Plan-review steps (pass 1, done)

- All 3 plan-review design steps done in `steps.md` and `plan.md`. `design.md` is not changed. The changed step items stay unchecked.
- `src`: the define-and-link step takes `src` and gives it to the delegate. The `VerifyError` test gives `nil` and a new delegate `DynamicClassLoader`.
- `*ns*` in tests: `Compiler.load` (`load-string`) and `Compiler/compile` push a binding of `*ns*` with its current value. So a `binding` of `*ns*` around `load-string` applies to the forms of the string, and the string needs no `ns` form. An `ns` form in the string changes only that inner binding.
- The test namespace name munges (`value-type.impl.loader-test` → `value_type.impl.loader_test`). Use the binary name that the macro computed (or the class that the form returns), not the namespace name, to find the class or the AOT file.

## Plan review (pass 2)

- Ambiguity review (plan pass 2): no ambiguity review feedback.
  - Not raised (trap for the AOT test, observed on JDK `27-jep401ea3`, Clojure 1.12.6): the `sourcePath` argument of `Compiler/compile` must contain a `.`. `Compiler.compile` names the `__init` class from the text before the last `.` of `sourcePath`. With `"probe_aot"`, it throws `StringIndexOutOfBoundsException` after the forms compile. `"probe/aot.clj"` works.
  - Not raised (AOT test): the `ns` form of the source string makes a new namespace that does not refer the test macro. Use the fully qualified macro symbol in the string (the test namespace is already loaded).
  - Checked and not raised: the arguments of `Compiler/compile` other than `sourcePath`; how the field-value check makes an instance (`Reflector/invokeConstructor`, knowledge page); the Slice 4 `ClassFormatError` case goes through the test macro (it needs the install for `:jvm-rejected`).
- Inconsistency review (plan pass 2): no inconsistency review feedback.
  - Checked and not raised: `plan.md` says "Key decisions (all fixed in design.md)", but two bullets are plan-level (the `src` forwarding; the test macro binary name from `*ns*`). This is not a conflict: design scope 2 sends each call to the delegate, and the design leaves the binary-name rule of the test macro open (the out-of-scope item is about the real macro). Do not treat the test macro rule as a design deviation in the implementation review.

## Plan review (pass 2, done)

- No new design steps. All items in `design-steps.md` are checked. The plan is ready for implementation (Slice 1).
- Base the loader on `capture-loader` in `spike/valhalla/src/spike/vt.clj`. Do not copy its public visibility (`defn-`, see plan pass 1 notes) or its local `->value-class` (use `value-type.impl.transform`).

## Implementation (slices 1–6, done)

- `src/value_type/impl/loader.clj`: `install!` (public), `capture-loader` and `define-value-class` (private). The proxy method has type hints on its params; no reflection on JDK 27 or JDK 25.
- Tests: `value-type.impl.loader-test`, 8 `deftest` vars. Full suite 29 tests pass (CLI, `JAVA_CMD` Valhalla). REPL `scry.core/run`: `:pass? true`, var-count 8.
- Trap: compute the expected binary name inside `(binding [*ns* test-ns] ...)`. Outside, `*ns*` is `user` during a CLI run.
- `VerifyError` bytes builder is local to the loader test (`super-before-write-class`); it uses `@#'transform/ACC_STRICT_INIT` and `DROP_STACK_MAPS`.
- Temp dirs: deleted in `finally` (`with-temp-dir`), not `deleteOnExit`.
- Docs: new assertions A-25 (`defineClass` does not verify), A-26 (`getDeclaredMethods` links and verifies).
- Not done here: move task to `munera/closed/` (lifecycle step after review); `java-lang-classfile.md` fact for `extract-task-knowledge`.

## Implementation review

- Added 3 steps to be addressed.

## Implementation review follow-up

- Addressed 3 review steps.
- "top-level do": the classes go to a private var `collected` (an atom). The test cannot embed an atom in the eval form (`Can't embed object in code`). A probe confirms two `LOADER` bindings in `do`: the parent of each capture loader is a plain `DynamicClassLoader`. In a vector form, the second parent is the first capture loader.
- `no-binding-test`: the thread delivers a caught `Throwable`; `deref` has a 1000 ms timeout.
- Full suite: 29 tests pass, no reflection warnings (JDK `27-jep401ea3`).

## Implementation review (pass 2)

- No new steps.

## Test review

- Added 3 steps to be addressed.

## Test review follow-up

- Addressed 3 test review steps.
- `install-binding-test`: binds `LOADER` with `with-bindings`; asserts `true`, a new loader, parent is the old value.
- `aot-write-before-define-test`: AOT compile of the mutable `long` fixture fails with `:jvm-rejected`; the class file exists and has no `ACC_IDENTITY`.
- `jvm-rejected-test`, `link-test`: `select-keys` on the contract keys.
- Full suite: 31 tests pass, no reflection warnings (JDK `27-jep401ea3`).

## Test review (pass 2)

- No new steps.

## Test review (pass 3)

- Added 1 step to be addressed.

## Test review (pass 3) follow-up

- Addressed 1 test review step.
- `point-ok?` → `point-state`: returns `{:value? :x :y}`; tests assert `(= point-expected (point-state cls))`.
- Full suite: 31 tests pass, no reflection warnings (JDK `27-jep401ea3`).

## Test review (pass 4)

- Added 1 step to be addressed.

## Test review (pass 4) follow-up

- Addressed 1 test review step.
- `no-aot-write-test`: asserts `(value? cls)` on the `eval` result, so the target path ran.
- Full suite: 31 tests pass (JDK `27-jep401ea3`).

## Test review (pass 5)

- No new steps.

## Docs review

- Added 2 steps to be addressed.

## Docs review follow-up

- Addressed 2 docs review steps.
- Probe re-run (JDK `27-jep401ea3`): define of `super-before-write-class` bytes succeeds (`isValue` true); `getDeclaredMethods` gives `VerifyError`. Recorded in `verification.md` "Other observations"; A-25, A-26 cite it.
- Summary pipeline: transform → write (AOT only) → define → link (`LinkageError` → `:jvm-rejected`).

## Docs review (pass 2)

- No new steps.

## Code-shaper review

- Added 3 steps to be addressed.

## Code-shaper review follow-up

- Addressed 3 code-shaper review steps.
- `link-test`: `cname` from `binary-name` under `test-ns`.
- `aot-compile` helper (dir, source path, source string → cause of `CompilerException` or nil); shares `compile-cause` with `compiler-cause`.
- `no-binding-test`: `.join` removed; `deref` with timeout is the only wait.
- Full suite: 31 tests pass (JDK `27-jep401ea3`); no reflection warnings.

## Code-shaper review (pass 2)

- Added 1 step to be addressed.

## Code-shaper review (pass 2) follow-up

- Addressed 1 code-shaper review step.
- AOT tests: one namespace symbol per test. `aot-compile` takes `ns-sym` and writes the `ns` form; `munged-path`, `aot-class-name`, `aot-class-file` derive the source path, the binary name and the class file with `namespace-munge`.
- Full suite: 31 tests pass (JDK `27-jep401ea3`); no reflection warnings.

## Code-shaper review (pass 3)

- Added 1 step to be addressed.

## Code-shaper review (pass 3) follow-up

- Addressed 1 code-shaper review step.
- `binary-name` takes `ns` and `name`; `aot-class-name` removed. Test macros give `*ns*`, AOT tests give `ns-sym`, `link-test` and `jvm-rejected-test` give `test-ns` (no `binding`).
- Full suite: 31 tests pass (JDK `27-jep401ea3`); no reflection warnings.

## Code-shaper review (pass 4)

- Added 1 step to be addressed.

## Code-shaper review (pass 4) follow-up

- Addressed 1 code-shaper review step.
- `munged-path` only replaces `.` with `/` on a munged name. `aot-compile` gives it `(namespace-munge ns-sym)`; `aot-class-file` gives it the `binary-name` result.
- Full suite: 31 tests pass (JDK `27-jep401ea3`); no reflection warnings.

## Code-shaper review (pass 5)

- Added 1 step to be addressed.

## Code-shaper review (pass 5) follow-up

- Addressed 1 code-shaper review step.
- `compiler-cause` renamed to `eval-cause`.
- Full suite: 31 tests pass (JDK `27-jep401ea3`).

## Code-shaper review (pass 6)

- Added 2 steps to be addressed.

## Code-shaper review (pass 6) follow-up

- Addressed 2 code-shaper review steps.
- `form-str` prints a form with `*print-meta*` true. `load-string` and `aot-compile` use it, so the source strings keep the `^long` tag.
- `mutable-form` added next to `point-form`. `aot-compile` takes a form. `jvm-rejected-test` and the AOT tests use the fixture forms; no hand-written source strings.
- `point-state` also gives the type of field `x`, so `catch-paths-test` shows that each path compiles a `long` field. With `pr-str`, the "load-string" case fails (checked).
- Full suite: 31 tests pass (JDK `27-jep401ea3`); no reflection warnings.

## Code-shaper review (pass 7)

- No new steps.
