# Design steps

- [x] Use the existing value-capability guard for the AOT check.
  The design adds key `:value-class-unavailable` for the AOT check. The
  receiving boundary already throws `:value-capability-unavailable` with
  `:reason` for the same condition (`core/require-value-capability!`). The
  native-field guard uses one function and one key at analysis and at
  evaluation (`field-capability/require-field-capability!`, D14). Use one
  guard and one key for value-class capability, or state why a second key is
  necessary. Name the owner of the emitted guard in Architecture and D14.
  `impl.runtime` cannot own it: it requires no capability namespace.
  Update Contract and Acceptance 3 to match.
- [x] SCOPE_QUESTION: D19 says one namespace owns every compiler assumption,
  so that one place changes for a new Clojure version. The scope list leaves
  other `Compiler` couplings outside the boundary: `impl.fields` uses
  `Compiler$HostExpr/maybeSpecialTag`, `maybeArrayClass`, `maybeClass` and
  `Compiler/munge`, and `impl.loader` uses `Compiler/writeClassFile`. A human
  decides whether the boundary includes these, or whether D19 and Acceptance 1
  state that they stay outside.
  Decision (user, 2026-11-06): include them. The boundary owns every
  `clojure.lang.Compiler` use in `src`; Scope, Concepts, Architecture,
  Acceptance 1 and 6, and D19 updated.
- [x] Define "all definitions" in the `:not-installed` contract.
  Scope makes only ordinary value definitions receiving. Ordinary fallback
  definitions expand to `guarded-deftype` and install no capture. State
  whether `:not-installed` applies to value definitions only, or whether
  ordinary fallback definitions also become receiving. State the message for an
  ordinary definition: the current receiving message says "value field
  capture", which is wrong for a definition without fields.
- [x] Make the position and reach of the value-class check exact.
  "Before the class is used" can mean before the definition form or before
  `check-value-class`. In AOT output the definition form loads the class, so
  state that the check comes before the definition form. State whether every
  value expansion emits the check (direct evaluation and source loading too)
  or only AOT output; the design calls it the "AOT check". State how the error
  reaches the caller: the wrapper at namespace initialization and at
  invocation for a form inside a function. The usage guide lists this for
  every error.
- [x] Specify the startup measurement and the D17 outcome.
  State what is measured (probe cost only, or AOT application start with and
  without the check), the baseline and the run count. State whether this task
  changes the D17 commitment after the measurement, or keeps it provisional
  until the user decides. Acceptance 6 does not list D17.
- [x] Make the first Contract item agree with the other Contract items.
  It says behaviour does not change except for the new AOT check. The other
  items change behaviour: `:not-installed` for an ordinary definition moves
  from run time (`check-value-class`) to analysis, and a replayed ordinary
  expansion defines a value class where it gave `:not-value-class`. List
  these changes as exceptions.
- [x] Make the Problem statement agree with the D18 gap row for native fields.
  Problem says value-class AOT output does not run the probe. The D18 row in
  `embodiment.md` limits the gap to output without native non-nil fields:
  native-field output emits `require-field-capability!`, and field capability
  derives from value-class capability. State the order of the two emitted
  checks and which key and reason native-field AOT output gives under `off`
  and under a failed self-check.
- [x] Use one list of namespaces that use the compiler boundary.
  Problem and the D19 gap row name `transform`. Scope says "both bytecode
  backends". Architecture says "backends". `impl.value-backend` is not named.
  The record suffix (`fields/record-hidden-fields`) is in `impl.fields`, which
  is not named. Name the namespaces in Problem, Scope, Architecture and
  Acceptance 1 the same way.
- [x] Complete the Acceptance 6 document list.
  - Source names D4 as changed, and `compiler-and-capture.md` says D19 and
    D20 change D4. Acceptance 6 does not list D4 or that note.
  - `embodiment/runtime.md` says that only `loader` uses `Compiler/LOADER`,
    that `loader` and `capability` construct `DynamicClassLoader`, and gives
    a components table and a dependency direction without the boundary.
    Acceptance 6 lists only runtime interfaces and data flow.
  - D17 and D18 say value-class AOT output checks at namespace
    initialization. Scope says the check runs where the form is evaluated.
    Acceptance 6 does not list D17 or D18 text.
- [x] State how Acceptance 3 forces a failed self-check in the child AOT JVM.
  No mechanism exists for this (only the in-process `classify` test with
  `support/constant-value-class`). State whether the test replaces the
  owner accessor `impl.capability/capability` in the child JVM before
  `require` (D14: the accessor is the test replacement point), or whether
  production code gets a test hook. A production hook (for
  example a property value) changes the public surface. Also state which
  saved output the check uses (without native fields, with native fields, or
  both) and where the form is (top level or inside a function).
- [x] Make the two outputs of the startup measurement exact.
  "Output of the parent commit of the guard change" gives a baseline that
  differs only by the guard if the guard is in a commit of its own. If the
  guard commit also contains the D20 or boundary change, or if "output with
  the guard" comes from a later commit, the two outputs differ by more than
  the guard. State that the two outputs differ only in the guard form (for
  example: the guard is a separate commit, and the "with guard" output comes
  from that commit), so that the plan orders the commits to match.
- [x] Limit Acceptance 1 to production namespaces.
  Acceptance 1 says that no namespace other than the compiler boundary
  constructs `DynamicClassLoader` or reads `Compiler/LOADER`. Test namespaces
  do both now: 11 test files bind or read `Compiler/LOADER` (for example
  `expansion_checks.clj`, `capture_failure_test.clj`, `core_test.clj`), and
  test code constructs `DynamicClassLoader` 23 times (for example
  `probe_test_support.clj`, `loader_test.clj`). `dev/spike` does the same.
  Scope does not include changes to these tests, and Acceptance 5 requires
  that the suites pass. State that Acceptance 1 applies to `src` namespaces
  (the boundary users), or state which test code must use the boundary.
- [x] Make the `:not-value-class` message agree with the D20 contract.
  The Contract says that a replayed or transferred ordinary expansion
  installs at analysis and no longer gives `:not-value-class`. The
  `:not-value-class` message in `core/error-message` still names "the
  expansion was evaluated apart from its macroexpansion (for example
  (eval (macroexpand form)))" as a known cause. The usage guide error list
  (`doc/usage.md`, `:not-value-class` causes) has the same cause. Acceptance 1
  says "Shape errors and messages are unchanged". The Contract states only the
  `:not-installed` message change. State the new `:not-value-class` message
  (without the replay cause) in the Contract, and limit "messages are
  unchanged" in Acceptance 1 to shape errors.
- [x] Make the class-definition limit in Acceptance 1 exact.
  Scope puts "one define function for user and probe classes" in the
  boundary. Acceptance 1 limits only the construction of
  `DynamicClassLoader`. It does not say whether other namespaces in `src`
  can call `.defineClass`, subclass `DynamicClassLoader` (the capture proxy
  in `impl.loader`), or import it for type hints. Now `impl.probe-builder`
  calls `.defineClass` on a loader argument (`define-probe`, used by
  `define-probe-class` and by tests), and `impl.loader` calls it in
  `define-and-link` and in the capture proxy. State which of these code uses
  only the boundary can have, so that Acceptance 1 can be checked with a
  search of `src`. Also state whether `probe-builder/define-probe` and
  `define-probe-class` stay (and call the boundary define function) or move.
- [x] Make D19 agree with the Acceptance 1 limit and the boundary users.
  D19 (`decisions/compiler-boundary.md`, updated in `c392fc51`) says "No
  other namespace refers to `clojure.lang.Compiler`". Acceptance 1 limits
  this to code in `src`: docstrings and messages can name `Compiler/LOADER`,
  and test and `dev` namespaces bind `Compiler/LOADER` and construct
  `DynamicClassLoader` (32 files). Architecture says "all references ...
  in `src`", which also reads as text. D19 also names the users as "`core`,
  `fields`, `loader`, both bytecode backends and the probes"; Concepts names
  `impl.transform`, `impl.value-backend`, `impl.identity-backend` and the
  three probe namespaces. Use the Acceptance 1 limit (code use in `src`) in
  D19 and Architecture, and one user list, or state why they differ.
- [x] State what the boundary holds for constructor shape and field order.
  Scope and Acceptance 1 put constructor shape, field order and record
  suffix in the boundary, and say that the boundary users read them only
  from it. Now these facts are in bytecode checks, not in data:
  `impl.transform` `expected-code`, `constructor-difference`,
  `record-constructor-difference` and `delegating-code` (on
  `java.lang.classfile`); `impl.identity-backend` `hidden-indexes` and
  `closed-checks` (on `clojure.asm`); `impl.fields/record-hidden-fields`
  (data, used by `core` and `transform`). The boundary loads on Java 8, so
  it cannot hold the `java.lang.classfile` checks. State what form the facts
  take in the boundary (for example data that the backends read: the
  constructor parameter order, the three record constructors and their
  arities, the hidden field names and tags), which of these functions stay
  in their namespace and read that data, and where the
  `:unexpected-constructor` and identity shape errors are thrown. Then
  state how Acceptance 1 checks the first sentence: the search in Acceptance 1
  finds only compiler code use, not shape facts.
- [x] Plan: state the receiving macro for ordinary value definitions.
  Slice 4 says "expand ... to a receiving macro" but does not name it. The
  `(let [] ...)` wrapper of `policy-expansion` is load-bearing: one-step
  `macroexpand` stops at `let*`. A bare receiving macro form at the top
  would expand on and throw `:not-installed` outside `Compiler/LOADER`
  (Acceptance 2). State whether ordinary value definitions take the
  existing receiving branch (`(let [] (value-form modes))` with modes `[]`,
  through `value-field-deftype` and `value-field-deftype*`) and whether
  `value-expansion` is removed, or name the new macro and its wrapper.
- [x] Plan: state what slice 5 does with `aot-value-output-off-test`.
  Slice 5 says "interim limit removed". After the guard emission this test
  fails (saved output without native fields throws under `off`), and slice 5
  must keep all suites green. Slice 6 adds the Acceptance 3 `off` test for
  the same output. State whether slice 5 rewrites this test to expect
  `:value-capability-unavailable` `:disabled` (and slice 6 extends it with
  the form inside a function and the failed self-check), or deletes it and
  slice 6 replaces it.
- [x] Plan: state how slice 7 builds and runs the two outputs.
  The outputs come from two commits (slice 5 and its parent). `compile`
  also writes AOT classes for the `value-type` namespaces that the fixture
  loads (`aot_fixture.clj` `compile-form`). State how each output is built
  (for example a `git worktree` per commit, compile in a child JVM with that
  `src`), which library is on the run classpath (the same `src` for both
  runs, or the AOT library classes in each output), and where the script
  is (committed under `dev/bench`, as other benchmarks, or not committed).
- [x] Plan: add `core/in-place?` to the slice 2 literal replacement.
  Design Architecture puts `core` record shape detection under "Shape
  checks ... read the shape data", and adds a boundary function for the
  hidden-field index (n - 4 + k). The pass-7 slice note lists `core` ~119
  (`(+ (- n 4) k)`) as a literal to replace. Steps slice 2 "Replace shape
  literals" lists only `impl.transform` and `impl.identity-backend`, and
  the `core` reader step covers only the third element. Add `in-place?` as
  a user of the boundary index function in plan.md and steps.md.
- [x] Plan: name the saved sources and check forms of the slice-6 tests.
  Slice 5 names the fixture and the check form for each test. Slice 6
  does not:
  - Form inside a function: no saved source is named. The candidate is
    `public-field-aot/function-source` (`checked.aot-fixture`, `(defn
    install [] (defvaluetype Holder [^String ^:non-nil required]))`),
    compiled now only with the identity producer. Its check form
    `function-aot-checks/invoke-install` gives each invocation failure as
    `registry/failure` (`:class` and `:message`, no `:data`). Thus it
    cannot check `:value-capability-unavailable` and `:disabled` as
    Acceptance 3 requires. State the saved source (with
    `config/value-producer`), the check function that gives the per
    invocation root failure with `:data` narrowed to `:value-type/error`
    and `:reason` (as `aot-load` does), and the policies.
  - Failed self-check: state the two saved sources (for example
    `checked.aot-fixture` with `public-field-aot/identity-source` and
    `restricted.aot-fixture` with `field-aot/restricted-source`), the
    namespace and name of the new check function that replaces the
    accessor and then calls `aot-load`, the receiver (EA3 without the
    switch property) and the policies. Precedent for the replacement:
    `controlled_value_capability_test.clj` (`with-redefs` of
    `value-type.impl.capability/capability`).
- [x] Plan: pass the `:jvm-rejected` check to the boundary, as design.md says.
  Design Architecture: `impl.loader` "gives the byte rewrite and these
  checks to the boundary as functions", where the checks are the field
  class check and the `:jvm-rejected` error. plan.md and steps.md slice 1
  name only the byte rewrite and the field class check. They also say
  that the boundary define function does not wrap `LinkageError`. But the
  capture proxy, which moves to the boundary, defines and links the
  target class (now `loader/define-target-class`). With the plan as it is,
  the implementer must either drop `:jvm-rejected` for the target class
  (a Contract change) or keep the target define in `impl.loader`
  (compiler code use outside the boundary, Acceptance 1). In plan.md and
  slice 1, add the `:jvm-rejected` wrap of the target define and link as a
  third function from `impl.loader`. Also state that the rewrite argument
  is a function of the delegate loader: the boundary constructs the
  delegate, and backend `prepare` takes it
  (`value-backend/prepare`, `identity-backend/prepare`).
- [x] Plan: state the capture-loader entry point that tests use after slice 1.
  Slice 1 moves the capture proxy into `compiler/install-capture!`, which
  reads and sets `Compiler/LOADER` and returns a boolean. Five test sites
  call the private `#'loader/capture-loader` `[target parent
  specifications representation shape]` directly, with an explicit parent
  and no `LOADER` binding, and call `.defineClass` on the returned loader:
  `test/compatibility/value_type/capture_failure_test.clj` (~20),
  `identity_loader_test.clj` (~17),
  `test/runtime/value_type/impl/removed_field_test.clj` (~189, ~255),
  `impl/loader_test.clj` (~342). `loader_test.clj` `link-test` (~246) calls
  `#'loader/define-target-class` with a `(DynamicClassLoader.)`. State
  whether the boundary exposes a capture-loader constructor (parent and
  the `prepare`, `check`, `define-target`, `rewrite-companions?`
  functions) that `install-capture!` uses, so that `impl.loader` keeps
  `capture-loader` and `define-target-class` with their arguments and the
  tests do not change; or state how slice 1 rewrites these tests.
- [x] Plan: state what slice 2 does with the hidden-field count literal 4.
  Design Acceptance 1 says the shape checks that Architecture names take
  the shape facts from the boundary, not from literals (check by review).
  Steps slice 2 lists the literals to replace, but not these uses of the
  hidden-field count:
  - `core/record-shape` (~143): `(= 4 (count hidden))`.
  - `impl.identity-backend/closed-checks` (~173): `(< n 4)`.
  - `impl.transform/record-constructor-difference` (~406): `(< (count
    fields) 4)`. The steps range "~406-419 (record constructor arities)"
    includes this line, but this literal is a minimum field count, not an
    arity.
  State whether each use reads the boundary (for example `(count
  record-hidden-fields)` or the main record constructor count 4), or
  stays as a literal, so that the Acceptance 1 review has one rule. Edit
  plan.md and the slice-2 items in steps.md to match.
