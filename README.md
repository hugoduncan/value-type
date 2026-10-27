# value-type

Clojure's `deftype` creates objects with identity. value-type provides
`defvaluetype` to define [JEP 401 value objects](https://openjdk.org/jeps/401)
with familiar `deftype` syntax. It uses value classes on a supported
Valhalla JVM and ordinary `deftype` classes otherwise.

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

## Important behavior and limitations

- Value objects have no identity. Equality depends on their fields;
  identity-based operations such as locking are not supported.
- Mutable fields are not supported in either representation.
- Fallback uses ordinary `deftype` identity semantics. It does not emulate
  value equality. Code that permits fallback must work with both forms.
- Ahead-of-time compilation fixes the representation. Value-class output
  requires a compatible preview-enabled JVM; it does not convert to fallback.

The startup property `value-type.fallback` controls unsupported JVMs:

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
policy, AOT limits and error handling. For native null-restricted atomic
arrays with an explicit initial value, see
[value-array allocation](doc/usage.md#value-array-allocation). Allocation needs
an internal-package export in addition to preview; it has no ordinary-array
fallback.

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

## Documentation

- [API reference](doc/API.md): macro syntax and contract.
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
