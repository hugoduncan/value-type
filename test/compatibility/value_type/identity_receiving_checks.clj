(ns value-type.identity-receiving-checks
  "Baseline-safe checks of receiving and nested identity declarations. Each
   check runs in a cell, defines in the cell namespace (*ns*, read once on
   the cell thread) and returns inspected facts; a failure gives a failure
   map of cell-registry."
  (:require [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.cell-registry :as registry]
            [value-type.impl.capability :as capability]
            [value-type.transfer-checks :as transfer])
  (:import (clojure.lang Compiler RT)
           (java.lang.reflect Field)))

(defn- evaluate
  "Evaluate form in the cell namespace n."
  [n form]
  (binding [*ns* n]
    (eval form)))

(defn- resolved
  "Return the name of what sym resolves to in namespace n, else nil."
  [n sym]
  (some-> (ns-resolve n sym) str))

(defn- define
  "Return the definition map of f, a definition of simple-name in n: the
   result of (success Class) with :new-class? and :factory? (factory
   arguments args), or registry/failed-definition with failure-of (a
   failure map function of cell-registry)."
  [n simple-name failure-of f success & args]
  (registry/outcome #(registry/failed-definition failure-of % n simple-name)
                    f
                    (fn [^Class owner]
                      (merge (success owner)
                             (apply registry/defined n simple-name owner args)))))

(def holder-types
  "Holder type names of receiving-policy, in result order."
  '[FirstHolder SecondHolder])

(defn receiving-policy
  "Analyze a fixed generated identity expansion for each holder type
   (holder-types) in the cell namespace. Per holder, give the definition
   map: whether two calls of the analyzed function return the same Class
   and the field :type, or the failed definition (cell-registry/thrown
   with :absent?)."
  []
  (let [n *ns*]
    (mapv (fn [owner]
            (let [form (list 'value-type.core/identity-field-deftype
                             (registry/class-name n owner) [:generated] owner
                             [(with-meta 'value {:tag 'String :non-nil true})])
                  construct (delay (evaluate n (list 'fn [] form)))]
              (define n owner registry/thrown #(@construct)
                (fn [^Class owner-class]
                  {:same-class? (identical? owner-class (@construct))
                   :type (:type (checks/field-facts (.getField owner-class "value")))})
                "value")))
          holder-types)))

(defn incompatible-value
  "Evaluate a fixed value expansion. Give the receiver :capability and the
   :outcome: the definition map, or the root failure with :data
   (cell-registry/root-failure) and :absent?. Give the class name of
   Holder in the cell namespace."
  []
  (let [n *ns*
        holder (registry/class-name n 'Holder)]
    {:class-name holder
     :capability (capability/capability)
     :outcome (define n 'Holder registry/root-failure
                #(evaluate n (list 'value-type.core/value-field-deftype
                                   holder [:generated]
                                   'Holder [(with-meta 'value {:tag 'String :non-nil true})]))
                (constantly {:accepted true})
                "value")}))

(defn generated-value
  "Evaluate a value declaration with a String non-nil field. Give value?,
   the field facts (consumer-aot-checks/field-facts) and, per construction (direct, factory,
   reflective), the stored valid value and the outcomes of nil and 42, and
   the :definition map of Holder."
  []
  (let [n *ns*
        ^Class owner (evaluate n '(value-type.core/defvaluetype
                                    Holder [^String ^:non-nil value]))
        ^Field field (.getField owner "value")]
    (merge (checks/field-facts field)
           {:definition (registry/defined n 'Holder owner "valid")
            :value? (checks/value-class? owner)
            :constructions (checks/construction-results n owner field "valid" 42)})))

(def parent-types
  "Parent type names of the nested declarations, in result order."
  '[FirstParent SecondParent])

(defn- nested
  "Return per expand (transfer/expands) and per parent (parent-types) the
   result of (f expansion owner). expansion returns the expansion of the
   public declaration of owner with a non-nil Child field, expanded in the
   cell namespace n with a new Compiler/LOADER binding, as eval of each
   top-level form gives: a capture loader that the expansion installs stays
   in that frame and does not reach the later expansions and evaluations."
  [n f]
  (mapv (fn [expand]
          (let [expander (transfer/expanders expand)]
            (mapv (fn [owner]
                    (f #(with-bindings {#'*ns* n
                                        Compiler/LOADER (RT/makeClassLoader)}
                          (expander (list 'value-type.core/defvaluetype owner
                                          [(with-meta 'child {:tag 'Child :non-nil true})])))
                       owner))
                  parent-types)))
        transfer/expands))

(defn strict-nested
  "Define deftype Child, then expand nested public declarations. Per
   declaration, give the expansion outcome and what the parent and its
   factory resolve to. :child is the definition map of Child."
  []
  (let [n *ns*
        child (evaluate n '(deftype Child [optional]))]
    {:child (registry/defined n 'Child child nil)
     :detail (:detail (capability/capability))
     :declarations (nested n (fn [expansion owner]
                               {:outcome (registry/outcome registry/root-failure
                                                           #(do (expansion) :expanded)
                                                           identity)
                                :owner (resolved n owner)
                                :factory (resolved n (registry/factory-symbol owner))}))}))

(defn nested-warning-scope
  "Define value type Child, then evaluate nested public declarations. Per
   declaration, give the field type, its :definition map, :cell-loader?
   (Compiler/LOADER of the cell frame is unchanged after the expansion)
   and, per construction (direct, factory, reflective), whether the stored
   value is the input child and the outcomes of nil and 42. :child is the
   definition map of Child."
  []
  (let [n *ns*
        cell-loader @Compiler/LOADER
        ^Class child-class (evaluate n '(value-type.core/defvaluetype Child [optional]))
        child (evaluate n '(->Child nil))]
    {:child (registry/defined n 'Child child-class nil)
     :declarations
     (nested
      n
      (fn [expansion owner]
        (let [form (expansion)
              cell-loader? (identical? cell-loader @Compiler/LOADER)
              ^Class owner-class (evaluate n form)
              ^Field field (.getField owner-class "child")]
          {:definition (registry/defined n owner owner-class child)
           :cell-loader? cell-loader?
           :type (:type (checks/field-facts field))
           :constructions
           (checks/input-results n owner-class field child 42)})))}))
