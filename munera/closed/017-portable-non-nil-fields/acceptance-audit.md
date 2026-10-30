# Acceptance audit

The user accepts the existing implementation evidence as sufficient. Criteria 1–8
are accepted on the combined evidence below, not on a claim of exhaustive testing.
Criterion 9 has delivered documentation and recorded passing checks; final validation
of the delivered tree passes as B1 in [blockers.md](blockers.md).

| Criterion | Accepted evidence |
| --- | --- |
| 1 — Declarations | [Declaration audit](declaration-acceptance-audit.md): metadata, concrete tags, disabled fields and structured errors. |
| 2 — Construction | [Constructor audit](constructor-acceptance-audit.md), [Java/AOT audit](aot-acceptance-audit.md), [saved bytes](saved-bytecode-audit.md): real constructor paths and all four enforcement configurations. |
| 3 — Policy | Constructor, receiving and [controlled-capability audits](controlled-capability-audit.md): representation policy, generated enforcement without native support and bounded diagnostics. Strict baseline child definition is expected to fail. |
| 4 — Field behavior | Constructor and [public read audits](public-read-acceptance-audit.md), backend and attribute evidence: constrained/nullable fields, primitive slots, methods and companions. Exhaustive hierarchy/attribute combinations are not required. |
| 5 — Boundaries | [Packaging audit](packaging-acceptance-audit.md) and [failure-propagation audit](failure-propagation-audit.md): real public/backend failures plus source inspection of classification and retry boundaries. Unreachable malformed bytecode need not be tested through public declarations. |
| 6 — Compilation | [Receiving audit](receiving-acceptance-audit.md) and [exact-Class audit](exact-class-audit.md): expansion transfer, redefinition, sibling loaders and cross-process replacement. |
| 7 — AOT | AOT, saved-bytecode and controlled-capability audits: fixed representations/enforcement, separate source-free Clojure/Java consumers and incompatible receiver rejection. |
| 8 — Matrix | Recorded explicit child-JVM policy/enforcement cases, normal/interpreter execution, native flattening controls and controlled absence. No exhaustive cross-product requirement is added. |
| 9 — Delivery | [Documentation audit](documentation-audit.md) and recorded suite/quality results. Final delivered-tree validation passes. |

## Completion rule

Do not reopen accepted criteria from older partial-status statements. Detailed audits
retain their evidence limits, but their outstanding exploratory suggestions are not
additional task blockers. A newly demonstrated contract defect must be reported
explicitly. Otherwise complete B1, report implementation complete and close the task.
