(ns value-type.impl.jvm-format
  "The one owner of the JEP 401 class-format rules (D15): flag values, the
  version rule and the value-constructor order rule. Imports
  `java.lang.classfile`, so load it only behind the D12 API checks."
  (:import
   (java.lang.classfile ClassFile)))

(set! *warn-on-reflection* true)

(def ^:private default-row
  {:major (ClassFile/latestMajorVersion)
   :minor ClassFile/PREVIEW_MINOR_VERSION
   :identity-class-flag 0x20
   :strict-field-flag 0x800
   :constructor-order :fields-before-super})

(defn row
  "Return the complete format row of the running JVM:
  `{:major :minor :identity-class-flag :strict-field-flag
  :constructor-order}`. Only the default row exists."
  []
  default-row)

(defn require-constructor-order!
  "Throw `IllegalStateException` when the `:constructor-order` of
  format-row is not the supported order (fields before super). Return nil."
  [{:keys [constructor-order]}]
  (when-not (= :fields-before-super constructor-order)
    (throw (IllegalStateException.
            (str "Unsupported value-constructor order: " constructor-order)))))
