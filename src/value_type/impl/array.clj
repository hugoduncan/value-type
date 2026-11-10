(ns value-type.impl.array
  "Lazy, baseline-safe access to the optional native array allocator."
  (:require [value-type.impl.capability :as capability])
  (:import (java.lang.invoke MethodHandles)
           (java.lang.reflect Array Modifier)))

(set! *warn-on-reflection* true)

(defn passes-round-trip?
  "True when a null-restricted array of length 2, allocated by allocator with
   initial value `a` (42), then given `b` (43) at index 1, reads back `a` at
   index 0 and `b` at index 1 with their own `value`. cls has a public `long`
   field `value` and a public `(long)` constructor. Exceptions propagate
   without `InvocationTargetException`."
  [allocator ^Class cls]
  (let [make (capability/probe-instance-fn cls)
        field (.getField cls "value")
        a (make 42)
        b (make 43)
        array (allocator cls 2 a)]
    (Array/set array 1 b)
    (let [e0 (Array/get array 0)
          e1 (Array/get array 1)]
      (and (identical? e0 a)
           (= 42 (.getLong field e0))
           (identical? e1 b)
           (= 43 (.getLong field e1))))))

(def ^:private round-trip-detail
  "The value-array round trip failed the semantic self-check.")

(defn- check-allocation-access
  "Check the allocator API, value predicate and access without
   changing module access. Missing APIs, signatures and access give
   classified absences."
  []
  (try
    (let [allocator (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                                "newNullRestrictedAtomicArray"
                                (into-array Class [Class Integer/TYPE Object]))]
      @capability/value-predicate
      (.unreflect (MethodHandles/lookup) allocator)
      {:available? true})
    (catch ClassNotFoundException error
      {:available? false :reason :missing-array-api :cause error})
    (catch NoSuchMethodException error
      {:available? false :reason :missing-array-signature :cause error})
    (catch IllegalAccessException error
      {:available? false :reason :array-access-denied :cause error})))

(defn- discover
  []
  (let [{:keys [probe-class] :as prerequisites} (capability/capability)]
    (cond
      (not (:available? prerequisites))
      prerequisites

      (nil? probe-class)
      (throw (IllegalStateException.
              "Available value-class capability has no :probe-class."))

      :else
      (let [result (check-allocation-access)]
        (if-not (:available? result)
          result
          (let [allocator (requiring-resolve 'value-type.impl.array-allocation/allocate)
                predicate (requiring-resolve 'value-type.impl.array-allocation/value-class?)]
            (if (passes-round-trip? allocator probe-class)
              {:available? true :allocator allocator :predicate predicate}
              {:available? false :reason :failed-self-check :detail round-trip-detail})))))))

(def ^:private detected-allocation-capability (delay (discover)))

(defn allocation-capability
  "The cached allocation capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-allocation-capability)

(defn require-allocation
  "Return cached allocation functions, or throw a capability error."
  []
  (let [{:keys [available? cause reason detail] :as result} (allocation-capability)]
    (when-not available?
      (throw (ex-info
              (if (#{:failed-self-check :disabled} reason)
                (str "Value-array allocation is unavailable. " detail)
                (str "Value-array allocation requires a supported Valhalla JVM, "
                     "--enable-preview and "
                     "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"))
              (assoc (dissoc result :cause :available?)
                     :value-type/error :array-unavailable)
              cause)))
    result))

(defn value-class?
  "Query runtime value status using the resolved predicate."
  [operations component]
  (true? ((:predicate operations) component)))

(defn allocate
  "Allocate without retaining the array or translating native failures."
  [operations component length initial]
  ((:allocator operations) component length initial))

(defn- check-inspection-method-access
  [^Class owner method-name parameters static?]
  (let [method (.getMethod owner method-name (into-array Class parameters))]
    (when-not (and (= Boolean/TYPE (.getReturnType method))
                   (= static? (Modifier/isStatic (.getModifiers method))))
      (throw (NoSuchMethodException. (str owner "/" method-name " signature"))))
    (.unreflect (MethodHandles/lookup) method)))

(defn- check-inspection-access
  []
  (try
    (let [owner (Class/forName "jdk.internal.value.ValueClass")]
      (check-inspection-method-access Class "isValue" [] false)
      (check-inspection-method-access owner "isNullRestrictedArray" [Object] true)
      (check-inspection-method-access owner "isAtomicArray" [Object] true)
      (check-inspection-method-access owner "isFlatArray" [Object] true)
      {:available? true})
    (catch ClassNotFoundException error
      {:available? false :reason :missing-array-api :cause error})
    (catch NoSuchMethodException error
      {:available? false :reason :missing-array-signature :cause error})
    (catch IllegalAccessException error
      {:available? false :reason :array-access-denied :cause error})))

(defn- discover-inspection
  []
  (let [result (check-inspection-access)]
    (if (:available? result)
      {:available? true
       :inspect-array (requiring-resolve 'value-type.impl.array-inspection/inspect-array)}
      result)))

(def ^:private detected-inspection-capability (delay (discover-inspection)))

(defn inspection-capability
  "The cached inspection capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-inspection-capability)

(defn require-inspection
  "Return the cached inspection function, or throw a capability error."
  []
  (let [{:keys [available? cause] :as result} (inspection-capability)]
    (when-not available?
      (throw (ex-info
              (str "Array inspection requires a supported Valhalla JVM and "
                   "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")
              (assoc (dissoc result :cause :available?)
                     :value-type/error :array-inspection-unavailable)
              cause)))
    result))

(defn inspect-array
  "Inspect a validated reference array without retaining it."
  [operations array component]
  ((:inspect-array operations) array component))
