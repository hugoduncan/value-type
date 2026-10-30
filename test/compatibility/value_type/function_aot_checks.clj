(ns value-type.function-aot-checks
  "Baseline-safe checks of a source-free saved checked.aot-fixture/install."
  (:require [clojure.java.io :as io]
            [value-type.consumer-aot-checks :as checks])
  (:import (java.lang.reflect Field)))

(defn- inspect-holder
  "Construct Holder from valid; return its field facts and invalid outcomes."
  [field-names valid invalid]
  (let [factory (resolve 'checked.aot-fixture/->Holder)
        instance (apply factory valid)
        owner (class instance)]
    {:value? (checks/value-class? owner)
     :fields (into {}
                   (map (fn [field-name]
                          (let [^Field field (.getField owner field-name)]
                            [field-name
                             {:type (.getName (.getType field))
                              :annotations (checks/annotation-names field)
                              :value (.get field instance)}])))
                   field-names)
     :rejections (mapv (fn [arguments] (checks/outcome #(apply factory arguments))) invalid)}))

(defn invoke-install
  "Require checked.aot-fixture and invoke its install function twice.

   Return source absence, loading output and, per invocation, the Holder
   facts or the root install failure (class and message). Inspection
   failures propagate."
  [field-names valid invalid]
  (let [loading (java.io.StringWriter.)]
    (binding [*err* loading] (require 'checked.aot-fixture))
    {:source-absent? (nil? (io/resource "checked/aot_fixture.clj"))
     :loading-err (str loading)
     :invocations
     (mapv (fn [_]
             (let [installed (try ((resolve 'checked.aot-fixture/install)) :accepted
                                  (catch Throwable thrown (checks/failure thrown)))]
               (if (= :accepted installed)
                 (inspect-holder field-names valid invalid)
                 installed)))
           (range 2))}))

(defn invoke-install-form
  "Return a child form that prints the invoke-install result for the arguments."
  [field-names valid invalid]
  (list 'do
        '(require 'value-type.function-aot-checks)
        (list 'prn (list 'value-type.function-aot-checks/invoke-install
                         field-names valid invalid))))

(defn install-result
  "Return the expected invoke-install result with invocation repeated twice."
  [invocation]
  {:source-absent? true :loading-err "" :invocations (vec (repeat 2 invocation))})
