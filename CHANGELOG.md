# Changelog

## [Unreleased]

- Initial release. Add `defvaluetype` and `defvaluetype*` for JEP 401 value
  classes with `deftype` fallback, `:non-nil` fields and experimental
  `value-type.array` functions for null-restricted arrays.
- Add the `defvaluetype*` option `:value-type/closed true`: a closed record
  shape with no `__meta` and `__extmap` in value output. The main
  constructor rejects a non-nil `__meta` or `__extmap` argument, so `create`
  and the map literal reject undeclared keys.
- Add `value-type.capability/report`, a read-only EDN report of value-class,
  native non-nil field, array allocation and array inspection capability.
- The value-class probe runs a semantic self-check, and the value-array
  probe does an allocation round trip. The native field probe checks nil
  rejection. New report reasons: `:probe-not-value-class`,
  `:failed-self-check` and `:native-restriction-ignored`. A probe class that
  is not a value class, and a JVM that accepts nil in a `NullRestricted`
  field, now give classified absences, not invariant errors. The probes use
  `java.lang.classfile`, not `clojure.asm`.
- The `value-type.fallback` policy is read and validated at the first read
  (the first fallback decision or the first `report` call), not at namespace
  load. A policy set with `System/setProperty` before the first read applies;
  a change after the first read has no effect. An invalid value fails at the
  first read. `report` adds `:fallback-policy` (`:silent`, `:warn` or
  `:error`), the policy in effect.
- Add the JVM property `value-type.value-classes` (`auto` or `off`). `off`
  disables value classes with no code change: `report` shows `:disabled`
  for `:value-class`, `:native-non-nil-field` and `:array-allocation`, and
  definitions use fallback. An invalid value fails at the first capability
  read.
- Every value expansion is a receiving form and starts with a value-class
  guard. A replayed or transferred ordinary value expansion
  (`(eval (macroexpand form))`) installs at analysis and defines a value
  class; on a JVM without value classes it throws
  `:value-capability-unavailable`. Saved AOT value output throws
  `:value-capability-unavailable` with the reason (for example `:disabled`
  under `value-type.value-classes=off`) where the form is evaluated, also
  for native-field output (was `:field-capability-unavailable`).
  `:not-installed` comes only from the receiving macro at macroexpansion,
  with a class-capture message. `check-value-class` takes one argument and
  throws only `:not-value-class`. All compiler code use moves to the
  internal compiler boundary `value-type.impl.compiler`.
