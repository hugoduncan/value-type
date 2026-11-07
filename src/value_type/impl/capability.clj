(ns value-type.impl.capability
  "Baseline-safe checks for the value-class bytecode contract."
  (:import (clojure.lang DynamicClassLoader)
           (java.lang.reflect InvocationTargetException Method)))

(set! *warn-on-reflection* true)

(def value-predicate
  "The cached `Class::isValue` Method, or nil on a JVM with no value classes."
  (delay
    (try
      (.getMethod Class "isValue" (make-array Class 0))
      (catch NoSuchMethodException _ nil))))

(defn value-class?
  "True when cls is a value class. False when `Class::isValue` is missing."
  [^Class cls]
  (let [^Method predicate @value-predicate]
    (boolean (and predicate (.invoke predicate cls (object-array 0))))))

(defn- unavailable
  [reason ^Throwable error]
  {:available? false :reason reason :detail (.getMessage error)})

(defn- required-apis
  []
  (let [classfile (try
                    (Class/forName "java.lang.classfile.ClassFile" false
                                   (.getClassLoader Class))
                    (catch ClassNotFoundException error
                      (unavailable :missing-classfile-api error)))]
    (if (map? classfile)
      classfile
      (if-let [predicate @value-predicate]
        {:predicate predicate}
        (unavailable :missing-value-predicate
                     (NoSuchMethodException. "java.lang.Class.isValue()"))))))

(def ^:private not-value-class-detail
  "The JVM defined the probe class, but the probe class is not a value class.")

(def ^:private self-check-detail
  "The value-class probe failed the semantic self-check.")

(defn probe-instance-fn
  "Return a fn of a `long` that makes an instance of cls with its public
   `(long)` constructor. The fn removes `InvocationTargetException` and throws
   its cause."
  [^Class cls]
  (let [ctor (.getConstructor cls (into-array Class [Long/TYPE]))]
    (fn [n]
      (try
        (.newInstance ctor (object-array [(long n)]))
        (catch InvocationTargetException error
          (throw (.getCause error)))))))

(defn passes-self-check?
  "True when instances 42, 43 and 42 of cls have their own `value`, 42 and 43
   are not `identical?`, the two 42 instances are `identical?` and no instance
   is `identical?` to nil. cls has a public `long` field `value` and a public
   `(long)` constructor. Does not read the value predicate."
  [^Class cls]
  (let [make (probe-instance-fn cls)
        field (.getField cls "value")
        a (make 42)
        b (make 43)
        c (make 42)]
    (and (= 42 (.getLong field a))
         (= 43 (.getLong field b))
         (= 42 (.getLong field c))
         (not (identical? a b))
         (identical? a c)
         (not-any? nil? [a b c]))))

(defn classify
  "Classify the defined probe Class cls. Not a value class gives
   `:probe-not-value-class`; a failed `passes-self-check?` gives
   `:failed-self-check`; else `{:available? true :probe-class cls}`."
  [^Class cls]
  (cond
    (not (value-class? cls))
    {:available? false :reason :probe-not-value-class
     :detail not-value-class-detail}

    (not (passes-self-check? cls))
    {:available? false :reason :failed-self-check :detail self-check-detail}

    :else
    {:available? true :probe-class cls}))

(defn- probe
  []
  (let [define-probe-class (requiring-resolve
                            'value-type.impl.probe-builder/define-probe-class)
        klass (try
                (define-probe-class (DynamicClassLoader.) "CapabilityProbe" Long/TYPE false)
                (catch UnsupportedClassVersionError error
                  (unavailable :unsupported-class-version error)))]
    (if (map? klass)
      klass
      (classify klass))))

(def ^:private switch-property "value-type.value-classes")

(def ^:private disabled-detail
  "Value classes are disabled by value-type.value-classes=off.")

(defn- switch
  "Read the value-class switch. Absent or `auto` gives nil; `off` gives the
   `:disabled` result; other values throw IllegalArgumentException."
  []
  (let [value (System/getProperty switch-property "auto")]
    (case value
      "auto" nil
      "off" {:available? false :reason :disabled :detail disabled-detail}
      (throw (IllegalArgumentException.
              (str "Invalid " switch-property ": " (pr-str value)
                   "; expected auto or off"))))))

(defn detect
  "Return capability and an unavailable reason, without applying policy.
   The `value-type.value-classes` switch is read first: `off` gives
   `:disabled` and defines no probe class; an invalid value throws
   IllegalArgumentException. Only the switch, missing required APIs,
   known-probe version rejection and the D12 classified probe observations
   permit absence. All other failures propagate.
   No user class is defined by this check. An available result holds the probe
   Class as `:probe-class`."
  []
  (or (switch)
      (let [apis (required-apis)]
        (if (contains? apis :available?) apis (probe)))))

(def ^:private detected-capability (delay (detect)))

(defn capability
  "The cached value-class capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-capability)
