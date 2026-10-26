(require '[clojure.pprint :as pprint]
         '[value-type.object-size :as sizes])
(import '[jdk.internal.value ValueClass])

(let [results
      (mapv
       (fn [[^Class component initial]]
         {:class (.getName component)
          :layouts
          (mapv (fn [[kind a]]
                  {:kind kind :flat? (ValueClass/isFlatArray a)
                   :atomic? (ValueClass/isAtomicArray a)
                   :null-restricted? (ValueClass/isNullRestrictedArray a)})
                [[:ordinary (make-array component 10)]
                 [:restricted-atomic (ValueClass/newNullRestrictedAtomicArray component 10 initial)]
                 [:restricted-non-atomic (ValueClass/newNullRestrictedNonAtomicArray component 10 initial)]])})
       [[value_type.object_size.Value1 (sizes/->Value1 0)]
        [value_type.object_size.Value2 (sizes/->Value2 0 0)]])]
  (pprint/pprint {:java (System/getProperty "java.runtime.version") :results results}))
