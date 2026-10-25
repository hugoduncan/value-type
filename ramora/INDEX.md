# Ramora index

Read this file first. Read the other files only when you need them.

## Documents

- [defvaluetype design](defvaluetype-design.md): architecture of the
  `defvaluetype` macro. Purpose, context, decisions, embodiment,
  qualities, risks, assertions.

- [Controlled fallback](defvaluetype-design/fallback.md): implemented policy,
  capability boundary, warning and Error behaviour, AOT limits and tests.
  Verified on Java 8 and Valhalla 27 EA; see the scoped evidence.

- [Library packaging](defvaluetype-design/embodiment.md#library-packaging):
  source-only JAR, build artifacts, consumer requirements and README commands.

- [Development quality workflow](defvaluetype-design/embodiment/artifacts.md#development-quality-workflow):
  development-only tools, generated API sources, quality commands, hooks and
  CI; separate runtime-test requirements.

## Related

- `README.md`: project summary.
- `AGENTS.md`: agent rules.
- `mementum/state.md`: working memory.
- `munera/plan.md`: task order.
