# Standalone object size

## Result

On this Valhalla build, empty `deftype` and `defvaluetype` objects have
equal shallow sizes. With 1–3 primitive `long` fields, each value
object uses 8 more bytes than its identity-object counterpart.
There is no size saving in these cases.

| Primitive `long` fields | `deftype` bytes | `defvaluetype` bytes | Difference (value − identity) |
| ---: | ---: | ---: | ---: |
| 0 | 16 | 16 | 0 |
| 1 | 24 | 32 | +8 |
| 2 | 32 | 40 | +8 |
| 3 | 40 | 48 | +8 |

All ten target records in each of the eight cases had the same size.
None was marked freed. These results apply only to materialized,
standalone objects with this field layout and VM setup. They do not
measure flattened arrays, retained heap size, allocation rate or
method-call time. The [historical timing report](benchmark.md) remains
separate and uses a different Criterium release.

## Reproduce

Run from the repository root with the Valhalla JDK and Clojure CLI.
The exact command for the preserved run was:

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java bash bench/object-size.sh
```

Change `JAVA_CMD` to your Valhalla `java` path if necessary. The isolated
`:object-size` alias uses the published Clojars artifact
`org.hugoduncan/criterium` `0.5.251-ALPHA`, not a local build. It does
not change the `:bench` alias.

The wrapper calls `criterium.agent/extract-agent!` in a setup JVM, then
loads the bundled native agent in a second JVM with
`-agentpath:<extracted-absolute-path>`. It checks attachment before
measurement. No runtime attachment or custom instrumentation agent is
used. Native support was verified only on macOS/aarch64.

The command writes `results.edn`, `raw.edn`, `stdout.log` and
`stderr.log` under `target/object-size/`. Both JVMs write to the logs,
not the terminal. Each run removes these four old files first. A
failure exits nonzero; inspect the logs and any raw evidence. Missing
or inconsistent measurements are errors, not zero-byte results.

## Environment

Preserved run timestamp: `2026-10-23T23:13:22.658960Z`.

| Setting | Recorded value |
| --- | --- |
| JDK runtime / VM build | `27-jep401ea3+1-1` |
| Java version | `27-jep401ea3` |
| Vendor | Oracle Corporation |
| VM | OpenJDK 64-Bit Server VM |
| OS | Mac OS X `27.0.1` |
| Architecture | `aarch64` |
| Clojure | `1.12.6` |
| Criterium | `0.5.251-ALPHA` |
| `ObjectAlignmentInBytes` | `8` (default) |
| `UseCompressedOops` | `true` (ergonomic) |
| `UseCompressedClassPointers` | `true` (default) |
| `UseCompactObjectHeaders` | `false` (default) |

All four queried VM settings were available. The physical header and
field offsets were not measured and remain unknown.

The measurement JVM arguments were `-XX:-OmitStackTraceInFastThrow`,
`--enable-preview`, `-agentpath:<extracted-absolute-path>` and
`-Dclojure.basis=.cpcache/4149937437.basis`. The full agent path is
preserved in the EDN metadata. The bundled `macos-arm64` agent SHA-256 is:

```text
fbf14546dcbb868c1ab0ca8c61b290bb60bdc2c3d6fe75bd0aa3f7d7b9435897
```

## Method and limits

The paired declarations in `bench/value_type/object_size.clj` differ
only in macro and type name. Fields are `f0`, `f1`, `f2`, in that
order, all primitive `long`. Sample index `i` (0–9) supplies
`long(i + field-index)` to both kinds. Empty types have no fields.
No measurement fields, protocols or interfaces are added.

Reflection checks all non-static fields by name and type.
`Class.isValue` is false for `value_type.object_size.Identity0` through
`Identity3`, and true for `value_type.object_size.Value0` through
`Value3`. The results identify each exact class, field map and values.

Each case traces ten constructor calls with
`criterium.agent/with-allocation-tracing`. A vector retains the objects
through record collection; `Reference.reachabilityFence` follows
inspection. Selection uses the exact class name and allocating thread
(thread 3 in this run). The validator requires ten non-freed target
records with one positive integer `:object_size`. Raw evidence includes
unrelated allocations as well as targets.

Criterium reports per-object bytes from native allocation tracing.
JVMTI [SampledObjectAlloc.size](https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html#SampledObjectAlloc)
refers to `GetObjectSize` semantics: an implementation-specific storage
approximation. Here this is shallow object size, including the header
and alignment padding reported by the VM. It excludes class metadata,
referenced objects and the retaining vector. It is not a portable
byte-level layout specification.

Adding each `long` after the first increases both sizes by 8 bytes.
The nonempty value objects have an additional 8-byte reported size
relative to the matching identity objects. This establishes a size
difference, not its internal cause. The measurements do not identify
header contents or field offsets, so they cannot assign that difference
to a particular header field or padding rule. Do not generalize to
other field types, VM builds or header settings.

## Preserved evidence

- [Validated results and metadata](object-size/results.edn): eight
  cases and four ordered rows, including signed differences.
- [Complete raw records and metadata](object-size/raw.edn): written
  before validation.
- [Standard output](object-size/stdout.log).
- [Standard error](object-size/stderr.log).

These files are from the implementation run, not the planning probe.
Pure validation tests and real-class layout tests run without an
attached native agent:

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  clojure -M:test:object-size -m scry.cli --namespace value-type.object-size-test
```
