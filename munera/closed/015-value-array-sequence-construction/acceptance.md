# Sequence construction acceptance

The task is closed at the user's request after implementation, final acceptance,
review follow-ups and knowledge extraction. All seven design criteria pass within
the recorded JVM scope. This record uses retained final runs. Later review checks
are recorded in [implementation notes](implementation.md); full acceptance was
not repeated after those follow-ups. Closure changes records only; runtime tests
were not rerun.

## Environment and reproduction

Runtime checks use Clojure 1.12.6 on macOS arm64, Valhalla
`27-jep401ea3+1-1` and Temurin `1.8.0_492-b09`. Quality checks use Temurin
`21.0.10+7-LTS`. Exact executable paths, commands, flags and host versions are
retained with each group:

- Library and layout: [commands](evidence/slice-4e-commands.sh),
  [environment](evidence/slice-4e-environment.txt), [CLI record](evidence/slice-4e-run.log).
- Fallback/loading: [commands](evidence/slice-4f-commands.sh),
  [environment](evidence/slice-4f-environment.txt), [CLI record](evidence/slice-4f-run.log).
- Packaging: [commands](evidence/slice-4g-commands.sh),
  [environment](evidence/slice-4g-environment.txt), [CLI record](evidence/slice-4g-run.log).
- Quality commands and environment: slice 4d in [implementation notes](implementation.md).

Supported library runs use preview (from `:test`) and
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. Layout controls add
`-XX:+UseArrayFlattening` or `-XX:-UseArrayFlattening`. Fallback parents use
Java 8 or EA3 with preview off/on; each child selects its own startup flags.
The sequence matrix crosses six configurations with three fallback policies
and two namespace require orders. Build consumers use only the JAR and runtime
dependencies, not checkout sources. Scripts retain results outside `target/`.

## Final results

All structured runs below report `:pass? true`, zero failures and zero errors.
The retained CLI records report exit zero and no Scry failure EDN files.
Counts are tests/assertions. This slice re-read all eight structured files
with evaluation disabled and checked summaries, result statuses and failures.

| Check | Result | Structured evidence |
| --- | --- | --- |
| Full supported library | 89/2963 | [library](evidence/slice-4e-library.edn) |
| Sequence/allocation/layout, flattening enabled | 22/1350 | [enabled](evidence/slice-4e-enabled.edn) |
| Sequence/allocation/layout, flattening disabled | 22/1350 | [disabled](evidence/slice-4e-disabled.edn) |
| Java 8 fallback/loading | 20/3377 | [baseline](evidence/slice-4f-baseline.edn) |
| EA3 preview off fallback/loading | 20/3377 | [preview off](evidence/slice-4f-preview-off.edn) |
| EA3 preview on fallback/loading | 20/3352 | [preview on](evidence/slice-4f-preview-on.edn) |
| Full build and isolated consumers | 9/103 | [build](evidence/slice-4g-build.edn) |
| Quickdoc | 11/217 | [API contracts](evidence/slice-4d-quickdoc.edn) |

Formatting, lint, Vale and API freshness passed in slice 4d. Final acceptance
links and whitespace were checked after this record was written.

## Acceptance mapping

1. **Construction:** the full library and both layout runs cover both arities,
   finite lists, vectors and lazy inputs, one/several entries, distinct explicit
   seeds and all shared zero-field, primitive-field and reference-field fixtures.
   Assertions check component, exact length, order and contents.
2. **Validation:** sequence tests cover empty/nil input, explicit empty results,
   null/wrong seeds, invalid components and first/middle/last invalid entries.
   Exact error maps check indexes and exclude input, rejected values and buffers.
   Non-seqable input retains ordinary Clojure errors.
3. **Input effects:** non-caching Seqable tests detect repeated traversal;
   unchunked and chunked inputs check early validation and later failure effects.
   Realization exceptions retain their identity. Failed calls return no partial
   array, and a later valid call succeeds. See also [slice 2a](evidence/slice-2a.edn).
4. **Length:** the actual buffering loop uses checked-next-length. Focused checks
   accept MAX_VALUE and reject attempted length 2147483648 without narrowing or
   large allocation. These checks remain in the final library run.
5. **Native semantics and ownership:** selected-slot stores from both arities
   check all slots after valid and rejected writes in both layout modes.
   Sequence tests require neither diagnostics nor a flat layout. The slice 2b
   state review found call-local input, buffer and result; buffering completes
   before allocation, and copying exposes no unfinished array to caller code.
6. **Runtime boundaries and packaging:** final fallback suites include the
   36-child sequence matrix plus allocation, diagnostics, class fallback and
   loading regressions. Unsupported calls reject repeatedly before input effects.
   The focused [sequence matrix](evidence/slice-3a.edn) records 1116 assertions.
   Final build checks cover both arities and explicit empty input in supported
   JAR-only consumers, plus Java 8 and unsupported EA3, including export-only EA3.
7. **Documentation and combined verification:** usage, docstrings, generated API,
   runtime guidance, release notes and architecture describe finite inputs,
   seeds, empty input, indexed errors, temporary O(n) references and failure
   effects. [Examples](evidence/slice-4a-examples.edn) and final Quickdoc checks
   pass. The final table covers library, layout, fallback/loading and packaging.

## Limits

Evidence applies to the recorded EA3/Java 8 configurations, not future JVM APIs
or universal layout behavior. No sequence allocation amount or speed was measured.
Infinite input is unsupported; memory exhaustion can occur before the integer
limit. Chunked producers can realize ahead, and failures do not undo input effects.
No production code, tests, dependencies or design contract changed in this slice.
