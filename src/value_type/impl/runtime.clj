(ns value-type.impl.runtime
  "Per-library-runtime fallback policy, capability cache and warning state."
  (:require [value-type.impl.capability :as detector]
            [value-type.impl.field-capability :as fields]))

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

(def ^:private detected-field-capability (delay (fields/detect)))

(defn field-capability
  "Return cached native field capability without applying fallback policy."
  []
  @detected-field-capability)

(defn require-field-capability!
  "Require native field enforcement independently of ordinary fallback policy.
   Return nil on support; otherwise throw a structured definition error. Probe
   defects propagate unchanged. This guard is safe to call again in receivers."
  [class-name]
  (let [{:keys [available? reason detail]} (field-capability)]
    (when-not available?
      (throw
       (ex-info
        (str "Cannot define " class-name
             ": native nil-restricted fields are unavailable. " detail)
        {:value-type/error :field-capability-unavailable
         :class-name class-name
         :reason reason})))))
