(ns value-type.capability
  "Read-only report of the optional JVM capabilities that value-type uses
  and of the fallback policy in effect."
  (:require [value-type.impl.array :as adapter]
            [value-type.impl.capability :as capability]
            [value-type.impl.field-capability :as field-capability]
            [value-type.impl.runtime :as runtime]))

(defn report
  "Return the capability results and the fallback policy in effect of this
  runtime as an EDN-printable map.

  Keys:
  - `:value-class`: this runtime can define value classes.
  - `:native-non-nil-field`: value classes use native restriction for
    eligible `:non-nil` fields.
  - `:array-allocation`: `make-array` and `into-array` can allocate.
  - `:array-inspection`: `array-info` can inspect.
  - `:fallback-policy`: the fallback policy in effect, `:silent`, `:warn` or
    `:error`.

  Each capability value is `{:available? true}` or
  `{:available? false :reason r}`. Reasons:
  - `:value-class`: `:missing-classfile-api`, `:missing-value-predicate`,
    `:unsupported-class-version`, `:probe-not-value-class`,
    `:failed-self-check`.
  - `:native-non-nil-field`: the value-class reasons,
    `:native-restriction-ignored`.
  - `:array-allocation`: the value-class reasons, `:missing-array-api`,
    `:missing-array-signature`, `:array-access-denied`,
    `:failed-self-check`.
  - `:array-inspection`: `:missing-array-api`, `:missing-array-signature`,
    `:array-access-denied`.

  Each value equals the `:available?` and `:reason` entries of the cached
  result that definitions and array functions use. Probes that have not run
  yet run now; a probe defect propagates and no partial report is returned.

  The report reads the `value-type.fallback` policy before it runs a probe.
  The first read fixes the policy; later property changes have no effect.
  An invalid policy value throws IllegalArgumentException before any probe
  runs; no partial report is returned."
  []
  (let [fallback-policy (runtime/fallback-policy)
        public #(select-keys % [:available? :reason])]
    {:fallback-policy fallback-policy
     :value-class (public (capability/capability))
     :native-non-nil-field (public (field-capability/capability))
     :array-allocation (public (adapter/allocation-capability))
     :array-inspection (public (adapter/inspection-capability))}))
