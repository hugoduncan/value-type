# value-type

A deftype like facility to allow creation of [JEP 401: Value
Objects](https://openjdk.org/jeps/401) in
Clojure.

Defines a `defvaluetype` macro.  The macro is as compatible with
`deftype` as possible.

See the generated [API reference](doc/API.md) for the macro contract.
Regenerate it with `bb api-docs`; check it with `bb api-docs --check`.

## Usage

### Dependency

The project has no release. Add a local dependency to `deps.edn`.
`<value-type-dir>` is the directory of a checkout of this repository.

```clojure
{:deps {org.hugoduncan/value-type {:local/root "<value-type-dir>"}}}
```

### JDK requirement

For value classes, use a JDK with JEP 401 (the Valhalla
early-access build, see "Development"). Start the JVM with
`--enable-preview`, for example in an alias:

```clojure
{:aliases {:dev {:jvm-opts ["--enable-preview"]}}}
```

### Fallback policy

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

### AOT limits

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

### Example

Change `deftype` to `defvaluetype`. The syntax, the options and the
specs are the same. This example assumes supported value classes with
preview enabled. On fallback, the two instances are not identical, and
`Class.isValue` can be absent.

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

## Development

### Install development tools

Run commands from the repository root. Install Git, curl, CA certificates,
unzip, xz and Python 3 with venv support. On Ubuntu 24.04, also install
`build-essential`. Allow network access to tool releases, Maven repositories,
GitHub and Vale style packages for the first run.

Install mise 2026.4.11 using the [mise installer](https://mise.jdx.dev/installing-mise.html):

```sh
curl https://mise.run | MISE_VERSION=v2026.4.11 sh
export PATH="$HOME/.local/bin:$PATH"
mise trust
mise install
```

Review `mise.toml` before you trust the checkout. It pins Babashka, Clojure
CLI, clj-kondo, cljfmt, Vale, pre-commit and Temurin Java 21. Use
`mise exec -- COMMAND` to select these tools, or activate mise in your shell
as described in its installation guide. The commands below assume activation;
without it, prefix each command with `mise exec --`.

Java 21 is for development tools, not a runtime minimum. Unset `JAVA_CMD`
when running quality checks so a previous runtime-test selection does not
replace the tooling JVM. Library tests need the separate Valhalla setup
below. Tool dependencies are development-only aliases, not runtime dependencies.
Quickdoc v0.2.6 is pinned by commit in `deps.edn`; the proselint style package
is pinned to v0.1.0 in `.vale.ini`.

### Quality checks

Prepare dependency lint configuration, then run the checks:

```sh
unset JAVA_CMD
bb clj-kondo:copy-config
bb clj-fmt:check
bb clj-kondo:lint
bb vale
bb api-docs --check
clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc
```

Lint also prepares dependency configuration automatically. Each alias basis
is resolved separately. Tooling uses project configuration and reproducible
Clojure dependency resolution; no global lint configuration or local Scry
checkout is needed. Downloads and imports are ignored by Git.

| Command | Purpose |
| --- | --- |
| `bb clj-fmt check [PATHS...]` | Check explicit paths, or the project scope if omitted. |
| `bb clj-fmt fix [PATHS...]` | Format those paths in place. |
| `bb clj-fmt:check` | Check the project scope without edits. |
| `bb clj-fmt:fix` | Format the project scope in place. |
| `bb clj-kondo:copy-config` | Download dependencies and import their lint configuration. |
| `bb clj-kondo:lint` | Fail on code warnings and errors. |
| `bb vale` | Sync styles, report suggestions, and fail on prose warnings and errors. |
| `bb api-docs` | Regenerate the committed `doc/API.md`. |
| `bb api-docs --check` | Fail on missing or stale API output without writing it. |

Formatting and code lint cover `src/`, `test/`, `test-build/`,
`test-fallback/`, `bench/`, `bb/`, `test-quickdoc/`, `.clj-kondo/hooks/`,
`build.clj`, `bb.edn` and `deps.edn`. They exclude historical `spike/`
code, generated output, caches and dependency imports.

Vale checks only `README.md`, prose in `AGENTS.md` and `doc/API.md`.
Code blocks and lambda instruction lines are protected. Task records,
knowledge records and design evidence are outside the prose gate.
Fix generated API prose in source docstrings or `bb/value_type/api_docs.clj`,
then regenerate it; do not edit `doc/API.md` directly. Generation is
deterministic. The focused tests check API content as well as freshness,
real file operations and command failures. Test failures produce EDN details
in `.scry-results/` and a nonzero exit.

GitHub Actions runs the five quality checks above on pull requests and
pushes to `master`. It prepares dependencies from the pinned setup and
checks that tracked files remain unchanged. It does not repair files or
run the runtime-test matrix. Setup and tool errors fail the checks.

### Install commit hooks

```sh
pre-commit install
pre-commit run --all-files
```

Keep mise tools available on the Git process PATH, including for editor
commits. Alternatively, run commits with `mise exec -- git commit`.
The local hooks format selected maintained Clojure/EDN files and run
project lint. Changes to owned lint configuration also trigger lint.
The formatter **can edit files**. A formatting change stops the commit:
review the diff, stage the intended changes yourself, then retry. Hooks
never run `git add` or stage unrelated edits. pre-commit temporarily saves
unstaged edits; conflicting formatter changes are rolled back rather than
merged into the index. Vale and API checks run separately, not in these hooks.

Real-process integration probes run in disposable Git checkouts:

```sh
bash test-tooling/hooks.sh
bash test-tooling/ci-failures.sh
```

These check controlled failures and file/index safety with real tools, not
mocks. They need the same installed tools and dependency access.

### JDK requirement

The value-path tests need a JDK with JEP 401 value classes: the Valhalla
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

### Verified fallback matrix

Tests use Clojure 1.12.6 with Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1`, with preview disabled and enabled. This is tested
coverage, not a new minimum JDK or a claim about future EA formats.
Run the separate fallback suite without inheriting the value-only alias:

```sh
JAVA_CMD=<java8-home>/bin/java clojure -M:fallback-test
JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -M:fallback-test
JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -J--enable-preview -M:fallback-test
```

Cross-JVM tests also need both executables. Set `VALUE_TYPE_BASELINE_JAVA`
and `VALUE_TYPE_VALHALLA_JAVA` to their full executable paths when they are
not at the local defaults recorded in the task 009 plan. Build tests use
both JVMs for isolated JAR consumers. Fallback tests use real processes,
including source-free AOT consumers, and the same Scry exit/failure rules.

### Build a local JAR

Run these commands from the repository root with the Clojure CLI and
Git installed. Dependency downloads need repository access unless they
are cached.

```sh
clojure -T:build jar
clojure -T:build clean
```

**Both commands remove all existing `target/` output**, including
`target/object-size/` benchmark results. Copy any results you need to
keep outside `target/` first. `jar` cleans before packaging; `clean`
only removes generated output and also succeeds when `target/` is absent.

The artifact is `target/value-type-0.1.N.jar`, with Maven coordinates
`org.hugoduncan/value-type`. `N` is `git rev-list --count HEAD`.
The `build/jar` function returns
`{:jar-file "target/value-type-0.1.N.jar" :version "0.1.N"}`.
The JAR command needs Git and usable checkout history; failed Git
commands or invalid counts cause an error, with no fallback version.
The clean command does not need Git metadata.

A shallow checkout counts only available history. Different branches
can have the same count, so this version is not a unique release ID.
This command creates a local artifact; it does not install or publish
it. The project has no published release.

The source-only JAR contains the library sources, Maven dependency and
project metadata, and the full EPL-2.0 license. It contains no compiled
classes, bundled dependencies, tests, benchmarks or build code. Packaging
does not load or AOT-compile the library. Consumers still need the declared
runtime dependencies. Value classes also need a supported JEP 401 Valhalla
JDK with `--enable-preview`; other supported Clojure JVMs use the fallback
policy.

### Run the build tests

Select the supported Valhalla JDK with `JAVA_CMD` as above. These checks
also need Git, the Clojure CLI and access to uncached dependencies.

```sh
clojure -M:build-test -m scry.cli --dir test-build
```

This separate suite checks the build contract in temporary projects and
runs an isolated consumer with only the built JAR and runtime dependency
JARs. It selects `test-build/`; it does not replace the library test
command above. Exit status and failure files follow the same Scry rules.

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

### Compare standalone object size

Use a Valhalla JDK with JEP 401 support (see "Select the JDK"). Run
from the repository root:

```sh
JAVA_CMD=<valhalla-jdk-home>/bin/java bash bench/object-size.sh
```

This compares shallow size for paired types with 0–3 primitive `long`
fields. The isolated `:object-size` alias enables preview and uses
Criterium `0.5.251-ALPHA`. The wrapper extracts its bundled native agent
and loads it at JVM startup. This route is verified on macOS/aarch64.

Results and raw evidence go to `target/object-size/results.edn` and
`raw.edn`; setup and measurement logs go to `stdout.log` and `stderr.log`
in that directory. Each run replaces these four files. On a nonzero
exit, inspect the logs. See the [size report](ramora/defvaluetype-design/object-size.md)
for the recorded results, environment and limits. This is a manual
measurement, not a performance gate.

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

## License

[Eclipse Public License 2.0](LICENSE) (EPL-2.0). The full license text
is included in the source checkout and the built JAR.
