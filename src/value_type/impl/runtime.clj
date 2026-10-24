(ns value-type.impl.runtime
  "Per-library-runtime fallback policy, capability cache and warning state."
  (:require [value-type.impl.capability :as detector]))

(def ^:private policy
  (let [configured (System/getProperty "value-type.fallback" "silent")]
    (when-not (contains? #{"silent" "warn" "error"} configured)
      (throw (IllegalArgumentException.
              (str "Invalid value-type.fallback: " (pr-str configured)
                   "; expected silent, warn or error"))))
    configured))

(def ^:private detected-capability (delay (detector/detect)))
(def ^:private warned? (atom false))

(defn capability
  "Return the cached capability result. This does not apply fallback policy."
  []
  @detected-capability)

(defn enforce-fallback!
  "Apply initialized policy to an attempted fixed identity representation.
   Context describes the unavailable capability or fixed fallback artifact.
   Strict policy throws Error on every call; warn emits once per runtime."
  [context]
  (let [message (str "value-type: identity deftype fallback: " context)]
    (case policy
      "silent" nil
      "warn" (when (compare-and-set! warned? false true)
               (binding [*out* *err*] (println message)))
      "error" (throw (Error. message)))))

(defmacro guarded-deftype
  "Internal expansion guard. Enforce fixed fallback policy during analysis
   and evaluation, including AOT namespace initialization."
  [name fields & opts+specs]
  (enforce-fallback! "fixed fallback representation")
  `(do
     (enforce-fallback! "fixed fallback representation")
     (deftype ~name ~fields ~@opts+specs)))
