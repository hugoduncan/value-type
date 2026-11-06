(ns value-type.capability
  "Read-only report of the optional JVM capabilities that value-type uses."
  (:require [value-type.impl.array :as adapter]
            [value-type.impl.capability :as capability]
            [value-type.impl.field-capability :as field-capability]))

(defn report
  "Return the capability results of this runtime as an EDN-printable map.

  Keys:
  - `:value-class`: this runtime can define value classes.
  - `:native-non-nil-field`: value classes use native restriction for
    eligible `:non-nil` fields.
  - `:array-allocation`: `make-array` and `into-array` can allocate.
  - `:array-inspection`: `array-info` can inspect.

  Each value is `{:available? true}` or `{:available? false :reason r}`.
  Reasons:
  - `:value-class`, `:native-non-nil-field`: `:missing-classfile-api`,
    `:missing-value-predicate`, `:unsupported-class-version`.
  - `:array-allocation`: the value-class reasons, `:missing-array-api`,
    `:missing-array-signature`, `:array-access-denied`.
  - `:array-inspection`: `:missing-array-api`, `:missing-array-signature`,
    `:array-access-denied`.

  Each value equals the `:available?` and `:reason` entries of the cached
  result that definitions and array functions use. Probes that have not run
  yet run now; a probe defect propagates and no partial report is returned.
  The report does not read the `value-type.fallback` policy."
  []
  (let [public #(select-keys % [:available? :reason])]
    {:value-class (public (capability/capability))
     :native-non-nil-field (public (field-capability/capability))
     :array-allocation (public (adapter/allocation-capability))
     :array-inspection (public (adapter/inspection-capability))}))
