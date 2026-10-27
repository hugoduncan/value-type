(ns value-type.array-matrix-checks
  "Baseline-safe child checks. Value fixtures are evaluated only on supported rows.")

(defn- unavailable-call
  []
  (try
    ((resolve 'value-type.array/make-array) nil -1 nil)
    {:status :unexpected-success}
    (catch clojure.lang.ExceptionInfo error
      {:status :rejected
       :data (ex-data error)
       :message (ex-message error)
       :exception (.getName (class error))
       :cause (some-> error ex-cause class .getName)})))

(defn- allocation
  []
  (eval
   '(do
      (value-type.core/defvaluetype MatrixPoint [^long x])
      (let [seed (MatrixPoint. 7)
            replacement (MatrixPoint. 9)
            first-array (value-type.array/make-array MatrixPoint 3 seed)
            second-array (value-type.array/make-array MatrixPoint 3 seed)
            initial? (= [seed seed seed] (vec first-array))]
        (aset first-array 1 replacement)
        {:status :allocated
         :component? (= MatrixPoint (.getComponentType (class first-array)))
         :length (alength first-array)
         :initial? initial?
         :changed? (= [seed replacement seed] (vec first-array))
         :independent? (= [seed seed seed] (vec second-array))}))))

(defn check
  "Load public namespaces in order, then return structured boundary outcomes."
  [require-order supported?]
  (run! require require-order)
  {:runtime (System/getProperty "java.runtime.version")
   :flags (vec (.getInputArguments
                (java.lang.management.ManagementFactory/getRuntimeMXBean)))
   :policy (System/getProperty "value-type.fallback")
   :require-order require-order
   :outcomes (if supported? [(allocation)] (mapv (fn [_] (unavailable-call)) (range 2)))})
