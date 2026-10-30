# value-type

Clojure's `deftype` creates objects with identity. value-type provides
`defvaluetype` to define [JEP 401 value objects](https://openjdk.org/jeps/401)
with familiar `deftype` syntax. It uses value classes on a supported
Valhalla JVM and ordinary identity classes otherwise, subject to application
fallback policy. Required, typed fields retain their constraints in either
representation.

For collections of value objects, `value-type.array` creates native arrays
that reject null elements and provide atomic element access. The JVM can
store values directly in the array instead of through separate object
references. This storage layout is called flattening; it is not guaranteed.

## Project status

The project is experimental and has no published release. Use it to
experiment with value classes in Clojure; do not assume a stable API or
production readiness. Value-class support depends on an early-access JVM.

Tests cover Clojure 1.12.6 on Java 8 and Valhalla `27-jep401ea3+1-1`,
with preview disabled and enabled on Valhalla. This is tested coverage,
not a guarantee for future JVM versions. See the
[verification commands](doc/development/runtime.md#verified-fallback-matrix).

## Installation and quick start

Install the Clojure CLI and clone this repository:

```sh
git clone https://github.com/hugoduncan/value-type.git
```

In your application, add this to `deps.edn`. Replace `<value-type-dir>`
with the path to the cloned repository:

```clojure
{:deps {org.hugoduncan/value-type {:local/root "<value-type-dir>"}}
 :aliases {:dev {:jvm-opts ["--enable-preview"]}}}
```

### JDK requirement

For value classes, select a supported Valhalla JDK as described in the
[JDK setup guide](doc/development/runtime.md#jdk-requirement). Set `JAVA_CMD`
to its `bin/java`, then start a REPL from your application directory:

```sh
export JAVA_CMD=<valhalla-jdk-home>/bin/java
clojure -M:dev
```

### Example

Define and construct a value type:

```clojure
(require '[value-type.core :refer [defvaluetype]])

(defvaluetype Point [^long x ^long y]
  Object
  (toString [_] (str "(" x "," y ")")))

(str (->Point 1 2))                       ;=> "(1,2)"
(identical? (->Point 1 2) (->Point 1 2))   ;=> true
(.isValue Point)                          ;=> true
```

The last two results require supported value classes with preview enabled.
To try ordinary-JVM fallback, start with `clojure -M` instead. Construction
still works, but the two instances are not identical, and `Class.isValue`
can be absent.

### Non-nil fields

Use an explicit concrete-class tag and `:non-nil` metadata to require a
non-null nested value:

```clojure
(defvaluetype Segment [^Point ^:non-nil start
                       ^Point ^:non-nil end])
(def segment (->Segment (->Point 1 2) (->Point 3 4)))
(.-x (.-start segment))                  ;=> 1
```

These declarations also work on ordinary JVMs when fallback policy permits
identity classes. Constructors retain concrete field types and reject nil and
wrong types. Eligible value fields use native enforcement when available; other
fields use generated constructor checks. Neither path guarantees flattening.
Field enforcement needs no internal-package export.

Tags can name already defined concrete identity or value classes, including
`String`. The old `:nil-restricted` key is rejected, even when false or nil.
See the [field contract](doc/usage.md#portable-non-nil-fields) for eligible tags,
errors and deployment limits.

### Value arrays

Array construction needs a supported Valhalla JVM, preview and access to an
internal JVM package. With `JAVA_CMD` set as above, start a new REPL:

```sh
clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:dev
```

Evaluate the `Point` definition above, then create and use arrays:

```clojure
(require '[value-type.array :as va])

;; Allocate three slots, each with the supplied initial value.
(def points (va/make-array Point 3 (->Point 0 0)))
(alength points)                         ;=> 3
(aset points 1 (->Point 4 5))
(str (aget points 1))                    ;=> "(4,5)"

;; Build an array from finite input, in input order.
(def path (va/into-array Point [(->Point 1 2) (->Point 3 4)]))
(mapv str path)                          ;=> ["(1,2)" "(3,4)"]

;; Empty input needs an explicit non-null seed; the seed adds no slot.
(alength (va/into-array Point (->Point 0 0) [])) ;=> 0

;; Inspect actual JVM properties, including whether storage is flat.
(select-keys (va/array-info points) [:null-restricted? :atomic?])
;=> {:null-restricted? true, :atomic? true}
(:flat? (va/array-info points))           ;=> true or false, depending on layout
```

These are mutable arrays of immutable value objects. Use ordinary array
operations such as `aget`, `aset` and `alength`. Stores of null or incompatible
values fail. Atomic element access does not make compound updates atomic or
remove the need for thread coordination.

Unlike non-nil fields, array construction has **no ordinary-JVM fallback**.
`into-array` buffers the input before allocation; it is not a streaming
constructor. `array-info` also accepts ordinary reference arrays. It needs
the export flag, but ordinary-array inspection does not need preview on the
tested EA3 build.

See the [API reference](doc/API.md) for all three functions and the
[usage guide](doc/usage.md#value-array-allocation) for validation and error
handling.

## Important behavior and limitations

- Value objects have no identity. Equality depends on their fields;
  identity-based operations such as locking are not supported.
- Mutable fields are not supported in either representation.
- Fallback uses ordinary `deftype` identity semantics. It does not emulate
  value equality. Code that permits fallback must work with both forms.
- Ahead-of-time compilation fixes representation and field enforcement.
  Value-class output requires a compatible preview-enabled JVM; it does not
  convert to fallback. Identity output retains its constructor checks.

The startup property `value-type.fallback` controls identity fallback, including
definitions with non-nil fields. Missing native field enforcement alone does not
trigger fallback, warnings or strict-policy rejection:

| Value | Behavior when fallback is needed |
|---|---|
| `silent` (default) | Use `deftype` without a diagnostic. |
| `warn` | Warn once per loaded library runtime, then use `deftype`. |
| `error` | Reject with `java.lang.Error`. |

Set the property before the library loads. For example, to reject fallback:

```sh
clojure -J-Dvalue-type.fallback=error -M:dev
```

See the [usage guide](doc/usage.md) for field comparison rules, fallback
policy, AOT limits and error handling.

## Performance

Value classes do not guarantee lower memory use or faster execution. In the
recorded Valhalla tests, standalone `defvaluetype` objects with 1–3 `long`
fields were 8 bytes larger than equivalent `deftype` objects. Empty objects
had equal shallow sizes. Method-call measurements found no stable
interface-call penalty. These results apply only to the tested JVM and
workloads; they do not measure flattened arrays or total application memory.

See the [method-call investigation](ramora/defvaluetype-design/interface-call.md)
and [object-size comparison](ramora/defvaluetype-design/object-size.md) for
methods, results and limits. Measure your own workload before choosing a
representation for performance.

Separate [typed-array measurements](ramora/defvaluetype-design/typed-array.md)
found storage and traversal benefits for flattened arrays of single-`long`
values on the tested JVM. These are workload-specific results, not a promise
for other value types or layouts. The [array benchmark](bench/typed-array/README.md)
uses the public allocator; the retained measurements used the internal JVM
allocator directly and do not measure `into-array` construction.

## Documentation

- [API reference](doc/API.md): macro syntax and array function contracts.
- [Usage guide](doc/usage.md): detailed behavior and limitations.
- [Development guide](doc/development.md): tools, quality checks, tests,
  builds and benchmarks.

## AI usage

AI has been used to write and review code, tests and documentation for
this project. Automated tests and AI review do not guarantee
correctness. Evaluate the library for your intended use.

## License

[Eclipse Public License 2.0](LICENSE) (EPL-2.0). The full license text
is included in the source checkout and the built JAR.
