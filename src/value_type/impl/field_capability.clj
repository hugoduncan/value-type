(ns value-type.impl.field-capability
  "Baseline-safe probe of native non-nil field enforcement."
  (:require [value-type.impl.capability :as capability])
  (:import (clojure.lang DynamicClassLoader)
           (java.lang.reflect InvocationTargetException)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(defn- verify-enforcement
  [^Class leaf ^Class holder]
  (let [value (.newInstance (.getConstructor leaf (into-array Class [Long/TYPE]))
                            (object-array [(long 42)]))
        ctor (.getConstructor holder (into-array Class [leaf]))
        instance (.newInstance ctor (object-array [value]))
        readback (.get (.getField holder "value") instance)]
    (when-not (and (= leaf (.getType (.getField holder "value")))
                   (= value readback)
                   (= 42 (.getLong (.getField leaf "value") readback)))
      (throw (IllegalStateException. "Non-nil field probe readback invariant failed")))
    (let [rejected? (try
                      (.newInstance ctor (object-array [nil]))
                      false
                      (catch InvocationTargetException error
                        (if (instance? NullPointerException (.getCause error))
                          true
                          (throw error))))]
      (when-not rejected?
        (throw (IllegalStateException. "Non-nil field probe accepted null"))))
    {:available? true}))

(defn- probe
  []
  (let [major (capability/latest-major-version)
        prefix (str "value_type/probe/Fields_"
                    (.replace (str (UUID/randomUUID)) "-" "_"))
        leaf-name (str prefix "Leaf")
        holder-name (str prefix "Holder")
        loader (DynamicClassLoader.)
        leaf (.defineClass loader (.replace leaf-name "/" ".")
                           (capability/probe-bytes major leaf-name "J" false) nil)
        holder (.defineClass loader (.replace holder-name "/" ".")
                             (capability/probe-bytes major holder-name
                                                     (str "L" leaf-name ";") true)
                             nil)]
    (verify-enforcement leaf holder)))

(defn detect
  "Prove valid nested construction and native null rejection without exports.
   Inherit only the value probe's narrow API/version absence classifications.
   Field linkage, verification and enforcement failures remain errors."
  []
  (let [prerequisites (capability/detect)]
    (if (:available? prerequisites)
      (probe)
      prerequisites)))
