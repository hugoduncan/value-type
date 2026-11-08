# Plan

## Approach

Implement in slices that each keep all suites green, ordered so that the
startup-measurement baseline is exact: the emitted guard form is a commit of
its own, after the boundary and D20 commits, and that commit changes no other
expansion output ("Startup measurement" in design.md).

Key decisions (from design.md and the implementation.md planning notes):

- New namespace `value-type.impl.compiler` (the compiler boundary). It
  depends only on Clojure, loads on Java 8, requires no value-type namespace
  and does not import `java.lang.classfile`. It owns:
  - `Compiler/LOADER` capture: the capture proxy of `DynamicClassLoader`,
    the delegate loader and the `LOADER` binding (the `isBound` read, the
    `.set` and the parent `@Compiler/LOADER`). Two boundary functions:
    - `compiler/capture-loader` `[target parent prepare check
      define-target rewrite-companions?]`: constructs the delegate
      (`DynamicClassLoader` with `parent`), calls `prepare` once, and
      returns the proxy. It reads and sets no `LOADER` binding. The proxy
      `defineClass` `[class-name bytes src]` is the entry point of the
      capture tests.
    - `compiler/install-capture!` `[make-loader]`: when `LOADER` is
      bound, it calls `make-loader` with `@Compiler/LOADER` as the parent,
      sets `LOADER` to the result and returns `true`; else it returns
      `false` and changes nothing. `loader/install!` calls it with a
      function of the parent that calls `loader/capture-loader`, and
      returns its result (the `install!` contract does not change).
    `impl.loader` keeps the private `capture-loader` `[target parent
    specifications representation shape]` with its arguments and timing.
    It makes the arguments below from `target`, `specifications`,
    `representation` and `shape`, and calls `compiler/capture-loader`. It
    has no `DynamicClassLoader` return hint; the test sites hint the
    returned loader themselves. Thus the five test sites that call
    `#'loader/capture-loader` and `link-test` (`#'loader/define-target-class`
    with a `(DynamicClassLoader.)`) do not change
    (`capture_failure_test.clj`, `identity_loader_test.clj`,
    `removed_field_test.clj` (2), `loader_test.clj`). The three functions
    and the boolean that `impl.loader` gives `compiler/capture-loader`:
    - `prepare`: a function of the delegate loader that returns
      `{:owner :companion}` (the byte rewrite). `impl.loader` closes it
      over target, specifications and shape, and resolves the backend
      `prepare` (`value-backend/prepare`, `identity-backend/prepare`, both
      `[target specifications delegate shape]`) with `requiring-resolve`
      inside it, so `core → loader → transform` stays lazy.
      `compiler/capture-loader` calls it once, at construction (install
      time, not at the first define), as `loader/capture-loader` does now.
    - `check`: a function of the delegate loader, the field class check
      (`check-field-classes!` closed over target and specifications). The
      proxy calls it at the target define, before the target rewrite, as
      now (`capture_failure_test.clj` and
      `restricted-class-mismatch-test` expect their errors there).
    - `define-target`: a function of `[delegate class-name bytes src]`
      that returns the class. `impl.loader` keeps it (now
      `define-target-class`): it calls the boundary define function,
      links the class (`.getDeclaredMethods`) and wraps a `LinkageError`
      as `:jvm-rejected`. It has no `DynamicClassLoader` type hint. The
      boundary calls it for the target class in place of its own define.
    - `rewrite-companions?`: a boolean, true when specifications is not
      empty. The boundary writes companion bytes in AOT only then, as now.
    The boundary writes the target bytes with `Compiler/writeClassFile`
    when `*compile-files*` is true, and defines companion classes with
    its define function, with no `:jvm-rejected` wrap (as now).
  - One define function for user and probe classes, with a loader
    parameter (tests pass their own loader). It does not wrap
    `LinkageError`; `:jvm-rejected` stays in `impl.loader`
    (`define-target`, pass-6 slice).
  - A probe-loader constructor for `impl.capability` and
    `impl.field-capability`.
  - Type tag resolution (`Compiler$HostExpr/maybeSpecialTag`,
    `maybeArrayClass`, `maybeClass`), `Compiler/munge` and
    `Compiler/writeClassFile` (decision A).
  - Shape data as Clojure data: `record-hidden-fields` (name, tag,
    delegating argument per entry), hidden-field index function
    (n - 4 + k), record constructor counts (0, 2, 4 hidden parameters),
    deftype* constructor count (1) and super constructor owner/descriptor
    (`java/lang/Object`, `()V`). Precedent: `impl.jvm-format` `format-row`.
- Bytecode shape checks stay in place and read the shape data:
  `impl.transform` (`expected-code`, `constructor-difference`,
  `record-constructor-difference`, `delegating-code`,
  `removed-field-names`), `impl.identity-backend` (`hidden-indexes`,
  `closed-checks`). Shape errors and messages unchanged.
  `jvm-format/require-constructor-order!` stays (D15, format fact).
- Rule for the slice-2 literals (Acceptance 1 review): in the named shape
  checks and in `core` record shape detection, no number literal states a
  shape fact. The hidden-field count is `(count
  compiler/record-hidden-fields)`; the record constructor data derives its
  main entry (4) from it, so the boundary has no second literal 4. Each
  use of the count 4 reads that count:
  - `core/record-shape` ~143: `(= 4 (count hidden))` (all hidden names
    found).
  - `impl.identity-backend/closed-checks` ~173: `(< n 4)` (the minimum
    parameter count; it stops a negative index in `hidden-indexes`
    before `aget`). The check stays; only its bound changes.
  - `impl.transform/record-constructor-difference` ~406 `(< (count
    fields) 4)` (minimum field count) and ~411 `(- (count fields) 4)`
    (declared field count).
  The other literals of `record-constructor-difference` read the record
  constructor data: the parameter counts 0 (~414, `(count declared)`) and
  2 (~415, `(+ 2 ...)`) and the main entry (~413, all fields) are its
  entries; the constructor count 3 (~419) is the count of its entries.
  Not shape facts, and they stay: instruction positions in a code list
  (`main-constructor-part` `(<= pos 2)`, derived from `expected-code`),
  slot arithmetic, and docstring text such as "n - 4".
- Value-class guard `require-value-capability!` moves from `core` to
  `impl.capability` (the result owner, D14). It reads the owner accessor
  `impl.capability/capability` through its var (no direct linking), so the
  D14 test replacement point works in a child JVM.
- D20: ordinary value definitions of `defvaluetype` and `defvaluetype*`
  expand to a receiving macro. They take the existing receiving branch of
  `policy-expansion`: `(let [] ~(value-form modes))` with modes `[]`, that
  is `value-field-deftype` (shape `:plain`) or `value-field-deftype*`
  (shape from `declared-shape`), then `value-receiving`. No new macro.
  The `let` wrapper is load-bearing: one-step `macroexpand` stops at
  `let*` and does not expand the receiving macro (Acceptance 2). With
  modes `[]` and no enabled field, `fields/receiving-specifications`
  passes (count 0 = 0). `value-expansion` is removed, and the
  `definition` parameter of `policy-expansion` (used only by
  `value-expansion`) is removed with it. `check-value-class` loses
  `installed`.
  The receiving `:not-installed` message names class capture. The
  `:not-value-class` message drops the replay cause. The `:not-installed`
  branch of `core/error-message` has no caller and is removed. Ordinary
  fallback definitions keep `guarded-deftype`/`guarded-deftype*`.
- Guard emission: each value receiving macro emits
  `(impl.capability/require-value-capability! ...)` as the first form of
  its expansion, before `require-field-capability!` and before the
  definition form. This is the dedicated measurement commit.
- `aot-value-output-off-test` (`test/cross-jvm/value_type/value_class_switch_test.clj`):
  slice 5 rewrites it in place. It keeps the saved output
  (`checked.aot-fixture`, `public-field-aot/identity-source`: value output
  without native fields, top-level form), uses the check form
  `value-class-switch-checks/aot-load` (as `restricted-load`) and expects
  `:value-capability-unavailable` `:disabled` at namespace initialization
  under every policy, as `aot-native-field-off-test` does (`harness/keyed`,
  `:data`, message contains the disabled detail). Slice 6 adds the form
  inside a function and the failed self-check; it does not replace this
  test.
- Startup measurement build (slice 7): a committed script
  `dev/bench/aot-startup/run.sh` (outside all suites, as
  `dev/bench/typed-array/run.sh`), with the guard commit (slice 5) as
  argument and the EA3 JDK from `JDK` (default
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). It writes to
  `target/aot-startup/` (not committed). Build:
  - One `git worktree add --detach` per commit (guard commit and its
    parent) under `target/aot-startup/trees/`; removed at the end.
  - One fixture source, outside the worktrees: namespace
    `startup.fixture` with one top-level ordinary value definition
    (for example `(defvaluetype Point [x])`).
  - For each commit, AOT compile `startup.fixture` in a child JVM (EA3,
    `--enable-preview`) with classpath = the Clojure jars, the `src` of
    that worktree and the fixture source directory
    (`(binding [*compile-path* out] (compile 'startup.fixture))`, as
    `aot_fixture.clj` `compile-form`).
  - `compile` also writes the library classes (`value_type/**`). Copy
    `value_type/**` of the guard-commit output to one shared library
    directory, then delete `value_type/**` from both outputs. Thus each
    output holds only the `startup/**` classes, and the two outputs differ
    only in the emitted guard form.
  - Run classpath for both outputs: the Clojure jars (`clojure -Spath`
    without `src`), the shared library directory and the output. No
    source directory. JVM options: `--enable-preview` only.
  - Each run: `java --enable-preview -cp <cp> clojure.main -e` with a form
    that requires `startup.fixture`, then prints the milliseconds from
    `RuntimeMXBean.getStartTime` to `System.currentTimeMillis`.
  - Order: for each output, 2 discarded runs, then 10 measured runs; the
    runs of the two outputs alternate.
  - Context: 10 fresh JVMs with the same classpath that require
    `value-type.impl.capability` and time the first
    `impl.capability/capability` call with `System/nanoTime`; record the
    median.
- Failed self-check in the child AOT JVM: the child check form requires
  `value-type.impl.capability` and replaces the accessor with
  `with-redefs-fn` on a resolved var before requiring the saved namespace
  (pass-2 slice). No production hook.
- Slice-6 tests (Acceptance 3), in
  `test/cross-jvm/value_type/value_class_switch_test.clj` (namespace
  docstring extended to the AOT value-class guard). New check functions
  in `test/compatibility/value_type/value_class_switch_checks.clj`; they
  require, then `resolve`, as `aot-load`. Each test compiles with
  `config/value-producer` (no switch property) through
  `fixture/source-free-consumers`, under `config/policies`, and expects
  the failure keyed as `aot-native-field-off-test` (`harness/keyed`:
  class `clojure.lang.ExceptionInfo`, message contains the detail,
  `:data` `{:value-type/error :value-capability-unavailable :reason <r>}`).
  - `aot-value-output-function-off-test`: saved source
    `public-field-aot/function-source` (`checked.aot-fixture`, `(defn
    install [] (defvaluetype Holder [^String ^:non-nil required]))`,
    value output without native fields). Receiver: `valhalla` with
    `switched "off"`. Check function
    `value-class-switch-checks/aot-install` `[ns-sym source-path]`:
    returns `:source-absent?`, `:load` (the `aot-load` outcome) and
    `:invocations`: `registry/twice` of the `install` call, each the
    `registry/outcome` with `registry/root-failure` and `:data` narrowed
    to `:value-type/error` and `:reason` (as `aot-load`). Expected:
    `:source-absent?` true, `:load` `:accepted`, `:invocations`
    `harness/entries` of `registry/each-attempt` of the keyed failure
    with reason `:disabled` and the disabled detail.
    `function-aot-checks/invoke-install` does not change (it gives no
    `:data`).
  - `aot-failed-self-check-test`: saved sources
    `public-field-aot/identity-source` (`checked.aot-fixture`, without
    native fields) and `field-aot/restricted-source`
    (`restricted.aot-fixture`, with native fields), both top-level forms.
    Receiver: `valhalla` without `switched`. Check function
    `value-class-switch-checks/aot-load-failed-self-check`
    `[ns-sym source-path detail]`: requires `value-type.impl.capability`,
    resolves `#'value-type.impl.capability/capability`, then calls
    `aot-load` inside `with-redefs-fn` with `(constantly {:available?
    false :reason :failed-self-check :detail detail})`. Expected:
    `:source-absent?` true and `:load` the keyed failure with reason
    `:failed-self-check`; the message contains `detail` (EA3 has
    `Class::isValue`, so the guard message gives the detail). In
    `restricted.aot-fixture` the guard of `Leaf` throws first; key and
    reason are the same.
- Acceptance 1 check: case-sensitive search of `src` for `Compiler`,
  `DynamicClassLoader`, `.defineClass` and the hidden field names; matches
  outside the boundary must be text only (allowed text listed in the
  pass-6 slice note). Review of the named shape-check functions for
  literal shape facts.

## Risks

- Startup cost of the guard in AOT output: unknown until measured. The
  measurement is in scope; D17 commitment does not change here.
- Expansion shape change breaks transfer, AOT, native-field AOT and D9
  tests; cross-JVM expectation changes are known
  (`value_class_switch_test.clj`: `:field-capability-unavailable` →
  `:value-capability-unavailable`, and the interim `off` limit).
- closed-record emits `defvaluetype*`; its expansion changes — run its
  tests after the D20 slice.
- Lazy-load invariants: `core` must not eagerly require `loader` or
  `transform`; the boundary must not require any value-type namespace (no
  cycle with `impl.fields → impl.capability`). Verify after slice 1.
- `record-hidden-fields` gains a third element; all destructuring readers
  (`core`, `transform`, `impl.fields`) must be updated together
  (pass-7 slice literal list).
- EA3 availability for AOT tests and measurement (existing cross-JVM
  infrastructure already uses it).

## Slice order

1. Compiler boundary namespace: compiler code use (capture, define,
   probe loader, type tags, munge, writeClassFile) moved in; boundary
   users migrated (`loader/capture-loader` gives `prepare`, `check`,
   `define-target` and `rewrite-companions?` to `compiler/capture-loader`;
   `loader/install!` calls `compiler/install-capture!`; capture tests
   unchanged); `impl.probe-builder`
   drops its `DynamicClassLoader` import and hints.
2. Shape data in the boundary: `record-hidden-fields` moves with the
   delegating argument; literals replaced in `core` (`in-place?` uses the
   boundary index function in place of `(+ (- n 4) k)`; `record-shape`
   reads the hidden-field count), `impl.transform` and
   `impl.identity-backend` (rule for the slice-2 literals); readers
   updated.
3. Guard move: `require-value-capability!` to `impl.capability`; `core`
   calls it at analysis as before. No expansion output change.
4. D20 receiving expansion: ordinary value definitions take the existing
   receiving branch (modes `[]`); `value-expansion` removed;
   `check-value-class` loses `installed`; messages updated; expansion,
   transfer and replay tests.
5. Guard emission (dedicated commit): emit the guard as the first form of
   every value expansion; no other expansion output change. Cross-JVM
   expectations updated in the same commit (`aot-native-field-off-test`,
   `aot-value-output-off-test` rewritten for the top-level `off` case).
6. AOT guard tests (Acceptance 3): in-function `off`
   (`aot-value-output-function-off-test`), failed self-check for both
   saved outputs (`aot-failed-self-check-test`); top-level `off` without
   and with native fields is covered by the slice-5 tests.
7. Startup measurement: `dev/bench/aot-startup/run.sh` builds the two
   outputs from worktrees, runs them, and the result is recorded in
   implementation.md.
8. Docs and closure: D19, D1, D3, D4, D9, deftype* D1, D14, D17, D18,
   `embodiment/runtime.md`, embodiment gap table, usage guide, docstrings,
   `bb api-docs`, CHANGELOG, `mementum/state.md`,
   `mementum/knowledge/controlled-fallback-boundaries.md` stale sentence;
   final Acceptance 1 search and full suite run.
