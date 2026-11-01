(ns value-type.function-aot-checks
  "Baseline-safe checks of a source-free saved install function: the :ns
   of a spec defines install, which defines the :owner Holder."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks])
  (:import (java.lang.reflect Field)))

(defn- inspect-holder
  "Construct the :owner Holder of spec through its :ns factory from the
   :valid checked values (through :arguments); return its :checked field
   facts (field-facts with :stored) and the :invalid outcomes."
  [{:keys [ns owner checked arguments valid invalid]}]
  (let [factory (resolve (registry/factory-symbol ns (last (.split ^String owner "\\."))))
        construct #(apply factory (apply arguments %))
        instance (construct valid)
        owner-class (class instance)]
    {:value? (checks/value-class? owner-class)
     :fields (into {}
                   (map (fn [field-name]
                          (let [^Field field (.getField owner-class field-name)]
                            [field-name
                             (assoc (checks/field-facts field) :stored (.get field instance))])))
                   checked)
     :rejections (mapv (fn [values] (checks/outcome #(construct values))) invalid)}))

(defn invoke-install
  "Require the :ns of spec and invoke its install function twice.

   Return absence of the :source of spec, loading output and, per
   invocation, the Holder facts of spec (see `consumer-aot-checks`) or the
   root install failure (class and message). Inspection failures
   propagate."
  [{:keys [source ns] :as spec}]
  (let [loading (java.io.StringWriter.)]
    (binding [*err* loading] (require (symbol ns)))
    {:source-absent? (checks/source-absent? source)
     :loading-err (str loading)
     :invocations
     (registry/twice (fn []
                       (let [installed (registry/outcome registry/failure
                                                         #((resolve (symbol ns "install"))))]
                         (if (= :accepted installed)
                           (inspect-holder spec)
                           installed))))}))

(defn invoke-install-form
  "Return a child form whose value is the invoke-install result for spec
   (see `consumer-aot-checks`); its :arguments give the arguments of the
   function Holder."
  [spec]
  (registry/check-form 'value-type.function-aot-checks/invoke-install spec))

(defn holder-invocation
  "Return the expected inspect-holder facts of spec: value?, fields (the
   field facts of each :checked field) with :stored from :valid, and
   spec-rejections."
  [spec fields value?]
  {:value? value?
   :fields (into {}
                 (map (fn [[field-name stored]]
                        [field-name (assoc (get fields field-name) :stored stored)]))
                 (zipmap (:checked spec) (:valid spec)))
   :rejections (checks/spec-rejections spec)})

(defn install-result
  "Return the expected invoke-install result: invocation for each attempt."
  [invocation]
  {:source-absent? true :loading-err "" :invocations (registry/each-attempt invocation)})
