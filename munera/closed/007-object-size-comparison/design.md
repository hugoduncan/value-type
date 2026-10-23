# 007 Object-size comparison

## Intent

The shallow heap size of a `deftype` object and a `defvaluetype`
object is known for matching types with 0, 1, 2 and 3 fields on the
Valhalla JDK. The comparison uses Criterium `0.5.251-ALPHA` from
Clojars and records bytes per object with a reproducible command.
Equal, smaller and larger results are all valid outcomes.

## Problem and context

Task 006 measured operation time, not object size. Its allocation
case can be affected by escape analysis and cannot establish the
size of a materialized object. This task supplies that separate
measurement without changing the library implementation.

- Library: `value-type.core/defvaluetype` in `src/`.
- Existing measurement tools: `bench/` and the `:bench` alias in
  `deps.edn`, using Criterium `0.5.245-ALPHA`. The implemented
  `:object-size` alias separately uses `0.5.251-ALPHA`.
- JDK selection: `JAVA_CMD`, with `--enable-preview`.
- Prior results: `ramora/defvaluetype-design/benchmark.md`.
- Measurement guidance: `mementum/knowledge/benchmarking-value-types.md`.
- Confirmed dependency: `org.hugoduncan/criterium`, Maven version
  `0.5.251-ALPHA`, published at `https://repo.clojars.org/`.
  The request's `0.5.251-ALPGA` and “clojaars” refer to this release
  and Clojars. The published POM and JAR were inspected.

## Measurement contract

Compare four type pairs: empty fields, then 1, 2 and 3 primitive
`long` fields. Primitive `long` is the design default because task
006 uses it and it avoids referenced-object size ambiguity. This
choice must be visible in the results; field count alone does not
specify a layout.

Within each pair, declarations differ only in the defining macro
and type name. Field order, hints and supplied values match. Do not
add instance fields, protocols or interfaces for measurement support.
Verify the generated classes have the expected instance fields and
that the value classes are recognized as value classes by the JVM.

“Size” means the shallow size in bytes of a materialized standalone
object, including the header and alignment padding reported by the
measurement tool. It excludes class metadata, referenced objects,
containers and flattened array storage. It is not allocation rate,
retained heap size or a sum of nominal field widths.

Keep objects reachable during measurement. Associate each result
with the exact generated class. Do not report unrelated allocations
or an optimized-away allocation as object size. A missing measurement
is an error, not zero bytes.

## Approach and decisions for planning

Use Criterium's object-size or per-object allocation measurement
facility in this exact release. Prefer the smallest supported route
that returns size for the target object. Do not infer size from
elapsed time or process heap differences, and do not substitute a
different library or version without approval.

The JAR contains `criterium.agent.instrumentation/object-size`, which
calls `Instrumentation.getObjectSize`, and native allocation tracing
with per-object size records. Presence of these facilities does not
prove that either is ready to use on the selected Valhalla build.
Planning must verify the supported entry point, agent setup, platform
support and size semantics. Record the chosen route and required JVM
options. If neither route supports this comparison, record the
failure and ask for a decision rather than invent size results.

Keep this as a manual measurement under the existing benchmark area,
not a performance gate. Prefer an isolated measurement dependency
configuration if upgrading the shared alias would affect task 006.
If the shared alias changes, verify that the existing timing command
still runs. Preserve its historical results and version labels.

## Acceptance

1. One documented command runs all eight cases on the selected
   Valhalla JDK using the published `0.5.251-ALPHA` artifact.
2. Results contain four rows, ordered by field count, with `deftype`
   bytes, `defvaluetype` bytes and their signed byte difference
   (`defvaluetype` minus `deftype`). Preserve machine-readable results
   and the raw measurement evidence.
3. Evidence identifies each target class and its fields, confirms
   value-class status, and explains how the reported bytes correspond
   to shallow object size. Check more than one instance per case;
   report discrepancies instead of selecting a convenient result.
4. A results document records the exact command, JDK/VM build, OS,
   architecture, Clojure and Criterium versions, agent setup and JVM
   options. Record relevant alignment, compressed-reference and
   header settings where available; identify unknown settings.
5. Explain observed differences without assuming a size saving.
   State that results apply only to this field layout and VM setup.
   Link the results from the design documentation and document the
   runnable command in README.
6. Library behavior is unchanged. The existing test suite passes.
   Historical task 006 measurements are not replaced by size results.

## Out of scope

Library optimizations; method-call timing investigations; mixed,
reference or nested value fields; deep/retained size; array flattening;
GC throughput; multiple JDKs or header configurations; a general
object-layout tool. These are possible separate tasks, not required
extensions of this comparison.
