# Compiler boundary and receiving expansion

## Intent

Put all coupling to Clojure compiler output in one boundary, use one
expansion style for all value definitions, and protect value-class output on a
JVM where value classes are unavailable. Realize defvaluetype D19, D20 and the
value-class guard of D17 and D18 (the AOT initialization check).

Priorities, in order: correct, clear failures; one place to change for a new
Clojure version; one expansion style; startup cost of AOT applications.

## Problem and context

- Assumptions about compiler output are in `core` (record shape detection),
  `impl.fields` (record suffix, `record-hidden-fields`, type tag resolution
  through `Compiler$HostExpr/maybeSpecialTag`, `maybeArrayClass` and
  `maybeClass`, field names through `Compiler/munge`), `impl.loader`
  (`Compiler/LOADER` capture, class definition, class file output through
  `Compiler/writeClassFile`), `impl.transform` (value
  output: constructor shape, field order, record suffix), `impl.value-backend`
  (removed record fields, through `impl.transform`) and
  `impl.identity-backend` (identity output: constructor shape). The probe
  namespaces `impl.capability` and `impl.field-capability` each make their own
  `DynamicClassLoader`, and `impl.probe-builder` defines the probe classes in
  it. A Clojure change touches several namespaces. The probes and the loader
  share a hidden dependency on `DynamicClassLoader`.
- Ordinary value definitions expand to `check-value-class <class> <installed>`
  with a literal. A replayed expansion carries `installed=true` into a new JVM
  and gives a misleading `:not-value-class`. Definitions with enabled non-nil
  fields already use a receiving macro.
- Value-class AOT output without native non-nil fields does not read the
  value-class result. On a capable JVM with a failed self-check (task 023) or
  the switch `off` (task 025), it loads as value classes. Value-class AOT
  output with native non-nil fields emits `require-field-capability!`. Field
  capability derives from value-class capability, so this output throws
  `:field-capability-unavailable` with the reason of the value-class result
  (`:disabled` or `:failed-self-check`).

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D19, D20
(`decisions/compiler-boundary.md`), D17 and D18
(`decisions/probe-and-format.md`); changed D1, D3, D4, D9, D14 (guard
ownership); deftype* D1.

## Concepts

- Compiler boundary: the namespace that owns the model of compiler output
  (the shape data), class definition through `DynamicClassLoader`, and all
  compiler code use in `src`.
- Shape data: the facts about the shape of `deftype*` output (constructor
  shape, field order and record suffix), held in the compiler boundary as
  Clojure data (Architecture). It is not bytecode code.
- Compiler code use: code (not text in docstrings, comments or messages)
  that does one of these:
  - imports, refers to or type-hints with `clojure.lang.Compiler`, a nested
    class of it (`Compiler$HostExpr` and others) or
    `clojure.lang.DynamicClassLoader`;
  - binds or reads `Compiler/LOADER`;
  - constructs or subclasses (`proxy`) `DynamicClassLoader`;
  - calls `.defineClass`.
- Boundary users: the namespaces that use the compiler boundary: `core`,
  `impl.fields`, `impl.loader`, `impl.transform`,
  `impl.identity-backend`, and the probe namespaces `impl.capability`,
  `impl.field-capability` and `impl.probe-builder`.
- Receiving macro: an expansion step that installs the capture loader during
  its own analysis.
- Value-class guard: `impl.capability/require-value-capability!`. It returns
  nil when the cached value-class result is available. Else it throws
  `ex-info` with `:value-type/error :value-capability-unavailable`,
  `:class-name` and the `:reason` of the result. It is the current private
  `core/require-value-capability!`, moved, with the same message.

## Scope

Include:

- One compiler-boundary namespace: `Compiler/LOADER` capture, the shape
  data (constructor shape, field order, record suffix), one define function
  for user and probe classes, type tag resolution
  (`Compiler$HostExpr/maybeSpecialTag`, `maybeArrayClass`, `maybeClass`),
  field name munging (`Compiler/munge`) and class file output
  (`Compiler/writeClassFile`). The boundary users use it.
  Thus a new Clojure version changes one namespace (D19).
- Unsupported shapes keep their current errors and "Fix: use Clojure 1.12".
- Ordinary value definitions of `defvaluetype` and `defvaluetype*` expand to
  a receiving macro. `check-value-class` loses its `installed` argument.
  Ordinary fallback definitions keep `runtime/guarded-deftype` and
  `runtime/guarded-deftype*`. They install no capture and are not receiving.
- The value-class guard moves from `core` to `impl.capability`. Each value
  receiving macro calls it at analysis, as now, and emits it as the first form
  of its expansion. Every value expansion emits it, also for direct evaluation
  and source loading; the guard is not specific to AOT output.
- The startup measurement in "Startup measurement", recorded in
  `implementation.md` and reported to the user.

Exclude:

- Fallback for compiler shape mismatches (rejected in D19).
- Support for other Clojure versions.
- Changes to identity output, guards or policy.
- A change of the D17 commitment. D17 keeps the AOT check provisional; the
  user decides after the measurement.

## Contract

- Direct evaluation, source loading and AOT keep their behaviour on supported
  JVMs, with these exceptions:
  - Value-class output evaluated where the cached value-class result is
    unavailable throws `:value-capability-unavailable` from the value-class
    guard (below).
  - Native-field value output in that condition throws
    `:value-capability-unavailable`, not `:field-capability-unavailable`.
  - `:not-installed` for an ordinary value definition comes from the receiving
    macro at macroexpansion, not from `check-value-class` at run time.
  - A replayed or transferred ordinary value expansion installs at analysis.
    On a capable JVM it defines a value class, where it gave
    `:not-value-class`. On a JVM without value-class capability it throws
    `:value-capability-unavailable` at analysis, where it gave
    `:not-value-class` at run time.
  - The receiving `:not-installed` message names class capture, not field
    capture.
  - The `:not-value-class` message does not name replay as a cause (below).
- `check-value-class` throws only `:not-value-class`, at run time, when the
  class is not a value class. Where the JVM has `Class::isValue`, the message
  is: "Class <name> is not a value class. The capture loader did not catch
  the class. Known cause: a defvaluetype nested in a method body; fix: move
  the inner defvaluetype form to the top level." The replay cause
  (`(eval (macroexpand form))`) is removed, because a replayed expansion
  installs at analysis. On a JVM without `Class::isValue`, the message does
  not change.
- `:not-installed` applies to receiving definitions only: all value
  definitions, and identity definitions with enabled fields or the closed
  shape, as now. Ordinary fallback definitions do not throw it. The receiving
  macro throws it when it expands without a `Compiler/LOADER` thread binding,
  for example in `clojure.walk/macroexpand-all`. One-step `macroexpand` of a
  public form stops at the receiving macro and does not throw. The message
  for every receiving definition is: "Cannot define class <name>: class
  capture requires a compiler loader. The defvaluetype form was macroexpanded
  outside compilation (no Compiler/LOADER binding). Fix: evaluate the
  defvaluetype form directly."
- Value-class guard at evaluation:
  - Position: the first form of each value expansion, before
    `require-field-capability!` (native-field output) and before the
    definition form. Compiled output can load the class before the guard runs,
    because the compiled form refers to the class. The guard stops the
    definition form, the factory and the class result; it does not stop class
    loading. On a JVM that rejects the class file, the class-loading error
    can come first, as now.
  - Result: where the cached value-class result is unavailable, it throws
    `:value-capability-unavailable` with the reason of the result, for example
    `:disabled` or `:failed-self-check`. Thus native-field output gives
    `:value-capability-unavailable` for these reasons.
    `:field-capability-unavailable` remains for an available value-class
    result without native field support.
  - How the caller gets the error: a top-level form runs the guard at
    namespace initialization. `require` of the AOT namespace throws
    `ExceptionInInitializerError` with the `ex-info` as cause; a retry can
    throw `NoClassDefFoundError`. A form inside a function permits namespace
    loading and throws the `ex-info` directly at each invocation. In direct
    evaluation and source loading, the guard call at analysis throws first,
    as now, as the cause of a `CompilerException`.

## Architecture

- Dependency direction: the boundary users depend on the compiler boundary;
  it depends only on Clojure. The boundary loads on Java 8.
- Boundary content: all compiler code use in `src` (Concepts). The boundary
  wraps these compiler calls; it does not change their results. The boundary
  users get them from the boundary as follows:
  - `impl.fields`: type tag resolution and field name munging.
  - `impl.loader`: the `Compiler/LOADER` binding, the capture loader (the
    `proxy` of `DynamicClassLoader` and its delegate loader), class
    definition and class file output. `impl.loader` keeps the backend
    selection, the field class check and the `:jvm-rejected` error, and
    gives the byte rewrite and these checks to the boundary as functions.
  - `impl.capability` and `impl.field-capability`: a new probe loader from a
    boundary function, in place of `(DynamicClassLoader.)`.
  - `impl.probe-builder`: keeps `probe-bytes`, `define-probe` and
    `define-probe-class`. `define-probe` keeps its loader parameter (tests
    give their own loader) and calls the boundary define function. It has
    no type hint with `DynamicClassLoader`.
- Shape data in the boundary (data and functions that derive positions from
  it; no class-file API):
  - Record suffix: `record-hidden-fields`, moved from `impl.fields`. Each
    entry has the name, the tag and the delegating argument of one hidden
    field, in place order: `__meta` and `__extmap` (no tag, argument `nil`),
    `__hash` and `__hasheq` (tag `int`, argument `0`). The hidden fields are
    the last four instance fields and the last four parameters of the main
    record constructor. A boundary function gives the index of each hidden
    field in a list of n fields (n - 4 + k for entry k).
  - Record constructors: the count of hidden fields in the parameters of
    each of the three record constructors, after the declared fields: 0, 2
    and 4. The constructor with 4 is the main constructor. The other two
    call it, with the delegating argument for each hidden field that is not
    a parameter.
  - Constructor shape and field order of `deftype*` output: one
    constructor; its parameters are the instance fields in declared order;
    its code calls the super constructor (owner `java/lang/Object`,
    descriptor `()V`), then writes each field in declared order. The data
    holds the constructor count (1) and the super constructor owner and
    descriptor.
  - Not shape data: the order of field writes and super call in a value
    constructor (`jvm-format/require-constructor-order!`, D15). It is a
    class-file format fact and stays in `impl.jvm-format`.
- Shape checks stay in their namespaces and read the shape data:
  - `impl.transform`: `expected-code`, `constructor-difference`,
    `record-constructor-difference`, `delegating-code` and
    `removed-field-names` stay (they use `java.lang.classfile`, which the
    boundary cannot load). They take the constructor counts, the hidden
    field positions and names, the delegating arguments and the super
    constructor from the boundary. `impl.transform` throws
    `:unexpected-constructor`, as now.
  - `impl.identity-backend`: `hidden-indexes` and `closed-checks` stay (on
    `clojure.asm`). `hidden-indexes` takes the positions of `__meta` and
    `__extmap` from the boundary, not from the literals n - 4 and n - 3.
    `impl.identity-backend` throws its shape errors ("unsupported shape",
    "Fix: use Clojure 1.12"), as now.
  - `core` (record shape detection, the hidden-field fix text) and
    `impl.fields` (the hidden-field check of non-nil fields) read
    `record-hidden-fields` from the boundary. `core` throws its shape
    errors, as now.
  - `impl.value-backend` reads the removed fields through
    `transform/removed-field-names`, as now.
- Value-class guard owner: `impl.capability`, the owner of the value-class
  result. The guard reads the owner accessor `impl.capability/capability`, as
  all readers do (D14). `core` calls it at receiving analysis and emits it.
  `impl.field-capability` keeps the native-field guard. `impl.runtime` cannot
  own the guard, because it requires no capability namespace. D14 ownership
  names both guards.

Rejected: keep two expansion styles; compiler fallback; a separate AOT probe;
a second key `:value-class-unavailable` (two keys for one condition); a guard
in AOT output only (two expansion shapes); bytecode shape checks in the
boundary (it loads on Java 8 and cannot use `java.lang.classfile`).

## Startup measurement

- Subject: the start cost that the value-class guard adds to an AOT
  application. In AOT output, the guard runs the value-class probe at the
  first evaluation of a value form (loads `java.lang.classfile`).
- Method: EA3 with `--enable-preview`, one saved namespace with one top-level
  ordinary value definition, source not on the classpath. Measure wall time
  from JVM start to the end of `require` of that namespace for two outputs of
  the same source, with the same classpath and JVM options.
- Outputs: the two outputs differ only in the emitted guard form. The
  emitted guard form is added in a commit of its own, after the boundary and
  D20 commits; that commit changes no other expansion output. The plan orders
  the commits to match.
  - Output with the guard: compiled from that commit.
  - Baseline: compiled from the parent of that commit (receiving output
    without the emitted guard).
- Runs: discard 2 runs, then make 10 runs of each output. Record the median,
  minimum and maximum. As context, record the in-process time of the first
  `impl.capability/capability` call.
- Limits to record: one machine, one JVM build, no class-data sharing control.
- Outcome: report the result to the user. This task does not change the D17
  commitment.

## Qualities and risks

- Better: maintainability, transfer correctness, AOT safety.
- Cost: the expansion shape changes; transfer, AOT, native-field AOT and D9
  tests change.
- Risk: an AOT application with a top-level value definition runs the probe
  at namespace initialization. Measure (see "Startup measurement").
- Risk: closed-record emits `defvaluetype*`; its expansion changes. Check its
  tests.

## Acceptance

1. The boundary users read the shape data (constructor shape, field order
   and record suffix) only from the compiler boundary. Check: in `src`, the
   hidden field names (`__meta`, `__extmap`, `__hash`, `__hasheq`) occur as
   code only in the boundary; matches elsewhere are text (docstrings,
   comments and messages). The shape checks that Architecture names take
   the constructor counts, the hidden field positions and the super
   constructor from the boundary, not from literals (check by review of
   these functions). This search finds shape facts; the next search finds
   compiler code use. In `src`, only the compiler boundary has compiler
   code use (Concepts). This includes type tag
   resolution, `Compiler/munge`, `Compiler/writeClassFile`, the capture
   loader and `.defineClass`. Check: a search of `src` for `Compiler`,
   `DynamicClassLoader` and `.defineClass` finds matches outside the
   boundary only in text (docstrings, comments and messages, for example the
   receiving message in the Contract). Test and `dev` namespaces are outside
   this limit. They can bind `Compiler/LOADER` to simulate compilation and
   construct `DynamicClassLoader` to isolate classes; this task does not move
   that code to the boundary. Shape errors and their messages are unchanged.
2. Tests: one-step `macroexpand` does not throw; full expansion without
   `LOADER` throws `:not-installed` with the receiving message; a replayed
   ordinary expansion defines a value class in a fresh JVM; a transferred
   ordinary expansion on a JVM without value classes throws
   `:value-capability-unavailable` at analysis; `defvaluetype*` the same.
3. AOT tests on EA3, for saved value output:
   - Without native fields, under `value-type.value-classes=off`: a top-level
     form throws `:value-capability-unavailable` `:disabled` at namespace
     initialization; a form inside a function permits namespace loading and
     throws at invocation.
   - With native fields, under `off`: `:value-capability-unavailable`
     `:disabled`.
   - A controlled failed self-check gives `:value-capability-unavailable`
     `:failed-self-check`, for a top-level form in each saved output
     (without native fields and with native fields). Mechanism: the child
     check form requires `value-type.impl.capability`, replaces the owner
     accessor `impl.capability/capability` (the D14 test replacement point)
     with a function that returns `{:available? false :reason
     :failed-self-check :detail <text>}`, then requires the saved namespace
     while the replacement is in effect. Production code gets no test hook
     and no property; the public surface does not change.
4. Startup measurement recorded in `implementation.md` with the method and
   limits of "Startup measurement", and reported to the user.
5. All runtime, fallback, cross-JVM, build and tooling suites pass, including
   the `deftype` compatibility corpus. Lint, format, Vale and API checks pass.
6. Docs:
   - defvaluetype D19 names the shape data, type tag resolution, field name
     munging and class file output as boundary content, states that the
     shape checks stay in `impl.transform` and `impl.identity-backend` and
     read the shape data, limits other namespaces as
     Acceptance 1 does (compiler code use in `src`), and names the boundary
     users of Concepts.
   - defvaluetype D1, D3, D4, D9 and deftype* D1 rewritten for the single
     expansion style and the compiler boundary. Remove the D19 and D20 notes:
     the scope note of `compiler-and-capture.md`, and the notes in D1, D9 and
     deftype* D1. D9 and the deftype* scope text (`context.md`) do not name
     replay as a cause of `:not-value-class`.
   - D14 ownership names the value-class guard in `impl.capability`.
   - D17 and D18: the value-class guard runs where the form is evaluated
     (namespace initialization for a top-level form, invocation for a form
     inside a function) and throws `:value-capability-unavailable`. The D17
     commitment does not change.
   - `embodiment/runtime.md`: components table (boundary row), dependency
     direction, the Boundaries text on `Compiler/LOADER`,
     `DynamicClassLoader` and the other `Compiler` uses, invariants (guard row); runtime interfaces and
     data flow.
   - Usage guide: error list (with the `:not-value-class` causes), "How you
     get the error", the value-class switch limit, the AOT limits and the
     replay limit in "`deftype*` forms". Docstrings of `defvaluetype`, `defvaluetype*`
     and `check-value-class`, then `doc/API.md` with `bb api-docs`.
   - `CHANGELOG.md`, the embodiment gap table (D17 AOT, D18 AOT, D19, D20),
     `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
