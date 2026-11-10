# Array adapter measurement comparison

## Reproduction

Reflective revision: `0472b9fae6b444627eec37db12d6bcab16050abc`.
Direct revision: `5143b4feb20a05f0dc71ab13b09b56775d851fb6`.
Both runs used the same machine, classpath, Clojure 1.12.6, EA3
`27-jep401ea3+1-1`, preview/export flags and measurement script digest
`b1236dd66cd4f88a92df9b1d5cf35ff5d4e9331e92cce7359cc9609246906d0b`.
The environment files agree on these inputs. Host: macOS ARM64, Darwin 27.

Run from the repository root:

```sh
VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  bb dev/bench/array-adapter/run.clj \
  munera/open/029-lazy-direct-call-array-adapter/evidence/direct
```

Exit 0. Each revision has 145 validated records from 15 fresh children:
15 setup records, 10 first-use records, 20 warmup records and 100 measured
warmed samples. Each warmed case has five forks, one second warmup per fork,
and five 200-ms samples per fork. Children have a 120-second bound.
`reflective/` and `direct/` retain commands, tool versions, environment,
raw samples, GC observations and summary EDN.

## Results

Values are median (minimum–maximum). Warmed values are ns/operation;
first-use and setup values are milliseconds.

| Case | Reflective | Direct |
| --- | --- | --- |
| Warm allocation, length 0 | 98.09 (88.86–135.39) | 60.67 (59.12–64.33) |
| Warm allocation, length 4 | 123.51 (119.33–146.38) | 91.64 (90.15–94.13) |
| Warm ordinary inspection | 100.73 (88.89–122.42) | 43.12 (42.92–43.91) |
| Warm restricted inspection | 92.48 (90.74–119.91) | 42.75 (42.52–43.12) |
| First allocation | 1.229 (1.033–1.344) | 4.322 (3.918–5.280) |
| First inspection | 0.449 (0.402–0.492) | 4.275 (3.876–5.004) |
| Setup | 330.972 (322.587–390.954) | 329.419 (323.399–346.228) |

All direct warmed samples are below the corresponding reflective range.
First use consistently regresses: about 3.09 ms for allocation and 3.83 ms
for inspection at the medians. This is a startup cost, not a warmed regression.

## First-use investigation

The direct path must load and compile its optional source namespace after
access validation. The reflective path has no corresponding namespace load.
To test this explanation, ten additional fresh children loaded the applicable
adapter before running the unchanged measurement script. No discovery function
was forced. This is an investigation control, not a supported startup strategy:
eager loading would violate baseline-safe loading.

For each `mode` in `allocation inspection` and `fork` in `1 2 3 4 5`, use
`CP=$(clojure -Spath)` and this command from the repository root (output is
retained in `preloaded-control/$mode-$fork.log`):

```sh
$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  --enable-preview --add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -cp "$CP" clojure.main -e "
(require 'value-type.array 'value-type.core)
(let [start (System/nanoTime)]
  (require 'value-type.impl.array-$mode)
  (prn {:adapter :$mode :load-ns (- (System/nanoTime) start)}))
(binding [*command-line-args*
          [\"$mode\" \"5143b4feb20a05f0dc71ab13b09b56775d851fb6\" \"$fork\"
           \"munera/open/029-lazy-direct-call-array-adapter/evidence/preloaded-control/$mode-$fork.edn\"]]
  (load-file \"dev/bench/array-adapter/measure.clj\"))"
```

All ten children exited 0 within the 120-second shell-tool bound.
Adapter load times were 4.172–5.452 ms for allocation and 3.976–5.382 ms
for inspection. With that work outside the first-use interval, first allocation
was 0.562 ms (0.501–0.599) and first inspection was 0.277 ms (0.234–0.492).
These observations support lazy source compilation as the reason for the
repeatable first-use regression. They do not provide an additive cost breakdown:
preloading also changes compiler and class-loading state. Control setup excludes
public namespace loading and is not comparable with the main setup figures.
No production change is needed: this one-time cost follows the required lazy,
source-only boundary and does not breach a speed threshold in the design.

## Limits

Setup includes public namespace loading and fixture definition, not JVM startup.
First allocation excludes value-class discovery but includes array discovery and
its self-check. First inspection uses ordinary arrays without allocation discovery.
The volatile sink and per-operation clock add shared overhead. Samples are short,
case order is fixed, and baseline and direct runs were not interleaved. The ranges
are observations, not confidence intervals. These results apply only to this
host, build and workload; they are not general performance guarantees.
