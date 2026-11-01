(ns value-type.value-capability
  "Expectations of the value capability rejection of a saved value class
   on a receiver without the capability, in the cross-JVM tests."
  (:require [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]))

(defn unavailable-failure
  "Return the expected `:value-capability-unavailable` failure for
   class-name on a receiver with capability, without `:absent?` and
   `:message`. Error text is not contractual; see `unavailable`."
  [class-name {:keys [reason]}]
  {:class "clojure.lang.ExceptionInfo"
   :data {:value-type/error :value-capability-unavailable
          :class-name class-name
          :reason reason}})

(defn- unavailable-names
  "Return the texts that the unavailable message of class-name names."
  [class-name]
  [class-name "fixed value representation is unavailable"
   config/preview-option])

(defn unavailable
  "Return [description predicate] (see cell-harness/described) of the
   `unavailable-failure` of class-name on a receiver with capability, with
   `:absent? true`, and a `:message` that names class-name, the cause, the
   fix and extra-names. Both receivers without capability (no value
   classes, preview off) need `--enable-preview`. A capability `{:reason
   '<detail>}` gives the description."
  [class-name capability extra-names]
  (harness/with-entry
    (assoc (unavailable-failure class-name capability) :absent? true)
    :message
    (harness/contains (concat (unavailable-names class-name) extra-names))))
