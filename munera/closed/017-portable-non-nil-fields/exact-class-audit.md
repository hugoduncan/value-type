# Exact-Class acceptance audit

The slice 4 exact-Class item is covered by the following real compilation tests.
This audit does not close the general hierarchy, failure-propagation or full matrix
items. No production change or additional fixture is needed for this reconciliation.

| Boundary | Tests and evidence | Limits |
| --- | --- | --- |
| Receiving resolution | identity-receiving-test and value-receiving-test assert identical field Classes, retained references and constructor rejection. | Public and internal controls are distinct. |
| Same-name redefinition | receiving-redefined-child-test in both namespaces reanalyzes the same expansion after child replacement. Each owner retains its own child Class and rejects the other child. | Recorded Java 8 and preview-disabled EA3 identity runs; preview-enabled EA3 native/generated runs, normal and interpreter. |
| Isolated sibling loaders | isolated-sibling-loader-test uses real URLClassLoader siblings, not the shared DynamicClassLoader name cache. Both owners accept only their own child through factory, direct and reflective construction. | Identity children: baseline identity owners and strict preview-enabled generated value owners. This is not native value-child sibling coverage. |
| Stale imports | identity-loader-test rejects repeated mismatches without owner/factory installation or receiving-analysis warnings; corrected imports permit all three constructor entries. | Not an all-policy unexpected probe/transform failure test. |
| Cross-process replacement | child-transfer-test serializes both public expansions from a producer with a different child shape. Consumers resolve the receiver-local Class through eval, function analysis and metadata-preserving source loading. | Separate JVMs cannot compare Class objects directly. Receiver shape and identical receiver field Class prove local resolution. |

## Transfer policy results

- Baseline-selected identity parents succeed under silent and warn on Java 8 and
  EA3 preview off/on. Warn emits one fallback warning per receiver.
- Those fixed identity expansions reject twice under strict policy on all three
  configurations, without warnings or parent/factory installation. The replacement
  child remains installed. This is rejection evidence, not successful construction.
- Value-selected generated parents with identity children succeed under all policies
  on preview-enabled EA3, with final concrete storage and no native annotations.
- Native parents with replacement value children succeed under all policies on
  preview-enabled EA3, with final concrete storage and NullRestricted retained.
- Successful transfers check factory, direct and reflective construction, retained
  child references, nil rejection and wrong-type rejection. All transfer cases run
  normally and with the interpreter, without internal exports.

The full child-transfer namespace was rerun for this audit: four tests, 546 passing
assertions, exit 0 and an empty Scry failure directory. Earlier exact-Class runs and
commands are recorded in implementation.md. Native value-child siblings and every
policy crossed with every loader fixture are not claimed by this focused audit;
keep the separate full matrix item open.
