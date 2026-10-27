(ns value-type.array-layout-test
  (:require [clojure.test :refer [deftest is]]
            [value-type.array :as array]
            [value-type.core :refer [defvaluetype]])
  (:import (java.lang.invoke MethodHandles)))

(defvaluetype LayoutPoint [^long x])

(deftest flattening-control-test
  ;; This EA3 one-long layout responds to the explicit startup control.
  (let [disabled? (some #{"-XX:-UseArrayFlattening"}
                        (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean)))
        method (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                           "isFlatArray" (into-array Class [Object]))
        handle (.unreflect (MethodHandles/lookup) method)
        result (array/make-array LayoutPoint 3 (LayoutPoint. 7))]
    (is (= (not disabled?)
           (.invokeWithArguments handle ^java.util.List [result])))))
