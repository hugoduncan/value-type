# Value-type behavior and limitations

See the [README](../README.md) for installation and a quick start, and the
[API reference](API.md) for public API contracts.

## Fallback policy

Fallback adds no minimum JDK beyond the supported Clojure runtime. On a
JVM without supported value-class capability, `defvaluetype` uses ordinary
identity `deftype` semantics, with generated constructor checks for enabled
non-nil fields. See the [field contract](#portable-non-nil-fields).
Fallback does not emulate value equality or identity. Libraries
that allow fallback must work with both representations.

Set the JVM property before dependencies load, not in `-main`:

```sh
clojure -J-Dvalue-type.fallback=error -M -m your.application
```

| `value-type.fallback` | Behavior when fallback is needed |
|---|---|
| `silent` (default) | Use `deftype` without a diagnostic. |
| `warn` | Warn on standard error once per loaded library runtime. |
| `error` | Reject every attempt with `java.lang.Error`. |

Configuration is read and validated once. Invalid values fail at load time;
later property changes are unsupported. There are no per-type overrides.
All policies use value classes when supported. Requiring the library alone
does not warn. Concurrent attempts across types and namespaces share one
warning flag; independent library runtimes have separate flags.

Selecting fallback during `macroexpand` or `macroexpand-1` applies policy,
even if the expansion is discarded. Returned expansions retain identity
representation and enforce the receiving runtime's policy when compiled
and evaluated. Catching a strict failure does not permit a later attempt.
Source compiler operations wrap the Error in `Compiler$CompilerException`;
inspect its cause. A direct runtime policy rejection throws plain Error.
Invalid forms and loader or transform defects remain failures, not fallback.
Both representations reject mutable fields.

## AOT limits

AOT compilation fixes representation. A fallback artifact stays an identity
class on a capable JVM. The guard applies the receiving application's policy
where the fallback form is evaluated: warn once or reject under `error`.
A top-level form enforces policy during namespace initialization. A form
inside a function permits namespace loading, even under `error`, and enforces
policy when that function runs. Repeated strict calls each throw plain Error.
After strict initialization failure, a retry can throw `NoClassDefFoundError`
rather than run the guard again; this is also a Java Error. Guards cannot
prevent all prior JVM class loading and do not cover direct class access
that bypasses evaluation of the guarded form.

Value-class AOT output uses the compiling JDK's feature version and preview
minor version. It requires a compatible preview-enabled JVM; it does not
convert to identity classes on another JVM.

## Differences from `deftype`

In value mode, objects have no identity. These differences come from the JVM.
They do not apply to ordinary identity fallback:

- `identical?` (the Java `==` operator, `acmp`) compares field values.
- The default `equals` and `hashCode` use field values.
- A `double` field compares by bit pattern in `identical?`. So `NaN`
  equals `NaN`, and `0.0` does not equal `-0.0`.
- A field that holds an identity object compares by reference. Two
  equal vectors that are not the same object make the value objects
  not equal.
- A field that holds a value object (for example a boxed `Long` under
  `--enable-preview`, or a `defvaluetype` instance) compares by its
  field values.
- `locking`, `WeakReference` and other identity operations throw
  `IdentityException`.

Other differences:

- No mutable fields. A field with truthy `:volatile-mutable` or
  `:unsynchronized-mutable` metadata gives an error.
- Field and value-path errors are `ex-info` with the key `:value-type/error` and
  `:class-name`. The [`defvaluetype` Errors list](API.md#value-type.core/defvaluetype) gives
  the data keys of each error:
  - `:invalid-field`: a field declaration is not valid (at macroexpansion),
    or a field tag resolves to a different Class in the compilation loader
    (`:reason :field-class-mismatch`, in the loader).
  - `:mutable-field`: a field is mutable (at macroexpansion).
  - `:not-installed`: an ordinary definition was macroexpanded outside compilation
    (at run time), or a definition with enabled fields was fully expanded
    outside compilation, for example with `clojure.walk/macroexpand-all`
    (at macroexpansion). Evaluate the `defvaluetype` form directly.
  - `:not-value-class`: the class is an identity class. The causes
    and fixes:
    - An ordinary definition without enabled non-nil fields was not
      compiled directly (for example `(eval (macroexpand form))`).
      Evaluate the `defvaluetype` form directly.
    - A `defvaluetype` is inside a method body of another
      `defvaluetype`. Move the inner form out of the method body to
      the top level.
  - `:value-capability-unavailable`: a transferred value expansion reaches a
    JVM that cannot define value classes (receiving boundary).
  - `:field-capability-unavailable`: a transferred expansion with fixed
    `:native` modes reaches a JVM without native field support (receiving
    boundary).
  - `:invalid-field-enforcement`: transferred enforcement modes are not valid,
    or identity fields have modes that are not `:generated` (receiving boundary).
  - `:jvm-rejected`: the JVM rejected a generated user class, for value or
    identity output. This is not retried as fallback.
  - `:unexpected-constructor`: for value output, the Clojure compiler output
    has an unknown constructor shape.
  - `:unsupported-shape`: for identity output, the Clojure compiler output
    has an unknown class or constructor shape.
  - A `deftype` syntax error gives the error of `deftype`.
- How you get the error: `:invalid-field`, `:mutable-field` and the
  macroexpansion `:not-installed` are the cause of a `CompilerException`
  (macroexpansion error). The receiving-boundary errors
  (`:value-capability-unavailable`, `:field-capability-unavailable` and
  `:invalid-field-enforcement`) are the cause of a `CompilerException`
  (the transferred expansion is macroexpanded in analysis). The loader errors
  (`:invalid-field` `:field-class-mismatch`, `:jvm-rejected`,
  `:unexpected-constructor` and `:unsupported-shape`) are the cause of a
  `CompilerException` (the class is defined in analysis). The run-time
  `:not-installed` and `:not-value-class` come directly from `eval`, and are
  the cause of a `CompilerException` from `load`, `require` and
  `load-string`. Use `ex-cause` to get the `ex-info` from the
  `CompilerException`. A `(catch clojure.lang.ExceptionInfo ...)` does not
  catch a `CompilerException`.
- Value-class AOT output uses the class file version of the compiling JDK
  with the preview minor version. It loads only on the same JDK feature
  release with preview enabled. Ordinary fallback output has neither of
  these value-class requirements.

For enabled non-nil fields, both `macroexpand` and `macroexpand-1`
retain a receiving boundary that installs a receiver-local loader. Their expansions
support separate `eval` (including `(eval (macroexpand form))`),
metadata-preserving source loading and function analysis followed by invocation.
Receiving analysis resolves local field Classes but retains the selected
representation and enforcement modes. Incompatible receivers reject fixed value
output rather than select identity output. Fixed identity output remains subject
to the receiving fallback policy, even on a value-capable JVM.
Preserve field metadata when serializing expansions.

## Value-array allocation

`value-type.array/make-array` allocates a one-dimensional native,
null-restricted atomic array. This experimental API is verified on Valhalla
`27-jep401ea3+1-1` with Clojure 1.12.6. Select that JVM and start with both
flags:

```sh
export JAVA_CMD=<valhalla-jdk-home>/bin/java
clojure -J--enable-preview \
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M
```

```clojure
(require '[value-type.core :refer [defvaluetype]]
         '[value-type.array :as va])

(defvaluetype ArrayPoint [^long x])
(def points (va/make-array ArrayPoint 3 (ArrayPoint. 0)))
(alength points)                         ;=> 3
(.-x (aget points 2))                    ;=> 0
(aset points 1 (ArrayPoint. 7))
(.-x (aget points 1))                    ;=> 7
```

Every slot starts with the supplied initial value. The component must be a
non-primitive runtime value class, not an identity class. It need not come
from `defvaluetype`, but the JVM makes the final allocation decision.
Length must be an integer from zero through `Integer/MAX_VALUE`. A valid
length does not guarantee sufficient memory. The initial value must be
non-null and compatible with the component, even when length is zero.

The result works with ordinary Clojure and Java array operations. Later
null stores throw `NullPointerException`; incompatible stores throw
`ArrayStoreException`. These are native exceptions, not library validation
errors. The caller owns the mutable slots after return; the library retains
no array. Atomic element access does not provide safe publication,
synchronization or atomic compound updates.

Validation stops at the first error in this order. Each library validation
error is `ex-info` with a `:value-type/error` key:

| Order | Key | Meaning |
| --- | --- | --- |
| 1 | `:array-unavailable` | Required JVM capability, API or access is absent. |
| 2 | `:invalid-array-component` | The component is not a non-primitive value class. |
| 3 | `:invalid-array-length` | The length is not an integer in the accepted range. |
| 4 | `:invalid-array-element` | The initial value is null or incompatible. |

Error data includes `:reason` and relevant `:component` and `:length`
context. Available underlying causes are retained. Native allocation
rejection, unexpected failures and JVM errors propagate; they do not mean
that capability is absent.

Requiring `value-type.array` is safe on Java 8 and on Valhalla without
preview or exports. Allocation capability is checked lazily and cached per
loaded library runtime; startup configuration is fixed. Unsupported calls
report `:array-unavailable`, even if their inputs are also invalid.
There is no ordinary-array fallback or warning policy. Array allocation is
independent of `value-type.fallback`; that property controls ordinary fallback
through `defvaluetype`, including definitions with non-nil fields.

Flattening is not guaranteed. A non-flat restricted atomic array is a valid
result, including with array flattening disabled. Support for other JVM
builds or untested value classes is not implied. No clone, copy or
performance guarantee is made.


## Value-array sequence construction

`value-type.array/into-array` builds the same native null-restricted atomic
arrays from finite seqable input. Use the JVM flags and `ArrayPoint` definition
above. Both forms preserve input order and exact length:

```clojure
(def inferred (va/into-array ArrayPoint [(ArrayPoint. 2) (ArrayPoint. 5)]))
(mapv #(.-x %) inferred)                 ;=> [2 5]
(def explicit (va/into-array ArrayPoint (ArrayPoint. 99)
                             (list (ArrayPoint. 2) (ArrayPoint. 5))))
(mapv #(.-x %) explicit)                 ;=> [2 5]
(alength (va/into-array ArrayPoint (ArrayPoint. 0) []))  ;=> 0
(alength (va/into-array ArrayPoint (ArrayPoint. 0) nil)) ;=> 0
```

The two-argument form uses the first element as its allocation seed. Empty
or nil input throws `:empty-array-input`; use the explicit-seed form instead.
The three-argument form requires a compatible non-null seed even for empty
input. The seed adds no output slot and need not equal the first input value.
A null field inside a valid value object is not a null array element.

Validation checks allocation capability, then the component, then the explicit
seed (if supplied), before consuming input. These checks use the allocation
errors above. Explicit-seed errors have reason `:null-initial` or
`:incompatible-initial`, with no element index or invented length. Unsupported
calls report `:array-unavailable` without consuming input; there is no fallback.

During traversal, each proposed length is checked before the element is
validated and stored. Null or incompatible entries throw `ex-info` with
`:value-type/error :invalid-array-element`, `:component`, a zero-based `:index`
and reason `:null-element` or `:incompatible-element`. This includes the first
entry in the inferred-seed form. Error data does not retain the input or the
rejected element. Non-seqable input uses ordinary Clojure input errors.

The constructor traverses the original input once into temporary storage,
then allocates and copies the buffered values. This costs O(n) temporary
references in addition to the result array; it is not streaming or zero-copy.
Infinite input is unsupported. Length cannot exceed `Integer/MAX_VALUE`;
memory can run out before that limit. No speed or allocation benefit is claimed.

Input realization failures propagate unchanged. Failure does not undo input
side effects and does not promise to consume the rest of the input. Chunked
lazy producers can realize ahead; one traversal does not mean exactly one
callback per requested element. Only a complete array is returned. Temporary
storage and unfinished arrays are not retained in library state. Returned
arrays have the ownership, later-store and concurrency limits described above;
flattening is not required or guaranteed.

## Value-array diagnostics

`value-type.array/array-info` inspects an existing reference array. It accepts
ordinary Object/String arrays, typed value arrays and restricted arrays from
`make-array`. Empty arrays and arrays whose components are arrays are valid.
It returns a complete map with these keys:

| Key | Value |
| --- | --- |
| `:component-class` | The runtime component `Class`. |
| `:component-value?` | boolean: the component is a value class. |
| `:null-restricted?` | boolean: the JVM reports null restriction. |
| `:atomic?` | boolean: the JVM reports atomic element access. |
| `:flat?` | boolean: the JVM reports flattened storage. |

```clojure
(va/array-info (object-array 0))
;=> {:component-class java.lang.Object, :component-value? false,
;    :null-restricted? false, :atomic? true, :flat? false}
(va/array-info points) ; Inspect the restricted array from the example above.
```

The example result is verified on Valhalla `27-jep401ea3+1-1` with Clojure
1.12.6. Inspection requires the export flag shown above. Ordinary reference
arrays do not require `--enable-preview` for inspection on that build.
Creating value classes and allocating restricted arrays still require preview.
Inspection capability is separate from allocation capability and class fallback.
It is checked lazily and cached per loaded runtime; set flags before startup.

Validation precedes capability checks, unlike allocation. Null, non-array and
primitive-array inputs throw `ExceptionInfo` with
`:value-type/error :invalid-array`, even on unsupported JVMs. For a valid
reference array, missing API support or access throws `ExceptionInfo` with
`:value-type/error :array-inspection-unavailable`. Available causes are retained.
Unexpected failures propagate. The function returns all five properties or
fails; it does not use false for unknown facts.

Inspection does not change or retain the caller's array. Properties come from
the JVM, not from a class name or successful allocation. A value component does
not prove flattening or null restriction. A false `:flat?` is valid, including
with flattening disabled. Atomic access does not provide compound-update safety,
safe publication or caller synchronization.

This is an experimental API, not a general object inspector or a layout-size
estimate. Support for other JVM builds is not implied. Use the public wrapper:
native diagnostic calls with null can crash the tested JVM. See the
[runtime guide](development/runtime.md) for verification commands.

## Capability report

`value-type.capability/report` returns the optional JVM capabilities of the
loaded runtime as an EDN map. Use it to check a deployment before you define
types or allocate arrays.

```clojure
(require '[value-type.capability :as capability])
(capability/report)
;=> {:value-class {:available? true},
;    :native-non-nil-field {:available? true},
;    :array-allocation {:available? false, :reason :array-access-denied},
;    :array-inspection {:available? false, :reason :array-access-denied}}
```

The example result is from Valhalla `27-jep401ea3+1-1` with
`--enable-preview` and without the export flag.

| Key | Meaning |
| --- | --- |
| `:value-class` | `defvaluetype` can define value classes. |
| `:native-non-nil-field` | Eligible `:non-nil` fields use native restriction. |
| `:array-allocation` | `make-array` and `into-array` can allocate. |
| `:array-inspection` | `array-info` can inspect. |

Each value is `{:available? true}` or `{:available? false :reason r}`. The
reason keywords are in the [API reference](API.md). Each value equals the
`:available?` and `:reason` entries of the cached result that definitions and
array functions use. `report` runs the probes that have not run yet. A probe
defect propagates; no partial report is returned.

`report` does not read the `value-type.fallback` policy. Loading
`value-type.capability` does not load `value-type.core` and needs no JEP 401
support, preview flag, export or `java.lang.classfile`. Results are cached per
loaded runtime; set JVM flags before startup.

## Portable non-nil fields

Use an explicit concrete-class tag and `:non-nil` metadata:

```clojure
(defvaluetype Point [^long x ^long y])
(defvaluetype Segment [^Point ^:non-nil start
                       ^{:tag Point :non-nil true} end])
(defvaluetype Label [^String ^:non-nil text])
```

Only `true` enables the constraint. Absent, false and nil metadata retain ordinary
field behavior; other values are definition errors. The removed `:nil-restricted`
key is an error even when its value is false or nil. There is no compatibility alias.

The tag must name an already defined concrete, non-primitive class. Identity
classes, value classes, fallen-back defvaluetype classes and external classes are
eligible. Interfaces, abstract classes, arrays, forward references and enclosing
types are not eligible. Mutable fields remain unsupported. Plain hints and disabled
constraints keep ordinary type-hint and primitive-field behavior.

Factories, direct Clojure and Java constructors, and reflective constructors reject
nil with `NullPointerException` and wrong types with `ClassCastException`.
Reflection can wrap these exceptions in `InvocationTargetException`. Constrained
fields have final storage of the declared concrete type, but reference constructor
parameters remain Object. Constructor order and arity do not change. Nested objects
can contain ordinary fields that permit nil. Error text and the failure order for
multiple invalid arguments are not part of the contract.

Class representation and field enforcement are separate choices:

| Representation | Field enforcement |
|---|---|
| Identity fallback | Generated constructor checks |
| Value, with an eligible tag and native field capability | Native restriction |
| Value, otherwise | Generated constructor checks |

The fallback policy controls identity representation, not the enforcement choice.
Missing native field support alone does not warn or fail under strict policy.
Generated checks run in actual constructors, not only in factories. Reads add no
policy checks. Unexpected probe, loader, verification and transformation failures
remain errors; they do not select weaker output.

Requiring `value-type.core` is safe on Java 8 and preview-disabled EA3.
Identity output does not require optional JDK interfaces or preview. Value
output requires preview; field construction needs no internal-package export.
Real-JVM evidence covers Java 8 and Valhalla `27-jep401ea3+1-1` with Clojure
1.12.6. Controlled capability cases do not establish support for other JVMs.
See the [runtime instructions](development/runtime.md) for verification
commands and limits.

Field declaration errors are `ExceptionInfo` with `:value-type/error`,
`:class-name` and `:reason`. Invalid fields use `:invalid-field` and include
`:field`. The `:invalid-field` reasons are:

- Declarations: `:removed-nil-restricted` and `:invalid-non-nil` (with
  `:value`); `:missing-tag`, `:unresolved-tag`, `:enclosing-type`,
  `:primitive-type`, `:array-type`, `:interface-type` and `:abstract-type`
  (with `:tag`).
- Receiving boundary, native mode: `:identity-type` (with `:tag`). The tag
  names an identity class.
- Loader: `:field-class-mismatch`. The tag resolves to a different Class in the
  compilation loader.

Compiler operations can wrap errors in `CompilerException`. Declarations
are validated before enforcement selection. Discarded expansions still validate
declarations and apply representation policy.

AOT fixes both representation and enforcement. Saved owners and companions work
without producer source with separate Clojure and Java callers. Identity artifacts
retain generated checks on capable JVMs; guarded forms still apply receiving
fallback policy. Top-level guards run during namespace initialization;
function-contained guards run on invocation. Direct Java class access does not
execute namespace policy guards, but constructors still enforce field constraints.
Value artifacts require the same JDK feature release with preview enabled. Java 8
and preview-disabled EA3 reject them under every policy. There is no load-time
conversion or enforcement reselection.

The contract excludes reflective mutation with access checks suppressed, Unsafe,
instrumentation, and allocation or restoration that bypasses constructors.
Neither enforcement mode guarantees flat storage or better performance. Array
allocation retains its separate native-only capability boundary.

## `deftype*` forms

Libraries that emit `deftype*` can use `value-type.core/defvaluetype*`. It
takes the `deftype*` arguments and evaluates to the defined Class. Use it in
the same top-level position as `deftype*`, not inside a method body.

```clojure
(value-type.core/defvaluetype* my.ns/Point my.ns.Point [x y]
  :implements [clojure.lang.IType])
```

Two field shapes are accepted:

- Plain: no field has a hidden name (`__meta`, `__extmap`, `__hash`,
  `__hasheq`; the namespace is ignored).
- Record: the fields end with `__meta __extmap ^int __hash ^int __hasheq`,
  unqualified, with no tag on `__meta` and `__extmap`. This is the shape that
  the Clojure compiler gives `defrecord`.

The option `:value-type/closed true`, in the `deftype*` option position,
selects the closed record shape. `false`, `nil` and an absent option do not
select it; the form keeps the plain or record shape. The option requires the
record shape. When the option occurs more than once, the last occurrence
applies, as in `deftype*`. `defvaluetype*` removes each occurrence of the
option from the `deftype*` call.

Fields can be non-nil, as in
[portable non-nil fields](#portable-non-nil-fields), in both shapes. A
hidden-name field cannot be non-nil. The main constructor checks each enabled
field. All constructors, `create`, record literals, compiled record constants
and reflection reject nil with `NullPointerException` and wrong types with
`ClassCastException`. The compiler, class initialization and reflection can
wrap these exceptions (for example in `CompilerException`,
`ExceptionInInitializerError` or `InvocationTargetException`); find the
exception in the cause chain. The error text is not specified.

These declaration checks run at macroexpansion, before representation policy,
in this order; the first failure wins:

1. Mutable fields give `:mutable-field`.
2. Other shapes give `:invalid-field` with `:reason :record-shape`.
3. A `:value-type/closed` value other than true, false or nil gives
   `:invalid-option` with `:reason :invalid-value`; `:value-type/closed true`
   on a plain-shape form gives `:reason :record-shape-required` (data
   `:option`, `:value`).
4. `:nil-restricted` metadata gives `:removed-nil-restricted`; a `:non-nil`
   value other than true, false or nil gives `:invalid-non-nil`.
5. A hidden-name field with `:non-nil true` gives `:reason :hidden-field`.
6. The tag and type reasons of `defvaluetype` for enabled fields.

An invalid form never selects fallback. Identity output with enabled fields or
the closed shape gives `:unsupported-shape` when a delegating constructor
writes an enabled field, or when a constructor both creates and calls the
target.

Caller obligations for record shapes:

- Do not read or write `__hash` or `__hasheq`.
- Pass 0 for both hash arguments in explicit constructor calls.
- Make the code work with both value and identity output.
- Supply each enabled field. `create` from a map with no entry for an
  enabled field fails, and an `assoc` that calls a constructor (as
  `defrecord` does) rejects nil for an enabled field. Code that builds
  records from partial maps must supply each enabled field.

Caller obligations for closed shapes, in addition:

- Do not read or write `__meta` or `__extmap`.
- Pass nil for both in explicit constructor calls.
- Do not give the type metadata or extension keys through these fields, for
  example with `withMeta` or `assoc` methods that use them.

Value output removes both hash fields and ignores those arguments. The
compiler-generated constructors, `create` and `getBasis` keep their
contracts, so record literals and compiled record constants work. Methods
must compute hashes on each call. Owner or companion code that accesses a
removed field gives `:invalid-field` with `:reason :removed-field-access`.
Reflective access is not checked. Identity output keeps both fields; they hold
0 when callers meet these obligations. value-type does not validate the
hash arguments.

For a closed form, value output removes `__meta`, `__extmap`, `__hash` and
`__hasheq`; constructors keep their descriptors, and owner or companion
access to any of the four fields gives `:removed-field-access`. Identity
output keeps all four fields; `__meta` and `__extmap` hold nil. In both
outputs, the main constructor throws `IllegalArgumentException` for a
non-nil `__meta` or `__extmap` argument, before the non-nil field checks.
Thus `create` and the map literal reject an undeclared key. The positional
literal, the constructor with only the declared fields and compiled record
constants are not affected. The compiler, the reader and reflection can wrap
the exception. The error text is not specified.

Representation policy, fallback and AOT behave as for `defvaluetype`. The
fallback expansion with neither enabled fields nor the closed shape is guarded
`deftype*` output with no transform. With enabled fields or the closed shape,
the value and fallback expansions are receiving forms that repeat the
declaration checks; the fallback receiver gives identity output with generated
checks. Receivers of a transferred fallback expansion apply their own policy.
A value expansion with neither enabled fields nor the closed shape evaluated
apart from its macroexpansion fails with `:not-installed` or
`:not-value-class`; it never selects fallback. With enabled fields or the
closed shape, see the receiving boundary paragraph in [Differences from
`deftype`](#differences-from-deftype): separate `eval` of the expansion works,
and only a full expansion outside compilation (for example
`clojure.walk/macroexpand-all`) gives `:not-installed`. An unexpected
constructor shape gives `:unexpected-constructor`.

Exclusions: mutable caches, value-equality emulation,
changes to `defrecord` and serialization guarantees. Real-JVM evidence covers
Clojure 1.12.6, Java 8 and Valhalla `27-jep401ea3+1-1` with preview off and
on. It does not establish other JVMs, Clojure versions, object size or
performance.
