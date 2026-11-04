# Plan

## Approach

Implement `value-type.core/defvaluetype*` per the design and D1–D7, in six
slices. The D3 feasibility gate comes first: a retained spike on Valhalla
EA3 with preview must prove that value-class delegating constructors that
call `this(…)` verify and run. If the gate fails, record the evidence in
`implementation.md` and stop for a design decision.

Key decisions:

- **Gate first (D3, D7).** Model the spike on `spike/value-array-semantics/`
  (README with commands) plus a ramora evidence report. The spike transforms
  a record-shaped form and exercises every constructor, `create`, both
  record literal forms and a compiled record constant, with verification
  enabled.
- **Validation at the public boundary (D1, D2, D6).** `defvaluetype*` runs
  three declaration checks at macroexpansion, before policy, in order:
  `:mutable-field`, `:record-shape`, field metadata
  (`:removed-nil-restricted` then `:non-nil-unsupported` per field, first
  faulty field in field order). All three examine only symbols in a
  `fields` vector; other shapes go to `deftype*`. Use a shared
  symbols-in-vector helper so the three checks stay equal. Reuse
  `check-fields` and `fields` behaviour and error keys; all errors carry
  `:class-name`.
- **Expansion mirrors `defvaluetype` (D1, D5).** `defvaluetype*` takes the
  `deftype*` arguments `tagname classname fields & opts+specs`. The class
  comes from `classname`, the second argument, not from `tagname`:
  `:class-name` and `install!` use `(str classname)`, and `<class>` is
  `(symbol (str classname))` (this drops the metadata that `classname` can
  carry). Both expansions end with `<class>`, because the `deftype*` special
  form evaluates to nil (`NewInstanceExpr.eval` returns null for a deftype).
  `deftype*` defines the class during analysis, so the fully qualified
  `<class>` resolves with no `import`.
  - Value expansion: `(let [] (deftype* …) (check-value-class <class>
    installed) <class>)` with the loader installed at expansion.
  - Fallback: a new macro `guarded-deftype*` in `value-type.impl.runtime`,
    beside `guarded-deftype`, takes the `deftype*` arguments unchanged. It
    calls `enforce-fixed-fallback!` at its expansion and emits
    `(do (enforce-fixed-fallback!) (deftype* tagname classname fields …)
    <class>)`, untransformed. The runtime macro adds `<class>`, as
    `guarded-deftype` returns the class through `deftype`; the
    `value-type.core` fallback expansion is
    `(do (runtime/guarded-deftype* …))`, as in `defvaluetype`, and adds
    nothing.
  - Reuse `:not-installed` / `:not-value-class`; keep existing message
    text unless evidence demands otherwise.
- **Transform via a new record path (D3, D4).** The current input check is
  `constructor-difference` in `value-type.impl.transform` (exactly one
  constructor). Add a record-shape path, not a wider rule: find the main
  constructor by descriptor (all fields); accept the two delegating
  constructors when each only loads `this` and its arguments, pushes the
  expected constants, and `invokespecial`s the main constructor; check the
  main-constructor body as the plain path does (below); other sets and other
  main-constructor bodies give `:unexpected-constructor`. Rewrite only the
  main constructor; remove
  `__hash`/`__hasheq` from value output; keep descriptors, argument slots
  (including long/double), metadata and `create`/`getBasis` unchanged.
  - Interface: a new public function
    `transform/->record-value-class [bs class-loader]` holds the record
    path. It takes no `storage`: D6 gives `defvaluetype*` no
    specifications, so storage is always empty. `->value-class
    [bs storage class-loader]`, `constructor-difference` and
    `value-class-transform` do not change. `->record-value-class` uses its
    own class transform, because `value-class-transform` rewrites every
    constructor: same version and `ACC_IDENTITY` handling,
    `strict-init-transform` on the kept instance fields, the constructor
    rewrite on the main constructor only, the two hash fields dropped, and
    the delegating constructors copied unchanged. Other constructor sets
    and other main-constructor bodies give `:unexpected-constructor`
    through `unexpected-constructor!`, before any bytes change.
  - Main-constructor body: the rewrite replaces the whole body, so the
    record path checks it first, to lose no code silently. It reuses
    `expected-code` over all instance fields of the input bytes, hash
    fields included (the input declares them and its main constructor
    writes them): `aload 0`, `Object.<init>`, then `aload 0`, load
    argument, `putfield` for each field in field order, then `return`.
    `first-difference` compares the body without debug elements
    (`debug-element?`). A difference gives `:unexpected-constructor` with
    `:class-name`; the message part is `super call` for position 1 or 2,
    else `field writes`, as in `constructor-difference`.
  - `value-backend/prepare` takes the shape as a new last argument:
    `[target specifications delegate shape]`. `:plain` returns the current
    `:owner` and `:companion` transforms unchanged. `:record` selects
    `->record-value-class` in `:owner` (with the removed-field checks
    below) and ignores the specifications, which are empty.
  - Callers that change: `value_backend.clj` (`prepare`), `loader.clj`
    (`capture-loader` passes the shape to `value-backend/prepare`; the
    `install!` docstring names `->record-value-class` for the record
    shape) and the direct callers of `install!`/`capture-loader` in
    `test/` (below). No test calls `value-backend/prepare`. The direct
    `->value-class` calls in
    `test/runtime/value_type/impl/transform_test.clj` and
    `test/runtime/value_type/impl/method_attributes_test.clj` do not
    change; new record-path tests call `->record-value-class`.
- **Removed-field check in the value backend (D4).** The capture loader
  checks each owner or companion class when it defines it: a
  `getfield`/`putfield` that names the target class and a removed field
  gives `:invalid-field` `:reason :removed-field-access` with `:field` and
  `:class-name` (the target class); `:field` is `__hash` when both are
  accessed. Identity fallback installs no loader and runs no check.
  - Location: the scan uses `java.lang.classfile`, so it lives in
    `value-type.impl.value-backend`. The capture loader resolves that
    namespace only for value output. `value-type.impl.loader` also loads on
    Java 8 (identity definitions with non-nil fields), so it gets no scan
    and no new import. For the record shape, `prepare` composes the owner
    check after `->record-value-class` in `:owner`, and the companion check
    in `:companion` (the scan returns the companion bytes unchanged).
  - `loader.clj` changes only to carry the shape (and in the `install!`
    docstring): `install!` and `capture-loader` take the shape (`:plain`
    or `:record`) as a new last argument and pass it to
    `value-backend/prepare` only. `defvaluetype`
    passes `:plain`. `identity-backend/prepare` does not change:
    `defvaluetype*` fallback installs no loader, so identity output is
    never record-shaped. The `defineClass` branches of `capture-loader` do
    not change. The direct callers of `install!` and `capture-loader` in
    `test/` (runtime, compatibility and cross-JVM suites) add the shape
    argument; spikes have their own loaders and do not change.
  - Check point: the owner check reads the bytes after
    `->record-value-class`, inside `(owner bs)`, so before
    `writeClassFile`. The transform has removed the main-constructor
    writes, so every `getfield`/`putfield` of a removed field that remains
    is an access. The check needs no constructor
    matching. The companion check runs inside `(companion bs)`, before the
    companion define.
  - AOT guarantee: the capture loader writes no transformed bytes for a
    rejected class. The owner check throws before `writeClassFile`; the
    loader writes companion files only with specifications, which
    `defvaluetype*` never has. The compiler writes the original bytes
    before it defines a class (defvaluetype D7,
    `ramora/defvaluetype-design/decisions/compiler-and-transform.md`), so
    after `:unexpected-constructor` or `:removed-field-access` under
    `*compile-files*` the untransformed compiler file of the owner or
    companion class stays.
  - Error order: `->record-value-class` runs first in `:owner` and throws
    `:unexpected-constructor` before it changes bytes. Thus an owner with an
    unsupported constructor set and a removed-field access gives
    `:unexpected-constructor`, and the access check does not run.
    design.md and ramora do not order these two loader errors; this order
    follows from the transform owning constructor-shape validation.
- **Evidence (D7, Acceptance 6).** Record-shaped test forms implement
  `IRecord`, `java.util.Map`, `ILookup`, `IObj`, `IHashEq` with the methods
  the design lists (hash methods compute from entries, never read hash
  fields); plain forms implement `IType`. Mirror existing tests:
  `core_test.clj` (`not-installed-test`, `not-value-class-test`),
  `transfer_checks.clj` (receiving modes `:eval`, `:analysis`, `:source`),
  AOT and policy checks. Verify on Java 8 and EA3 preview off/on; record
  commands, structured results and limits in `implementation.md`.
- **Documentation (Acceptance 7).** Usage and generated API docs state
  syntax, caller obligations (zero hash arguments, no hash-field access,
  work with both outputs), fallback, exclusions and evidence limits, using
  the design's identity hash-field statement ("hold 0 when callers meet
  these obligations"). Ramora embodiment describes delivered behaviour;
  keep design.md and `ramora/deftype-star-design.md` equal if any contract
  changes.

## Risks

- **D3 gate failure.** JEP 401 may reject `this(…)` in value-class
  constructors on EA3. Mitigation: gate first; stop with evidence, no
  silent constructor replacement.
- **Compiler-bytecode coupling.** The delegating-constructor and
  main-constructor body checks are exact; a Clojure compiler change breaks
  them. Mitigation: strict checks fail with `:unexpected-constructor`,
  never silently.
- **Baseline loading.** The removed-field scan uses `java.lang.classfile`;
  in `value-type.impl.loader` it would break Java 8 identity definitions.
  Mitigation: keep the scan in `value-type.impl.value-backend`, change
  `loader.clj` only to carry the shape, check on Java 8 that loading the
  loader loads no value-output namespace, and run the existing regression
  suites (`defvaluetype` passes `:plain` and keeps its paths).
- **Serialization.** Records implement `Serializable`; value-class
  behaviour is unverified and remains unpromised. Do not add tests that
  imply a serialization guarantee.
- **EA3 environment availability.** Cross-JVM evidence needs the exact EA3
  build; record limits in `implementation.md` if a configuration cannot run.

## Slice order

1. **Feasibility gate (D3/D7 spike).** Retained EA3 preview experiment plus
   evidence report. Gate for all later slices.
2. **Declaration validation and macro skeleton.** Shape predicates, the
   three ordered checks, `defvaluetype*` with capability/policy and the
   guarded fallback expansion.
3. **Value transform record path.** Main-constructor rewrite, hash-field
   removal, delegating-constructor and `create`/`getBasis` preservation,
   `:unexpected-constructor` for other sets and other main-constructor
   bodies.
4. **Removed-field access check.** Value-backend scan rejecting owner and
   companion access to removed fields in value output; Java 8 baseline
   loading check.
5. **Integration, fallback, receiving and AOT evidence.** Both shapes,
   both representations, all record entry points, policies, receiving
   modes, AOT, Java 8 and EA3 preview off/on.
6. **Documentation and ramora embodiment.** Usage docs, API docs, ramora
   update, final quality checks.
