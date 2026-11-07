(ns value-type.impl.array
  "Lazy, baseline-safe access to the optional native array allocator."
  (:require [value-type.impl.capability :as capability])
  (:import (java.lang.invoke MethodHandles)
           (java.lang.reflect Array InvocationTargetException Method Modifier)))

(set! *warn-on-reflection* true)

(defn- invoke
  [^Method method target arguments]
  (try
    (.invoke method target (object-array arguments))
    (catch InvocationTargetException error
      (throw (.getCause error)))))

(defn passes-round-trip?
  "True when a null-restricted array of length 2, allocated by allocator with
   initial value `a` (42), then given `b` (43) at index 1, reads back `a` at
   index 0 and `b` at index 1 with their own `value`. cls has a public `long`
   field `value` and a public `(long)` constructor. Exceptions propagate
   without `InvocationTargetException`."
  [^Method allocator ^Class cls]
  (let [make (capability/probe-instance-fn cls)
        field (.getField cls "value")
        a (make 42)
        b (make 43)
        array (invoke allocator nil [cls (int 2) a])]
    (Array/set array 1 b)
    (let [e0 (Array/get array 0)
          e1 (Array/get array 1)]
      (and (identical? e0 a)
           (= 42 (.getLong field e0))
           (identical? e1 b)
           (= 43 (.getLong field e1))))))

(def ^:private round-trip-detail
  "The value-array round trip failed the semantic self-check.")

(defn- allocation-methods
  "Resolve the allocator and the value predicate, and check access without
   changing module access. Missing APIs, signatures and access give
   classified absences."
  []
  (try
    (let [allocator (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                                "newNullRestrictedAtomicArray"
                                (into-array Class [Class Integer/TYPE Object]))
          predicate @capability/value-predicate]
      (.unreflect (MethodHandles/lookup) allocator)
      {:available? true :allocator allocator :predicate predicate})
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
      (let [result (allocation-methods)]
        (if (and (:available? result)
                 (not (passes-round-trip? (:allocator result) probe-class)))
          {:available? false :reason :failed-self-check :detail round-trip-detail}
          result)))))

(def ^:private detected-allocation-capability (delay (discover)))

(defn allocation-capability
  "The cached allocation capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-allocation-capability)

(defn require-allocation
  "Return cached allocation methods, or throw a capability error."
  []
  (let [{:keys [available? cause reason detail] :as result} (allocation-capability)]
    (when-not available?
      (throw (ex-info
              (if (= :failed-self-check reason)
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
  [methods component]
  (true? (invoke (:predicate methods) component [])))

(defn allocate
  "Allocate without retaining the array or translating native failures."
  [methods component length initial]
  (invoke (:allocator methods) nil [component length initial]))

(defn- inspection-method
  [^Class owner method-name parameters static?]
  (let [method (.getMethod owner method-name (into-array Class parameters))]
    (when-not (and (= Boolean/TYPE (.getReturnType method))
                   (= static? (Modifier/isStatic (.getModifiers method))))
      (throw (NoSuchMethodException. (str owner "/" method-name " signature"))))
    (.unreflect (MethodHandles/lookup) method)
    method))

(defn- discover-inspection
  []
  (try
    (let [owner (Class/forName "jdk.internal.value.ValueClass")]
      {:available? true
       :predicate (inspection-method Class "isValue" [] false)
       :null-restricted? (inspection-method owner "isNullRestrictedArray" [Object] true)
       :atomic? (inspection-method owner "isAtomicArray" [Object] true)
       :flat? (inspection-method owner "isFlatArray" [Object] true)})
    (catch ClassNotFoundException error
      {:available? false :reason :missing-array-api :cause error})
    (catch NoSuchMethodException error
      {:available? false :reason :missing-array-signature :cause error})
    (catch IllegalAccessException error
      {:available? false :reason :array-access-denied :cause error})))

(def ^:private detected-inspection-capability (delay (discover-inspection)))

(defn inspection-capability
  "The cached inspection capability result of this runtime.
   Every reader reads through this var; tests replace it with `with-redefs`."
  []
  @detected-inspection-capability)

(defn require-inspection
  "Return cached inspection methods, or throw a capability error."
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
  [methods array component]
  {:component-class component
   :component-value? (value-class? methods component)
   :null-restricted? (invoke (:null-restricted? methods) nil [array])
   :atomic? (invoke (:atomic? methods) nil [array])
   :flat? (invoke (:flat? methods) nil [array])})
