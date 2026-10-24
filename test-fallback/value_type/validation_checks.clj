(ns value-type.validation-checks
  "Field validation checks using the real compiler."
  (:require [value-type.core]))

(defn- root-cause [error]
  (if-let [cause (ex-cause error)]
    (recur cause)
    error))

(defn check-fields
  "Check both mutable keys and false values without replacing collaborators."
  []
  (mapv
   (fn [[key enabled?]]
     (let [type-name (gensym "Field")
           form (list 'value-type.core/defvaluetype type-name
                      [(with-meta 'x {key enabled?})])]
       (try
         (let [cls (eval form)]
           {:key key :enabled? enabled? :final?
            (java.lang.reflect.Modifier/isFinal
             (.getModifiers (.getDeclaredField ^Class cls "x")))})
         (catch Throwable error
           {:key key :enabled? enabled?
            :error (:value-type/error (ex-data (root-cause error)))
            :absent (nil? (resolve type-name))}))))
   [[:volatile-mutable true] [:volatile-mutable false]
    [:unsynchronized-mutable true] [:unsynchronized-mutable false]]))

(defn invalid-forms
  "Return real compiler rejection for invalid deftype-compatible forms."
  []
  (mapv
   (fn [fields]
     (try
       (eval (list 'value-type.core/defvaluetype (gensym "Invalid") fields))
       :accepted
       (catch Throwable error
         (.getName (class (root-cause error))))))
   ['[__hash] '(x)]))
