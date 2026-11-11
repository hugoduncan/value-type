## Fallback architecture

These are design constraints, not claims of implementation coverage or
exhaustive JVM support. See [embodiment](../embodiment.md) for current coverage.

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-29 | intended | D10 | serves | portable source libraries and application-owned fallback policy | user requirements; [fallback](../fallback.md) | high |
| A-30 | intended | strict policy | enforces | Error on every attempted fallback, including retries after a caught Error | user clarification; D10 | high |
| A-31 | intended | warn policy | enforces | at most one warning per loaded library runtime, then ordinary deftype | D10 D13 | high |
| A-32 | intended | fallback boundary | forbids | eager loading of optional value-class APIs | D12 | high |
| A-33 | intended | fallback path | constrainedBy | no additional JDK minimum beyond supported Clojure | D12 | high |
| A-34 | intended | probe namespaces; impl.runtime | own | cached capability results (impl.capability, impl.field-capability, impl.array); policy fixed at the first read and atomic warning flag (impl.runtime) | D13 | high |
| A-35 | intended | fallback selection | forbids | treating transformation or loader defects as capability absence | D12 | high |
| A-36 | intended | AOT fallback form-evaluation guard | enforces | application policy during initialization for top-level forms and on invocation for forms inside functions, without changing compiled representation | D13 | medium |
| A-37 | intended | D10 configuration | chooses | value-type.fallback JVM property; silent default | D10 | high |

Verification must cover capability, source semantics, strict cause chains,
receiving policy, synchronized warnings, fixed AOT representation and isolated
source-JAR consumers. Defects must remain visible under all policies.
For A-36, source-free runtime-only fallback checks must verify that namespace
loading succeeds under strict policy before function calls each throw plain
Error. Direct class access is outside the guarded form. The guard does not
promise absence of loaded AOT classes before rejection, and failed initializer
retries do not re-enter it.
