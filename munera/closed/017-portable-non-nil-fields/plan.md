# Completion plan

## Authority and scope

The user accepts the implementation evidence as sufficient. Preserve the contract
in design.md and its bounded support claims. Use [blockers.md](blockers.md) as the
only remaining-work list. Historical progress notes and individual audit follow-ups
are evidence, not instructions to expand this task.

## Completion

B1 passes. The final compatibility runs exit 0 on Java 8 and EA3 preview off/on.
Prior current-revision runtime, build, release, tooling and quality results remain
valid. No implementation repair or additional test is required.

## Executed approach

1. Identify the final code/test revision and reuse recorded validation only where
   it covers that revision and the documented check.
2. Next run exit-verified compatibility suites on Java 8 and EA3 preview off/on.
   Runtime normal/interpreter, build, release, tooling and quality checks pass on
   code/test revision `3d19f26`. Run missing final suites and quality checks listed in B1. Use explicit JVMs,
   flags and separate test classpaths. Retain structured results before replacement.
3. If a check fails, identify the concrete defect and acceptance criterion, repair
   it, and rerun affected checks. Do not add speculative matrix combinations.
4. Record the final outcome in implementation.md. Update project state with verified
   current behavior, not more progress history.
5. When B1 passes, report implementation complete and close the task through the
   normal completion process. No further exploratory implementation pass is needed.

## Accepted verification approach

Public integration tests, backend tests, real capture tests and source inspection
provide complementary evidence. Not every injected backend failure must be
reproduced through public syntax. Controlled native absence uses the approved
runtime accessor override while compilation and construction remain real.

No production redesign, new testing switch or exhaustive combination matrix is
required to complete this task. Remaining limits stay documented; they do not
become blockers without a demonstrated contract failure.
