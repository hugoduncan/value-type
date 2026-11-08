# Steps

## Slice 1: compiler boundary namespace (compiler code use)

- [x] Create `src/value_type/impl/compiler.clj`: requires only Clojure,
      no value-type namespace, no `java.lang.classfile` import; loads on
      Java 8.
- [x] Add the one define function (loader parameter, calls `.defineClass`,
      does not wrap `LinkageError`).
- [x] Move the capture loader into the boundary: the delegate loader,
      the `proxy` of `DynamicClassLoader`, class file output
      (`Compiler/writeClassFile` under `*compile-files*`) and the
      `LOADER` binding. Two boundary functions:
      - `compiler/capture-loader` `[target parent prepare check
        define-target rewrite-companions?]`: constructs the delegate with
        `parent`, calls `prepare` once (at construction), returns the
        proxy; reads and sets no `LOADER` binding.
      - `compiler/install-capture!` `[make-loader]`: when
        `Compiler/LOADER` is bound (`isBound`), `.set` it to
        `(make-loader @Compiler/LOADER)` and return `true`; else return
        `false`.
      The arguments that `impl.loader` gives `compiler/capture-loader`:
      - `prepare`: function of the delegate that returns
        `{:owner :companion}`; it resolves the backend `prepare` with
        `requiring-resolve` (lazy `transform`).
      - `check`: function of the delegate, the field class check
        (`check-field-classes!` closed over target and specifications),
        called at the target define before the target rewrite.
      - `define-target`: function of `[delegate class-name bytes src]`
        (`define-target-class`): calls the boundary define function,
        links the class and wraps `LinkageError` as `:jvm-rejected`; no
        `DynamicClassLoader` type hint, and `impl.loader` no longer
        imports `Compiler` or `DynamicClassLoader`.
      - `rewrite-companions?`: boolean, true when specifications is not
        empty (companion bytes written in AOT only then).
      Companion classes use the boundary define function, not wrapped.
- [x] `impl.loader` keeps the private `capture-loader` `[target parent
      specifications representation shape]` (makes the four arguments,
      calls `compiler/capture-loader`; no `DynamicClassLoader` return
      hint) and the private `define-target-class` with its arguments.
      `loader/install!` calls `compiler/install-capture!` with a function
      of the parent that calls `capture-loader`; it keeps its contract
      and docstring meaning (backend selection, the field class check and
      `:jvm-rejected`). The test sites do not change:
      `#'loader/capture-loader` in `capture_failure_test.clj`,
      `identity_loader_test.clj`, `removed_field_test.clj` (2) and
      `loader_test.clj`; `#'loader/define-target-class` in
      `loader_test.clj` `link-test`.
- [x] Add a probe-loader constructor; `impl.capability` and
      `impl.field-capability` use it in place of `(DynamicClassLoader.)`.
- [x] `impl.probe-builder`: keep `probe-bytes`, `define-probe` (loader
      parameter kept for tests) and `define-probe-class`; `define-probe`
      calls the boundary define function; remove the `DynamicClassLoader`
      import and type hints.
- [x] Move type tag resolution (`Compiler$HostExpr/maybeSpecialTag`,
      `maybeArrayClass`, `maybeClass`) and `Compiler/munge` wrappers into
      the boundary; `impl.fields` reads them from the boundary.
- [x] Verify lazy-load invariants: `core` does not eagerly require
      `loader` or `transform`; the boundary requires no value-type
      namespace.
- [x] Run all suites; run lint and format; commit slice 1.

## Slice 2: shape data in the boundary

- [x] Move `record-hidden-fields` from `impl.fields` to the boundary and
      add the delegating argument per entry (`nil`, `nil`, `0`, `0`);
      add the hidden-field index function (n - 4 + k).
- [x] Add record constructor data (hidden parameter counts 0, 2, 4; main
      constructor is 4, derived from `(count record-hidden-fields)`, not
      a second literal) and deftype* constructor data (count 1, super
      constructor `java/lang/Object` `()V`).
- [x] Update all `record-hidden-fields` readers for the third element:
      `core` (`hidden-index`, `in-place?`, `hidden-fields-fix`,
      `record-shape`), `impl.transform` (`hash-field-names`,
      `removed-field-names`), `impl.fields` (`check-hidden-fields!`).
- [x] Replace shape literals with boundary data (rule for the slice-2
      literals in plan.md): `impl.transform` ~102
      (`"java/lang/Object"` `"()V"`), ~174 (constructor count 1);
      in `record-constructor-difference`: ~406 `(< (count fields) 4)`
      and ~411 `(- (count fields) 4)` read the hidden-field count
      `(count compiler/record-hidden-fields)`, ~413-415 (parameter
      counts 4, 0, 2) read the record constructor data, ~419 (`3`) reads
      the count of its entries; map `delegating-code`
      `ACONST_NULL`/`ICONST_0` from the delegating argument. Keep
      `main-constructor-part` `(<= pos 2)` (code position, not shape
      data).
- [x] `core/in-place?`: replace the index literal `(+ (- n 4) k)` with
      the boundary hidden-field index function. `core/record-shape`
      ~143: replace `(= 4 (count hidden))` with the hidden-field count.
- [x] Replace `impl.identity-backend/hidden-indexes` literals n - 4 and
      n - 3 with the boundary positions of `__meta` and `__extmap`;
      `closed-checks` ~173 keeps its guard and replaces `(< n 4)` with
      the hidden-field count; keep `object-descriptor` as is.
- [x] Check that shape errors and their messages are unchanged.
- [x] Run all suites; commit slice 2.

## Slice 3: guard move

- [x] Move `require-value-capability!` from `core` to `impl.capability`,
      same key `:value-capability-unavailable`, same message; it reads
      the owner accessor `impl.capability/capability` through its var.
- [x] `core` calls `impl.capability/require-value-capability!` at value
      analysis, as before; no expansion output change.
- [x] Update D9/guard unit tests for the new location.
- [x] Run all suites; commit slice 3.

## Slice 4: D20 receiving expansion

- [x] Expand ordinary value definitions of `defvaluetype` and
      `defvaluetype*` through the existing receiving branch of
      `policy-expansion`: `(let [] ~(value-form modes))` with modes `[]`
      (`value-field-deftype` / `value-field-deftype*`, then
      `value-receiving`). Keep the `let` wrapper. Remove
      `value-expansion` and the `definition` parameter of
      `policy-expansion`; update its docstring (every value expansion is
      receiving). Ordinary fallback definitions keep
      `guarded-deftype`/`guarded-deftype*` (not receiving).
- [x] Remove the `installed` argument from `check-value-class`;
      `value-receiving` emits `(check-value-class <class>)`; the
      function keeps the run-time `:not-value-class` check only.
- [x] Update the receiving `:not-installed` message to the Contract text
      (class capture, no `Compiler/LOADER` binding, fix: evaluate
      directly); it applies to all receiving definitions, so
      `not-installed-message` and `install-receiving!` lose the `kind`
      argument.
- [x] Update the `:not-value-class` message in `core/error-message`:
      remove the replay cause, keep the nested-defvaluetype cause and the
      `no-value-classes?` branch; remove the dead `:not-installed`
      branch of `error-message`.
- [x] Tests: one-step `macroexpand` does not throw; full expansion
      without `LOADER` throws `:not-installed` with the receiving
      message; a replayed ordinary expansion defines a value class in a
      fresh JVM; a transferred ordinary expansion on a JVM without value
      classes throws `:value-capability-unavailable` at analysis; same
      for `defvaluetype*`.
- [x] Update existing expansion, transfer, AOT and D9 tests for the new
      expansion shape (for example `test/runtime/value_type/core_test.clj`
      `check-value-class-test`, `test/runtime/value_type/deftype_star_test.clj`
      `value-expansion-receiving-test`,
      `test/compatibility/value_type/expansion_checks.clj`); run the
      closed-record tests.
- [x] Run all suites including the deftype compatibility corpus; commit
      slice 4.

## Slice 5: guard emission (dedicated measurement commit)

- [x] Emit `require-value-capability!` as the first form of every value
      expansion (before `require-field-capability!` and before the
      definition form), for ordinary and native-field value output.
- [x] Verify that this commit changes no other expansion output (diff of
      a sample expansion against the parent).
- [x] Update cross-JVM expectations in `value_class_switch_test.clj`:
      `aot-native-field-off-test` expects
      `:value-capability-unavailable` `:disabled`.
- [x] Rewrite `aot-value-output-off-test` in place (interim limit
      removed): same saved output (`checked.aot-fixture`,
      `public-field-aot/identity-source`, top-level form), check form
      `value-class-switch-checks/aot-load` with `'checked.aot-fixture` and
      `"checked/aot_fixture.clj"` (as `restricted-load`), all policies; expect
      `:value-capability-unavailable` `:disabled` at namespace
      initialization, keyed as `aot-native-field-off-test` (class
      `clojure.lang.ExceptionInfo`, message contains the disabled
      detail, `:data`).
- [x] Run all suites; commit slice 5 on its own.

## Slice 6: AOT guard tests (Acceptance 3)

- [x] Add check functions to
      `test/compatibility/value_type/value_class_switch_checks.clj`
      (require, then `resolve`, as `aot-load`):
      - `aot-install [ns-sym source-path]`: `:source-absent?`, `:load`
        (as `aot-load`) and `:invocations` (`registry/twice` of the
        `install` call; each a `registry/outcome` with
        `registry/root-failure`, `:data` narrowed to `:value-type/error`
        and `:reason`).
      - `aot-load-failed-self-check [ns-sym source-path detail]`: require
        `value-type.impl.capability`, resolve
        `#'value-type.impl.capability/capability`, call `aot-load` inside
        `with-redefs-fn` with `(constantly {:available? false :reason
        :failed-self-check :detail detail})`.
- [x] `aot-value-output-function-off-test` in
      `test/cross-jvm/value_type/value_class_switch_test.clj`, next to
      `aot-value-output-off-test`: `public-field-aot/function-source`
      (`checked.aot-fixture`) with `config/value-producer`, receiver
      `valhalla` with `switched "off"`, `config/policies`, check form
      `aot-install` with `'checked.aot-fixture` and
      `"checked/aot_fixture.clj"`. Expect `:source-absent?` true, `:load`
      `:accepted`, `:invocations` `harness/entries` of
      `registry/each-attempt` of the keyed failure (class
      `clojure.lang.ExceptionInfo`, message contains the disabled detail,
      `:data` `{:value-type/error :value-capability-unavailable :reason
      :disabled}`).
- [x] `aot-failed-self-check-test` in the same file: for
      `public-field-aot/identity-source` (`checked.aot-fixture`) and
      `field-aot/restricted-source` (`restricted.aot-fixture`), both with
      `config/value-producer`, receiver `valhalla` without `switched`,
      `config/policies`, check form `aot-load-failed-self-check` with a
      fixed detail text. Expect `:source-absent?` true and `:load` the
      keyed failure (class `clojure.lang.ExceptionInfo`, message contains
      the detail, `:data` `{:value-type/error
      :value-capability-unavailable :reason :failed-self-check}`).
- [x] Extend the namespace docstring of `value_class_switch_test.clj` to
      the AOT value-class guard tests.
- [x] Run all suites; commit slice 6.

## Slice 7: startup measurement

- [x] Write `dev/bench/aot-startup/run.sh` (committed, outside all
      suites; argument: the slice-5 commit; `JDK` default
      `~/.local/jdks/jdk-27.jdk/Contents/Home`; output
      `target/aot-startup/`).
- [x] Build the two outputs: one `git worktree add --detach` per commit
      (slice-5 commit and its parent) under `target/aot-startup/trees/`;
      AOT compile `startup.fixture` (one top-level ordinary value
      definition, source outside the worktrees) in an EA3 child JVM with
      `--enable-preview` and classpath = Clojure jars, the worktree
      `src` and the fixture source directory. Copy `value_type/**` of the
      slice-5 output to one shared library directory; delete
      `value_type/**` from both outputs; remove the worktrees.
- [x] Run each output with classpath = Clojure jars, the shared library
      directory and the output (no source), options `--enable-preview`;
      the child form requires `startup.fixture`, then prints the ms from
      `RuntimeMXBean.getStartTime` to `System.currentTimeMillis`. For
      each output, discard 2 runs, then 10 measured runs, alternating the
      outputs; record median, minimum and maximum.
- [x] Record as context the in-process time of the first
      `impl.capability/capability` call (`System/nanoTime`, 10 fresh
      JVMs, same classpath; median).
- [x] Record the method, results and limits (one machine, one JVM build,
      no CDS control) in `implementation.md`; report to the user; commit.

## Slice 8: docs and closure

- [x] D19 (`decisions/compiler-boundary.md`): shape data, type tags,
      munge, class file output as boundary content; shape checks stay in
      `impl.transform` and `impl.identity-backend`; compiler code use in
      `src` limit; Concepts user list.
- [x] Rewrite defvaluetype D1, D3, D4 (`compiler-and-capture.md`), D9
      (`validation-and-errors.md`) and deftype* D1
      (`interface-and-shape.md`) for the single expansion style and the
      boundary; remove the D19/D20 notes; remove the replay cause from D9
      and deftype* `context.md`.
- [x] D14 (`capability-report.md`): ownership names the value-class
      guard in `impl.capability`.
- [x] D17 and D18 (`probe-and-format.md`): guard runs where the form is
      evaluated; throws `:value-capability-unavailable`; D17 commitment
      unchanged.
- [x] `embodiment/runtime.md`: boundary row in the components table,
      dependency direction with boundary edges, Boundaries text
      (`Compiler/LOADER`, `DynamicClassLoader`, other `Compiler` uses,
      drop "`transform` has no Clojure compiler dependency"), invariants
      guard row, runtime interfaces and data flow.
- [x] Embodiment gap table (`embodiment.md`): D17 AOT, D18 AOT, D19, D20
      rows.
- [x] `doc/usage.md`: error list (`:not-value-class` causes without
      replay, `:not-installed` receiving-only,
      `:value-capability-unavailable` also at evaluation), "How you get
      the error", value-class switch limit, AOT limits, "`deftype*`
      forms" replay text (~line 598).
- [x] Docstrings of `defvaluetype`, `defvaluetype*` and
      `check-value-class`; regenerate `doc/API.md` with `bb api-docs`.
- [x] `CHANGELOG.md`; `mementum/state.md`; update the stale sentence in
      `mementum/knowledge/controlled-fallback-boundaries.md` (~line 165).
- [x] Final Acceptance 1 check: case-sensitive search of `src` for
      `Compiler`, `DynamicClassLoader`, `.defineClass` and the hidden
      field names; non-boundary matches are text only; review the named
      shape-check functions for literals.
- [x] Run all suites, lint, format, Vale and API checks; commit slice 8.

## Implementation review

- [x] Test the guard position in native-field value output. In
      `restricted.aot-fixture`, the ordinary `Leaf` guard throws first.
      Thus `aot-native-field-off-test` and the `restricted.aot-fixture`
      cell of `aot-failed-self-check-test` pass also when the `Holder`
      expansion has no guard, or has the guard after
      `require-field-capability!`. Add a check that fails in that case.
      Examples: keep `:class-name` in the narrowed failure and expect
      `restricted.aot_fixture.Holder` after `Leaf` loads (failed
      self-check redef after a separate `Leaf` namespace loads), or
      add a runtime expansion test (expand `value-field-deftype` with a
      `Compiler/LOADER` binding) that asserts the guard is the first
      form, before `require-field-capability!`.
- [x] `ramora/defvaluetype-design/embodiment/runtime.md` components
      table: the `value-type.impl.loader` row has 5 cells (the old
      responsibility text and decisions are left after the new text);
      remove the left text. The `value-type.impl.capability` row does
      not name the value-class guard (`require-value-capability!`, D14
      ownership); add it, as the `impl.field-capability` row names the
      native-field guard. Also update the `impl.capability` namespace
      docstring, which names only the bytecode-contract checks.
- [x] `src/value_type/impl/transform.clj` namespace docstring: "Uses
      only JDK classes ... No Clojure compiler classes." The namespace
      now requires the compiler boundary for the shape data. State that
      it reads the shape data from `impl.compiler` and has no compiler
      code use.

## Implementation test review

- [x] `test/runtime/value_type/core_test.clj` `check-value-class-test`:
      the test checks only that the `:not-value-class` message does not
      contain "macroexpand". No test checks the new Contract text (the
      nested-defvaluetype cause and the fix "move the inner defvaluetype
      form to the top level"); the removed `not-value-class-test` checked
      the fix. Where `Class::isValue` is present, assert that the message
      names the class, the nested cause and the fix.
- [x] `core_test.clj` `not-installed-enabled-field-test`: the Contract
      changes the message of the earlier field receivers from field
      capture to class capture. The test checks only the class name and
      `direct-fix`. Add `class-capture` to its `message-names?` check.
- [x] `test/runtime/value_type/impl/compiler_test.clj` pins the internal
      form of the shape data (entry element 2, function names, the
      literal index list) and has a tautology (`record-constructors` max
      equals `hidden-field-count`, which is its definition). Correct shape
      data is observable through the `deftype` compatibility corpus and the
      record transfer and capture tests. Remove the namespace, or keep
      only a check that fails when the data disagrees with the record
      shape that Clojure emits (for example the hidden-field indexes of
      the fields of a real `defrecord` class).

## Implementation test review 2

- [x] `test/runtime/value_type/impl/compiler_test.clj` `shape-data-test`
      reads the field order from `Class/getDeclaredFields`. The JDK does
      not specify the order of that array ("not in any particular
      order"), but the test uses positions in it to check
      `hidden-field-index`. The backends read the order from the class
      file. Read the instance field order of `ShapeRecord` from its class
      bytes (for example `clojure.asm.ClassReader` on the class resource),
      so the check depends only on the order that Clojure emits.

## Implementation test review 3

- [x] `test/runtime/value_type/impl/compiler_test.clj`
      `record-class-bytes` makes a temporary directory for the class file
      output and does not delete it, so each run leaves files in the
      system temporary directory. Use `value-type.test-support/with-temp-dir`
      (it deletes the directory after the body) and read the bytes inside
      its body.

## Implementation test review 4

- [x] The Contract gives one `:not-installed` message for every receiving
      definition, so identity receiving output (`identity-receiving`,
      `:identity`) changed from "identity field capture" to "class
      capture". No test expands an identity receiving definition with no
      `Compiler/LOADER` binding. Add a check: full expansion
      (`walk/macroexpand-all` on a raw thread) of an identity receiving
      form (enabled generated field or closed shape, fallback selected)
      throws `:not-installed` with the class name, the class-capture
      text and `direct-fix`. Examples: a check function in
      `test/compatibility/value_type/identity_receiving_checks.clj` that
      runs in a fallback cell, or a runtime test that expands
      `value-type.core/identity-field-deftype` directly under a policy
      that permits fixed fallback.

## Implementation review 3

- [x] Update `doc/development/runtime.md`, "Verify the value-class switch",
      to match the AOT tests. It still says "Other value output keeps value
      classes under `off` (1)", which contradicts the emitted guard and
      `aot-value-output-off-test`. State that saved output without native
      fields also throws `:value-capability-unavailable` with `:disabled`.
      Update the AOT coverage list to include rejection at function invocation
      and the controlled failed-self-check cases for both saved outputs.

## Implementation test review 5

- [x] Preserve and check the exception boundary in the new AOT guard tests.
      `value-class-switch-checks/narrowed-failure` uses
      `registry/root-failure`, which removes every wrapper. Thus
      `aot-value-output-off-test`, `aot-failed-self-check-test` and
      `aot-value-output-function-off-test` check the root error but cannot
      distinguish the Contract's `ExceptionInInitializerError` at namespace
      initialization from the direct `ExceptionInfo` at function invocation.
      Return the outer exception class or cause chain as well as the root
      data. Assert the initialization wrapper for top-level saved output
      and direct `ExceptionInfo` for each saved-function invocation. Keep
      the existing source-absence, error-key and reason checks.

## Implementation test review 6

- [x] Prove analysis-time rejection of transferred ordinary value expansions
      for both public macros (Acceptance 2). `ordinary-value-transfer-test`
      and `deftype*-ordinary-value-transfer-test` use `consume-data`, which
      keeps only the root failure. Also, `transfer-checks/evaluate-form`
      in `:analysis` mode compiles and invokes the function in one `eval`.
      Thus these checks can pass if the receiving analysis guard is removed
      and only the emitted evaluation guard rejects the definition. Add a
      check on an incapable receiver that compiles a function containing the
      transferred expansion without invoking it. Assert that compilation
      throws `CompilerException` with `:value-capability-unavailable` as
      its cause data. Cover `defvaluetype` and the ordinary plain and record
      `defvaluetype*` shapes, with both expansion forms. Keep the existing
      capable-receiver and evaluation checks.

## Documentation review

- [x] Update `README.md`, "Important behavior and limitations", for the
      value-class guard. The AOT bullet names only JVM compatibility, and
      the switch paragraph says `off` returns to identity semantics with no
      code change. State that saved value output instead rejects evaluation
      with `:value-capability-unavailable` (including `:disabled` under
      `off`), at namespace initialization or function invocation. Link to
      `doc/usage.md#aot-limits`. Keep the distinction between new definitions
      that select fallback and fixed value output that cannot convert.
- [x] Correct the loader call examples in
      `ramora/defvaluetype-design/embodiment/runtime/interfaces.md` and
      `runtime/data-flow.md`. They show `install!` with three arguments,
      but `src/value_type/impl/loader.clj` requires four:
      `[class-name specifications representation shape]`. Add `shape` to
      the interface, describe `:plain`, `:record` and `:closed`, and pass
      `:plain` in the ordinary `defvaluetype` data flow. The documented
      three-argument call would throw an arity error.

## Documentation review 2

- [x] Correct the capture-loading boundary in
      `ramora/defvaluetype-design/embodiment/runtime.md` and its
      `runtime/data-flow.md` and `runtime/fallback-interfaces.md` pages.
      They say only the available/value branch resolves `loader/install!`,
      and that fallback expansions do not install capture. In
      `src/value_type/core.clj`, `identity-receiving` also calls
      `install-receiving!` for enabled fields or the closed shape;
      `impl.loader` selects the baseline-safe identity backend. Limit the
      no-capture statements to ordinary fallback with neither enabled fields
      nor the closed shape. Distinguish lazy capture support (both
      representations) from the optional JDK value transform (value output
      only). Keep the no-`check-value-class` rule for identity output.
      Check the wording against `policy-expansion`, `identity-receiving`
      and `test/compatibility/value_type/identity_receiving_checks.clj`.

## Documentation review 3

- [x] Qualify the D17 startup-cost statement in
      `ramora/defvaluetype-design/decisions/probe-and-format.md` (Qualities).
      It says the AOT check runs the probe at the start of each AOT
      application. The guard runs only when a saved definition form runs:
      function-contained definitions defer it until invocation, and `off`
      rejects without running a probe. State that startup probing applies
      to an evaluated top-level value definition when capability is not
      already cached and the switch/API checks permit the probe. Keep the
      measured cost and the D17 commitment unchanged. Check against
      `impl.capability/detect`, `core/value-receiving` and
      `value-class-switch-test/aot-value-output-function-off-test`.

## Documentation review 4

- [x] Complete the native-field guard error descriptions in `doc/usage.md`
      (error list, "How you get the error" and capability-report reasons)
      and the `defvaluetype` docstring, then regenerate `doc/API.md`.
      They describe `:field-capability-unavailable` only at the receiving
      boundary; the report section says "Only a transferred expansion"
      fails. Saved AOT output with fixed native fields also emits
      `require-field-capability!`. When value-class capability is available
      but native field capability is unavailable, this guard rejects at
      namespace initialization or function invocation, without fallback or
      enforcement reselection. State these evaluation phases and distinguish
      them from the first value-class guard, which rejects unavailable value
      capability with `:value-capability-unavailable`. Check against
      `core/value-receiving`, `impl.field-capability/require-field-capability!`
      and `value-receiving-test/receiving-native-guard-order-test`.

## Documentation review 5

- [x] Update the tooling `macroexpand` risk row in
      `ramora/defvaluetype-design/risks-and-evolution.md`. Its mitigation says
      "Ordinary selection changes only the target name" and reserves deferred
      installation for non-nil fields. D20 now defers capture installation
      for every value definition to receiving analysis. State that both public
      macroexpand forms retain the receiving form without installing capture;
      full expansion reaches receiving analysis and needs a compiler loader
      binding. Keep the fresh-receiver verification requirement. Check against
      `core/policy-expansion`, `value-receiving`, and
      `core-test/not-installed-test` and `replayed-expansion-test`.

## Documentation review 6

- [x] Correct the value-class guard error-data example in
      `ramora/defvaluetype-design/embodiment/runtime/interfaces.md`.
      `{:value-type/error :value-capability-unavailable :class-name :reason}`
      assigns `:reason` as the class name and omits the `:reason` key.
      Show separate class-name and reason entries, for example
      `{:value-type/error :value-capability-unavailable
      :class-name "ns.Name" :reason :disabled}`. Check against
      `impl.capability/require-value-capability!` and the saved-output
      assertions in `value_class_switch_test.clj`.

## Documentation review 7

- [x] Correct the loader error-data example in
      `ramora/defvaluetype-design/embodiment/runtime/interfaces.md`.
      `{:value-type/error :jvm-rejected :class-name}` has an odd number
      of map forms and omits the class-name value. Show a complete map,
      for example `{:value-type/error :jvm-rejected :class-name "ns.Name"}`.
      Check against `impl.loader/define-target-class` and
      `impl.loader-test/jvm-rejected-test` and `link-test`. The earlier
      guard-example step corrected a different map on this page.

## Documentation review 8

- [x] Correct the AOT-write enforcement owner in the invariants table of
      `ramora/defvaluetype-design/embodiment/runtime.md`. The row "The AOT
      file equals the defined bytes" still names `loader`: "write after
      transform". D19 moved that write to `impl.compiler/capture-loader`
      through `write-class-file!`; `impl.loader` now supplies callbacks and
      does not write class files. Name the compiler boundary as the enforcement
      point. Check against `src/value_type/impl/compiler.clj` and
      `impl.loader-test/aot-write-test` and `aot-write-before-define-test`.

## Code-shaper review

- [x] Make worktree cleanup in `dev/bench/aot-startup/run.sh` run on failure
      as well as success. With `set -e`, a failed compile exits before
      `git worktree remove`; the next run deletes the directory with `rm -rf`
      but leaves its Git registration. `git worktree add` then fails because
      the path is missing but already registered (exit 128, confirmed in a
      temporary repository). Register an exit cleanup before creating the
      worktrees, remove only this run's worktrees, and keep the original
      failure status. Handle stale registrations at these script-owned paths
      before resetting the output directory. Verify that a forced compile
      failure leaves no registered benchmark worktrees and that a retry can
      create both worktrees without manual Git cleanup.
