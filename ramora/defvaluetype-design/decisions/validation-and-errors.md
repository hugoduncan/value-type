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

Changed by [D20](compiler-boundary.md): all value definitions use a receiving
macro. `:not-installed` comes from receiving analysis without a `LOADER`
binding, and `check-value-class` has no `installed` argument. Where the text
below names the ordinary expansion or the `installed` argument, D20 governs.

- Choice: the value-class expansion calls the public function
  `value-type.core/check-value-class` with the fully qualified class
  symbol and the `install!` result. It throws `ex-info` when the class
  is not a value class:
  - `:not-installed`: `install!` returned `false`. The form was
    macroexpanded with no `LOADER` binding, then evaluated on the ordinary
    path (no enabled non-nil fields). Fix:
    evaluate the `defvaluetype` form directly.
  - `:not-value-class`: `install!` returned `true`, but the class is
    an identity class. Causes and fixes:
    - An ordinary expansion was evaluated apart from its macroexpansion
      (`(eval (macroexpand form))`). Fix: evaluate the `defvaluetype`
      form directly.
    - A nested `defvaluetype`. Fix: move the inner form out of the
      method body to the top level.
  - For ordinary definitions, unavailable capability selects fallback before loader
    installation (D10 D12), not through this result check.
- Expansions with enabled non-nil fields use a receiving macro (`value-field-deftype` or
  `identity-field-deftype`), which installs a receiver-local loader during analysis. Both expansion forms support separate eval,
  metadata-preserving source loading and function analysis then invocation.
  Receiving checks preserve selected representation and enforcement; native absence
  alone does not reject a generated-check definition. Result checks must apply to
  the selected representation, not require a value class for identity fallback.
  See the [field contract](../../non-nil-field-design.md).
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
- Ordinary definitions: throwing `:not-installed` at macroexpansion is
  rejected. Tools call `macroexpand` with no `LOADER` binding only to show
  the expansion; the run-time check throws instead.
- Enabled non-nil fields: the receiving macro throws `:not-installed` at
  macroexpansion when no compiler `LOADER` is bound, for example under
  `clojure.walk/macroexpand-all`. One-step `macroexpand` of the public form
  stops at the receiving macro and does not throw.
- Define errors: the capture loader (`value-type.impl.loader`) wraps
  each `LinkageError` of the define (`UnsupportedClassVersionError`,
  `ClassFormatError`, `VerifyError`) in `ex-info` with
  `:value-type/error :jvm-rejected` and `:class-name`. The cause
  stays. The JVM message already names `--enable-preview`. The define
  is in analysis, so `core` cannot catch these errors. The loader
  links the class (`getDeclaredMethods`) after the define, because
  `DynamicClassLoader.defineClass` does not verify. So `VerifyError`
  comes at define time.
- Rationale: some ordinary-definition paths skip D3, for example
  `(eval (macroexpand form))`. On the value-class path, never accept
  an identity class. These failures do not permit fallback.
- Commitment: firm.

