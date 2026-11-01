(ns value-type.validation-checks
  "Field validation checks using the real compiler."
  (:require [value-type.cell-registry :as registry]
            [value-type.core]))

(defn check-fields
  "Check both mutable keys and false values without replacing collaborators.
   Define in *ns*; each entry has :key and :enabled?, merged with the
   definition map, or with the failed definition (cell-registry/root-failure
   and :absent?)."
  []
  (mapv
   (fn [[key enabled?]]
     (let [type-name (gensym "Field")
           form (list 'value-type.core/defvaluetype type-name
                      [(with-meta 'x {key enabled?})])]
       (merge {:key key :enabled? enabled?}
              (registry/outcome
               #(registry/failed-definition registry/root-failure % *ns* type-name)
               #(eval form)
               (fn [^Class cls]
                 (assoc (registry/defined *ns* type-name cls nil)
                        :final? (java.lang.reflect.Modifier/isFinal
                                 (.getModifiers (.getDeclaredField cls "x")))))))))
   [[:volatile-mutable true] [:volatile-mutable false]
    [:unsynchronized-mutable true] [:unsynchronized-mutable false]]))

(def invalid-fields
  "Field vectors that defvaluetype rejects as deftype-compatible forms, in
   check order: pairs of the field vector and a text that the root message
   names."
  [['[__hash] "cannot be used as field names"]
   ['(x) "No fields vector given"]])

(defn invalid-forms
  "Return real compiler rejection for each field vector of invalid-fields,
   in *ns*: :accepted, or the failed definition (cell-registry/failure and
   :absent?)."
  []
  (mapv
   (fn [[fields]]
     (let [type-name (gensym "Invalid")]
       (registry/outcome
        #(registry/failed-definition registry/failure % *ns* type-name)
        #(eval (list 'value-type.core/defvaluetype type-name fields)))))
   invalid-fields))
