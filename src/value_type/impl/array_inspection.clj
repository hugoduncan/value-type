(ns value-type.impl.array-inspection
  "Optional direct calls, loaded only after inspection access checks pass."
  (:import (jdk.internal.value ValueClass)))

(set! *warn-on-reflection* true)

(defn value-class?
  "Return the runtime value status of component."
  [^Class component]
  (.isValue component))

(defn inspect-array
  "Return all five facts for a validated reference array."
  [array component]
  {:component-class component
   :component-value? (value-class? component)
   :null-restricted? (ValueClass/isNullRestrictedArray array)
   :atomic? (ValueClass/isAtomicArray array)
   :flat? (ValueClass/isFlatArray array)})
