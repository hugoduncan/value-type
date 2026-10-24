# Interface-call investigation

## Result

The earlier 1.14 interface ratio did not recur. Repeated measurements and
Java controls do not show a stable value-class penalty in this workload.
The inspected C2 loops contain no interface dispatch: the JVM inlined both
receiver methods. Within each language pair, the main loop instruction bytes
are identical.

This does not prove equal performance for all interface calls. It does not
identify the cause of the original measurement. That run has no JIT trace.

## Environment and method

Measured 2026-10-24 UTC on Apple M5 Max, macOS `27.0.1`, Valhalla
`27-jep401ea3+1-1`, Clojure 1.12.6, Criterium `0.5.245-ALPHA`.
Production baseline: `f3a5b1b`. No production or existing benchmark code changed.
JVM options match `:bench`: preview, experimental options, compiler blackhole.

Source: `spike/interface-call/`. Run from the repository root:

```sh
bash spike/interface-call/run.sh
bash spike/interface-call/diagnose.sh
python3 spike/interface-call/extract-jit.py
```

The timing matrix has four fresh JVMs for each language and array length:
16 JVMs total, run sequentially. Odd trials measure identity first; even
trials measure value first. Language order also alternates. Each JVM measures
both kinds once. Criterium uses its normal JIT warmup, median batch time,
and a 10-second limit, not a fixed 10-second measurement duration.

The Clojure cases reuse the original pass functions and instance builder.
The Java control uses `javac` identity and value classes, each with two
`long` fields, a primitive-return interface method and checked addition.
Both use `Object[]`, not flattened arrays. Separate pass methods keep receiver
profiles separate. The control is Java-only for the types and loop; the
measurement driver is still Clojure/Criterium. The driver verifies value-class
status and the expected sums before timing.

## Fresh-JVM results

Ratio = value time / identity time. Each row summarizes four ratios, not
pooled timing samples. These ranges are observed ranges, not confidence intervals.

| Implementation | Elements | Trial ratios, in order | Median ratio | Range |
|---|---:|---|---:|---|
| Clojure | 1024 | 1.068, 1.012, 0.960, 1.043 | 1.028 | 0.960–1.068 |
| Clojure | 2048 | 1.053, 0.964, 1.053, 0.961 | 1.008 | 0.961–1.053 |
| Java | 1024 | 1.044, 0.983, 0.993, 1.038 | 1.015 | 0.983–1.044 |
| Java | 2048 | 1.024, 0.972, 1.047, 0.958 | 0.998 | 0.958–1.047 |

All ratios fall within the existing `[0.9, 1.1]` classification band.
At 2048, both languages favor whichever kind runs first. The 1024 results
are mixed. This is evidence of measurement-order sensitivity, not proof of
its physical cause. CPU placement, frequency and thermal state were not controlled.

The unchanged full benchmark also ran once per length in separate JVMs:

| Elements | Identity interface ns/element | Value interface ns/element | Interface ratio | Protocol ratio |
|---|---:|---:|---:|---:|
| 1024 | 0.444 | 0.434 | 0.977 | 1.008 |
| 2048 | 0.488 | 0.521 | 1.068 | 1.009 |

Interface pass-time scaling was 2.20 for identity and 2.40 for value;
protocol scaling was approximately 2.00 for both. These pass the existing
`[1.5, 2.5]` fold check. They do not establish exact linear scaling.

## JIT evidence

Two separate diagnostic JVMs used `LogCompilation` and targeted
`CompileCommand=print`. Their timings are excluded from the matrix above.
Both identity and value `norm` methods are marked `inline (hot)` in C2
compilation tasks. Both languages compile both pass methods at level 4.

The JDK has no `hsdis`. The extraction script preserves the emitted main-code
bytes in a temporary Mach-O object and disassembles them with Apple LLVM
`objdump`. Addresses in that output are relative to the extracted code;
use the original assembly log for relocation and runtime-call annotations.

- Clojure: each final non-OSR C2 pass has 824 main-code bytes. Offsets
  `0xd0`–`0x184` are identical: 46 instructions, including the unrolled loop
  and loop-limit handling. Field loads, type checks and checked additions
  match. No interface dispatch or value-only check occurs in this loop.
- Java: each final non-OSR C2 pass has 728 main-code bytes. Offsets
  `0xcc`–`0x178` are identical: 44 instructions.
- Outside these loops, paired listings differ in class metadata constants,
  relocated runtime branches and entry-barrier data. They are not fully
  byte-identical methods.

These are standalone pass compilations from diagnostic runs. We did not
inspect every caller compilation or every timing JVM. Equal instruction
sequences also do not imply equal cache behavior or object size.

## Evidence and conclusion

[Evidence directory](interface-call/evidence/) preserves all 16 per-JVM EDN
results, timing order, unchanged full benchmark output, original targeted
assembly logs, extracted compilation tasks and disassembly. Full unfiltered
compilation XML remains under ignored `target/interface-call/` (about 102 MB).
The original per-sample Criterium distributions were not exported.

The justified conclusion is **no stable slowdown detected in this experiment**,
not a general performance guarantee. There is no evidence here for a
library fix or an inherent JEP 401 interface-call cost. The historical 1.14
result remains evidence of an individual run, not an established regression.
