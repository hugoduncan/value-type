(ns value-type.impl.field-capability
  "Baseline-safe probe of native non-nil field enforcement."
  (:require [value-type.impl.capability :as capability]
            [value-type.impl.compiler :as compiler])
  (:import (java.lang.reflect InvocationTargetException)))

(set! *warn-on-reflection* true)

(def ^:private restriction-ignored-detail
  "The JVM accepted nil in a NullRestricted probe field.")

(defn- verify-enforcement
  [^Class leaf ^Class holder]
  (let [value ((capability/probe-instance-fn leaf) 42)
        ctor (.getConstructor holder (into-array Class [leaf]))
        holder-field (.getField holder "value")
        leaf-field (.getField leaf "value")
        instance (.newInstance ctor (object-array [value]))
        readback (.get holder-field instance)]
    (when-not (and (= leaf (.getType holder-field))
                   (= value readback)
                   (= 42 (.getLong leaf-field readback)))
      (throw (IllegalStateException. "Non-nil field probe readback invariant failed")))
    (let [rejected? (try
                      (.newInstance ctor (object-array [nil]))
                      false
                      (catch InvocationTargetException error
                        (if (instance? NullPointerException (.getCause error))
                          true
                          (throw error))))]
      (if rejected?
        {:available? true}
        {:available? false
         :reason :native-restriction-ignored
         :detail restriction-ignored-detail}))))

(defn- probe
  []
  (let [define-probe-class (requiring-resolve
                            'value-type.impl.probe-builder/define-probe-class)
        loader (compiler/probe-loader)
        leaf (define-probe-class loader "FieldsLeaf" Long/TYPE false)
        holder (define-probe-class loader "FieldsHolder" leaf true)]
    (verify-enforcement leaf holder)))

(defn detect
  "Prove valid nested construction and native null rejection without exports.
   Inherit the value probe's classified absences. A JVM that accepts nil in a
   NullRestricted field gives :native-restriction-ignored. Field linkage and
   verification failures remain errors."
  []
  (let [prerequisites (capability/capability)]
    (if (:available? prerequisites)
      (probe)
      prerequisites)))

(def ^:private detected-field-capability (delay (detect)))

(defn capability
  "The cached native non-nil field capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-field-capability)

(defn require-field-capability!
  "Require native field enforcement independently of ordinary fallback policy.
   Return nil on support; otherwise throw a structured definition error. Probe
   defects propagate unchanged. This guard is safe to call again in receivers."
  [class-name]
  (let [{:keys [available? reason detail]} (capability)]
    (when-not available?
      (throw
       (ex-info
        (str "Cannot define " class-name
             ": native non-nil field enforcement is unavailable. " detail)
        {:value-type/error :field-capability-unavailable
         :class-name class-name
         :reason reason})))))
