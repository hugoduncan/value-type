(ns value-type.impl.runtime
  "Per-library-runtime fallback policy, warning state and fallback guards.
   Capability results are cached by their probe namespaces.")

(set! *warn-on-reflection* true)

(def ^:private policy
  (let [configured (System/getProperty "value-type.fallback" "silent")]
    (when-not (contains? #{"silent" "warn" "error"} configured)
      (throw (IllegalArgumentException.
              (str "Invalid value-type.fallback: " (pr-str configured)
                   "; expected silent, warn or error"))))
    configured))

(def ^:private warned? (atom false))

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

(defn enforce-fixed-fallback!
  "Apply initialized policy to a fixed identity representation in an
   expansion. Call during both analysis and evaluation."
  []
  (enforce-fallback! "fixed fallback representation"))

(defmacro guarded-deftype
  "Internal expansion guard. Enforce fixed fallback policy during analysis
   and evaluation, including AOT namespace initialization."
  [name fields & opts+specs]
  (enforce-fixed-fallback!)
  `(do
     (enforce-fixed-fallback!)
     (deftype ~name ~fields ~@opts+specs)))

(defmacro guarded-deftype*
  "Internal expansion guard for `deftype*` forms. Enforce fixed fallback
   policy during analysis and evaluation. Evaluate to the defined Class,
   because `deftype*` evaluates to nil."
  [tagname classname fields & opts+specs]
  (enforce-fixed-fallback!)
  `(do
     (enforce-fixed-fallback!)
     (deftype* ~tagname ~classname ~fields ~@opts+specs)
     ~(symbol (str classname))))
