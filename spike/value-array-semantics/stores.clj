(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [allocation semantic-checks]}}}} stores
  (:require [clojure.pprint :as pprint])
  (:import ArrayAccess))

(load-file "spike/value-array-semantics/semantic_checks.clj")

(load-file "spike/value-array-semantics/support/allocation.clj")

(def access-paths
  [{:name :clojure
    :length (fn [^objects array] (alength array))
    :read (fn [^objects array index] (aget array index))
    :write (fn [^objects array index value] (aset array index value))}
   {:name :java
    :length #(ArrayAccess/length %)
    :read #(ArrayAccess/read %1 %2)
    :write #(ArrayAccess/write %1 %2 %3)}])

(def replacements
  {:empty (allocation/->EmptyValue)
   :long (allocation/->LongValue 43)
   :pair (allocation/->PairValue 43 71)
   :reference-null (allocation/->ReferenceValue "replacement")
   :reference (allocation/->ReferenceValue nil)})

(defn store-cases
  "Check each write against a new array and retain all resulting slots."
  [fixture access length kind index]
  (let [{:keys [component seed]} fixture
        array (allocation/allocate component length seed)
        replacement (get replacements (:name fixture))
        value (case kind :valid replacement :null nil :wrong "wrong component")
        read-slots #(mapv (fn [slot] ((:read access) array slot)) (range length))
        before (read-slots)
        result (allocation/observe
                (keyword "store" (str (name (:name fixture)) "-" (name (:name access))
                                      "-" length "-" (name kind) "-" index))
                {:fixture (:name fixture) :access (:name access) :length length :store kind :index index}
                #(do ((:write access) array index value)
                     :stored))
        after (read-slots)]
    (assoc result :operation :array-store
           :checks {:length-matches? (= length ((:length access) array))
                    :initial-slots-match? (every? #(= seed %) before)
                    :final-slots-match? (= (if (= :valid kind)
                                             (assoc before index replacement)
                                             before) after)
                    :payload-changed? (not= before after)})))

;; Allocation evidence and checks must finish before dependent probes run.
(allocation/run-probe! (first *command-line-args*))

(let [cases (into [] (for [fixture allocation/fixtures access access-paths
                           length [1 3] kind [:valid :null :wrong] index (range length)]
                       (store-cases fixture access length kind index)))]
  (spit (second *command-line-args*)
        (with-out-str (pprint/pprint {:schema-version 1 :phase :stores :cases cases})))
  (semantic-checks/stores! cases))
