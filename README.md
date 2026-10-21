# value-type

A deftype like facility to allow creation of [JEP 401: Value
Objects](https://openjdk.org/jeps/401) in
clojure.

Defines a `defvaluetype` macro.  The macro is as compatible with
`deftype` as possible.

## Usage

### Dependency

The project has no release. Add a local dependency to `deps.edn`.
`<value-type-dir>` is the directory of a checkout of this repository.

```clojure
{:deps {org.hugoduncan/value-type {:local/root "<value-type-dir>"}}}
```

### JDK requirement

The library needs a JDK with JEP 401 value classes (the Valhalla
early-access build, see "Development"). The JVM must run with
`--enable-preview`, for example in an alias:

```clojure
{:aliases {:dev {:jvm-opts ["--enable-preview"]}}}
```

### Example

Change `deftype` to `defvaluetype`. The syntax, the options and the
specs are the same.

```clojure
(ns example.point
  (:require [value-type.core :refer [defvaluetype]]))

(defvaluetype Point [^long x ^long y]
  Object
  (toString [_] (str "(" x "," y ")")))

(identical? (->Point 1 2) (->Point 1 2)) ;=> true
(.isValue Point)                          ;=> true
```

## Differences from `deftype`

Value objects have no identity. These differences come from the JVM:

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
- Errors are `ex-info` with the key `:value-type/error` and
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
    - The JVM has no value classes. Use a JEP 401 JDK with
      `--enable-preview`.
  - `:jvm-rejected`: the JVM rejected the class (for example no
    `--enable-preview`).
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
- AOT output uses the class file version of the compiling JDK with
  the preview minor version. It loads only on the same JDK feature
  release.

## Development

### JDK requirement

The library needs a JDK with JEP 401 value classes: the Valhalla
early-access build. Download it from https://jdk.java.net/valhalla/.
Other JDKs do not have value classes. On another JDK, the environment
test fails and names the cause.

### Select the JDK

The `clojure` script uses `JAVA_CMD` first. Set it to the `java` of
the Valhalla JDK. `<valhalla-jdk-home>` is the home directory of the
Valhalla JDK.

```sh
export JAVA_CMD=<valhalla-jdk-home>/bin/java
```

`JAVA_HOME` alone is not sufficient: the `clojure` script uses `java`
on `PATH` before `JAVA_HOME`.

### Run the tests

```sh
clojure -M:test -m scry.cli
```

The command exits 0 only when at least one test runs and all tests
pass. Failure details go to `.scry-results/`.

### Run the benchmark

The method-call benchmark compares `defvaluetype` with `deftype`. Set
`JAVA_CMD` to the Valhalla `java` (see "Select the JDK"), then:

```sh
clojure -M:bench -m value-type.bench
```

An optional argument sets the array length (default 1024). It must be
an integer from 1 to 2147483647, inclusive (the JVM array-length
range). Available memory can impose a lower limit. Invalid input is
rejected before measurement. The results are in
`ramora/defvaluetype-design/benchmark.md`.

### Start a REPL

```sh
clojure -M:test:nrepl
```

The REPL has `--enable-preview` and the test path. Run the tests in
the REPL with:

```clojure
(do (require 'scry.core)
    (scry.core/run {:namespaces ['value-type.environment-test]}))
```
