(ns value-type.capability-matrix-checks
  "Baseline-safe child checks of the capability report matrix. Loads no
   value-type namespace before the require order of the cell."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]))

(defn- report []
  ((resolve 'value-type.capability/report)))

(defn- owner-report
  "Return the report that the owner accessors give (:available? and
   :reason only) and the policy accessor."
  []
  (let [public #(select-keys ((resolve %)) [:available? :reason])]
    {:fallback-policy ((resolve 'value-type.impl.runtime/fallback-policy))
     :value-class (public 'value-type.impl.capability/capability)
     :native-non-nil-field (public 'value-type.impl.field-capability/capability)
     :array-allocation (public 'value-type.impl.array/allocation-capability)
     :array-inspection (public 'value-type.impl.array/inspection-capability)}))

(defn- definition
  "Define CapPoint (an ordinary value type). Return :value, :identity or
   the registry/failed-definition map of CapPoint (strict policy)."
  []
  (let [n *ns*]
    (registry/outcome
     #(registry/failed-definition % n 'CapPoint)
     #(eval '(value-type.core/defvaluetype CapPoint [^long x]))
     #(if (checks/value-class? %) :value :identity))))

(defn- native-field
  "Define CapHolder with one :non-nil CapPoint field. Return true when the
   field is native (NullRestricted)."
  []
  (let [c (eval '(value-type.core/defvaluetype CapHolder [^CapPoint ^:non-nil p]))]
    (checks/native-field? (.getField ^Class c "p"))))

(defn- array-call
  "Call f. Return its value, or the :value-type/error and :reason ex-data
   of an error thrown by f."
  [f error]
  (try (f) (catch clojure.lang.ExceptionInfo e
             (let [data (ex-data e)]
               (if (= error (:value-type/error data))
                 (select-keys data [:value-type/error :reason])
                 (throw e))))))

(defn- allocation [component seed]
  (array-call #(do ((resolve 'value-type.array/make-array) component 1 seed) :allocated)
              :array-unavailable))

(defn- inspection []
  (array-call #(do ((resolve 'value-type.array/array-info) (object-array 1)) :inspected)
              :array-inspection-unavailable))

(defn- outcomes
  "Return the definition and array outcomes of this runtime."
  []
  (let [defined (definition)
        rejected? (map? defined)
        component (if rejected? Object (resolve 'CapPoint))
        seed (when-not rejected? ((resolve '->CapPoint) 1))]
    {:definition defined
     :native (if rejected? :skipped (native-field))
     :allocation (allocation component seed)
     :inspection (inspection)}))

(defn check
  "Load the namespaces of require-order in order. When the order starts
   with value-type.capability, call report before any definition or array
   call; else after them. Then read the owner accessors (:owner) and call
   report again."
  [require-order]
  (run! require require-order)
  (let [report-first? (= 'value-type.capability (first require-order))
        early (when report-first? (report))
        result (outcomes)
        first-report (or early (report))]
    (assoc result
           :report first-report
           :owner (owner-report)
           :final (report))))
