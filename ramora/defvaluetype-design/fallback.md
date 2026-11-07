# Controlled fallback

Application policy selects class representation for all definitions. Enabled
[non-nil fields](../non-nil-field-design.md) preserve their field constraint
through native or generated enforcement. Missing native field support alone does
not trigger fallback, warning or strict rejection. The field design's embodiment
section records the implemented contract and accepted evidence limits.

## D10. Controlled fallback

- Subject: unavailable value-class support and policy authority.
- Choice: use value classes whenever this library can define them.
  Otherwise apply the application policy:

  | Policy | Result when support is unavailable |
  |---|---|
  | `silent` | Define an identity class, with no message. |
  | `warn` | Print one warning to standard error, then use an identity class. |
  | `error` | Throw a Java `Error` on every attempt. Define no fallback class. |

- Strict retries: catching or ignoring an earlier `Error` does not
  permit a later definition. Cache capability, not permission to skip
  an error. Direct calls throw plain `java.lang.Error`; source compiler operations
  wrap it in `Compiler$CompilerException`. Failed AOT initializer retries
  throw `NoClassDefFoundError`, also an Error, without guard re-entry.
- Authority: applications control policy before dependent namespaces
  load. Libraries use `defvaluetype` without weakening application
  policy. There are no per-type overrides in this design.
- Configuration: JVM property `value-type.fallback`, with
  values `silent`, `warn`, `error`; default `silent`. Read and validate
  it once per loaded library runtime. Invalid values fail clearly.
  Changing it after initialization is not supported. This gives source
  libraries silent fallback by default and lets applications set policy
  before dependency loading.
- Semantics: fallback has ordinary identity semantics, not value-semantics
  emulation. Enabled non-nil fields retain concrete storage and generated checks.
  Identity, default equality, hashing and identity-sensitive
  operations can differ. Libraries that permit fallback must tolerate
  both representations. Mutable fields remain invalid in both modes
  (D8); changing JVM capability must not make such source valid.
- Rejected: unconditional failure (prevents portable libraries), one
  warning per type (noise), throw only once then continue (breaks strict
  policy), and value-semantics emulation (new semantics and cost). Generated field
  checks preserve a declared invariant; they do not emulate value semantics.
- Qualities: improves portability and application control; loses uniform
  identity and equality semantics across JVMs when fallback is allowed.
- Commitment: firm for the three behaviours, repeated strict failure
  and configuration surface. Plain `java.lang.Error` is selected. Source compilation wraps it in
  CompilerException; failed AOT initializer retries can throw
  NoClassDefFoundError instead.

## D12. Capability boundary

See [capability boundary](fallback/capability-boundary.md).

## D13. Fallback lifecycle

See [fallback lifecycle](fallback/lifecycle.md).

## Verification

D11 requires isolated JVM tests:

- Public namespaces `value-type.core` and `value-type.capability` and the
  packaged source JAR load without optional APIs.
- All policies on a non-value JVM and Valhalla with preview disabled.
- Value classes on supported Valhalla with preview enabled, for all policies.
- No output in silent mode; one warning across types, namespaces and
  concurrent attempts; repeated strict attempts fail without defining types.
- Error cause and compiler-wrapper behaviour; invalid policy rejection.
- Same field validation in both modes; no fallback for implementation defects.
- Fallback protocols, factories, type hints and ordinary identity semantics.
- AOT compilation and fresh-JVM namespace loading under different policies
  and capabilities, including strict rejection of fallback artifacts.

## Selected detector

See [selected detector](fallback/selected-detector.md).
