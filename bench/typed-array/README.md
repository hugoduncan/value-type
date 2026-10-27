# Typed-array benchmark

Run from the repository root with Clojure CLI and the Valhalla EA3 JDK:

```sh
bash bench/typed-array/run.sh
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
  `value-type.array/make-array` with an explicit seed, then filled by index.

The public allocator uses an experimental internal JVM API. It requires
preview and `--add-exports`; it is not a supported Java SE API. These arrays
reject null elements. Native diagnostics remain independent of the public
allocator. The control configuration
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
  Public allocator validation and call overhead are included for the restricted
  case. This is not a construction-time or GC pause test. `into-array` is not
  measured.
- **Traversal:** sum all fields with a primitive loop, with separate access
  sites for each case. Check the sum before and after timing. Criterium
  `0.5.245-ALPHA` consumes results through its compiler blackhole. Divide
  elapsed batch median by batch size and array length.

The default Criterium time limit is 60 seconds per case. For an initial
screen, use `BENCH_SECONDS=6`; inspect warnings before drawing timing
conclusions. `SIZES` and `TRIALS` can select runs:

```sh
SIZES=1048576 TRIALS='0 1 2' bash bench/typed-array/run.sh target/typed-array-extended
```

Do not edit `run.clj` while a measurement JVM is reading it. A Clojure script
can continue reading forms after the timed work finishes.

## Validate and summarize

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  clojure -M bench/typed-array/summarize.clj \
  target/typed-array/results target/typed-array/summary.edn
```

The validator requires all three rotations per configuration and size. It
checks flattening, value status, sums, storage, allocation bounds and positive
timing results. Size expectations are specific to this recorded VM setup.
Reports identify the restricted allocator with `:restricted-allocator`.
The retained results linked from the [original report](../../ramora/defvaluetype-design/typed-array.md)
used the direct internal allocator, before this benchmark moved to the public API.
Do not treat those results as measurements of the public allocator.
No production code or normal test configuration is changed.
