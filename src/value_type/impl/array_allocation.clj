(ns value-type.impl.array-allocation
  "Optional direct calls, loaded only after allocation access checks pass."
  (:import (jdk.internal.value ValueClass)))

(set! *warn-on-reflection* true)

(defn value-class?
  "Return the runtime value status of component."
  [^Class component]
  (.isValue component))

(defn allocate
  "Allocate a native null-restricted atomic array. Native failures propagate."
  [^Class component length initial]
  (ValueClass/newNullRestrictedAtomicArray component (int length) initial))
