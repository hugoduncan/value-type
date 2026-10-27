# Value-type behavior and limitations

See the [README](../README.md) for installation and a quick start, and the
[API reference](API.md) for public API contracts.

## Fallback policy

Fallback adds no minimum JDK beyond the supported Clojure runtime. On a
JVM without supported value-class capability, `defvaluetype` uses ordinary
identity `deftype`. It does not emulate value equality or identity. Libraries
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
  `:class-name`:
  - `:mutable-field`: a field is mutable (at macroexpansion).
  - `:not-installed`: the form was macroexpanded outside compilation.
    Evaluate the `defvaluetype` form directly.
  - `:not-value-class`: the class is an identity class. The causes
    and fixes:
    - The form was not compiled directly (for example
      `(eval (macroexpand form))`). Evaluate the `defvaluetype` form
      directly.
    - A `defvaluetype` is inside a method body of another
      `defvaluetype`. Move the inner form out of the method body to
      the top level.
  - `:jvm-rejected`: the JVM rejected a user class on the selected
    value path. This is not retried as fallback.
  - `:unexpected-constructor`: the Clojure compiler output has an
    unknown shape.
  - A `deftype` syntax error gives the error of `deftype`.
- How you get the error: `:mutable-field` is the cause of a
  `CompilerException` (macroexpansion error). `:jvm-rejected` and
  `:unexpected-constructor` are the cause of a `CompilerException`
  (the class is defined in analysis). `:not-installed` and
  `:not-value-class` come directly from `eval`, and are the cause of a
  `CompilerException` from `load`, `require` and `load-string`. Use
  `ex-cause` to get the `ex-info` from the `CompilerException`. A
  `(catch clojure.lang.ExceptionInfo ...)` does not catch a
  `CompilerException`.
- Value-class AOT output uses the class file version of the compiling JDK
  with the preview minor version. It loads only on the same JDK feature
  release with preview enabled. Ordinary fallback output has neither of
  these value-class requirements.

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
independent of `value-type.fallback`; that property still controls class
creation through `defvaluetype`.

Flattening is not guaranteed. A non-flat restricted atomic array is a valid
result, including with array flattening disabled. Support for other JVM
builds or untested value classes is not implied. Sequence construction is not yet implemented. No clone, copy or
performance guarantee is made.


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
