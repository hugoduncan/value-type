(require '[clojure.edn :as edn]
         '[clojure.java.io :as io])

(defn stop-tree!
  [handle deadline]
  ;; Keep parents alive to reap their children before stopping the parents.
  (with-open [children (.children handle)]
    (doseq [child (vec (.toArray children))]
      (stop-tree! child deadline)))
  (when (.isAlive handle)
    (.destroyForcibly handle)
    (.get (.onExit handle)
          (max 1 (- deadline (System/nanoTime)))
          java.util.concurrent.TimeUnit/NANOSECONDS)))

(let [[command-file result-file] *command-line-args*
      command (edn/read-string (slurp command-file))
      process (.start (doto (ProcessBuilder. ^java.util.List command)
                        (.inheritIO)))
      root (.toHandle process)
      deadline (+ (System/nanoTime) (* 180 1000000000))
      outcome (loop []
                (cond
                  (.waitFor process 100 java.util.concurrent.TimeUnit/MILLISECONDS)
                  {:exit (.exitValue process)}

                  (or (.exists (io/file "timeout-ready"))
                      (>= (System/nanoTime) deadline))
                  {:timed-out? true}

                  :else (recur)))
      handles (with-open [descendants (.descendants root)]
                (into [root] (.toArray descendants)))]
  (try
    (stop-tree! root (+ (System/nanoTime) (* 15 1000000000)))
    (.waitFor process 1 java.util.concurrent.TimeUnit/SECONDS)
    (spit result-file
          (pr-str (assoc outcome :processes
                         (mapv (fn [handle]
                                 {:pid (.pid handle) :alive? (.isAlive handle)})
                               handles))))
    (catch Exception error
      (spit result-file (pr-str (assoc outcome :cleanup-error (str error))))
      (System/exit 1))))
