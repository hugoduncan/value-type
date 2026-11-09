# value-type API reference

This file is generated from source docstrings with Quickdoc. Do not edit `doc/API.md` by hand.

See the [README](../README.md) for installation, examples and tested JVMs.

See the [usage guide](usage.md) for detailed behavior and error handling.

Regenerate from the repository root with `bb api-docs`. Check without writing with `bb api-docs --check`.

# Table of contents
-  [`value-type.array`](#value-type.array)  - Native null-restricted atomic value arrays (experimental Valhalla API).
    -  [`array-info`](#value-type.array/array-info) - Return actual properties of a reference array (experimental Valhalla API).
    -  [`into-array`](#value-type.array/into-array) - Build a native null-restricted atomic array from finite seqable input.
    -  [`make-array`](#value-type.array/make-array) - Allocate a native null-restricted atomic array with every slot set to initial.
-  [`value-type.capability`](#value-type.capability)  - Read-only report of the optional JVM capabilities that value-type uses and of the fallback policy in effect.
    -  [`report`](#value-type.capability/report) - Return the capability results and the fallback policy in effect of this runtime as an EDN-printable map.
-  [`value-type.core`](#value-type.core)  - Define JEP 401 value classes with [<code>defvaluetype</code>](#value-type.core/defvaluetype), a <code>deftype</code> compatible macro.
    -  [`defvaluetype`](#value-type.core/defvaluetype) - As <code>deftype</code> (see its docstring for syntax, options, specs, the <code>-&gt;name</code> factory and the import).
    -  [`defvaluetype*`](#value-type.core/defvaluetype*) - As <code>deftype*</code>, with the same arguments.

-----
# <a name="value-type.array">value-type.array</a>


Native null-restricted atomic value arrays (experimental Valhalla API).

   Allocation has no ordinary-array fallback. Use ordinary Clojure array operations.




## <a name="value-type.array/array-info">`array-info`</a>
``` clojure
(array-info array)
```
Function.

Return actual properties of a reference array (experimental Valhalla API).

   Accepts ordinary and restricted reference arrays, including empty arrays.
   Returns the runtime Class as :component-class and boolean :component-value?, :null-restricted?,
   :atomic? and :flat? properties. Null, non-array and primitive-array inputs
   throw ExceptionInfo with :value-type/error :invalid-array before capability
   checks. Arrays whose components are arrays are accepted.

   Inspection requires a supported JVM and
   `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`, but ordinary
   reference-array inspection does not require preview. Missing support or
   access throws ExceptionInfo with :value-type/error :array-inspection-unavailable.
   Returns all five properties or fails; unknown facts are not false.
   Native failures propagate. Inspection does not change or retain the array.
   Flattening is not guaranteed. Atomic access does not provide safe publication,
   compound-update safety or caller synchronization.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L116-L142">Source</a></sub></p>

## <a name="value-type.array/into-array">`into-array`</a>
``` clojure
(into-array component values)
(into-array component initial values)
```
Function.

Build a native null-restricted atomic array from finite seqable input.

   Requires the allocation capability and component accepted by make-array.
   Checks these before input realization; the explicit seed is also checked
   before realization, even for empty input. With two arguments, the first
   element is the seed; empty or nil input throws :empty-array-input. With
   three arguments, empty or nil input is accepted and initial adds no slot.
   The seed must be compatible and non-null. Use this arity for empty input.

   Traverses input once into O(n) temporary references, then allocates and copies
   in order. Null or incompatible entries throw :invalid-array-element with a
   zero-based :index. Length cannot exceed Integer/MAX_VALUE; memory can run out
   sooner. Infinite input is unsupported. Input failures propagate unchanged;
   effects are not undone and chunked producers can realize ahead. Returns only
   a complete array. Non-seqable input uses ordinary Clojure input errors.
   Failure does not promise full input consumption. The caller owns mutable
   slots; flattening is not guaranteed.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L82-L114">Source</a></sub></p>

## <a name="value-type.array/make-array">`make-array`</a>
``` clojure
(make-array component length initial)
```
Function.

Allocate a native null-restricted atomic array with every slot set to initial.

   Requires a supported Valhalla JVM, `--enable-preview`,
   `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` and JVM property
   `value-type.value-classes` not `off`. Checks capability,
   component, length and initial value in that order. The component must be a
   non-primitive runtime value class. Length must be an integer from zero
   through Integer/MAX_VALUE. A compatible non-null initial value is required
   even at length zero. Native allocation failures propagate.

   The caller owns the mutable slots. Flattening is not guaranteed. Atomic
   element access does not provide safe publication or compound-update safety.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L38-L56">Source</a></sub></p>

-----
# <a name="value-type.capability">value-type.capability</a>


Read-only report of the optional JVM capabilities that value-type uses
  and of the fallback policy in effect.




## <a name="value-type.capability/report">`report`</a>
``` clojure
(report)
```
Function.

Return the capability results and the fallback policy in effect of this
  runtime as an EDN-printable map.

  Keys:
  - `:value-class`: this runtime can define value classes.
  - `:native-non-nil-field`: value classes use native restriction for
    eligible `:non-nil` fields.
  - `:array-allocation`: `make-array` and `into-array` can allocate.
  - `:array-inspection`: `array-info` can inspect.
  - `:fallback-policy`: the fallback policy in effect, `:silent`, `:warn` or
    `:error`.

  Each capability value is `{:available? true}` or
  `{:available? false :reason r}`. Reasons:
  - `:value-class`: `:missing-classfile-api`, `:missing-value-predicate`,
    `:unsupported-class-version`, `:probe-not-value-class`,
    `:failed-self-check`, `:disabled`.
  - `:native-non-nil-field`: the value-class reasons,
    `:native-restriction-ignored`.
  - `:array-allocation`: the value-class reasons, `:missing-array-api`,
    `:missing-array-signature`, `:array-access-denied`,
    `:failed-self-check`.
  - `:array-inspection`: `:missing-array-api`, `:missing-array-signature`,
    `:array-access-denied`.

  Each value equals the `:available?` and `:reason` entries of the cached
  result that definitions and array functions use. Probes that have not run
  yet run now; a probe defect propagates and no partial report is returned.

  The report reads the `value-type.fallback` policy before it runs a probe.
  The first read fixes the policy; later property changes have no effect.
  An invalid policy value throws IllegalArgumentException before any probe
  runs; no partial report is returned.

  The report reads the `value-type.value-classes` property with the
  value-class capability, after the policy. Valid values are `auto` and
  `off`; no property is `auto`. `off` gives `:disabled` for
  `:value-class`, `:native-non-nil-field` and `:array-allocation`, and
  runs no value-class probe. An invalid value throws
  IllegalArgumentException; no partial report is returned. The first
  read fixes the result; later property changes have no effect.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/capability.clj#L9-L58">Source</a></sub></p>

-----
# <a name="value-type.core">value-type.core</a>


Define JEP 401 value classes with [`defvaluetype`](#value-type.core/defvaluetype), a `deftype`
  compatible macro.




## <a name="value-type.core/defvaluetype">`defvaluetype`</a>
``` clojure
(defvaluetype name [& fields] & opts+specs)
```
Macro.

As `deftype` (see its docstring for syntax, options, specs, the
  `->name` factory and the import). Define a final JEP 401 value class
  when supported, or an ordinary identity class under fallback policy when
  identity fallback is permitted.

  Uses value classes on a supported JEP 401 JDK with `--enable-preview`,
  unless JVM property `value-type.value-classes` is `off`. Otherwise uses
  identity storage with generated checks for enabled fields.
  Representation policy is independent of field enforcement. JVM property
  `value-type.fallback` sets the policy: silent (default), warn (once per
  library runtime), or error (reject with Java Error). Compiler operations
  wrap that Error in CompilerException. The policy applies if it is set
  before the first read: the first fallback decision or the first
  [`value-type.capability/report`](#value-type.capability/report) call. A namespace that evaluates a fallback
  definition at top level makes the first read when it loads, so prefer the
  JVM option `-Dvalue-type.fallback=...`. An invalid value fails at the first
  read; changes after the first read have no effect. Requiring alone does not
  read the policy or warn. Concurrent types and namespaces share warning
  state.

  Selecting fallback during macroexpansion applies policy even if the
  expansion is discarded. Returned expansions enforce receiving policy at
  compilation and evaluation. Strict retries reject after a caught failure.
  Fallback retains ordinary identity, equality and hashing semantics.
  Invalid forms and implementation defects are not retried as fallback.

  AOT fixes representation. Fallback enforces receiving policy where the
  form is evaluated, even on a capable JVM. Top-level forms enforce it during
  namespace initialization. Forms inside functions permit namespace loading
  and enforce policy on invocation; repeated strict calls throw plain Error.
  Failed initialization retries can throw NoClassDefFoundError (also an Error).
  Guards do not cover direct class access that bypasses evaluation of the
  guarded form, or guarantee that no class was loaded before rejection.
  Value artifacts do not convert.
  Fallback adds no minimum JDK beyond the supported Clojure runtime; see
  README for tested JVM versions and verification commands.

  Non-nil fields: ^Point ^:non-nil field requires an explicit tag naming an
  already defined concrete class, including identity and external classes. The
  explicit form is ^{:tag Point :non-nil true} field. Only true enables the
  constraint; absent, false and nil keep ordinary behavior. Other metadata
  values are definition errors. The removed :nil-restricted key is rejected by
  presence, including false and nil; use :non-nil instead. Missing/unresolved
  tags, primitives, interfaces, abstract classes, arrays, forward types and
  the enclosing type are not eligible. Construction rejects nil with
  NullPointerException and other types with ClassCastException, including
  direct and reflective constructors. Reflection can wrap these exceptions.
  Enabled fields use native enforcement when eligible and available, otherwise
  generated constructor checks. Missing native support alone does not invoke
  fallback policy. No internal-package exports are needed; flattened storage
  is not guaranteed. Non-nil field storage has the declared concrete type;
  reference constructor parameters remain Object, with unchanged order and
  arity. Nested values may contain fields that permit nil. Receiving checks
  also apply to transferred expansions. Both `macroexpand` forms retain
  receiver-local loader installation for every value definition and for
  identity definitions with enabled fields, supporting separate `eval`,
  metadata-preserving source loading and function analysis then
  invocation. Value AOT artifacts require the same JDK feature release with
  preview enabled and have no identity fallback; each saved value form
  throws `:value-capability-unavailable` where it is evaluated when the
  value-class capability is unavailable (for example
  `value-type.value-classes=off`). Identity
  artifacts retain generated checks on baseline and capable JVMs.

  Restriction: no mutable fields (`:volatile-mutable` or
  `:unsynchronized-mutable`).

  In value mode, differences from `deftype`: instances have no identity.
  - `identical?` (the Java `==` operator, `acmp`) compares field values.
  - The default `equals` and `hashCode` use field values.
  - A `double` field compares by bit pattern in `identical?` (`NaN`
    equals `NaN`; `0.0` does not equal `-0.0`).
  - A field that holds an identity object compares by reference (two
    equal vectors that are not the same object give instances that
    are not equal).
  - A field that holds a value object (for example a boxed `Long`
    under `--enable-preview`, or a [`defvaluetype`](#value-type.core/defvaluetype) instance) compares
    by its field values.
  - `locking`, `WeakReference` and other identity operations throw
    `IdentityException`.
  - AOT output is tied to one JDK feature release.

  Errors (`ex-info` with `:value-type/error` and `:class-name`; compiler
  wrapping applies):
  - `:invalid-field` (macroexpansion; data `:field`, `:reason`): an invalid
    field declaration.
  - `:mutable-field` (macroexpansion; data `:fields`): a mutable field.
  - `:not-installed` (macroexpansion): a value definition, or an identity
    definition with enabled fields, fully expanded with no compilation
    loader binding (for example `clojure.walk/macroexpand-all`). One-step
    `macroexpand` does not throw.
  - `:not-value-class` (run time): the class is not a value class, for
    example a [`defvaluetype`](#value-type.core/defvaluetype) nested in a method body.
  - `:value-capability-unavailable` (receiving boundary or evaluation; data
    `:reason`): value output on a JVM where the value-class capability is
    unavailable: a transferred value expansion at analysis, saved AOT
    output where the form is evaluated.
  - `:field-capability-unavailable` (receiving boundary or evaluation; data
    `:reason`): output with fixed `:native` modes on a JVM with value-class
    capability but without native field support. Transferred expansions fail
    at analysis; saved AOT output fails at namespace initialization or function
    invocation. There is no fallback or enforcement reselection. The value-class
    guard runs first and throws `:value-capability-unavailable` if value-class
    capability is unavailable.
  - `:invalid-field-enforcement` (receiving boundary; data `:modes`): invalid
    transferred enforcement modes, or identity fields with non-generated modes.
  - `:invalid-field` (loader; data `:field`, `:reason :field-class-mismatch`):
    the field tag resolves to a different Class in the compilation loader.
  - `:jvm-rejected` (loader): the JVM rejects the generated class.
  - `:unexpected-constructor` (loader; value output): the constructor is not the
    supported `deftype` shape.
  - `:unsupported-shape` (loader; identity output): the class or constructor is
    not the supported `deftype` shape for generated checks.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/core.clj#L284-L408">Source</a></sub></p>

## <a name="value-type.core/defvaluetype*">`defvaluetype*`</a>
``` clojure
(defvaluetype* tagname classname [& fields] & opts+specs)
```
Macro.

As `deftype*`, with the same arguments. Evaluate to the defined Class.
  Define a JEP 401 value class when supported, or an ordinary identity
  class under the `value-type.fallback` policy (see [`defvaluetype`](#value-type.core/defvaluetype)). Use
  it in the same top-level position as `deftype*`.

  Field shapes:
  - Plain: no field has a hidden name (`__meta`, `__extmap`, `__hash`,
    `__hasheq`; namespace ignored).
  - Record: the fields end with `__meta __extmap ^int __hash ^int __hasheq`,
    unqualified, with no tag on `__meta` and `__extmap`.
  Other shapes give `:invalid-field` with `:reason :record-shape`.
  Option `:value-type/closed true` selects the closed record shape and
  requires the record shape; `false`, `nil` and an absent option do not
  select it. The last occurrence applies. [`defvaluetype*`](#value-type.core/defvaluetype*) removes each
  occurrence from the `deftype*` call.

  Caller obligations for record shapes: do not read or write `__hash` or
  `__hasheq`, and pass 0 for both hash arguments in explicit constructor
  calls. Value output removes both fields and ignores those arguments.
  Identity output keeps both fields; they hold 0 when callers meet these
  obligations. value-type does not validate the hash arguments.

  Closed shape: also do not read or write `__meta` or `__extmap`, pass nil
  for both in explicit constructor calls, and do not give the type
  metadata or extension keys through these fields. Value output removes
  all four hidden fields; constructors keep their descriptors. Identity
  output keeps all four; `__meta` and `__extmap` hold nil. In both
  outputs the main constructor throws `IllegalArgumentException` for a
  non-nil `__meta` or `__extmap` argument, before the non-nil field
  checks, so `create` and the map literal reject an undeclared key. The
  compiler, the reader and reflection can wrap the exception. Error text
  is not specified.

  Non-nil fields: `:non-nil` metadata as for [`defvaluetype`](#value-type.core/defvaluetype), in both
  shapes. Hidden-name fields cannot be non-nil. The main constructor checks
  each enabled field, so each constructor, `create`, record literals,
  compiled record constants and reflection reject nil with
  `NullPointerException` and wrong types with `ClassCastException`. The
  compiler, class initialization and reflection can wrap these exceptions;
  find the exception in the cause chain. Error text is not specified.
  With enabled fields or the closed shape, the expansion is a receiving
  form, also on fallback, and receivers repeat the declaration checks.

  Restrictions: no mutable fields and no `:nil-restricted` field metadata.

  Errors (`ex-info` with `:value-type/error` and `:class-name`), checked at
  macroexpansion before representation policy, in this order:
  - `:mutable-field` (data `:fields`).
  - `:invalid-field` `:reason :record-shape` (data `:field`).
  - `:invalid-option` `:reason :invalid-value` or `:record-shape-required`
    (data `:option`, `:value`).
  - `:invalid-field` `:reason :removed-nil-restricted` or `:invalid-non-nil`
    (data `:field`, `:value`).
  - `:invalid-field` `:reason :hidden-field` (data `:field`): a hidden-name
    field with `:non-nil true`.
  - The [`defvaluetype`](#value-type.core/defvaluetype) tag and type reasons of enabled fields.
  Errors of value output, when the capture loader defines the class:
  - `:unexpected-constructor`: the constructors are
    not the plain or the record shape.
  - `:invalid-field` `:reason :removed-field-access` (data `:field`): a
    captured class reads or writes a removed field: `__hash` or
    `__hasheq`, and for the closed shape also `__meta` or `__extmap`.
  Errors of identity output with enabled fields or the closed shape, when
  the capture loader defines the class:
  - `:unsupported-shape`: a delegating constructor writes an enabled field,
    or a constructor both creates and calls the target.
  Other policy, capability, receiving, loader and run time errors are as for
  [`defvaluetype`](#value-type.core/defvaluetype). A value expansion is a receiving form; it
  never selects fallback. Separate `eval` of the expansion works, and only a full
  expansion outside compilation (for example `clojure.walk/macroexpand-all`)
  gives `:not-installed`; see doc/usage.md.

  Excluded: mutable caches, value-equality emulation and
  serialization guarantees. Evidence covers Clojure 1.12.6 and the primary
  Java 8 and Valhalla builds in [the JVM facts](../dev/jvm/jvms.edn).
  See [the runtime guide](development/runtime.md) for selection and support limits.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/core.clj#L410-L501">Source</a></sub></p>
