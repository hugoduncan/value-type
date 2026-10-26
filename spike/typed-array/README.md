# Typed-array probe

Run from the repository root with Clojure CLI and the Valhalla EA3 JDK:

```sh
bash spike/typed-array/run.sh
```

The default JDK is `~/.local/jdks/jdk-27.jdk/Contents/Home`.
Override `JDK` if needed. Output defaults to `target/typed-array`;
pass another directory as the first argument.

The script runs sequential JVMs at 65,536 and 1,048,576 elements, with
three rotated case orders and two VM configurations. Each element contains
one primitive `long` with its array index as the value. Cases are:

- Ordinary typed `deftype` array.
- Ordinary typed `defvaluetype` array.
- The same value class in a null-restricted atomic array, created through
  `jdk.internal.value.ValueClass/newNullRestrictedAtomicArray`.

The internal API requires `--add-exports` and is not a supported Java SE or
library API. These arrays reject null elements. The control configuration
sets `-XX:-UseArrayFlattening` without changing the constructor or traversal.
The probe records actual flattening, atomicity and null restriction; a typed
array class name alone does not establish the physical representation.

## Measurements

- **Storage:** Java instrumentation measures array shallow size. For a
  non-flat array, sum the shallow sizes of its elements as well. The loop
  creates a distinct field value per element; elements contain no references.
  For a flat array, array size already includes the element payloads, so
  element reads are not added as separate allocations.
- **Allocation:** after 30 warmup builds, use the current thread's
  `ThreadMXBean` allocation counter across 30 builds. Publish each built
  array to a volatile sink so it escapes. Report bytes per build, not time.
  Small constructor/call overhead is included. This is not a GC pause test.
- **Traversal:** sum all fields with a primitive loop, with separate access
  sites for each case. Check the sum before and after timing. Criterium
  `0.5.245-ALPHA` consumes results through its compiler blackhole. Divide
  elapsed batch median by batch size and array length.

The default Criterium time limit is 60 seconds per case. For an initial
screen, use `BENCH_SECONDS=6`; inspect warnings before drawing timing
conclusions. `SIZES` and `TRIALS` can select runs:

```sh
SIZES=1048576 TRIALS='0 1 2' bash spike/typed-array/run.sh target/typed-array-extended
```

Do not edit `run.clj` while a measurement JVM is reading it. A Clojure script
can continue reading forms after the timed work finishes.

## Validate and summarize

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  clojure -M spike/typed-array/summarize.clj \
  target/typed-array/results target/typed-array/summary.edn
```

The validator requires all three rotations per configuration and size. It
checks flattening, value status, sums, storage, allocation bounds and positive
timing results. Size expectations are specific to this recorded VM setup.
No production code or normal test configuration is changed.
