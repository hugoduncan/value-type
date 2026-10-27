(ns value-type.impl.array
  "Lazy, baseline-safe access to the optional native array allocator."
  (:require [value-type.impl.capability :as capability])
  (:import (java.lang.invoke MethodHandles)
           (java.lang.reflect InvocationTargetException Method)))

(defn- discover
  []
  (let [prerequisites (capability/detect)]
    (if-not (:available? prerequisites)
      prerequisites
      (try
        (let [allocator (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                                    "newNullRestrictedAtomicArray"
                                    (into-array Class [Class Integer/TYPE Object]))
              predicate (.getMethod Class "isValue" (make-array Class 0))]
          ;; Check access without invoking allocation or changing module access.
          (.unreflect (MethodHandles/lookup) allocator)
          {:available? true :allocator allocator :predicate predicate})
        (catch ClassNotFoundException error
          {:available? false :reason :missing-array-api :cause error})
        (catch NoSuchMethodException error
          {:available? false :reason :missing-array-signature :cause error})
        (catch IllegalAccessException error
          {:available? false :reason :array-access-denied :cause error})))))

(def ^:private allocation-capability (delay (discover)))

(defn require-allocation
  "Return cached allocation methods, or throw a capability error."
  []
  (let [{:keys [available? cause] :as result} @allocation-capability]
    (when-not available?
      (throw (ex-info
              (str "Value-array allocation requires a supported Valhalla JVM, "
                   "--enable-preview and "
                   "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")
              (assoc (dissoc result :cause :available?)
                     :value-type/error :array-unavailable)
              cause)))
    result))

(defn- invoke
  [^Method method target arguments]
  (try
    (.invoke method target (object-array arguments))
    (catch InvocationTargetException error
      (throw (.getCause error)))))

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
                   (= static? (java.lang.reflect.Modifier/isStatic (.getModifiers method))))
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

(def ^:private inspection-capability (delay (discover-inspection)))

(defn require-inspection
  "Return cached inspection methods, or throw a capability error."
  []
  (let [{:keys [available? cause] :as result} @inspection-capability]
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
