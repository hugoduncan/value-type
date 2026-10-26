(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [allocation semantic-checks]}}}} diagnostics
  (:require [clojure.pprint :as pprint]))

(load-file "spike/value-array-semantics/semantic_checks.clj")

(load-file "spike/value-array-semantics/support/allocation.clj")

(def queries ["isFlatArray" "isNullRestrictedArray" "isAtomicArray"])

(defn inputs
  "Build real arrays and invalid query arguments independently of diagnostics."
  []
  (into [{:name :object :array (object-array 3)}
         {:name :identity :array (make-array allocation.IdentityValue 3)}
         {:name :primitive :array (long-array 3)}
         {:name :null :array nil}
         {:name :non-array :array "not an array"}]
        cat
        (mapv (fn [{:keys [name component seed]}]
                (into [{:name (keyword (str (clojure.core/name name) "-ordinary"))
                        :array (make-array component 3)}]
                      (mapv (fn [length]
                              {:name (keyword (str (clojure.core/name name) "-restricted-" length))
                               :restricted? true
                               :array (allocation/allocate component length seed)})
                            [0 1 3])))
              allocation/fixtures)))

(defn diagnostic-case
  "Retain each query result separately, including false and exceptions."
  [{:keys [name array restricted?]} query]
  (assoc (allocation/observe
          (keyword "diagnostic" (str (clojure.core/name name) "-" query))
          {:input name :query query :restricted? (boolean restricted?)
           :class (when array (.getName (class array)))}
          #(.invoke (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                                query (into-array Class [Object]))
                    nil (object-array [array])))
         :operation :array-diagnostic))

;; Allocation evidence and checks must finish before dependent probes run.
(allocation/run-probe! (first *command-line-args*))

(let [selector (nth *command-line-args* 2 nil)
      query-selection (nth *command-line-args* 3 nil)
      selected (filterv #(if selector
                           (= selector (name (:name %)))
                           (not (contains? #{:null :non-array} (:name %))))
                        (inputs))
      cases (into [] (for [input selected
                           query (if query-selection [query-selection] queries)]
                       (do (println :invoking (:name input) query)
                           (flush)
                           (diagnostic-case input query))))]
  (spit (second *command-line-args*)
        (with-out-str (pprint/pprint {:schema-version 1 :phase :diagnostics :cases cases})))
  (if selector
    (assert (and (= 1 (count cases))
                 (= :pass (:status (first cases)))
                 (false? (:value (first cases)))) "Unexpected non-array diagnostic")
    (semantic-checks/diagnostics! cases)))
