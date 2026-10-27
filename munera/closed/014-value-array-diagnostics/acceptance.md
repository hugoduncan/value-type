# Diagnostic acceptance

Implementation is complete. The task remains open for review. All seven design
criteria are satisfied within the recorded JVM scope.

## Environment and reproduction

Runtime checks use Clojure 1.12.6, Scry 0.1.61, macOS arm64, Temurin
`1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1`.
See [final environment](evidence/slice-5g-environment.txt) and
[final control commands](evidence/slice-5g-commands.sh).

Executable selection:

```sh
export VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
export VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
export JAVA_CMD=$VALUE_TYPE_VALHALLA_JAVA
```

The final supported-library command was:

```sh
clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -M:test -m scry.cli --dir test
```

The disabled-layout run adds `-J-XX:-UseArrayFlattening` and replaces `--dir test`
with `--namespace value-type.array-test --namespace value-type.array-info-test
--namespace value-type.array-layout-test`. The test alias supplies preview.

The process matrix command was:

```sh
clojure -M:fallback-test --namespace value-type.array-info-boundary-test \
  --namespace value-type.array-boundary-test --namespace value-type.array-matrix-test
```

The parent has no preview or export flags. Each child selects its own flags.
Final fallback controls use Java 8, EA3 without preview and EA3 with preview;
none adds exports. Build tests use the EA3 parent with alias-supplied preview.
The retained control script runs structured checks before each CLI check and
checks that no failure EDN files remain before the next run.

## Final results

All listed in-process runs passed with zero failures or errors. Corresponding
CLI runs exited zero with no Scry failure files. Counts are tests/assertions.

| Check | Result | Structured evidence |
| --- | --- | --- |
| Full supported library, including enabled layout | 78/2060 | [library](evidence/slice-5e-library.edn) |
| Disabled array flattening | 14/1848 | [layout](evidence/slice-5e-disabled.edn) |
| Diagnostic and allocation process matrices | 4/2095 | [matrices](evidence/slice-5f-matrices.edn) |
| Java 8 fallback/loading | 15/166 | [baseline](evidence/slice-5g-baseline.edn) |
| EA3 preview off fallback/loading | 15/166 | [preview off](evidence/slice-5g-preview-off.edn) |
| EA3 preview on fallback/loading | 15/141 | [preview on](evidence/slice-5g-preview-on.edn) |
| Full build suite and isolated consumers | 9/103 | [build](evidence/slice-5g-build.edn) |
| Quickdoc | 11/199 | [documentation](evidence/slice-5d.edn) |

Formatting, lint, Vale, generated API freshness and documentation link checks
passed in slice 5d. Exact commands and earlier slice details are in
[implementation notes](implementation.md). Final record whitespace and local
acceptance links were checked after writing this record.

## Acceptance mapping

1. **Complete observed properties:** library tests compare all five keys and
   their types with independent direct JDK inspection. Cases include Object,
   String, nested arrays, all verified value fixtures and the wider non-flat
   fixture at lengths 0, 1 and 3. Contents remain unchanged, including after
   caller stores. See library and layout evidence.
2. **Safe invalid inputs:** slice 1 established the disposable-JVM safety gate
   before shared-JVM comparisons. The final matrix checks null, non-array and
   primitive-array public calls, normal exceptions, complete EDN, exit zero
   and 120-second timeouts. No native null diagnostics were called.
3. **Independent inspection capability:** 36 startup combinations cover six
   configurations, three policies and both require orders. Independent native
   observations distinguish absent APIs from denied invocation access. Invalid
   inputs retain `:invalid-array` on every row. Valid unsupported inputs report
   `:array-inspection-unavailable`. Export-only EA3 inspection succeeds before
   and after allocation rejects disabled preview.
4. **Layout controls:** enabled and disabled runs compare restriction and
   atomicity independently of flat status. Disabled flattening reports false
   without changing the other facts. Unsupported inspection never fabricates
   a partial result.
5. **Regressions and ownership:** library and allocation matrices pass. Final
   fallback, AOT and transfer controls pass on all three runtimes. Slice 3b
   reviewed delayed discovery and cache ownership: no caller arrays are stored,
   namespace loading resolves no optional API, and allocation does not force
   inspection. Unexpected invocation causes are not translated to capability
   absence.
6. **Packaged API and documentation:** isolated consumers load only the JAR and
   runtime dependencies. Supported consumers inspect ordinary and restricted
   arrays; Java 8, missing-export and export-only consumers check diagnostic
   errors and capability separation before value creation. Usage, generated
   API, runtime guidance, architecture status and Unreleased notes describe
   the delivered contract. Quickdoc tests cover the rendered API.
7. **Verification and evidence:** the table retains semantic, startup,
   fallback/loading, packaging and documentation results outside `target/`.
   Quality checks passed. Task 012 crash evidence was neither changed nor
   rerun as a library test.

## Limits

These results cover the recorded EA3 and Java 8 builds, not future JDK APIs.
Flat status is a runtime observation, not an allocation guarantee. Atomic
access does not establish compound-update safety, publication or caller
synchronization. Sequence construction, primitive-array inspection, performance
claims and native crash investigation remain outside this task.
