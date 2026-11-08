# Validation and error decisions

## D8. Validation at macroexpansion (failure handling)

- Choice: in both modes, when `fields` is a vector, reject with `ex-info`
  `:mutable-field` before the install: each symbol element with a
  truthy `:volatile-mutable` or `:unsynchronized-mutable` metadata
  value. `:fields` holds them in field order. Other elements and a
  non-vector `fields` go to `deftype`, which gives its own error.
- Rationale: a mutable field cannot be a value class field. Without
  this check the JVM rejects the class with a `ClassFormatError`
  (`:jvm-rejected`), which does not name the field. The truthy rule is
  the compiler rule, so `{:volatile-mutable false}` is accepted.
- No reserved-name check: `deftype` already rejects the reserved field
  names with `AssertionError` before any class is defined. This is a
  `deftype` rule, not a value-class rule. The same error as `deftype`
  is the most compatible behaviour.
- Commitment: firm.

## D9. Runtime check of the result (failure handling)

All value definitions use a receiving macro
([D20](compiler-boundary.md)).

- Choice: the receiving macro throws `ex-info` `:not-installed` at
  macroexpansion when no compiler `LOADER` is bound, for example under
  `clojure.walk/macroexpand-all`. This applies to all value definitions and
  to identity definitions with enabled fields or the closed shape; ordinary
  fallback definitions do not throw it. One-step `macroexpand` of the
  public form stops at the receiving macro and does not throw. Message:
  "Cannot define class <name>: class capture requires a compiler loader.
  The defvaluetype form was macroexpanded outside compilation (no
  Compiler/LOADER binding). Fix: evaluate the defvaluetype form directly."
- The value expansion calls the public function
  `value-type.core/check-value-class` with the fully qualified class
  symbol. It throws `ex-info` `:not-value-class` at run time when the class
  is an identity class. Known cause: a nested `defvaluetype` (the capture
  loader did not catch the class). Fix: move the inner form out of the
  method body to the top level. A replayed expansion
  (`(eval (macroexpand form))`) installs at analysis and is not a cause.
- Unavailable value-class capability throws `:value-capability-unavailable`
  from the value-class guard (D14), not from this result check: at analysis
  in direct evaluation, at evaluation for saved output (D17, D18).
- Receiving checks preserve selected representation and enforcement; native
  absence alone does not reject a generated-check definition. Result checks
  apply to the selected representation, not require a value class for
  identity fallback. See the [field contract](../../non-nil-field-design.md).
- For ordinary definitions, `value-type.impl.capability` checks the optional ClassFile API and
  `Class.isValue`, then defines and instantiates an isolated strict-field
  probe made by the probe builder (D16) and runs the semantic self-check
  (D17). Only missing required APIs, the value-class switch set to `off`
  (D18) and the D12 classified absences (probe class version, probe class
  not a value class, failed self-check) select fallback; see the [selected detector](../fallback/selected-detector.md).
  Other failures propagate. `value-type.impl.capability`
  caches the result (`capability`); `core` selects the representation
  before loading the optional loader. Both the detector and the value-path
  result check use the value-class predicate.
- Define errors: `value-type.impl.loader` (the target define it gives the
  capture loader of the compiler boundary) wraps
  each `LinkageError` of the define (`UnsupportedClassVersionError`,
  `ClassFormatError`, `VerifyError`) in `ex-info` with
  `:value-type/error :jvm-rejected` and `:class-name`. The cause
  stays. The JVM message already names `--enable-preview`. The define
  is in analysis, so `core` cannot catch these errors. The loader
  links the class (`getDeclaredMethods`) after the define, because
  `DynamicClassLoader.defineClass` does not verify. So `VerifyError`
  comes at define time.
- Rationale: a nested definition skips D3. On the value-class path, never
  accept an identity class. These failures do not permit fallback.
- Commitment: firm.

