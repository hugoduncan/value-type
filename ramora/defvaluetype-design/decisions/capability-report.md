# Capability report decision

## D14. Public capability report (interface and state)

- Subject: a read-only public view of the optional JVM capabilities.
- Choice: `value-type.capability/report` takes no arguments and returns a
  map with the keys `:value-class`, `:native-non-nil-field`,
  `:array-allocation` and `:array-inspection`. Each value is the
  `:available?` and `:reason` entries of the cached result of the owner:
  `impl.capability/capability`, `impl.field-capability/capability`,
  `impl.array/allocation-capability` and `impl.array/inspection-capability`.
  The report is EDN-printable. It contains no Methods, Classes or
  Throwables.
- Ownership: each probe namespace owns its cached result through one
  zero-arity accessor over a private delay. Every reader (definitions,
  array functions, guards, the report and tests) uses the accessor. The
  accessor is also the test replacement point. `impl.runtime` owns only the
  initialized policy, the warning flag (D13) and the fallback guards
  (`enforce-fallback!`, `guarded-deftype`, `guarded-deftype*`). The
  native-field guard `require-field-capability!` is in
  `impl.field-capability`.
- Boundary: loading `value-type.capability` and calling `report` do not read
  `value-type.fallback` and do not load `value-type.core` or `impl.runtime`.
  The namespace loads without JEP 401, preview, export or
  `java.lang.classfile` (D12).
- Failure: `report` runs probes that have not run. A probe defect propagates
  unchanged (D12); there is no partial report.
- Rejected: a report in `value-type.core` (it would load policy); a second
  cache for the report (results could differ from the results that
  definitions use); exposing `:detail`, `:cause` or Methods.
- Rationale: users can check a deployment before they define types or
  allocate arrays, and the report cannot disagree with the library
  behaviour, because it reads the same cache.
- Qualities: small public surface; one owner per result. Adds a 54-child
  cross-JVM matrix (6 runtimes × 3 policies × 3 require orders).
- Commitment: firm. Verified by `value-type.capability-test`
  (`report-translation-test`, `report-defect-propagation-test`),
  `value-type.capability-matrix-test`,
  `value-type.controlled-value-capability-test`,
  `value-type.runtime-test/invalid-policy-report-test` and the isolated
  source-JAR consumer.
