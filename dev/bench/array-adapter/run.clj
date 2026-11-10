#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns value-type.array-adapter.run)

(require '[babashka.process :as process]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[value-type.jvm-executable :as executable]
         '[value-type.jvm-facts :as facts])

(defn checked
  "Run a child with a finite bound and preserve its output in a file."
  [command log]
  (let [child (process/process command {:out (io/file log) :err :out})
        result (deref child 120000 ::timeout)]
    (when (= ::timeout result)
      (process/destroy-tree child)
      (throw (ex-info "Measurement child timed out" {:command command :log log})))
    (when-not (zero? (:exit result))
      (throw (ex-info "Measurement child failed" {:command command :log log :exit (:exit result)})))))

(defn summarize
  "Validate complete samples and report median and range in ns per operation."
  [records]
  (let [samples (filterv #(#{:first-use :sample} (:phase %)) records)
        groups (group-by :case samples)]
    (assert (= 6 (count groups)))
    (assert (= 15 (count (filterv #(= :setup (:phase %)) records))))
    (assert (= 20 (count (filterv #(= :warmup (:phase %)) records))))
    (into (sorted-map)
          (map (fn [[case-name rows]]
                 (let [first-use? (= :first-use (:phase (first rows)))
                       values (vec (sort (mapv :ns-per-operation rows)))]
                   (assert (= (if first-use? 5 25) (count rows)))
                   (assert (= #{1 2 3 4 5} (set (map :fork rows))))
                   (doseq [row rows]
                     (assert (pos? (:operations row)))
                     (assert (= (:ns-per-operation row)
                                (/ (double (:elapsed-ns row)) (:operations row)))))
                   [case-name {:samples (count rows) :median (nth values (quot (count values) 2))
                               :min (first values) :max (peek values)}])))
          groups)))

(let [[output] *command-line-args*
      _ (assert output "Supply a new evidence directory")
      directory (io/file output)
      _ (assert (not (.exists directory)) "Do not overwrite measurement evidence")
      _ (.mkdirs directory)
      platform (executable/platform (System/getProperty "os.name") (System/getProperty "os.arch"))
      all-facts (facts/read-facts)
      selections (mapv #(facts/select all-facts % platform (if (= % :baseline) :plain :preview-export))
                       [:baseline :valhalla])
      runtimes (mapv #(assoc % :java (executable/resolve! % (System/getenv)
                                                          (executable/default-root (System/getProperty "user.home"))))
                     selections)
      runtime (second runtimes)
      revision (str/trim (:out (process/shell {:out :string} "git rev-parse HEAD")))
      classpath (str/trim (:out (process/shell {:out :string} "clojure -Spath")))
      java (:java runtime)
      bin (.getParentFile (io/file java))
      commands (atom [])]
  (doseq [tool ["javac" "javap"]]
    (checked [(str (io/file bin tool)) "-version"] (str (io/file directory (str tool ".log")))))
  (spit (io/file directory "environment.edn")
        (pr-str {:revision revision :runtimes runtimes :classpath classpath
                 :host (:out (process/shell {:out :string} "uname -a"))
                 :runner-sha256 (:out (process/shell {:out :string} "shasum -a 256 dev/bench/array-adapter/measure.clj"))}))
  (doseq [mode ["allocation" "inspection" "warm"] fork (range 1 6)]
    (let [stem (str mode "-" fork)
          command (into [java] (concat (:jvm-opts runtime)
                                       ["-cp" classpath "clojure.main" "dev/bench/array-adapter/measure.clj"
                                        mode revision (str fork) (str (io/file directory (str stem ".edn")))]))]
      (swap! commands conj command)
      (spit (io/file directory "commands.edn") (pr-str @commands))
      (println "Measuring" stem)
      (checked command (str (io/file directory (str stem ".log"))))))
  (let [records (into [] (comp (filter #(re-matches #"(allocation|inspection|warm)-\d.edn" (.getName %)))
                               (mapcat #(mapv edn/read-string (str/split-lines (slurp %)))))
                      (.listFiles directory))]
    (spit (io/file directory "summary.edn") (pr-str (summarize records)))
    (println "Validated" (count records) "records")))
