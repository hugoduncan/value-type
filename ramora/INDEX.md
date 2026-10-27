# Ramora index

Read this file first. Read the other files only when you need them.

## Documents

- [defvaluetype design](defvaluetype-design.md): architecture of the
  `defvaluetype` macro. Purpose, context, decisions, embodiment,
  qualities, risks, assertions.

- [Value-type array design](value-type-array-design.md): native,
  null-restricted atomic allocation is implemented; final acceptance passed.
  Diagnostics are implemented;
  [final acceptance](../munera/closed/014-value-array-diagnostics/acceptance.md)
  passed within the recorded JVM scope. Task 014 is closed.
  Sequence construction is implemented in closed task 015;
[final acceptance](../munera/closed/015-value-array-sequence-construction/acceptance.md) passed within the recorded JVM scope.
  [Semantic report](value-type-array-design/semantic-verification.md),
  [runnable instructions](../spike/value-array-semantics/README.md) and
  [retained evidence](value-type-array-design/evidence/matrix-selected-slots/) cover EA3.

- [Controlled fallback](defvaluetype-design/fallback.md): implemented policy,
  capability boundary, warning and Error behaviour, AOT limits and tests.
  Verified on Java 8 and Valhalla 27 EA; see the scoped evidence.

- [Library packaging](defvaluetype-design/embodiment.md#library-packaging):
  source-only JAR, build artifacts, consumer requirements and development commands.

- [Development quality workflow](defvaluetype-design/embodiment/artifacts.md#development-quality-workflow):
  development-only tools, generated API sources, quality commands, hooks and
  CI; separate runtime-test requirements.

- [Development guide](../doc/development.md): contributor setup, quality
  checks, tests, builds and benchmarks.

- [Usage guide](../doc/usage.md): fallback, AOT limits, value semantics and
  error handling.

- [Release workflow](release-workflow.md): tooling ownership, preparation,
  publication order and verification limits.

## Related

- `README.md`: project summary.
- `AGENTS.md`: agent rules.
- `mementum/state.md`: working memory.
- `munera/plan.md`: task order.
