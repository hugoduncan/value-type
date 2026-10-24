# Benchmark

The method-call benchmark compares `defvaluetype` with `deftype` with
the same body. It gives evidence for the success criterion in
[Purpose](purpose.md): "Method call performance is equal to a
`deftype` with the same body." It is manual and not a gate (D11).
Code: `bench/value_type/bench.clj` (`value-type.bench`).

Follow-up: the [interface-call investigation](interface-call.md) on 2026-10-24
found no stable slowdown in 16 fresh JVMs. Java controls behaved similarly;
the inspected paired C2 loops had identical instructions. The results below
remain the historical 2026-10-20 measurements, not a general penalty claim.

## Environment

- JDK: `java.vm.version` `27-jep401ea3+1-1` (Valhalla early access).
- OS: macOS 27.0.1, aarch64. CPU: Apple M5 Max.
- Clojure 1.12.6. criterium `org.hugoduncan/criterium` `0.5.245-ALPHA`.
- JVM options (alias `:bench`): `--enable-preview`,
  `-XX:+UnlockExperimentalVMOptions`,
  `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`.
- criterium `:limit-time-s` 10 for each measurement.
- Date: 2026-10-20.

## Command

```sh
JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -M:bench -m value-type.bench
```

## Method

Each case runs one pass over an `Object` array of `n` = 1024
instances (element `i`: `x = i`, `y = 2i`) and adds a value for each
element to a `long` sum. Each (case, kind) has its own pass function
and its own arrays. Only `identical` reads a second array (`e2`). The time per call is the median time of one pass
divided by `n`. Ratio = `defvaluetype` / `deftype`. Criterion cases
(`interface`, `protocol`) call the same method body. Information
cases (`field`, `identical`, `allocate`) show related costs.

## Results

| Case | Kind | deftype (ns/call) | defvaluetype (ns/call) | Ratio |
|---|---|---:|---:|---:|
| `interface` | criterion | 0.454 | 0.520 | 1.14 |
| `protocol` | criterion | 0.550 | 0.550 | 1.00 |
| `field` | information | 0.377 | 0.319 | 0.85 |
| `identical` | information | 0.519 | 3.927 | 7.57 |
| `allocate` | information | 0.318 | 0.376 | 1.18 |

```clojure
{:n 1024,
 :results
 [{:case :interface,
   :criterion? true,
   :deftype-ns 0.4543863932291667,
   :defvaluetype-ns 0.5197088068181818,
   :ratio 1.143759616402224}
  {:case :protocol,
   :criterion? true,
   :deftype-ns 0.5503700657894737,
   :defvaluetype-ns 0.5504214638157895,
   :ratio 1.000093388121031}
  {:case :field,
   :criterion? false,
   :deftype-ns 0.37670158617424243,
   :defvaluetype-ns 0.3194091796875,
   :ratio 0.8479103656860049}
  {:case :identical,
   :criterion? false,
   :deftype-ns 0.518798828125,
   :defvaluetype-ns 3.9267578125,
   :ratio 7.568941176470588}
  {:case :allocate,
   :criterion? false,
   :deftype-ns 0.31812263257575757,
   :defvaluetype-ns 0.3760653409090909,
   :ratio 1.182139534883721}]}
```

## Classification

Bounds: "equal" when the ratio is in `[0.9, 1.1]`, "faster" below,
"slower" above.

- `interface`: 1.14, slower.
- `protocol`: 1.00, equal.

## Interpretation

- Success criterion: in this run, `interface` (primitive `long`
  return) is "slower" (1.14) and `protocol` (inline protocol method,
  boxed return) is equal (1.00). Thus A-21 is `contested`; A-28
  records the ratios.
- The fold-check run (2048) gives `interface` 1.05 (equal), compared
  with 1.14 at 1024. The `field` ratios are 0.85 and 0.88; the
  `allocate` ratios are 1.18 and 0.90, respectively. These two runs
  use different array lengths. They do not establish a run-to-run
  variance estimate or show that either classification is stable.
- Escape analysis could explain the similar absolute times for
  `field` and `allocate`. No generated code was inspected; these
  measurements do not establish that the cases compile to the same
  work for both kinds.
- `identical` is about 7.6 times slower for `defvaluetype`. For a
  value class, `identical?` (`acmp`) compares the field values; for
  `deftype` it compares references. This is the known semantic
  difference (see [Qualities](qualities.md)), not a method call cost.
- Limits: one machine, one JDK build, an alpha tool (the median path
  in `last-bench` is internal), the boxed return value of the
  `protocol` case, reference arrays only (no flattened arrays). The
  times are below 1 ns per call, so small absolute differences give
  visible ratio changes.
- A single captured instance gives a folded call for `defvaluetype`
  (about 0.12 ns against 3.9 ns for `deftype`): the JIT treats the
  trusted final fields as constants. Thus each case uses many
  instances, and the fold check below shows that the work is done.

## Fold check

`clojure -M:bench -m value-type.bench 2048`. Pass time at 2048 /
pass time at 1024 (must be in `[1.5, 2.5]`):

- `interface`: `deftype` 2.18, `defvaluetype` 2.00.
- `protocol`: `deftype` 2.01, `defvaluetype` 2.17.

## Validity

These results are valid only for the environment above. Run the
benchmark again on each new early-access build.
