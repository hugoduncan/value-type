# Steps: 002 Value class transform

## Slice 1: transform namespace

- [x] Create `src/value_type/impl/transform.clj` with namespace
      `value-type.impl.transform`, `(set! *warn-on-reflection* true)`,
      imports from `java.lang.classfile` and `java.lang.constant`
      only (no Clojure compiler classes).
- [x] Add private vars `ACC_IDENTITY` (0x20), `ACC_STRICT_INIT`
      (0x800) and a private `instance-field?` predicate (field without
      `ClassFile/ACC_STATIC`).
- [x] Add a private constructor-code builder: for each instance field
      in declaration order `aload_0`, `loadLocal` of the field type
      kind at the running slot (`slotSize` advances the slot),
      `putfield` (owner this class); then `aload_0`,
      `invokespecial java/lang/Object.<init>()V`; then `return`.
- [x] Add public `->value-class` (bytes → bytes) with a docstring that
      gives the input, the output and the error contract. Transform:
      version → `ClassFile/latestMajorVersion` +
      `ClassFile/PREVIEW_MINOR_VERSION`; class flags minus
      `ACC_IDENTITY`; instance field flags plus `ACC_STRICT_INIT`;
      constructor: `transformMethod` with a `MethodTransform` that
      replaces only the `CodeModel` (`.withCode` with the new code)
      and passes each other method element through with `.with` (not
      the spike `.withMethod`); all other elements pass through with
      the default shared constant pool.
- [x] Verify the namespace loads with no reflection warnings on the
      Valhalla JDK and on the default JDK.

## Slice 2: input check and error contract

- [x] Add a private error helper that throws `ex-info` with
      `:value-type/error :unexpected-constructor`, `:class-name`
      (binary name string), and a message containing the class name,
      the exact part string, the cause (not the supported `deftype`
      constructor shape) and the fix text with exact string
      `Clojure 1.12`.
- [x] Add check step 1: exactly one `<init>` method, else throw with
      part `count of constructors`.
- [x] Add check step 2: constructor parameter descriptor list equals
      the instance field descriptor list in declaration order, else
      throw with part `parameters`.
- [x] Add check step 3: strip `Label`, `LineNumber`, `LocalVariable`
      and `LocalVariableType` elements from the constructor code;
      compare position by position with the expected list of scope
      item 3 (loads match by `asLoadable` type kind and slot; slot
      rule `s(0)=1`, `+2` after `long`/`double` else `+1`; length
      `3n + 3`). First difference at position 1 or 2 → part
      `super call`; later (including length mismatch) → part
      `field writes`.
- [x] Wire the check into `->value-class` before the transform.

## Slice 3: test helpers

- [x] Create `test/value_type/impl/transform_test.clj` with namespace
      `value-type.impl.transform-test`.
- [x] Add a fixture helper: `eval` a `deftype`/`defrecord` form (names
      via `gensym`) with `*compile-files*` true and `*compile-path*`
      bound to a new temp directory; return the class bytes, the
      `Class` that `eval` returned, and the compile directory.
- [x] Add built-fixture helpers with `ClassFile/of`
      (`.withField`, `.withMethodBody`, `CodeBuilder` ops) for the
      three Verification 3 shapes: `(I)V` parameters mismatch;
      `(J)V` field-write-before-super; `(J)V` deftype shape with
      `lconst_0` instead of a slot-1 load.
- [x] Add the round-trip test loader: `proxy` of
      `clojure.lang.DynamicClassLoader` with a new
      `DynamicClassLoader` parent; override `loadClass`: fixture
      name → define output bytes; `fixture-name$...` → define bytes
      from the fixture compile directory; else delegate to the
      parent. Define with public `defineClass(String, byte[], Object)`
      and an atom cache. Instantiate via
      `clojure.lang.Reflector/invokeConstructor`.
- [x] Define the five Verification 1 fixtures: no fields; five fields
      `^int`, `^long`, `^double`, `^boolean`, untyped `Object` (slots
      1 2 4 6 7); protocol method with a branch; inner `fn` using
      `this`; static constants (keyword in a method body).

## Slice 4: structure tests (Verification 1)

- [x] For each fixture, test the output version equals
      `ClassFile/latestMajorVersion` and
      `ClassFile/PREVIEW_MINOR_VERSION`, class flags equal input minus
      `@#'value-type.impl.transform/ACC_IDENTITY`, and each instance
      field has input flags plus
      `@#'value-type.impl.transform/ACC_STRICT_INIT`.
- [x] For each fixture, test pass-through: this class, superclass,
      interfaces, static fields (name, descriptor, flags, attributes),
      instance field names/descriptors/attributes, other methods
      (name, descriptor, flags, code bytes), class attributes, and
      constructor name/descriptor/flags equal the input. Compare
      attributes by the rule in plan.md decision 6 ("Attribute
      comparison"): equal name lists, then by kind; another kind
      fails the test.
- [x] For each fixture, test the constructor code is the new shape
      (field writes in order, then super call, then return).

## Slice 5: round-trip tests (Verification 2)

- [x] For each fixture, define the output class in a round-trip
      loader; assert `Class::isValue` is true; a new instance has the
      field values; two instances with the same field values are
      `identical?` (same `Object` instance for the `Object` field; no
      `NaN`).
- [x] For each fixture-defined method, call it with the same arguments
      on the value object and on a `deftype` instance made from the
      `Class` that `eval` returned: the inner-`fn` method returns a
      result `identical?` to its own receiver; each other method
      returns `=` results that are not and contain no fixture
      instance.

## Slice 6: error tests (Verification 3)

- [x] Test a `defrecord` input throws `ex-info` with
      `:value-type/error :unexpected-constructor`, the correct
      `:class-name`, and a message containing `count of constructors`
      and `Clojure 1.12`.
- [x] Test the built `(I)V` fixture → part `parameters`; same ex-info
      shape and `Clojure 1.12` assertions.
- [x] Test the built write-before-super fixture → part `super call`.
- [x] Test the built `lconst_0` fixture → part `field writes`.

## Slice 7: verification and close-out

- [x] Run `clojure -M:test -m scry.cli` with `JAVA_CMD` set to the
      Valhalla `java`
      (`/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`);
      full suite passes (exit 0).
- [x] In a REPL, run `scry.core/run` on
      `value-type.impl.transform-test`; assert `:pass? true` and
      `[:summary :var-count]` equals the `deftest` count (> 0).
- [x] Confirm each public var has a docstring (input, output, error
      contract) and the namespace has no Clojure compiler
      requires/imports.
- [x] Update `mementum/state.md`: transform done; next step is the
      loader task.

## Review follow-ups (implementation review)

- [x] Robustness: `field-values` in `value-type.impl.transform-test`
      uses the order of `Class/getDeclaredFields`. The JDK does not
      specify this order. Read the instance field values by name
      (`a` … `e`, from the input `FieldModel` list or the form), so
      the round-trip check "a new instance has the field values" does
      not depend on reflection order.
- [x] Docstring: the error contract in the `->value-class` docstring
      gives only the ex-data keys. Add the message contract of
      design.md "Error contract": the message gives the class name,
      the part (`count of constructors`, `parameters`, `super call`
      or `field writes`), the cause and the fix (`Clojure 1.12`).
      Acceptance 5 needs the full error contract.

## Review follow-ups (test review)

- [x] Coverage: no test covers the length rule of scope item 3 ("This
      includes a missing `return` and extra elements" → part
      `field writes`). This rule guards the risk in the decision
      "Check the whole constructor shape" (a new field write). Add a
      built fixture: one `long` field, `(J)V`, the `deftype` shape
      plus one extra element before `return` (for example a second
      `aload 0`, `lload 1`, `putfield v`). Assert part `field writes`
      with `check-error`.
- [x] Extension: `check-error` asserts that the ex-data is equal to
      the map of the two keys. This blocks new keys in the data,
      which the error contract does not forbid. Assert only
      `:value-type/error` and `:class-name` (for example with
      `select-keys`).
- [x] Scope: `round-trip-test` asserts `(not (is-value? dc))` on the
      `deftype` class. This checks the Clojure fixture and the test
      helper, not the transform. Remove the assertion.

## Review follow-ups (test review, pass 3)

- [x] Signal: the expected constructor code in `value-ctor-code`
      uses the same rules as the transform (`TypeKind/from`,
      `asLoadable`, `reductions` of `slotSize`). An error in the slot
      rule or the load kind in both places does not fail the
      structure tests, and these are the tests that run without
      Valhalla. Add a literal assertion for the `:five-fields`
      fixture: the load slots of the output constructor are
      `[1 2 4 6 7]` and the load type kinds are `INT LONG DOUBLE INT
      REFERENCE` (design Verification 1).
- [x] Boundary: the error tests give the first difference at
      position 2 (`super call`) and at positions 4 and 6
      (`field writes`). No test covers position 3, the boundary of
      the rule "position 1 or 2 → `super call`; later →
      `field writes`". A change of `(<= pos 2)` to `(<= pos 3)` does
      not fail a test. Add a built fixture: one `long` field, `(J)V`,
      super call, then `return` (no field write; first difference at
      position 3). Assert part `field writes` with `check-error`.

## Review follow-ups (test review, pass 4)

- [x] Boundary: no error test gives the first difference at position
      1. The rule is "position 1 or 2 → `super call`". The tests cover
      only position 2 (`write-before-super-class`). A change of
      `(<= pos 2)` to `(= pos 2)` does not fail a test. Add a built
      fixture: one `long` field, `(J)V`, a first element that is not
      `aload 0` (for example `lload 1`, `pop2`, then the `deftype`
      shape). Assert part `super call` with `check-error`.
- [x] Signal: `structure-test` checks static fields only in the
      `(static? fi)` branch. Only the `:static-constants` fixture has
      a static field (`const__0`). If Clojure output changes and the
      keyword no longer makes a static field, the static pass-through
      check does not run and no test fails. Add a guard assertion in
      the `:static-constants` case: the input has at least one static
      field.

## Review follow-ups (test review, pass 5)

- [x] Coverage: no test covers the operands of the super call check.
      Each error fixture gives its first difference before or after
      position 2, or at position 2 with an element that is not an
      `invokespecial`. A change of `object-init?` to
      `(instance? InvokeInstruction e)` does not fail a test (checked
      with `with-redefs` on the error tests). This check is the proof
      that the superclass is `Object` (design scope item 3). Add a
      built fixture: one `long` field, `(J)V`, the `deftype` shape,
      but the super call is `invokespecial` of an `<init>()V` with
      another owner (for example `java/lang/Number`, with
      `.withSuperclass` to match). Assert part `super call` with
      `check-error`.
- [x] Coverage: no test covers the operands of the field write check.
      A change of `putfield?` to `(instance? FieldInstruction e)` does
      not fail a test (checked with `with-redefs`). Then a constructor
      that writes argument 1 to field `b` and argument 2 to field `a`
      passes the check, and the rebuilt constructor has different
      behaviour (the intent forbids wrong bytes). Add a built fixture:
      two `long` fields `a` `b`, `(JJ)V`, the `deftype` shape with the
      two `putfield` names swapped. Assert part `field writes` with
      `check-error`. `build-class` takes one field `v`; extend it to
      take the field list.

## Review follow-ups (test review, pass 6)

- [x] Coverage: no test covers the slot operand of the load check.
      A change of `load?` to ignore the slot (match only the
      instruction kind) does not fail a test (checked with
      `with-redefs` on the structure and error tests). Then a
      constructor that writes argument 2 to field `a` and argument 1
      to field `b` passes the check, and the rebuilt constructor has
      different behaviour (the intent forbids wrong bytes). Add a
      built fixture: two `long` fields `a` `b`, `(JJ)V`, the
      `deftype` shape with the two loads swapped (`lload 3` before
      `putfield a`, `lload 1` before `putfield b`). Assert part
      `field writes` with `check-error`.

## Review follow-ups (test review, pass 7)

- [x] Determinism (I/O): `compile-fixture` makes a new temp directory
      (`vt-fixture*`) for each fixture and does not delete it. Each
      suite run leaves 6 directories with class files in the temp
      directory (181 after the runs to date). Delete each directory
      after use (for example a recursive delete at the end of the
      test run, or `deleteOnExit` on each file and directory). The
      round-trip loader reads inner classes from `:dir`, so delete
      only after the round-trip test.
- [x] Clarity: `code-bytes` gives the type hint with
      `^java.lang.classfile.attribute.CodeAttribute identity` inside
      `some->`. This hides the intent (a parsed `CodeModel` is a
      `CodeAttribute`). Put the hint on the `.orElse` value directly,
      for example
      `(some-> ^CodeAttribute (.orElse (.code m) nil) .codeArray vec)`
      with `CodeAttribute` in the `:import`. Keep no reflection
      warnings.

## Review follow-ups (test review, pass 8)

- [x] Coverage: no test covers the owner operand of the field write
      check (scope item 3: "`putfield` with owner this class"). Each
      built fixture writes with owner `this`. A change of `putfield?`
      that ignores the owner does not fail a test. Then a constructor
      that writes field `v` of another class passes the check, and the
      rebuilt constructor has different behaviour (the intent forbids
      wrong bytes). Add a built fixture: one `long` field, `(J)V`, the
      `deftype` shape, but the `putfield` owner is another class (for
      example `vt.built.Other`, field `v`, `J`). Assert part
      `field writes` with `check-error`.

## Review follow-ups (test review, pass 9)

- [x] Consistency: the error test names use two schemes. Three tests
      have the name of the message part (`parameters-error-test`, `super-call-error-test`,
      `field-writes-error-test`), and the other tests have the name of
      the input shape (`swapped-loads-error-test`,
      `field-owner-error-test`). Six tests give `field writes` and
      three give `super call`. So a failure of `field-writes-error-test`
      does not tell which shape failed (it is the `lconst_0` input).
      Name each error test after its input shape (for example
      `lconst-error-test`, `write-before-super-error-test`). Give each
      error test a `testing` string with the same form (the shape and
      the first-difference position when it is known). Five tests have
      no `testing` string now.

## Review follow-ups (test review, pass 10)

- [x] Coverage: no test covers an element after `return` (the
      expected list ends first; design scope item 3: "where only one
      list has an element ... extra elements"). Each error fixture
      gives its first difference while both lists have an element.
      A change of `first-difference` that removes `(empty? preds)`
      from the `or` does not fail a test. Then such an input throws a
      `NullPointerException`, not the `ex-info` of the error contract
      (checked on JDK 25 with `with-redefs`). Add a built fixture: one
      `long` field, `(J)V`, the `deftype` shape, then a second
      `return` (dead code; it builds with the default stack maps).
      Assert part `field writes` with `check-error` and a `testing`
      string with position 7.

## Review follow-ups (test review, pass 11)

- [x] Coverage: no test covers the strip rule of scope item 3
      ("remove labels, line numbers and local variable entries
      (`LocalVariable`, `LocalVariableType`)"). The Clojure 1.12
      fixtures have only `LineNumber` elements in the constructor
      code. A change of `debug-element?` to match only `LineNumber`
      does not fail a test (checked on JDK 25 with `with-redefs`:
      the input below then throws `super call`). Add a built fixture:
      one `long` field, `(J)V`, the `deftype` shape with a bound
      start label, an end label, and `localVariable` entries for
      slots 0 and 1 and one `localVariableType` entry (signature
      from `java.lang.classfile.Signature/parseFrom`). Assert that
      `->value-class` returns bytes and that the output constructor
      code view is the value class shape.

## Review follow-ups (test review, pass 12)

- [x] Coverage: no test covers the last rule of design "Error
      contract" ("Other input errors (for example bytes that are not a
      class file) are the exceptions of `java.lang.classfile`. They are
      not wrapped."). The `->value-class` docstring gives this rule
      too. A change that wraps each exception in the
      `:unexpected-constructor` `ex-info` does not fail a test. Then
      `value-type.core` users see a wrong cause and a wrong fix. Add
      an error test: bytes that are not a class file (for example
      `(byte-array [1 2 3])`) throw an exception that is not
      `clojure.lang.ExceptionInfo` (checked on JDK 25:
      `IllegalArgumentException` "Bad magic number"). Use a `testing`
      string in the form of the other error tests.

## Review follow-ups (test review, pass 13)

- [x] Coverage: no test covers a class with no constructor (scope
      item 3 step 1: "exactly one method named `<init>`"). The only
      `count of constructors` test (`defrecord-error-test`) has more
      than one constructor. A change of the count check to
      `(> (count ctors) 1)` does not fail a test. Then an input with
      no `<init>` throws a `NullPointerException`, not the `ex-info`
      of the error contract (checked on JDK 25). Add a built fixture:
      one `long` field, no constructor (`build-class` always adds
      one; add a helper that builds the class without it). Assert part
      `count of constructors` with `check-error` and a `testing`
      string in the form of the other error tests.

## Review follow-ups (docs review)

- [x] Stale: `ramora/defvaluetype-design.md` "Status" says "There is
      no library code." This task adds `value-type.impl.transform`.
      Change the status: the transform (D5 D6) is implemented and
      tested; the loader and the macro are not.
- [x] Consistency: `ramora/defvaluetype-design/embodiment.md` says
      "`transform` depends only on `java.lang.classfile`", and the
      `value-type.impl.transform` namespace docstring says "Uses only
      `java.lang.classfile`". The namespace also imports
      `java.lang.constant` (`ClassDesc`, `ConstantDescs`) and
      `java.util.function.Consumer`. Make both texts agree with the
      imports (for example "only JDK classes: `java.lang.classfile`,
      `java.lang.constant`, `java.util.function`; no Clojure compiler
      classes"). Keep the A-18 rule.
- [x] Accuracy: `ramora/defvaluetype-design/assertions.md` gives A-13
      (D5 D6 realizedBy `value-type.impl.transform`) and A-18
      (`transform` does not depend on `clojure.lang.Compiler`) the
      status `intended`. The code and the tests of this task now show
      both. Change the status to `observed` and the basis to
      `src/value_type/impl/transform.clj` and
      `value-type.impl.transform-test`. In `embodiment.md`
      "Internal", add the ex-data key `:class-name` and the check
      steps (count of constructors, parameters, code) to the
      `->value-class` error comment, so the interface agrees with
      the docstring.

## Review follow-ups (code-shaper)

- [x] Simplicity: `check-constructor!` mixes computation and flow
      control. It computes three checks in nested `let` forms and
      throws at three points. Split it: a pure private
      `constructor-difference` (`ClassModel`, fields → part string or
      nil) that does the three checks in order, and one call of
      `unexpected-constructor!` in `->value-class` when the result is
      not nil. Keep the order and the part strings of scope item 3.
      The error tests must pass with no change.
- [x] Consistency: `->value-class` has the class transform inline (a
      `reify ClassTransform` with five `cond` branches). The field and
      the method transforms are named (`strict-init-transform`,
      `constructor-transform`). Extract a private
      `value-class-transform` (`this`, fields → `ClassTransform`), so
      `->value-class` reads as parse, check, transform.
- [x] Consistency: in `value-type.impl.transform-test`,
      `no-ctor-class` copies the class header of `build-class` (the
      flags `(bit-or 0x1 0x10 ACC_IDENTITY)`, the superclass, the
      field loop). Make one header source: for example `build-class`
      adds the constructor only when `ctor-desc` is not nil, and
      `no-ctor-class` calls it with nil. Keep each fixture byte shape.

## Review follow-ups (code-shaper, pass 2)

- [x] Consistency: `value-type.impl.transform-test` imports most JDK
      and Clojure classes, but it also uses fully qualified names:
      `clojure.lang.Reflector` in `is-value?` (`Reflector` is already
      imported), `java.lang.classfile.ClassBuilder` in `build-class`,
      `java.lang.classfile.constantpool.ClassEntry` in
      `superclass-name` and `structure-test`, `java.io.File` and
      `java.nio.file.attribute.FileAttribute` in `compile-fixture`,
      `clojure.lang.ExceptionInfo` in `thrown-data` and
      `not-class-file-error-test`. Import each class and use the short
      name. Also `build-class` gives the flags as literals
      (`0x1 0x10`, `0x11`, `0x1`) next to `@#'transform/ACC_IDENTITY`,
      and `static?` uses `Modifier/STATIC`. Use the named constants
      (`ClassFile/ACC_PUBLIC`, `ClassFile/ACC_FINAL`). Keep each
      fixture byte shape and no reflection warnings.
- [x] Simplicity: the 12 built-fixture error tests repeat one body:
      `(let [n (str "vt.built." (gensym "X"))] (check-error (f n) n
      part))`. The `gensym` prefixes do not agree with the shape names
      of the pass-9 rename (`int-param` → `Params`,
      `write-before-super` → `Order`, `swapped-writes` → `Swapped`,
      `other-super` → `Super`). Add one helper, for example
      `(check-built-error build part)`, that makes the class name with
      one prefix (`vt.built.Built`), calls `build` and `check-error`.
      Keep each `deftest` name and `testing` string, so a failure
      still names the shape.

## Review follow-ups (code-shaper, pass 3)

- [x] Consistency (naming, data shapes): in `value-type.impl.transform`,
      `this` names two shapes. In `->value-class` it is the
      `ClassEntry` (`.thisClass cm`); in `value-class-transform`,
      `constructor-transform` and `constructor-code` it is the
      `ClassDesc` (`.asSymbol`). Bind `this` to the `ClassDesc` in
      `->value-class` and give the `ClassEntry` another name (for
      example `entry`), so one name has one shape in the namespace.
- [x] Consistency (idioms): `value-type.impl.transform` defines a
      private `ACC_STATIC` (0x8), but the JDK gives
      `ClassFile/ACC_STATIC`, and the test namespace uses
      `ClassFile/ACC_STATIC` (`static?`). Use `ClassFile/ACC_STATIC`
      in `instance-field?` and remove the private var. Keep
      `ACC_IDENTITY` and `ACC_STRICT_INIT` (no JDK 25 constant).
      Change plan.md decision 3 and steps.md slice 1 item 2 to agree.
- [x] Simplicity: in `value-type.impl.transform-test`,
      `instruction-view` has two branches with the same body for
      `InvokeInstruction` and `FieldInstruction` (opcode, owner, name,
      type). Both give a `MemberRefEntry` (`.method`, `.field`). Use
      one helper on the `MemberRefEntry` for the two branches. Keep
      each view value.
- [x] Consistency: in `value-type.impl.transform-test`, the
      constructor code elements are read with the same inline form
      `(.elementList ^CodeModel (.orElse (.code (ctor cm)) nil))` in
      `ctor-code-view` and in `debug-elements-test`. Add one helper
      (for example `ctor-elements`) and use it in both places.

## Review follow-ups (code-shaper, pass 4)

- [x] Consistency: in `value-type.impl.transform-test`, the
      constructor test `#(= "<init>" (:name (method-view %)))` is
      inline in `ctor` and in the `others` function of
      `structure-test`. Add one private `ctor?` predicate (method
      model → boolean) and use it in both places.
- [x] Consistency: built class names come from two forms:
      `check-built-error` (`(str "vt.built." (gensym "Built"))`) and
      `debug-elements-test` (`(str "vt.built." (gensym "Debug"))`).
      Add one helper (for example `built-name`) that gives
      `vt.built.Built<n>` and use it in both places.
- [x] Robustness (enforceable invariants): the transform namespace
      has `(set! *warn-on-reflection* true)`, but the test namespace
      does not. The implementation notes state "no reflection
      warnings" for the test namespace; nothing in the file enforces
      it. Add `(set! *warn-on-reflection* true)` after the `ns` form
      and keep no reflection warnings on JDK 25 and on the Valhalla
      JDK.

## Review follow-ups (code-shaper, pass 5)

- [x] Consistency (data shapes): in `value-type.impl.transform-test`,
      `instruction-view` tags views in two forms. Load and return
      views use a keyword tag and a `TypeKind` enum
      (`[:load TypeKind/LONG 1]`, `[:return TypeKind/VOID]`).
      `member-ref-view` uses the opcode as a string
      (`["PUTFIELD" ...]`, `["INVOKESPECIAL" ...]`), so
      `value-ctor-code` must write these strings. Use one tag form for
      each view: for example the `Opcode` enum
      (`[Opcode/PUTFIELD owner name type]`), as the load view uses the
      `TypeKind` enum. Keep each comparison in `structure-test`,
      `five-fields-load-test` and `debug-elements-test`.
- [x] Simplicity: in `value-type.impl.transform-test`, the field write
      of the `deftype` shape for field `v`
      (`(.aload c 0) (.lload c 1) (.putfield c this "v" ConstantDescs/CD_long)`)
      is inline 7 times (`write-before-super-class`,
      `first-element-class`, `extra-write-class` (2), `other-super-class`,
      `after-return-class`, `debug-elements-class`). `super-call!` is a
      helper for the other shared part. Add one helper (for example
      `(write-v! this c)`), so each fixture shows only its difference
      from the `deftype` shape. Keep each fixture byte shape.

## Review follow-ups (code-shaper, pass 6)

- [x] Simplicity: in `value-type.impl.transform-test`, `attributes`
      selects the receiver type with a `cond` (`FieldModel`, else
      `ClassModel`). Both types extend
      `java.lang.classfile.AttributedElement`, which gives
      `attributes()`. Hint the argument as `AttributedElement` (add
      the import) and remove the `cond`. Keep each comparison in
      `structure-test` and no reflection warnings.

## Review follow-ups (code-shaper, pass 7)

- [x] Clarity (naming): in `value-type.impl.transform-test`,
      `round-trip-loader` binds a local fn `load` in `letfn`. This
      name shadows `clojure.core/load`, so the call `(load this name)`
      reads as the core function. Give the local fn a name that does
      not shadow a core var (for example `load-class`). Keep the
      loader behaviour and no reflection warnings.
