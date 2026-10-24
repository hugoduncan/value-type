## Fallback architecture

Scope: Clojure 1.12.6, Temurin 1.8.0_492-b09 and Valhalla
27-jep401ea3+1-1 with preview off/on. Task 009 implementation.md records
commands and results. A-33 remains a design constraint, not exhaustive JVM
coverage. Unknown future formats remain unverified.

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-29 | observed | D10 | serves | portable source libraries and application-owned fallback policy | user requirements; [fallback](../fallback.md) | high |
| A-30 | observed | strict policy | enforces | Error on every attempted fallback, including retries after a caught Error | user clarification; D10 | high |
| A-31 | observed | warn policy | enforces | at most one warning per loaded library runtime, then ordinary deftype | D10 D13 | high |
| A-32 | observed | fallback boundary | forbids | eager loading of optional value-class APIs | D12 | high |
| A-33 | intended | fallback path | constrainedBy | no additional JDK minimum beyond supported Clojure | D12 | high |
| A-34 | observed | capability/policy responsibility | owns | cached capability, initialized policy and atomic warning flag | D13 | high |
| A-35 | observed | fallback selection | forbids | treating transformation or loader defects as capability absence | D12 | high |
| A-36 | observed | AOT fallback form-evaluation guard | enforces | application policy during initialization for top-level forms and on invocation for forms inside functions, without changing compiled representation | D13 | medium |
| A-37 | observed | D10 configuration | chooses | value-type.fallback JVM property; silent default | D10; user approval 2026-10-23 | high |

Evidence for A-29–A-37: `value-type.runtime-test` verifies initialization,
capability, source semantics and strict cause chains; `value-type.transfer-test`
verifies receiving policy and synchronized warnings; `value-type.aot-test`
verifies fixed representation and source-free loading; `build-test` verifies
isolated source-JAR consumers. Existing core, loader and transform suites
verify defect visibility under all policies. For A-36,
`source-free-runtime-only-fallback-test` verifies that namespace loading succeeds
even under strict policy before function calls each throw plain Error. A-36
excludes direct class access that bypasses the guarded form and does not promise
absence of loaded AOT classes before rejection. Failed initializer retries do
not re-enter the guard.
