(ns value-type.jvm-runner
  "Baseline-safe process support for isolated runtime tests."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [value-type.cell-registry :as registry]
            [value-type.label-record :as record]
            [value-type.temp-dir :as temp])
  (:import (java.util List)
           (java.util.concurrent ExecutionException Semaphore TimeUnit)))

(def limit-variable
  "Name of the environment variable that sets the child limit."
  "VALUE_TYPE_TEST_JVM_LIMIT")

(defn read-limit
  "Parse a limit-variable value: nil gives 4; else a positive decimal
   integer not above Integer/MAX_VALUE. Throws ex-info that names the
   variable and the value."
  [value]
  (if (nil? value)
    4
    (let [n (when (re-matches #"[0-9]+" value) (bigint value))]
      (if (and n (pos? n) (<= n Integer/MAX_VALUE))
        (long n)
        (throw (ex-info (str "Invalid " limit-variable " value: " (pr-str value)
                             " (expected a positive decimal integer, at most "
                             Integer/MAX_VALUE ")")
                        {:variable limit-variable :value value}))))))

(def limit
  "Maximum number of child JVMs that run at one time in this parent JVM."
  (read-limit (System/getenv limit-variable)))

(def ^:private ^Semaphore permits (Semaphore. (int limit)))

(defn policy-option
  "Return the JVM option that sets registry/policy-property (the fallback
   policy) of a child."
  [policy]
  (str "-D" registry/policy-property "=" policy))

(defn- start-command [{:keys [java classpath form jvm-opts policy]
                       :or {jvm-opts []}}]
  (into [java]
        (concat jvm-opts
                (when policy [(policy-option policy)])
                ["-cp" classpath "clojure.main" "-e" form])))

(defn read-result
  "Return the read of a child stdout: on exit 0, :result of the last line
   as EDN (nil for empty stdout), or :read-error when it does not read;
   else :result nil."
  [exit ^String out]
  (if (= 0 exit)
    (try
      {:result (some-> out str/split-lines last edn/read-string)}
      (catch Exception e
        {:read-error (.getMessage e)}))
    {:result nil}))

(defn wait-ms
  "Return the wait time in ms of a child from process start: :timeout-ms
   (default 120000) plus 10000 for each cell after the first (:cell-count,
   default 1)."
  [{:keys [timeout-ms cell-count] :or {timeout-ms 120000 cell-count 1}}]
  (+ timeout-ms (* 10000 (dec cell-count))))

(defn run-facts
  "Return the failure facts of a run: :exit, :out, :err and :read-error
   (the keys that the run has)."
  [run]
  (select-keys run [:exit :out :err :read-error]))

(defn with-output-files
  "Call f of the stdout and stderr files of a temporary directory
   (temp/with-temp-dir), which is removed when f returns or throws. Return
   the value of f."
  [f]
  (temp/with-temp-dir
    "vt-jvm-"
    (fn [dir] (f (io/file dir "stdout") (io/file dir "stderr")))))

(defn- stop
  "Stop process and wait until it ends."
  [^Process process]
  (.destroyForcibly process)
  (.waitFor process))

(defn await-exit
  "Wait at most wait-ms for process to end. Return its exit value, or
   :timeout after the process is stopped."
  [^Process process wait-ms]
  (if (.waitFor process wait-ms TimeUnit/MILLISECONDS)
    (.exitValue process)
    (do (stop process) :timeout)))

(defn run-command
  "Run the process of command (a vector of strings) with the environment
   changes of env (a map of variable name to value; a nil value removes
   the variable), its output in with-output-files files. Wait at most
   wait-ms (await-exit), and stop a live process when the wait throws.
   Return :exit (an exit value or :timeout), :out and :err."
  [command env wait-ms]
  (with-output-files
    (fn [out-file err-file]
      (let [builder (doto (ProcessBuilder. ^List command)
                      (.redirectOutput ^java.io.File out-file)
                      (.redirectError ^java.io.File err-file))
            environment (.environment builder)]
        (doseq [[k v] env]
          (if (some? v) (.put environment k v) (.remove environment k)))
        (let [process (.start builder)]
          (try
            {:exit (await-exit process wait-ms)
             :out (slurp out-file)
             :err (slurp err-file)}
            (finally
              (when (.isAlive process)
                (stop process)))))))))

(defn- execute
  "Run one child while holding a permit. Return its result map."
  [options]
  (.acquire permits)
  (try
    (let [{:keys [exit out] :as run}
          (run-command (start-command options) nil (wait-ms options))]
      (merge run (read-result exit out)))
    (finally
      (.release permits))))

(defn- kind
  "The child kind of a run-jvm option map; :cell by default."
  [options]
  (get options :kind :cell))

(defn- first-indexed
  "Return (assoc (f index x) :index index) of the first x of xs for which
   f gives a map, or nil."
  [f xs]
  (first (keep-indexed #(some-> (f %1 %2) (assoc :index %1)) xs)))

(defn read-failure
  "Return the ex-info data of the first child of option-maps whose result
   has :read-error and whose kind is not :cell (:index, :exit, :out, :err,
   :read-error), or nil. Results pair with option-maps by index."
  [option-maps results]
  (first-indexed (fn [index result]
                   (when (and (not= :cell (kind (nth option-maps index)))
                              (:read-error result))
                     (run-facts result)))
                 results))

(defn thrown-failure
  "Return {:index i :thrown t} of the first outcome with :thrown, or nil.
   Outcomes are the deref outcomes of run-jvms ({:result r} or
   {:thrown t}) in input order."
  [outcomes]
  (first-indexed (fn [_ outcome] (when (:thrown outcome) (select-keys outcome [:thrown])))
                 outcomes))

(defn run-jvms
  "Run run-jvm option maps as child JVMs, at most limit at one time in this
   parent JVM. Return their results in input order when all have returned.

   Record each child start on the calling thread. Return or throw only
   after all children of the call return, also when a child throws. When
   a child throws (for example, its :java does not start), throw ex-info
   of the first such child (:index), with that exception as the cause. A
   :cell child whose last stdout line does not read as EDN gives
   :read-error and no :result. A producer or compile child with such a
   line throws ex-info (read-failure data). The ex-data of each throw has
   :results, the results in input order (nil for a child that throws)."
  [option-maps]
  (doseq [options option-maps]
    (record/child-start! (kind options) options))
  (let [outcomes (mapv (fn [f]
                         (try {:result @f}
                              (catch ExecutionException e {:thrown (.getCause e)})))
                       (mapv #(future (execute %)) option-maps))
        results (mapv :result outcomes)]
    (when-let [{:keys [index ^Throwable thrown] :as failure} (thrown-failure outcomes)]
      (throw (ex-info (str "Child " index " failed: " (.getMessage thrown))
                      (assoc (dissoc failure :thrown) :results results)
                      thrown)))
    (when-let [failure (read-failure option-maps results)]
      (throw (ex-info (str "Unreadable result of child " (:index failure) ": "
                           (:read-error failure))
                      (assoc failure :results results))))
    results))

(defn run-jvm
  "Run a Clojure source string in an explicitly selected JVM and classpath.

   Supply :java, :classpath and :form. Optional :jvm-opts defaults to [],
   :policy sets registry/policy-property, :timeout-ms defaults to 120000 and
   :cell-count to 1; the timeout from process start is :timeout-ms plus
   10000 ms for each cell after the first. :kind (:cell, :producer,
   :aot-compile or :java-compile; default :cell) selects the read rule and
   is recorded by the label recorder. No preview flag or parent classpath is
   added. Return :exit, :out, :err and :result (the last stdout line as EDN
   on exit 0). Timeout uses :exit :timeout. On exit 0 an unreadable last
   line gives :read-error and no :result for a :cell child, and throws for
   other kinds. Temporary output files are removed and a timed-out child is
   stopped before returning. This is run-jvms of one map."
  [options]
  (first (run-jvms [options])))
