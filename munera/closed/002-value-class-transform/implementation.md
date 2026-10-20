# Implementation

## Design review (architecture), 2026-10-20

- `ramora/META.md` and `doc/architecture.md` do not exist. The
  architecture sources are `AGENTS.md` and `ramora/defvaluetype-design/`
  (decisions D5 D6 D10, embodiment boundaries, qualities, assertion
  A-18).
- Checked and in agreement with the architecture: the
  `bytes → bytes` interface, no class-name or loader parameter, the
  A-18 dependency rule, the D6 shape check (the design adds a check of
  the parameters), the pass-through invariant, and the error key in the
  embodiment error set. Test-only use of `eval`, `*compile-files*` and
  `DynamicClassLoader` is outside the A-18 rule.
- Finding (actionable): the JDK-constants decision degrades the
  Testability quality target and gives D10 as the reason, but D10
  does not apply. See design-steps.md.
- architectural review added 1 new design step

## Design review (ambiguity), 2026-10-20

- The round-trip loader finding is also a feasibility risk. The
  inner-`fn` fixture fails when the value class is defined in a
  loader that the Clojure-defined `fn` class does not resolve through.
  The plan needs a loader decision before tests are written.
- ambiguity review added 3 new design steps

## Design review (inconsistency), 2026-10-20

- inconsistency review added 3 new design steps

## Notes for the design-steps slice

- Fix each item by editing design.md in place (munera: no amendment
  files). Write in simplified technical English. Scope is frozen: do
  not add field validation, ramora edits or a version parameter to
  close an item.
- Linked items:
  - Testability item: the spike (`spike/valhalla/src/spike/vt.clj`)
    uses literals (`^:const` 0x20, 0x800, `ACC_STATIC` 0x8) and the
    API value `ClassFile/latestMajorVersion`. Literals in `transform`
    keep the Changeability rule (flag values only in `transform`).
  - Loader item and the "Unique fixture class names" decision: a
    child-first test loader changes the reason for that decision.
    Update both together.
  - Spike result 7 (inner `fn` links to the value class) used the
    capture loader. It does not prove that the round trip works in a
    separate loader.
  - Final-field item: change only the wording of the Intent and the
    Invariant. The check stays without field flags (D8 is in the macro).
- Sources: `ramora/defvaluetype-design/{decisions,embodiment,qualities,verification}.md`,
  `test/value_type/environment_test.clj` (Valhalla and preview
  checks), `deps.edn` (Clojure 1.12.6, `:test` has `--enable-preview`).

## Design-steps slice, 2026-10-20

- All 7 design steps are done in design.md. No `SCOPE_QUESTION:` items.
- Testability item: chose literals (keeps the ramora target), not a
  trade-off note.
- Checks made for this design (scratch scripts, not in the repo):
  - JDK 25 (default `java`, no Valhalla): the spike `->value-class`
    loads and runs. Output 69.65535, class flags 0x11, field flags
    0x811.
  - Clojure 1.12 `deftype` with `^int ^long ^double ^boolean` and
    `Object` fields: the constructor code elements are `LineNumber`,
    then the shape in scope item 3. No `LocalVariable` entries. Loads
    are `ALOAD_0`, `ILOAD_1`, `LLOAD_2`, `DLOAD 4`, `ILOAD 6`
    (`boolean`), `ALOAD 7`. The constructor has only a `Code`
    attribute. Instance fields have no attributes. Class attributes:
    `SourceFile`, `SourceDebugExtension`.
  - JDK 27 jep401ea3: the round-trip test loader in design.md works
    with the inner-`fn` fixture and a `defprotocol` from `eval`.
- Snags for the test loader: a `proxy` cannot call `findLoadedClass`
  or `ClassLoader.defineClass(name, bytes, off, len)` (protected).
  Use `DynamicClassLoader.defineClass(name, bytes, nil)` and an atom
  as cache. Create instances with `clojure.lang.Reflector/invokeConstructor`.
- On a JDK without `Class::isValue`, a hinted `(.isValue ^Class c)`
  compiles to a reflective call with a warning. The test namespace
  still loads.

## Design review (architecture, pass 2), 2026-10-20

- Re-checked the revised design against D5 D6 D10 D11, the
  embodiment boundaries, the quality targets and A-18. The literals
  decision now keeps Testability and Changeability.
- Not filed: the round-trip tests need a Valhalla JDK. D11 puts
  value-semantics tests on the Valhalla JDK, and the Testability
  fitness condition ("Unit tests run on bytes") holds for the
  structure and error tests.
- architectural review added 1 new design step (Error clarity)

## Design review (ambiguity, pass 2), 2026-10-20

- Checked as clear: the code-check positions (`3n + 3`), slot rule,
  load matching by type kind, pass-through of parts not listed in
  scope item 2 ("changes only these parts"), and the round-trip loader.
- ambiguity review added 2 new design steps

## Design review (inconsistency, pass 2), 2026-10-20

- Not filed: the first Invariant ("a value class that the running JVM
  defines") holds only on a Valhalla JDK. On JDK 25 the output defines
  as an identity class. The ramora quality scope is the supported
  environment (a Valhalla JDK), so this is implicit.
- inconsistency review added 1 new design step

## Notes for the design-steps slice (pass 2)

- Open items: 4 (Error clarity, round-trip comparison, error fixture
  shapes, flag-value source). Earlier slice rules still apply.
- Linked items:
  - Error clarity and error fixture shapes both change "Error
    contract". Write the message contract first. Then decide if
    Verification 3 asserts the part names. Keep the part names of
    scope item 3; a test can match on them.
  - Flag-value source: the private-var option (`@#'value-type.impl.transform/...`)
    keeps the Changeability condition and keeps the Risk text true.
    It makes the structure tests a check of the transform logic, not
    of the flag values. The round trip checks the values.
- Facts for the round-trip item: a value object with no `equals`
  override compares fields, so `=` of two value objects works. A
  `deftype` instance and a value object are never `=`. Clojure `=` on
  `NaN` is false: do not use `NaN` in fixtures that compare with `=`.
- Error clarity: name the supported Clojure version as "Clojure 1.12"
  (`ramora/defvaluetype-design/purpose.md`), not the patch version in
  `deps.edn`.
- Sources: `ramora/defvaluetype-design/{qualities,purpose,risks-and-evolution,embodiment}.md`
  (Error clarity target, supported environment, risk row "Clojure
  changes `deftype` output", public error set).

## Design-steps slice (pass 2), 2026-10-20

- All 4 open design steps are done in design.md. No `SCOPE_QUESTION:`
  items.
- Error contract now fixes exact strings that tests match:
  `count of constructors`, `parameters`, `super call`,
  `field writes`, `Clojure 1.12`. Other message wording is free.
- Checked on JDK 25 (scratch script): the three built error fixtures
  of Verification 3 build with `ClassFile/of` and default stack maps
  (field write on `uninitializedThis` before the super call is
  accepted). Built code has no `LineNumber` elements. The
  `parameters` fixture needs a body; `aload_0`, super call, `aload_0`,
  `iload_1`, `i2l`, `putfield`, `return` works.
- Builder calls used: `.withField`, `.withMethodBody`, `CodeBuilder`
  `.aload .lload .iload .i2l .lconst_0 .putfield .invokespecial
  .return_`, `ConstantDescs/CD_Object`, `ConstantDescs/MTD_void`.

## Design review (architecture, pass 3), 2026-10-20

- Not filed: Verification 1 checks the version with
  `ClassFile/latestMajorVersion` and `ClassFile/PREVIEW_MINOR_VERSION`.
  These are API values, not literals, so the Changeability condition
  holds (the same reason as the private flag vars).
- no architectural review feedback

## Design review (ambiguity, pass 3), 2026-10-20

- Not filed: how the tests compare pass-through attributes
  (`java.lang.classfile` attributes have no value equality). This is a
  plan decision. With the default shared constant pool, the code bytes
  of other methods stay equal, as Verification 1 needs.
- ambiguity review added 1 new design step

## Design review (inconsistency, pass 3), 2026-10-20

- Checked against `decisions.md` D5 D6 D9, `embodiment.md`,
  `verification.md` rows 1-5, `mementum/state.md` and `munera/plan.md`:
  in agreement. The three built error fixtures in Verification 3 give
  the stated message parts under scope item 3 (first difference at
  position 2 for `super call`, at position 4 for `field writes`).
- Implementation hint: after a round trip, the `DynamicClassLoader`
  static cache maps the fixture name to the value class. Make the
  `deftype` comparison instance from the `Class` that `eval` returned,
  not from a lookup by name.
- no inconsistency review feedback

## Notes for the design-steps slice (pass 3)

- Open items: 1 (fixture with mixed field types). Edit only
  Verification 1 item 1 in design.md. Verification 2 uses "each
  fixture of Verification 1", so it inherits the change. Earlier slice
  rules still apply (edit in place, simplified technical English,
  scope frozen).
- The order `int`, `long`, `double`, `boolean`, `Object` is already
  checked on Clojure 1.12 (see "Design-steps slice", slots 1 2 4 6 7).
  It has a field after each two-slot field. Choosing it instead of the
  example order needs no new check. Adding `boolean` also covers
  `asLoadable` (`boolean` → `int` load).
- Field hints in the fixture form: `^int`, `^long`, `^double`,
  `^boolean`. No hint gives an `Object` field.

## Design-steps slice (pass 3), 2026-10-20

- The 1 open design step is done in design.md (Verification 1 fixture
  list). No `SCOPE_QUESTION:` items.
- Chose the field order `int`, `long`, `double`, `boolean`, `Object`
  (already checked on Clojure 1.12, slots 1 2 4 6 7), not the example
  order in the step. Verification 2 inherits the five-field fixture.
- Round-trip hint for this fixture: `identical?` (`acmp`) on value
  objects compares an `Object` field by reference. Give the two value
  objects the same `Object` instance, not two equal instances.

## Design review (architecture, pass 4), 2026-10-20

- Only change since pass 3: the Verification 1 fixture list. It is
  test-only and does not change the interface, the dependencies or the
  boundaries.
- no architectural review feedback

## Design review (ambiguity, pass 4), 2026-10-20

- Not filed: how the static-constants fixture makes static fields (for
  example a keyword in a method body; the `<clinit>` then passes
  through as an other method). This is a plan choice.
- no ambiguity review feedback

## Design review (inconsistency, pass 4), 2026-10-20

- no inconsistency review feedback

## Notes after design review (pass 4)

- Open design steps: 0. All three pass-4 reviews found nothing new. The
  design is stable. The next step is `create-task-plan`.
- For the plan: the plan decisions and the test helper facts are in
  the notes above (pass 3 ambiguity, inconsistency and design-steps
  slice; pass 4 ambiguity). Do not copy them into design.md.

## Plan review (ambiguity), 2026-10-20

- Checked on JDK 25 (scratch script): a `deftype` with a protocol
  method, a branch, a keyword and an inner `fn` has class attributes
  `SourceFile` and `SourceDebugExtension` only (no `InnerClasses`),
  no field attributes, and only `Code` on each method. Parsed
  attributes are `BoundAttribute` objects: `=` is identity and
  `toString` gives only the name. The keyword gives `const__0` and
  `<clinit>`. `deftype` also adds a static `getBasis` method.
- For the inconsistency turn: the spike rebuilds the constructor with
  `.withMethod` + `.withCode`. This drops each other constructor
  method element (scope item 2: "changes only these parts"). `deftype`
  constructors have only `Code`, so no current fixture shows it.
- ambiguity review added 2 new design steps

## Plan review (inconsistency), 2026-10-20

- Not filed: steps.md slice 1 says "imports from `java.lang.classfile`
  and `java.lang.constant` only", but the spike also imports
  `java.util.function.Consumer`. The design rule is only "nothing from
  the Clojure compiler" (acceptance 4). Read the step that way.
- inconsistency review added 2 new design steps

## Notes for the plan follow-up slice (plan review)

- Open items: 4 (attribute comparison, risk-1 action, constructor
  `transformMethod`, `JAVA_CMD` path). Fix them in plan.md and
  steps.md. design.md is read-only. Each item is in design already;
  no item needs a design change. Scope frozen; simplified technical
  English; edit in place.
- Each item touches plan.md and the matching steps.md item:
  - attribute comparison: plan decision 6, steps slice 4 item 2;
  - risk-1 action: plan "Risks" item 1 only;
  - constructor: plan decisions 1 and 5, steps slice 1 items 3-4.
    Verification 1 checks only constructor name, descriptor and
    flags. Do not add a test for other constructor elements (design
    does not ask for it; `deftype` gives none);
  - `JAVA_CMD`: plan decision 7, steps slice 7 item 1. Use the
    absolute path; `~` in `JAVA_CMD` is not safe (memory).
- Sources: `spike/valhalla/src/spike/vt.clj` (`ctor-code`, the
  `.withMethod` branch), `README.md` ("Development"),
  `mementum/memories/valhalla-jdk-selection-java-cmd.md`.

## Plan follow-up slice (plan review), 2026-10-20

- All 4 plan-review items are done in plan.md and steps.md. design.md
  is not changed.
- Checked on JDK 25 (scratch script, not in the repo):
  - `ClassBuilder.transformMethod` + `MethodTransform` that replaces
    only `CodeModel` with `.withCode`: an `Exceptions` attribute
    added to the input constructor stays in the output. Flags and
    descriptor stay.
  - Attribute accessors: `.attributeName` and
    `SourceFileAttribute.sourceFile` return a `Utf8Entry` (use
    `.stringValue`); `SourceDebugExtensionAttribute.contents` returns
    `byte[]`. Classes are in `java.lang.classfile.attribute`.
- Code bytes of a parsed method: `(.code m)` is an `Optional` of a
  `CodeAttribute`; `.codeArray` gives the bytes.
- In the psi shell, `java` on `PATH` is JDK 25 (`/usr/bin/java`
  stub). Valhalla runs need `JAVA_CMD` (plan decision 7).

## Plan review (ambiguity, pass 2), 2026-10-20

- Checked as clear after the follow-up slice: constructor
  `transformMethod`, attribute comparison by kind, risk-1 action,
  `JAVA_CMD` path, the three built error fixtures (each gives its
  part under scope item 3), the slot rule in slices 1-2.
- Not filed (implementer choices, same outcome): the methods and
  arguments of each fixture for the round-trip calls; how slice 4
  matches the new constructor code (opcode or type kind and slot); how
  slice 1 shows "no reflection warnings" (for example `require` with
  `*err*` visible on each JDK, result in this file).
- no ambiguity review feedback

## Plan review (inconsistency, pass 2), 2026-10-20

- Checked plan.md and steps.md against design.md: in agreement.
- Not filed: steps.md slice 6 items 3-4 do not repeat the full ex-info
  assertions of item 2. Design Verification 3 applies them to each
  input; implement them for all four error tests.
- Not filed: steps.md slice 7 item 2 (REPL run) does not name the JDK.
  `:pass? true` needs the round-trip tests, so start the REPL with the
  same `JAVA_CMD` as plan decision 7.
- no inconsistency review feedback

## Notes after plan review (pass 2)

- Open design steps: 0. Both pass-2 plan reviews found nothing new.
  No design-steps slice is needed. The plan is stable; the next step
  is `implement-task`.
- Do not edit design.md, plan.md or steps.md to close the "Not filed"
  items of pass 2. Follow the reading recorded there.

## Implementation pass 1, 2026-10-20

- All slices done in one pass. Full suite (Valhalla JDK, `JAVA_CMD`):
  exit 0, 9 tests. REPL `scry.core/run {:namespaces [...]}`: `:pass?
  true`, `:var-count` 6 = `deftest` count. Transform namespace loads
  with no reflection warnings on JDK 25 and JDK 27; the test namespace
  also has none on JDK 27.
- Risk 1 did not occur: all five design fixtures pass the check.
- Check: `expected-code` is a list of predicates, `first-difference`
  gives the 1-based position. A constructor with no `Code` gives
  position 1 → `super call`.
- Test loader: the `proxy` overrides both `loadClass` arities with one
  `letfn` (calling the protected 2-arity from the proxy reflects).
- `isValue` is called with `Reflector/invokeInstanceMethod`, so the
  test namespace compiles without warnings on any JDK.
- Fixtures use one `defprotocol Probe` in the test namespace; forms
  are evaluated with `*ns*` bound to the test namespace.
- Built error fixtures use class names `vt.built.<gensym>`; they are
  never defined, only parsed.

## Implementation review, 2026-10-20

- Full suite on the Valhalla JDK: exit 0, 9 tests, 123 assertions.
  No reflection warnings in the transform and test namespaces.
- added 2 steps to be addressed

## Review follow-ups, 2026-10-20

- addressed 2 review steps
- `field-values` reads fields by name; names come from the input
  class file `FieldModel` list (declaration order is specified).
- `->value-class` docstring gives the full message contract.
- Full suite on the Valhalla JDK: exit 0, 9 tests, 123 assertions.
  No reflection warnings.

## Implementation review (pass 2), 2026-10-20

- no new review feedback

## Test review, 2026-10-20

- added 3 steps to be addressed

## Test review follow-ups, 2026-10-20

- addressed 3 review steps
- Added `extra-write-class` (deftype shape plus a second write before
  `return`) and `extra-element-error-test` → part `field writes`.
- `check-error` compares only `:value-type/error` and `:class-name`.
- Removed `(not (is-value? dc))` from `round-trip-test`.
- Full suite on the Valhalla JDK: exit 0, 10 tests, 123 assertions.

## Test review (pass 2), 2026-10-20

- no new test review feedback

## Test review (pass 3), 2026-10-20

- added 2 steps to be addressed

## Test review (pass 3) follow-ups, 2026-10-20

- addressed 2 review steps
- Added `five-fields-load-test`: literal load slots `[1 2 4 6 7]` and
  kinds `INT LONG DOUBLE INT REFERENCE` of the output constructor.
- Added `no-write-class` and `no-field-write-error-test` (first
  difference at position 3 → `field writes`). Mutation `(<= pos 3)`
  fails this test.
- Full suite on the Valhalla JDK: exit 0, 12 tests, 130 assertions.

## Test review (pass 4), 2026-10-20

- added 2 steps to be addressed

## Test review (pass 4) follow-ups, 2026-10-20

- addressed 2 review steps
- Added `first-element-class` and `first-element-error-test` (first
  difference at position 1 → `super call`). Mutation `(= pos 2)`
  fails this test.
- Added a guard in `structure-test` for `:static-constants`: the input
  has a static field.
- Full suite on the Valhalla JDK: 13 tests, 136 assertions, 0 failed.

## Test review (pass 5), 2026-10-20

- added 2 steps to be addressed

## Test review (pass 5) follow-ups, 2026-10-20

- addressed 2 review steps
- `build-class` takes a superclass and a field list (3-arity keeps
  the old form).
- Added `other-super-class` and `super-owner-error-test`
  (`Number.<init>()V` → `super call`). Mutation `object-init?` →
  `(instance? InvokeInstruction e)` fails this test.
- Added `swapped-writes-class` and `swapped-writes-error-test`
  (`putfield` names swapped → `field writes`). Mutation `putfield?` →
  `(instance? FieldInstruction e)` fails this test.
- Full suite on the Valhalla JDK: 15 tests, 146 assertions, 0 failed.

## Test review (pass 6), 2026-10-20

- added 1 step to be addressed

## Test review (pass 6) follow-ups, 2026-10-20

- addressed 1 review step
- Added `swapped-loads-class` and `swapped-loads-error-test` (loads
  swapped → `field writes`). Mutation `load?` ignoring the slot fails
  this test.
- Full suite on the Valhalla JDK: 16 tests, 151 assertions, 0 failed.

## Test review (pass 7), 2026-10-20

- Full suite on the Valhalla JDK: 16 tests, 151 assertions, 0 failed.
- added 2 steps to be addressed

## Test review (pass 7) follow-ups, 2026-10-20

- addressed 2 review steps
- `compile-fixture` calls `deleteOnExit` on the directory and each
  file (`file-seq`, pre-order; the JVM deletes in reverse order). The
  round-trip loader can read `:dir` until JVM exit. Temp directory
  count before and after a suite run: 181 and 181.
- `code-bytes` hints the `.orElse` value as `CodeAttribute`
  (imported). No reflection warnings.
- Full suite on the Valhalla JDK: 16 tests, 151 assertions, 0 failed.

## Test review (pass 8), 2026-10-20

- added 1 step to be addressed

## Test review (pass 8) follow-ups, 2026-10-20

- addressed 1 review step
- Added `other-owner-class` (`putfield` owner `vt.built.Other`) and
  `field-owner-error-test` (part `field writes`). Mutation check:
  with the owner test removed from `putfield?`, only
  `field-owner-error-test` fails.
- Full suite on the Valhalla JDK: 17 tests, 156 assertions, 0 failed.

## Test review (pass 9), 2026-10-20

- Full suite on the Valhalla JDK: 17 tests, 156 assertions, 0 failed.
- Not filed: operand checks of `object-init?` (name, descriptor,
  opcode), the `putfield?` type and the `void-return?` kind. A
  mismatch of each gives bytecode that does not verify, so a test adds
  little.
- added 1 step to be addressed

## Test review (pass 9) follow-ups, 2026-10-20

- addressed 1 review step
- Renamed error tests after the input shape (`int-param`,
  `write-before-super`, `lconst`, `extra-write`, `no-write`,
  `other-super`). Each error test has a `testing` string
  "<shape>: first difference at position N" (no position for
  `count of constructors` and `parameters`). Positions checked with
  `first-difference` on each fixture.
- Full suite on the Valhalla JDK: 17 tests, 156 assertions, 0 failed.

## Test review (pass 10), 2026-10-20

- added 1 step to be addressed

## Test review (pass 10) follow-ups, 2026-10-20

- addressed 1 review step
- Added `after-return-class` and `after-return-error-test`. The
  default dead-code option patches the second `return` to `athrow`;
  after the strip, the extra element is still at position 7.
- With `(empty? preds)` removed from `first-difference` (`with-redefs`),
  the test errors with `NullPointerException`.
- Full suite on the Valhalla JDK: 18 tests, 161 assertions, 0 failed.

## Test review (pass 11), 2026-10-20

- added 1 step to be addressed

## Test review (pass 11) follow-ups, 2026-10-20

- addressed 1 review step
- Added `debug-elements-class` and `debug-elements-test`: a guard
  asserts the input has `Label`, `LocalVariable` and
  `LocalVariableType` elements; the output constructor code view is
  the value class shape.
- With `debug-element?` matching only `LineNumber` (`with-redefs`),
  the test errors with part `super call`.
- Full suite on the Valhalla JDK: 19 tests, 166 assertions, 0 failed.

## Test review (pass 12), 2026-10-20

- Not filed: a mutation of `debug-element?` that also strips
  `ExceptionCatch` (`PseudoInstruction`). Each exception handler needs
  a handler instruction (a fall-through to the `return` gives a stack
  size mismatch), so the check still finds an extra element. Checked
  on JDK 25.
- added 1 step to be addressed

## Test review (pass 12) follow-ups, 2026-10-20

- addressed 1 review step
- Added `not-class-file-error-test`: `(byte-array [1 2 3])` throws an
  exception that is not `ExceptionInfo` (not wrapped).
- Full suite on the Valhalla JDK: 20 tests, 168 assertions, 0 failed.

## Implementation review (pass 3), 2026-10-20

- no new review feedback

## Test review (pass 13), 2026-10-20

- added 1 step to be addressed

## Test review (pass 13) follow-ups, 2026-10-20

- addressed 1 review step
- Added `no-ctor-class` helper and `no-ctor-error-test`: a class with
  no `<init>` gives part `count of constructors`.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.

## Test review (pass 14), 2026-10-20

- no new test review feedback

## Test review (pass 15), 2026-10-20

- no new test review feedback

## Docs review, 2026-10-20

- No `CHANGELOG.md` or `doc/`. README is out of scope (design) and
  has no stale text.
- added 3 steps to be addressed

## Docs review follow-ups, 2026-10-20

- addressed 3 review steps
- Design status: transform implemented and tested; loader and macro
  not. A-13 and A-18 now `observed`. Embodiment and namespace
  docstring list the JDK imports; embodiment error comment gives
  `:class-name` and the check steps.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.

## Docs review (pass 2), 2026-10-20

- no new docs review feedback

## Code-shaper review, 2026-10-20

- added 3 steps to be addressed

## Code-shaper follow-ups, 2026-10-20

- addressed 3 review steps
- `check-constructor!` → pure `constructor-difference` (part or nil);
  `->value-class` throws once. Class transform extracted to
  `value-class-transform`. Test `build-class` adds the constructor
  only when `ctor-desc` is not nil; `no-ctor-class` calls it.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings.

## Code-shaper review (pass 2), 2026-10-20

- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
- added 2 steps to be addressed

## Code-shaper follow-ups (pass 2), 2026-10-20

- addressed 2 review steps
- Test namespace imports each class (`ClassBuilder`, `ClassEntry`,
  `File`, `FileAttribute`, `ExceptionInfo`); flags use
  `ClassFile/ACC_PUBLIC`, `ACC_FINAL`, `ACC_STATIC` (`Modifier`
  import removed). Built-fixture error tests call
  `check-built-error` (one prefix `vt.built.Built`); `deftest` names
  and `testing` strings unchanged.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings.

## Code-shaper review (pass 3), 2026-10-20

- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
- added 4 steps to be addressed

## Code-shaper follow-ups (pass 3), 2026-10-20

- addressed 4 review steps
- `->value-class`: `entry` is the `ClassEntry`, `this` the `ClassDesc`.
  Private `ACC_STATIC` removed; `instance-field?` uses
  `ClassFile/ACC_STATIC` (plan.md decision 3, slice 1 item 2 updated).
  Test: `member-ref-view` on the `MemberRefEntry` for invoke and field
  instructions; `ctor-elements` used in `ctor-code-view` and
  `debug-elements-test`.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings.

## Code-shaper review (pass 4), 2026-10-20

- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
- added 3 steps to be addressed

## Code-shaper follow-ups (pass 4), 2026-10-20

- addressed 3 review steps
- Test: private `ctor?` used in `ctor` and `structure-test`;
  `built-name` gives `vt.built.Built<n>` for `check-built-error` and
  `debug-elements-test`; `(set! *warn-on-reflection* true)` after `ns`.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings on JDK 25 and on the Valhalla JDK.

## Code-shaper review (pass 5), 2026-10-20

- added 2 steps to be addressed

## Code-shaper follow-ups (pass 5), 2026-10-20

- addressed 2 review steps
- Test: `member-ref-view` tags with the `Opcode` enum
  (`[Opcode/PUTFIELD ...]`, `[Opcode/INVOKESPECIAL ...]`);
  `value-ctor-code` agrees. Private `write-v!` replaces the 7 inline
  `deftype` field writes of `v`; each fixture byte shape unchanged.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings on JDK 25 and on the Valhalla JDK.

## Code-shaper review (pass 6), 2026-10-20

- added 1 step to be addressed

## Code-shaper follow-ups (pass 6), 2026-10-20

- addressed 1 review step
- Test: `attributes` hints `AttributedElement` (imported); no `cond`.
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings on JDK 25 and on the Valhalla JDK.

## Code-shaper review (pass 7), 2026-10-20

- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
- added 1 step to be addressed

## Code-shaper follow-ups (pass 7), 2026-10-20

- addressed 1 review step
- Test: `round-trip-loader` local fn `load` → `load-class` (no shadow of
  `clojure.core/load`).
- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  No reflection warnings on JDK 25 and on the Valhalla JDK.

## Code-shaper review (pass 8), 2026-10-20

- Full suite on the Valhalla JDK: 21 tests, 173 assertions, 0 failed.
  clj-kondo and cljfmt: no findings.
- Not filed: locals `name`, `class`, `bytes` in the test namespace
  shadow core vars, but only as values (no call reads as the core
  function, unlike `load` in pass 7).
- no new code-shaper feedback
