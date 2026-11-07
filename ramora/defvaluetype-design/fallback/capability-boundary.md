# Capability boundary

Part of [controlled fallback](../fallback.md).

## D12. Capability boundary

- Subject: deciding availability without loading unsupported APIs.
- Choice: separate JVM capability from application policy. Capability
  answers whether this library can define its supported value-class
  format, with an unavailable reason for diagnostics. It must cover
  missing support, required API availability and disabled preview.
  `Class::isValue` presence alone is insufficient.
- Boundary: the public namespaces `value-type.core` and
  `value-type.capability` and the fallback path must load without
  JEP 401, preview flags or `java.lang.classfile`. Load the value-class
  machinery only after the initial capability checks permit it. Identity
  definitions with enabled non-nil fields use a baseline-safe bytecode backend;
  ordinary fallback needs no transform.
  Capability detection must use APIs safe on the fallback runtime.
- Failure classification: only established capability absence permits
  fallback. Established absence: missing required APIs; probe class version
  rejection; a probe class that is defined but is not a value class
  (`:probe-not-value-class`); a failed semantic self-check
  (`:failed-self-check`, D17); the value-class switch set to `off`
  (`:disabled`, D18). For native-field capability only: the JVM accepts null
  in the `NullRestricted` probe field (`:native-restriction-ignored`); generated
  enforcement then applies. Invalid forms, unexpected constructor shapes, loader defects
  and transformation defects remain errors. Do not catch all definition
  failures and retry the user's type as an identity class.
- Rejected: eager transformer loading; detecting availability by failing
  to define each user type; treating every `LinkageError` as absence.
- Rationale: missing optional APIs must not prevent fallback. A defect
  must remain visible, including on a supported JVM.
- Qualities: improves portability and fault isolation. Adds a capability
  check and its verification burden.
- Commitment: firm boundary. Use API checks followed by the isolated strict-field format probe
  of the [selected detector](selected-detector.md). No new
  minimum fallback JDK is imposed beyond the supported Clojure runtime;
  the tested JDK matrix is a separate verification decision.
