(ns value-type.capability-rows
  "Expected capability reports (value-type.capability/report) of each
   runtime label of the test matrix. Requires only clojure.core.")

(defn- result [reason]
  (if reason {:available? false :reason reason} {:available? true}))

(def ^:private reasons
  "Runtime label to the reasons of [value-class native-non-nil-field
   array-allocation array-inspection]; nil is available."
  {:java8 [:missing-classfile-api :missing-classfile-api
           :missing-classfile-api :missing-array-api]
   :no-options [:unsupported-class-version :unsupported-class-version
                :unsupported-class-version :array-access-denied]
   :export-only [:unsupported-class-version :unsupported-class-version
                 :unsupported-class-version nil]
   :no-export [nil nil :array-access-denied :array-access-denied]
   :export [nil nil nil nil]
   :no-flattening [nil nil nil nil]})

(defn report
  "Return the expected report of runtime label."
  [label]
  (zipmap [:value-class :native-non-nil-field :array-allocation :array-inspection]
          (map result (reasons label))))
