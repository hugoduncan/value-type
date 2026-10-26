# Value-array semantic probes

Task 012 tests the internal JVM API directly. These probes do not implement
the proposed public array API. Run commands from the repository root.

## Prerequisites

Use Bash, the Clojure CLI, Babashka (`bb`), `shasum` and `unzip`.
The project dependencies must be available to `clojure -Spath`.
The primary JDK must include `java`, `javac`, `javap` and `lib/src.zip`.
The retained runs used Valhalla `27-jep401ea3+1-1` and ordinary Java
`1.8.0_492-b09` on macOS aarch64. These are build-specific tests, not a
portable JDK conformance suite.

```sh
export JDK="$HOME/.local/jdks/jdk-27.jdk/Contents/Home"
export JAVA8=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
"$JDK/bin/java" -version
"$JAVA8" -version
```

`JDK` is a directory; `JAVA8` is an executable. Do not add preview or exports
through global Java option variables. The runners select flags per process.
They use the project classpath without the `:test` or `:bench` aliases.

## Reproduce and validate

Choose output directories that do not exist. The matrix runner refuses reused
output to prevent stale files from counting as current observations.

```sh
bash spike/value-array-semantics/matrix.sh target/value-array-semantics/reproduce-1
bb spike/value-array-semantics/validate_matrix.clj target/value-array-semantics/reproduce-1
bash spike/value-array-semantics/matrix.sh target/value-array-semantics/reproduce-2
cmp target/value-array-semantics/reproduce-1/validation.edn target/value-array-semantics/reproduce-2/validation.edn
cmp target/value-array-semantics/reproduce-1/source-hashes.txt target/value-array-semantics/reproduce-2/source-hashes.txt
```

A successful runner and validator exit zero. Inspect `validation.edn` for
coverage and unresolved dependent cases; success does not mean those cases
were verified. Each run also checks that probe source hashes stayed unchanged.
Raw files contain process-specific paths and IDs, so do not require byte equality
of the complete output trees.

To validate the retained aggregate evidence without running JVM probes:

```sh
bb spike/value-array-semantics/validate_matrix.clj ramora/value-type-array-design/evidence/matrix-selected-slots
```

## Result format

Observation files use `:schema-version 1`, `:phase` and `:cases`. Environment
location depends on the writer:

- Discovery, startup and allocation files have a top-level `:environment`.
- Store and diagnostic files have no top-level `:environment`. Use the adjacent
  `allocation.edn`, written by the same JVM process. This also applies to an
  isolated query's `result.edn` when the query returns.
- `isolated.edn` has an `:environment` on each case, copied from that query's
  `allocation.edn` in its `{input}-{query}/` directory. Each case also records
  the command and exit status; fatal cases link to the fatal log.

For example, in the [retained selected-slot matrix](../../ramora/value-type-array-design/evidence/matrix-selected-slots/),
`semantics/default/stores/stores.edn` uses `stores/allocation.edn` under the
same layout. `semantics/default/diagnostics/diagnostics.edn` uses the adjacent
`allocation.edn`; `diagnostics/isolated.edn` uses each query subdirectory's
`allocation.edn`. The disabled layout has the same structure.

Each case has a stable namespaced `:id`, `:operation` and `:status`.
Tested cases include `:inputs`; unresolved startup cases can omit it.
`:pass` records a returned `:value`, including false or nil. `:rejection`
records exception chains in `:causes`. `:unresolved` records a dependency
or separate suite that was not tested in that process, with a `:reason`.
Isolated process records also distinguish `:fatal`.
These are observations; validators separately check coverage and expectations.

## Matrix and outputs

`startup/` covers preview on/off crossed with exports present/absent, plus
Java 8 without Valhalla flags. Discovery, strict value-class creation and
allocation are separate observations. Failed prerequisites leave dependent
cases unresolved, not native allocation rejections.

`semantics/default/` and `semantics/disabled/` contain allocation, store and
diagnostic cases. Both use preview and exports. The second adds
`-XX:-UseArrayFlattening`. Each layout checks 59 allocation observations,
120 Clojure/Java selected-slot store observations, 69 array queries and six isolated invalid
queries. The diagnostic process also repeats allocation checks.
`semantics/comparison.log` records the comparison. Exception messages can
change with layout; exception classes and contents must agree.

Each process records its exact command, exit status and raw log. EDN records
include environment and observations. Startup also retains Java versions,
JDK release data, signatures and bundled Java source. Source hashes identify
the probe inputs. Generated helper classes are disposable and are not part
of the retained evidence.

## Expected failures and safety

**Null diagnostic inputs deliberately crash fresh EA3 JVMs.** All three
queries produce SIGSEGV with exit 134 in both layouts. The runner isolates
each call and retains `hs_err.log`, command, exit and output. Shell abort
messages are expected. Never move these calls into the shared semantic JVM.
The validator requires fatal evidence; it does not treat a missing result as
false or as a Java exception. These runs may also trigger OS crash reporting.

API absence, denied module access, rejected seeds and rejected stores are
recorded separately. An unexpected exit or missing observation fails validation.
Unsupported flags on another build are not a successful matrix result.
Do not weaken the checks to make another build pass.

The probes use bounded lengths (-1, 0, 1 and 3), not heap-exhaustion tests.
They establish sequential contents and store restrictions, not concurrent
compound-update safety, performance, or a synchronization protocol.

## References

- [Task and acceptance](../../munera/closed/012-verify-value-arrays/design.md)
- [Proposed array design](../../ramora/value-type-array-design.md)
- [Retained matrix summary](../../ramora/value-type-array-design/evidence/matrix-selected-slots/validation.edn)
- [Historical one-long measurements](../../ramora/defvaluetype-design/typed-array.md)

The selected-slot matrix replaces the earlier 60-case store coverage. Each
write uses a fresh array and one selected index. All slots are checked after
each accepted or rejected write. Earlier evidence remains in `evidence/matrix/`
for history; the current validator requires the new 120-case contract.

### Evidence validator regression checks

Run `bb spike/value-array-semantics/check_validation.clj` from the repository
root. The checks copy retained matrix evidence to temporary directories. They
require valid evidence to pass and reject changed diagnostics, required IDs,
startup allocation status, flags, runtime versions and isolated records.
The 117 checks include required allocator and diagnostic signatures, seed-free
overload rejection, unrelated-method acceptance, and unresolved and rejected
baseline allocation records, plus
substituted isolated-layout evidence and runtime, preview, export and layout
flag changes for each of the 12 isolated processes. Each check compares all
file hashes before and after validation, including failed validation.

`validate_matrix.clj` and `validate_isolated.clj` are read-only.
`record_isolated.clj` creates records during a new diagnostic run; it is not a
repair command for retained evidence. Fatal-log references in matrix isolated
records are relative to their diagnostic directory. Raw command captures retain
the original execution paths. Older evidence outside the matrix keeps its
historical record format; the read-only validator requires portable references.

### Helper-loading checks

`support/allocation.clj` defines fixtures and allocation helpers.
`support/discover.clj` defines Java 8-safe discovery helpers. Loading either
file does not run probes or write evidence. The entry points call
`run-probe!` with explicit output paths. Stores and diagnostics complete
allocation checks first; startup writes independent discovery evidence first.

Run `bash spike/value-array-semantics/check_helper_loading.sh` to check loading
with no CLI arguments and with a sentinel output path in six fresh JVMs.
The check requires no output files and no standard output from helper loading.
