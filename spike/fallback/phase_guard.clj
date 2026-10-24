(ns fallback.phase-guard)

(def policy (System/getProperty "value-type.fallback" "silent"))

(defn enforce!
  "Applies the experimental receiving runtime's policy."
  []
  (when (= "error" policy)
    (throw (Error. "Fallback rejected by application policy"))))

(defmacro guarded-type
  "Checks permission during analysis and again during evaluation."
  [type-name fields]
  (enforce!)
  `(do
     (fallback.phase-guard/enforce!)
     (deftype ~type-name ~fields)))

(defmacro fallback-type
  "Selects fallback without expanding away its analysis guard."
  [type-name fields]
  (enforce!)
  `(do (fallback.phase-guard/guarded-type ~type-name ~fields)))
