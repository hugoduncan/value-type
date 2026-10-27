(ns value-type.array
  "Native null-restricted atomic value arrays (experimental Valhalla API)."
  (:refer-clojure :exclude [make-array])
  (:require [value-type.impl.array :as adapter]))

(defn- checked-length
  [component length]
  (when-not (and (integer? length) (<= 0 length Integer/MAX_VALUE))
    (throw (ex-info "Array length must be an integer from 0 through Integer/MAX_VALUE"
                    {:value-type/error :invalid-array-length
                     :component component :length length :reason :out-of-range-or-non-integer})))
  (int length))

(defn make-array
  "Allocate a native null-restricted atomic array with every slot set to initial.

   Requires a supported Valhalla JVM, `--enable-preview` and
   `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. Checks capability,
   component, length and initial value in that order. The component must be a
   non-primitive runtime value class. Length must be an integer from zero
   through Integer/MAX_VALUE. A compatible non-null initial value is required
   even at length zero. Native allocation failures propagate.

   The caller owns the mutable slots. Flattening is not guaranteed. Atomic
   element access does not provide safe publication or compound-update safety."
  [component length initial]
  (let [methods (adapter/require-allocation)]
    (when-not (and (instance? Class component)
                   (not (.isPrimitive ^Class component))
                   (adapter/value-class? methods component))
      (throw (ex-info "Array component must be a non-primitive value class"
                      {:value-type/error :invalid-array-component
                       :component component :reason :not-reference-value-class})))
    (let [size (checked-length component length)]
      (when-not (and (some? initial) (.isInstance ^Class component initial))
        (throw (ex-info "Array initial value must be non-null and compatible with the component"
                        {:value-type/error :invalid-array-element
                         :component component :length length
                         :reason (if (nil? initial) :null-initial :incompatible-initial)})))
      (adapter/allocate methods component size initial))))

(defn array-info
  "Return actual properties of a reference array (experimental Valhalla API).

   Accepts ordinary and restricted reference arrays, including empty arrays.
   Returns the runtime Class as :component-class and boolean :component-value?, :null-restricted?,
   :atomic? and :flat? properties. Null, non-array and primitive-array inputs
   throw ExceptionInfo with :value-type/error :invalid-array before capability
   checks. Arrays whose components are arrays are accepted.

   Inspection requires a supported JVM and
   `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`, but ordinary
   reference-array inspection does not require preview. Missing support or
   access throws ExceptionInfo with :value-type/error :array-inspection-unavailable.
   Returns all five properties or fails; unknown facts are not false.
   Native failures propagate. Inspection does not change or retain the array.
   Flattening is not guaranteed. Atomic access does not provide safe publication,
   compound-update safety or caller synchronization."
  [array]
  (let [array-class (class array)
        component (when (and array-class (.isArray ^Class array-class))
                    (.getComponentType ^Class array-class))]
    ;; Native null queries crash EA3; validate before any optional API access.
    (when-not (and component (not (.isPrimitive ^Class component)))
      (throw (ex-info "Array inspection requires a reference array"
                      {:value-type/error :invalid-array
                       :reason :not-reference-array})))
    (adapter/inspect-array (adapter/require-inspection) array component)))
