# Ramora index

Read this file first. Read the other files only when you need them.

## Documentation rules

Design documents describe the design, not the history of the project.
Keep implementation status and gaps between the design and its implementation
only in files named `embodiment.md`. These files describe the current state,
not delivery history. Linked component documents describe architecture, not
implementation status. Other sections state contracts, decisions, rationale,
constraints and required verification without completion reports. Keep task
progress, acceptance records and change history in Munera and Git. Experiment reports retain methods, observations and evidence
limits; they do not report feature delivery status.

## Documents

- [defvaluetype design](defvaluetype-design.md): architecture of the
  `defvaluetype` macro. Purpose, context, decisions, embodiment,
  qualities, risks, assertions.

- [Non-nil field design](non-nil-field-design.md): `:non-nil`
  constraint, native or generated enforcement, application-controlled identity
  fallback, and verification requirements.

- [deftype* design](deftype-star-design.md): `defvaluetype*` for
  callers that emit `deftype*`, including the record shape and the closed
  record shape. Compiler record constructors, hidden field removal, non-nil
  fields, fallback, exclusions and verification.

- [Value-type array design](value-type-array-design.md): native,
  null-restricted atomic allocation, sequence construction and diagnostics.
  [Semantic report](value-type-array-design/semantic-verification.md),
  [runnable instructions](../dev/spike/value-array-semantics/README.md) and
  [retained evidence](value-type-array-design/evidence/matrix-selected-slots/)
  describe the JVM boundary.

- [Controlled fallback](defvaluetype-design/fallback.md): policy,
  capability boundary, warning and Error behaviour, AOT limits and tests.

- [Capability report](defvaluetype-design/decisions/capability-report.md):
  D14, the public `value-type.capability/report`, the policy in effect and the
  cache owners.

- [Probe and format decisions](defvaluetype-design/decisions/probe-and-format.md):
  D15 JVM format table, D16 probes built with `java.lang.classfile`, D17
  semantic self-check, D18 value-class switch.

- [Compiler boundary decisions](defvaluetype-design/decisions/compiler-boundary.md):
  D19 one owner of compiler coupling, D20 receiving expansion for all
  definitions.

- [Verification and release decisions](defvaluetype-design/decisions/verification-and-release.md):
  D21 supported-JVM facts, D22 test tiers, D23 disassembly snapshots, D24
  install tested releases.

- [Library packaging](defvaluetype-design/embodiment.md#library-packaging):
  source-only JAR, build artifacts, consumer requirements and development commands.

- [Development quality workflow](defvaluetype-design/embodiment/artifacts.md#development-quality-workflow):
  development-only tools, generated API sources, quality commands, hooks and
  CI; separate runtime-test requirements.

- [Development guide](../doc/development.md): contributor setup, quality
  checks, tests, builds and benchmarks.

- [Usage guide](../doc/usage.md): fallback, AOT limits, value semantics,
  error handling, value arrays (allocation, sequence construction,
  diagnostics), the capability report and portable non-nil fields.

- [Release workflow](release-workflow.md): tooling ownership, preparation,
  publication order and verification limits.

## Related

- `README.md`: project summary.
- `AGENTS.md`: agent rules.
- `mementum/state.md`: working memory.
- `munera/plan.md`: task order.
