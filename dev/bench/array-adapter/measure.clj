#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns value-type.array-adapter.measure)

(def started (System/nanoTime))

(require '[value-type.array :as array]
         '[value-type.core :refer [defvaluetype]])
(defvaluetype Sample [^long value])
(def seeds [(Sample. 17) (Sample. 29)])
(def sink (volatile! nil))

(defn gc-state
  "Return cumulative collector counts and milliseconds."
  []
  (mapv (fn [^java.lang.management.GarbageCollectorMXBean bean]
          {:name (.getName bean) :count (.getCollectionCount bean)
           :time-ms (.getCollectionTime bean)})
        (java.lang.management.ManagementFactory/getGarbageCollectorMXBeans)))

(defn measure
  "Measure a bounded batch; retain the last result to prevent unused output."
  [operation duration-ns]
  (let [before (gc-state)
        start (System/nanoTime)
        count-ops (loop [index 0]
                    (vreset! sink (operation index))
                    (let [completed (inc index)]
                      (if (and (pos? duration-ns)
                               (< (- (System/nanoTime) start) duration-ns))
                        (recur completed)
                        completed)))
        elapsed (- (System/nanoTime) start)]
    {:elapsed-ns elapsed :operations count-ops
     :ns-per-operation (/ (double elapsed) count-ops)
     :gc-before before :gc-after (gc-state)}))

(let [[mode revision fork output] *command-line-args*
      metadata {:revision revision :fork (Long/parseLong fork)
                :build (System/getProperty "java.runtime.version")
                :flags (vec (.getInputArguments
                             (java.lang.management.ManagementFactory/getRuntimeMXBean)))
                :host {:os (System/getProperty "os.name")
                       :version (System/getProperty "os.version")
                       :architecture (System/getProperty "os.arch")}}
      emit! (fn [record] (spit output (str (pr-str (merge metadata record)) "\n") :append true))
      allocate (fn [length] (fn [index] (array/make-array Sample length (nth seeds (bit-and index 1)))))
      ordinary [(object-array 0) (object-array 4)]]
  (emit! {:case :setup :phase :setup :elapsed-ns (- (System/nanoTime) started)
          :operations 0})
  (case mode
    "allocation" (emit! (merge {:case :allocation-first :phase :first-use :sample 0}
                               (measure (allocate 4) 0)))
    "inspection" (emit! (merge {:case :inspection-first :phase :first-use :sample 0}
                               (measure #(array/array-info (nth ordinary (bit-and % 1))) 0)))
    "warm" (let [restricted [(array/make-array Sample 0 (first seeds))
                             (array/make-array Sample 4 (second seeds))]
                 cases [[:allocation-0 (allocate 0)]
                        [:allocation-4 (allocate 4)]
                        [:inspection-ordinary #(array/array-info (nth ordinary (bit-and % 1)))]
                        [:inspection-restricted #(array/array-info (nth restricted (bit-and % 1)))]]]
             (doseq [[case-name operation] cases]
               (emit! (merge {:case case-name :phase :warmup :sample 0}
                             (measure operation 1000000000)))
               (dotimes [sample 5]
                 (emit! (merge {:case case-name :phase :sample :sample sample}
                               (measure operation 200000000))))))))
