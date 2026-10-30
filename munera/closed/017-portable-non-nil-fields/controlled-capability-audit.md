# Controlled native-capability acceptance

This audit closes the controlled-absence evidence gap, not task acceptance or
support for another JVM. The design permits only the isolated replacement of
`value-type.impl.runtime/field-capability`. Compilation, loaders, transforms and
constructors remain real.

## Evidence

`test/compatibility/value_type/controlled_field_capability_test.clj` contains four
independent tests. `test/compatibility/value_type/function_redefinition_test.clj`
contains one more. Every consumer uses EA3 with preview, without internal exports,
under silent, warn and error in normal and interpreter execution.

| Boundary | Verified outcome |
| --- | --- |
| Public construction | Both expansion entry points compile eligible Long constraints inside the override. Value representation, final Long storage, no native annotations, valid values and factory/direct/reflective nil/type rejection remain intact. |
| Independent receiving | A separate strict producer serializes both expansions with metadata. Fresh consumers compile each expansion in its own process through eval, function analysis and metadata-preserving source loading. They with native capability unavailable and retain the same construction outcomes. |
| Source-free Clojure AOT | A strict producer saves generated Long output. Source is deleted and excluded from consumer classpaths. Consumers retain storage and all three construction entries without native capability. |
| Same-name redefinition | `function_redefinition_test/controlled-capability-repeated-definition-test` evaluates the same declaration twice through function analysis in one consumer. Each Long owner is a distinct value Class with final Long storage, no native annotations and factory/direct/reflective nil/type rejection. A plain `deftype` baseline (final Object storage, no annotations) accepts both. |
| Separate Java AOT | Java compilation occurs after producer source deletion. Direct Java constructors accept Long and reject null/wrong types. Reflection confirms final Long storage, value representation and no native annotation. |

No case emits a fallback warning. The Java consumer runs inside the approved
accessor override, but direct Java class access does not evaluate namespace policy
guards. The Clojure AOT case covers namespace loading.

## Verification

On 2026-10-27:

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  clojure -M:fallback-test --namespace value-type.controlled-field-capability-test
```

Exit 0; 4 tests, 174 assertions, no failures or errors; `.scry-results` is empty.
Log: `/tmp/task017-controlled-paths.log`.

On 2026-10-29, `--namespace value-type.function-redefinition-test` exits 0;
1 test, 18 assertions, no failures or errors.
Log: `/tmp/task017-function-redefinition.log`.

## Limits

Long is a real eligible value Class on this JVM. The accessor override establishes
controlled absence, not a second supported value JVM. String-only fixtures remain
native-ineligibility evidence. Receiving covers all three analysis paths, with each expansion isolated to avoid
same-name owner redefinition within one consumer. The redefinition test covers
same-name redefinition separately. This audit does not
close exact-Class loader isolation, unexpected failure propagation, general
attribute preservation or the complete runtime matrix.

Together with the incompatible artifact cases in the [AOT audit](aot-acceptance-audit.md),
these checks close the combined checklist item for incompatible value rejection
and generated-only output without a native requirement.
