# value-type

A deftype like facility to allow creation of (JEP 401: Value
Objects)[https://openjdk.org/jeps/401?utm_source=chatgpt.com] in
clojure.

Defines a `defvaluetype` macro.  The macro is as compatible with
`deftype` as possible.

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
