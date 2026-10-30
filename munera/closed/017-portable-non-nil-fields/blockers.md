# Completion blockers

The user accepts the existing implementation evidence as sufficient. This decision
covers acceptance criteria 1–8 and the recorded documentation evidence for criterion
9. It does not claim exhaustive coverage or support for additional JVMs.

No demonstrated functional defect is currently recorded as a blocker.

## B1 — Final validation of the delivered tree (complete)

- [x] Run the full runtime suite on preview-enabled EA3 in normal and interpreter
      execution.
- [x] Run exit-verified compatibility suites on Java 8 and EA3 preview off/on.
      All three final runs exit 0 with empty Scry failure directories.
- [x] Run build, release and tooling suites with their documented classpaths.
- [x] Run formatting, lint, maintained-prose, generated-API and whitespace checks.
- [x] Record commands, tested revision/tree, exit codes and structured results in
      implementation.md. Retain results before later runs replace them.

Completion: the documented suites and quality checks pass on the final code/test
revision. Existing current-revision results may be reused if their commands and
scope establish the same check. Documentation-only completion edits do not require
rerunning runtime suites; rerun affected documentation checks.

If a check fails, record a specific failure, its affected acceptance criterion and
its repair. A failure may require implementation work; an untested hypothetical
combination does not. Do not add tests merely to make the matrix exhaustive.

## Accepted limits — not blockers

- Controlled native absence uses the approved narrow with-redefs override. It does
  not establish support on a new JDK.
- Public tests, backend/capture tests and source inspection jointly establish failure
  behavior. Malformed bytecode that valid declarations cannot emit need not be
  manufactured through the public macro.
- Hierarchy, attributes, loader identity and policy evidence need not cover every
  possible combination. Preserve the existing bounded claims.
- Strict policy on a baseline JVM rejects a child defvaluetype before a parent can
  use it. Missing successful construction in that case is not a defect.
- Further value-capture fault variants and matrix permutations are not required for
  this task unless final validation exposes a concrete problem.

## Stop condition

When B1 passes, report implementation complete. Do not start another exploratory
implementation slice. Close the task through the normal completion process and
remove its entry from munera/plan.md; preserve evidence and its limits. Human
acceptance of sufficient evidence is already recorded here.
