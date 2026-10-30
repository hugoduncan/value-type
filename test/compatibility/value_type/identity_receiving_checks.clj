(ns value-type.identity-receiving-checks
  "Baseline-safe checks of receiving and nested identity declarations. Each
   check returns inspected facts; a failure gives the root failure class and
   message (consumer-aot-checks/failure)."
  (:require [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.runtime :as runtime])
  (:import (java.lang.reflect Field)))

(def ^:private ns-name* "value-type.identity-receiving-checks")

(defn- evaluate
  "Evaluate form in this namespace."
  [form]
  (binding [*ns* (the-ns (symbol ns-name*))]
    (eval form)))

(defn- resolved
  "Return the name of what sym resolves to in this namespace, else nil."
  [sym]
  (some-> (ns-resolve (the-ns (symbol ns-name*)) sym) str))

(defn- attempt
  "Return the result of f, else its root failure with the root ex-data."
  [f]
  (try (f)
       (catch Throwable thrown
         (let [root (loop [^Throwable cause thrown]
                      (if-let [nested (.getCause cause)] (recur nested) cause))]
           (assoc (checks/failure thrown) :data (ex-data root))))))

(defn receiving-policy
  "Analyze a fixed generated identity expansion for FirstHolder and
   SecondHolder. Per holder, give whether two evaluations return the same
   Class and the storage type, or the root failure."
  []
  (mapv (fn [owner]
          (let [form (list 'value-type.core/identity-field-deftype
                           (str (namespace-munge ns-name*) "." owner) [:generated] owner
                           [(with-meta 'value {:tag 'String :non-nil true})])]
            (attempt #(let [construct (evaluate (list 'fn [] form))
                            ^Class owner-class (construct)]
                        {:same-class? (identical? owner-class (construct))
                         :storage (.getName (.getType (.getField owner-class "value")))}))))
        '[FirstHolder SecondHolder]))

(defn incompatible-value
  "Evaluate a fixed value expansion. Give the receiver :capability and the
   :outcome, :accepted or the root failure."
  []
  {:capability (runtime/capability)
   :outcome (attempt #(do (evaluate '(value-type.core/value-field-deftype
                                      "value_type.identity_receiving_checks.Holder" [:generated]
                                      Holder [^String ^:non-nil value]))
                          :accepted))})

(defn generated-value
  "Evaluate a value declaration with a String non-nil field. Give value?,
   storage, annotation names and, per construction (direct, factory,
   reflective), the stored valid value and the outcomes of nil and 42."
  []
  (let [^Class owner (evaluate '(value-type.core/defvaluetype
                                  Holder [^String ^:non-nil value]))
        ^Field field (.getField owner "value")]
    {:value? (checks/value-class? owner)
     :storage (.getName (.getType field))
     :annotations (checks/annotation-names field)
     :constructions (mapv (fn [build]
                            {:stored (.get field (build ["valid"]))
                             :rejections [(checks/outcome #(build [nil]))
                                          (checks/outcome #(build [42]))]})
                          (checks/builders ns-name* owner))}))

(defn- nested
  "Return per expansion (macroexpand, macroexpand-1) and per parent
   (FirstParent, SecondParent) the result of (f expand owner). Expand
   returns the expansion of the public declaration of owner with a non-nil
   Child field."
  [f]
  (mapv (fn [expander]
          (mapv (fn [owner]
                  (f #(evaluate (list expander
                                      (list 'quote
                                            (list 'value-type.core/defvaluetype owner
                                                  [(with-meta 'child {:tag 'Child :non-nil true})]))))
                     owner))
                '[FirstParent SecondParent]))
        '[macroexpand macroexpand-1]))

(defn strict-nested
  "Define deftype Child, then expand nested public declarations. Per
   declaration, give the expansion outcome and what the parent and its
   factory resolve to."
  []
  (evaluate '(deftype Child [optional]))
  {:detail (:detail (runtime/capability))
   :declarations (nested (fn [expand owner]
                           {:outcome (attempt #(do (expand) :expanded))
                            :owner (resolved owner)
                            :factory (resolved (symbol (str "->" owner)))}))})

(defn nested-warning-scope
  "Define value type Child, then evaluate nested public declarations. Per
   declaration, give the field type, the class of the stored child, whether
   it is the input child, its optional value and, per construction (direct,
   factory, reflective), the outcomes of nil and 42."
  []
  (let [^Class child-class (evaluate '(value-type.core/defvaluetype Child [optional]))
        child (evaluate '(->Child nil))]
    (nested
     (fn [expand _]
       (let [^Class owner (evaluate (expand))
             ^Field field (.getField owner "child")
             builders (checks/builders ns-name* owner)
             stored (.get field ((second builders) [child]))]
         {:type (.getName (.getType field))
          :stored (.getName (class stored))
          :input? (identical? child stored)
          :optional (.get (.getField child-class "optional") stored)
          :rejections (mapv (fn [build]
                              [(checks/outcome #(build [nil]))
                               (checks/outcome #(build [42]))])
                            builders)})))))
