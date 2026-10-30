(ns value-type.field-policy-checks
  "Baseline-safe checks of public non-nil field declarations under fallback
   policy. Each check runs twice and returns data; a failed attempt gives
   the root failure class and message."
  (:require [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.runtime :as runtime])
  (:import (java.lang.reflect Field)))

(defn- attempts
  "Return the capability detail and two outcomes of f: its result or the
   root failure."
  [f]
  {:detail (:detail (runtime/capability))
   :attempts (mapv (fn [_] (try (f) (catch Throwable failure (checks/failure failure))))
                   (range 2))})

(defn expansion
  "Macroexpand-1 a RequiredPublic declaration with a Long non-nil field.
   An attempt gives :expanded on success."
  []
  (attempts #(when (macroexpand-1 '(value-type.core/defvaluetype RequiredPublic
                                     [^Long ^:non-nil value]))
               :expanded)))

(defn evaluation
  "Evaluate an EvaluatedField declaration with a non-nil field of tag, then
   construct it directly, by factory and reflectively. Valid and invalid are
   field values. An attempt gives, per construction, the field type, the
   stored valid value and the outcomes of nil and invalid."
  [tag valid invalid]
  (binding [*ns* (the-ns 'value-type.field-policy-checks)]
    (attempts
     #(do (eval (list 'value-type.core/defvaluetype 'EvaluatedField
                      [(with-meta 'value {:tag tag :non-nil true})]))
          (let [^Class owner (resolve 'EvaluatedField)
                ^Field field (.getField owner "value")]
            (mapv (fn [build]
                    {:type (.getName (.getType field))
                     :stored (.get field (build [valid]))
                     :rejections [(checks/outcome (fn [] (build [nil])))
                                  (checks/outcome (fn [] (build [invalid])))]})
                  (checks/builders "value-type.field-policy-checks" owner)))))))
