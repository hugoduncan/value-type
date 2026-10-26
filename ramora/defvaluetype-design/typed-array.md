# Typed value arrays

## Finding

Flattening provides a measured storage and allocation benefit, but an
ordinary typed value array does not automatically get it on this build.

Test environment: Valhalla `27-jep401ea3+1-1`, macOS `27.0.1`, aarch64,
Clojure `1.12.6`, Criterium `0.5.245-ALPHA`. Objects contain one primitive
`long`, set to the element index. Compressed references are 4 bytes;
object alignment is 8 bytes. The JVM uses `-Xms512m -Xmx2g`.

For 1,048,576 elements:

| Case | Flat? | Retained bytes | Approximate MiB | Allocation bytes/element |
| --- | --- | ---: | ---: | ---: |
| Ordinary typed `deftype` array | No | 29,360,144 | 28 | 28 |
| Ordinary typed `defvaluetype` array | No | 37,748,752 | 36 | 36 |
| Null-restricted atomic value array | Yes | 8,388,624 | 8 | 8 |

Each total includes a 16-byte array header. Allocation is the measured
current-thread allocation per complete array build, divided by element
count; the displayed values omit tiny per-build overhead.

Compared with the identity array, the flat array uses **71.4% less retained
storage** and about **71.4% fewer allocated bytes**. Compared with the
ordinary value array, the reduction is **77.8%**. This does not measure
GC pauses or construction time.

## Traversal timing

Six successful extended JVM runs used a 60-second Criterium limit per
case, with three rotated case orders per VM configuration. They reported
no limited-warmup or insufficient-sample warnings. At 1,048,576 elements:

| Case | Median ns/element, defaults | Range across three runs | Median ns/element, flattening off |
| --- | ---: | ---: | ---: |
| Identity array | 0.305 | 0.298–0.312 | 0.302 |
| Ordinary value array | 0.360 | 0.359–0.362 | 0.360 |
| Restricted value array | 0.239 | 0.239–0.242 | 0.359 |

The flattened case took about **22% less traversal time** than the identity
array and **34% less** than the ordinary value array, using ratios of the
run medians. Disabling flattening removed that advantage. These are
observed results for this repeated sequential scan, not confidence bounds
or a general value-class speedup.

An early extended trial wrote its report but failed afterward because the
script was edited while the JVM was still reading it. Its result was
excluded. Both rotation-zero configurations were repeated successfully
from unchanged source; only the successful replacements are published.

## The important API limit

The flat case uses the experimental internal API
`jdk.internal.value.ValueClass/newNullRestrictedAtomicArray`. It requires
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` and rejects null
array elements. It is not an API supplied by this library or supported by
Java SE. The ordinary arrays use Clojure `make-array` with the generated
class as the component type.

Actual flattening is checked with `ValueClass/isFlatArray`, not inferred
from an array class name. Null restriction, atomicity and component value
status are also recorded. With `-XX:-UseArrayFlattening`, the same restricted
array construction remains null-restricted but becomes non-flat. Its
storage and allocation rise from about 8 to 36 bytes per element.

A separate eligibility check found that the existing two-long value class
did not flatten in either the ordinary or the internal restricted array
forms. Thus the one-long result does not establish that wider classes get
the same benefit. The macro was not changed to enable other layouts.

## Measurement method

- Instrumentation measures array shallow size. For a non-flat array,
  add each element's shallow size. Each element has a distinct long value
  and no reference fields. For a flat array, its payload is already inside
  the measured array size; no element materialization is added.
- `ThreadMXBean` measures allocation on the constructing thread across
  30 builds after 30 warmup builds. A volatile sink retains each completed
  array in turn so it escapes. This counts temporary allocation too.
- Traversal sums all fields. Each case has a separate loop and access site.
  Sums are checked before and after timing. Criterium's compiler blackhole
  consumes the result. Batch medians are divided by batch size and length.
- Three fresh JVMs per configuration rotate case order. Timing JVMs run
  sequentially. Short screening runs use two lengths, 65,536 and 1,048,576;
  the longer timing follow-up uses 1,048,576.

Storage and allocation agreed across all screening runs and both lengths.
The flattening-disabled control removed the storage advantage. The short
runs also showed lower traversal times for the flat case, but some reported
insufficient samples and limited warmup. Do not use those short runs alone
for a precise timing claim.

## Reproduction and evidence

See [probe instructions](../../spike/typed-array/README.md).
`spike/typed-array/summarize.clj` validates each report's sums, layout flags,
size totals and allocation bounds. Production code is unchanged.

Retained evidence is under [typed-array](typed-array/):

- `screen/`: all 12 six-second screening runs, logs, summary and VM settings.
- `extended/`: longer timing follow-up and summary.

The result is for a repeated sequential scan of a small immutable payload.
It does not measure random access, concurrency, generic persistent
collections, reference-field payloads or application-wide performance.
The instrumentation agent is present during timing in all cases. No
assembly inspection or hardware cache-counter measurement was done.
