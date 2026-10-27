(ns value-type.array-sequence-checks
  "Baseline-safe sequence checks; value fixtures load only on supported rows.")

(defn- unavailable-call
  [explicit?]
  (let [effects (atom 0)
        input (lazy-seq (swap! effects inc) (list nil))
        constructor (resolve 'value-type.array/into-array)]
    (try
      (if explicit?
        (constructor nil nil input)
        (constructor nil input))
      {:status :unexpected-success :effects @effects}
      (catch clojure.lang.ExceptionInfo error
        {:status :rejected
         :effects @effects
         :data (ex-data error)
         :message (ex-message error)
         :exception (.getName (class error))
         :cause (some-> error ex-cause class .getName)}))))

(defn- construction
  []
  (eval
   '(do
      (value-type.core/defvaluetype SequencePoint [^long x])
      (let [seed (SequencePoint. 0)
            values [(SequencePoint. 7) (SequencePoint. 9)]
            inferred (value-type.array/into-array SequencePoint values)
            explicit (value-type.array/into-array SequencePoint seed values)
            empty-array (value-type.array/into-array SequencePoint seed nil)]
        (mapv (fn [array expected]
                {:status :constructed
                 :component? (= SequencePoint (.getComponentType (class array)))
                 :length (alength array)
                 :contents? (= expected (vec array))})
              [inferred explicit empty-array] [values values []])))))

(defn check
  "Load namespaces in order and report both sequence arities without diagnostics."
  [require-order supported?]
  (run! require require-order)
  {:runtime (System/getProperty "java.runtime.version")
   :flags (vec (.getInputArguments
                (java.lang.management.ManagementFactory/getRuntimeMXBean)))
   :policy (System/getProperty "value-type.fallback")
   :require-order require-order
   :outcomes (if supported?
               (construction)
               (mapv (fn [explicit?]
                       {:explicit? explicit?
                        :calls (mapv (fn [_] (unavailable-call explicit?)) (range 2))})
                     [false true]))})
