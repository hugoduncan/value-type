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
-  [`value-type.core`](#value-type.core)  - Define JEP 401 value classes with [<code>defvaluetype</code>](#value-type.core/defvaluetype), a <code>deftype</code> compatible macro.
    -  [`defvaluetype`](#value-type.core/defvaluetype) - As <code>deftype</code> (see its docstring for syntax, options, specs, the <code>-&gt;name</code> factory and the import).

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
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L115-L141">Source</a></sub></p>

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
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L81-L113">Source</a></sub></p>

## <a name="value-type.array/make-array">`make-array`</a>
``` clojure
(make-array component length initial)
```
Function.

Allocate a native null-restricted atomic array with every slot set to initial.

   Requires a supported Valhalla JVM, `--enable-preview` and
   `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. Checks capability,
   component, length and initial value in that order. The component must be a
   non-primitive runtime value class. Length must be an integer from zero
   through Integer/MAX_VALUE. A compatible non-null initial value is required
   even at length zero. Native allocation failures propagate.

   The caller owns the mutable slots. Flattening is not guaranteed. Atomic
   element access does not provide safe publication or compound-update safety.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L38-L55">Source</a></sub></p>

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
  no nil-restricted fields are enabled.

  Uses value classes on a supported JEP 401 JDK with `--enable-preview`.
  Otherwise uses ordinary identity `deftype` only without enabled restrictions.
  Enabled fields require native support under every policy. Set JVM property
  `value-type.fallback` before loading: silent (default), warn (once per
  library runtime), or error (reject with Java Error). Compiler operations
  wrap that Error in CompilerException. Configuration is read and validated
  once; invalid values fail and later changes are unsupported. Requiring
  alone does not warn. Concurrent types and namespaces share warning state.

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

  Native fields: ^Point ^:nil-restricted field requires an explicit tag
  naming an already defined concrete value class, including eligible external
  classes. The explicit form is ^{:tag Point :nil-restricted true} field.
  Only true enables restriction; absent, false and nil retain ordinary behavior.
  Other metadata values are definition errors. Missing/unresolved tags,
  primitives, identity classes, interfaces, abstract classes, arrays, forward
  types and the enclosing type are not eligible. Native construction rejects nil with
  NullPointerException and other types with ClassCastException, including direct
  and reflective constructors. Reflection can wrap these exceptions. Enabled
  fields reject unavailable native support under every fallback policy. No
  internal-package exports are needed; flattened storage is not guaranteed.
  Restricted storage has the declared concrete type; reference constructor
  parameters remain Object, with unchanged order and arity. Nested values may
  contain fields that permit nil. Invalid fields report :invalid-field with :field and
  :reason; unavailable support reports :field-capability-unavailable with :reason.
  Both include :class-name and :value-type/error; compiler wrapping still applies.
  Receiving checks also apply to transferred expansions. Both `macroexpand` forms
  retain receiver-local loader installation for enabled fields, supporting separate
  `eval`, metadata-preserving source loading and function analysis then invocation.
  Ordinary value definitions do not support separate expansion/evaluation this way.
  AOT artifacts require
  the same JDK feature release with preview enabled and have no identity fallback.

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

  Errors (`ex-info` with `:value-type/error` and `:class-name`):
  - `:mutable-field` (macroexpansion; data `:fields`): a mutable field.
  - `:not-installed` (run time): an ordinary definition macroexpanded with no
    compilation loader binding, then evaluated.
  - `:not-value-class` (run time): the class is not a value class.
  - `:jvm-rejected`, `:unexpected-constructor`: from the loader.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/core.clj#L99-L199">Source</a></sub></p>
