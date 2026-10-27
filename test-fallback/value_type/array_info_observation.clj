(ns value-type.array-info-observation
  "Independent, non-null JDK observations for diagnostic startup tests."
  (:import (java.lang.reflect Method Modifier)))

(defn- observe-method
  [^Class owner method-name parameter-types receiver arguments]
  (try
    (let [^Method method (.getMethod owner method-name
                                     (into-array Class parameter-types))]
      {:parameters (mapv #(.getName ^Class %) (.getParameterTypes method))
       :return (.getName (.getReturnType method))
       :static? (Modifier/isStatic (.getModifiers method))
       :invocation (try
                     {:value (.invoke method receiver (object-array arguments))}
                     (catch IllegalAccessException e
                       {:exception (.getName (class e))}))})
    (catch NoSuchMethodException e
      {:exception (.getName (class e))})))

(defn observe
  "Read signatures and invoke only on an ordinary, non-null Object array.
   Do not use the production adapter or change reflective access."
  []
  {:component-value
   (observe-method Class "isValue" [] Object [])
   :array
   (try
     (let [owner (Class/forName "jdk.internal.value.ValueClass")
           input (object-array 1)]
       (into {}
             (map (fn [method-name]
                    [method-name (observe-method owner method-name [Object]
                                                 nil [input])]))
             ["isNullRestrictedArray" "isAtomicArray" "isFlatArray"]))
     (catch ClassNotFoundException e
       {:exception (.getName (class e))}))})
