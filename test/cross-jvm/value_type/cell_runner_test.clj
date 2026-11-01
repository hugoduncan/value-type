(ns value-type.cell-runner-test
  "Permanent tests of the cell runner: Scope 3 policy rules in a child with
   more than one fallback cell, the cell isolation contract, and the
   outcome of a failing cell, a crashing child, a child with an unreadable
   result, a producer with an unreadable result, a child that does not
   start and a timed-out child, the child limit with its permit-wait
   rule, and the read of VALUE_TYPE_TEST_JVM_LIMIT at namespace load.
   The fixture children run with run-jvms; the tests assert on the returned
   data, so a failing fixture cell does not fail the suite."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]
            [value-type.temp-dir :as temp]))

(def ^:private fallback-cell-form
  "Define Point from source in the cell namespace through eval. Success
   gives the definition map; a rejection gives
   cell-registry/failed-definition."
  '(do
     (require 'value-type.core 'value-type.cell-registry)
     (let [cell-ns *ns*]
       (value-type.cell-registry/outcome
        #(value-type.cell-registry/failed-definition % cell-ns 'Point)
        #(eval '(value-type.core/defvaluetype Point [x]))
        #(value-type.cell-registry/defined cell-ns 'Point % 1)))))

(def ^:private cell-count 3)

(defn- fallback-child
  "Return a baseline child of policy with cell-count fallback cells.
   expected is the :expected value of each cell."
  [baseline policy warning expected]
  (harness/cell-child
   (assoc baseline :policy policy)
   (vec (for [i (range cell-count)]
          {:context [(pr-str [:scope-3 policy i])]
           :form fallback-cell-form
           :expected expected
           :fallback? true
           :warning warning}))))

(def ^:private defined registry/new-definition)

(deftest scope-3-policy-rules-test
  ;; Acceptance 4: warn-once, repeated strict rejection and silent fallback
  ;; hold for each cell of one child with more than one fallback cell.
  (let [{:keys [baseline]} (config/configuration)
        detail harness/unavailable-detail
        rejection (assoc (harness/policy-rejection detail)
                         :chain harness/source-rejection-chain
                         :absent? true)
        children [(fallback-child baseline "warn" detail defined)
                  (fallback-child baseline "error" detail rejection)
                  (fallback-child baseline "silent" detail defined)]
        expected-entries
        {"warn" (into [{:value defined :out "" :err (harness/warning-line detail)}]
                      (repeat (dec cell-count) {:value defined :out "" :err ""}))
         "error" (vec (repeat cell-count {:value rejection :out "" :err ""}))
         "silent" (vec (repeat cell-count {:value defined :out "" :err ""}))}
        results (runner/run-jvms children)]
    (doseq [[child run] (map vector children results)]
      (harness/assert-child-stderr child run)
      (testing (pr-str [:scope-3 (:policy child)])
        (harness/assert-run run {:exit 0})
        (is (= (expected-entries (:policy child)) (:result run)))
        (is (= (repeat cell-count true)
               (map :pass? (harness/cell-outcomes child run))))))))

(def ^:private loader-chain-form
  "Return the identity hashes of the Compiler/LOADER value and its parents,
   innermost first."
  '(loop [l @clojure.lang.Compiler/LOADER chain []]
     (if l
       (recur (.getParent ^ClassLoader l) (conj chain (System/identityHashCode l)))
       chain)))

(def ^:private cell-loader-index
  "Index of the cell loader in the loader chain of isolation-form:
   load-string and the eval of the top-level form each bind a fresh LOADER
   below it."
  2)

(def ^:private isolation-form
  "Read the cell state, then intern leaked and set *warn-on-reflection*.
   Both cells run this form, so their loader chains have the same depth."
  `(let [state# {:ns (str *ns*)
                 :loaders ~loader-chain-form
                 :leaked (some? (resolve '~'leaked))
                 :warn-on-reflection *warn-on-reflection*}]
     (intern *ns* '~'leaked 1)
     (set! *warn-on-reflection* true)
     state#))

(deftest cell-isolation-test
  ;; plan "Cell harness": each cell has a fresh namespace, a new LOADER
  ;; whose parent is the top-level LOADER value, and the with-bindings Vars
  ;; at their cell-start values.
  (let [{:keys [baseline]} (config/configuration)
        child (harness/cell-child
               (assoc baseline :policy "silent")
               (vec (for [i (range 2)]
                      {:context [(pr-str [:isolation i])] :expected :ok
                       :form isolation-form})))
        [run] (runner/run-jvms [child])
        [a b] (map :value (:result run))
        top (inc cell-loader-index)]
    (harness/assert-child-stderr child run)
    (testing "cell isolation"
      (harness/assert-run run {:exit 0})
      (is (= [{:out "" :err ""} {:out "" :err ""}]
             (map #(select-keys % [:out :err]) (:result run))))
      (is (= [(registry/cell-ns-name 0) (registry/cell-ns-name 1)] (map :ns [a b])))
      (testing "own loader, same parent"
        (is (= (count (:loaders a)) (count (:loaders b))))
        (is (< top (count (:loaders a))))
        (is (not= (get-in a [:loaders cell-loader-index])
                  (get-in b [:loaders cell-loader-index])))
        (is (= (drop top (:loaders a)) (drop top (:loaders b)))))
      (testing "cell-start bindings"
        (is (= [{:leaked false :warn-on-reflection false}
                {:leaked false :warn-on-reflection false}]
               (map #(select-keys % [:leaked :warn-on-reflection]) [a b])))))))

(defn- outcome-child
  "Return a baseline silent child of fixture cells with forms."
  [baseline options forms]
  (harness/cell-child
   (merge (assoc baseline :policy "silent") options)
   (vec (map-indexed (fn [i form]
                       {:context [(pr-str [:fixture i])] :form form :expected :ok})
                     forms))))

(def ^:private marker "cell-runner-marker")

(deftest failing-cell-outcome-test
  ;; Acceptance 5: a throwing cell fails alone and shows its own output.
  (let [{:keys [baseline]} (config/configuration)
        child (outcome-child
               baseline {}
               [:ok
                `(do (print ~marker) (binding [*out* *err*] (print ~marker))
                     (throw (ex-info "fixture failure" {})))
                :ok])
        [run] (runner/run-jvms [child])
        [a b c] (harness/cell-outcomes child run)
        thrown {:class "clojure.lang.ExceptionInfo" :message "fixture failure"}]
    (harness/assert-child-stderr child run)
    (testing "failing cell"
      (is (false? (:pass? b)))
      (is (= thrown (select-keys (get-in (some #(when (= :result (:check %)) %)
                                               (:checks b))
                                         [:actual :thrown])
                                 [:class :message])))
      (is (= {:exit 0 :out marker :err marker}
             (select-keys (:failure b) [:exit :out :err])))
      (is (= thrown (select-keys (:thrown (:failure b)) [:class :message]))))
    (testing "other cells"
      (is (= [true nil true nil] [(:pass? a) (:failure a) (:pass? c) (:failure c)])))))

(defn- assert-no-result
  "Assert that each outcome of child and its run fails with one :no-result
   check and the failure of the run (:exit, :out, :err, :read-error)."
  [child run]
  (doseq [{:keys [pass? checks failure]} (harness/cell-outcomes child run)]
    (is (false? pass?))
    (is (= [[:result :no-result]] (map (juxt :check :actual) checks)))
    (is (= (runner/run-facts run) failure))))

(deftest crashing-child-outcome-test
  ;; Acceptance 5: a child that exits gives no result for any cell.
  (let [{:keys [baseline]} (config/configuration)
        child (outcome-child
               baseline {}
               [:ok
                `(do (.println System/out ~marker) (.flush System/out)
                     (System/exit 3))])
        [run] (runner/run-jvms [child])]
    (harness/assert-child-stderr child run)
    (testing "crashing child"
      (harness/assert-run run {:exit 3})
      (is (str/includes? (str (:out run)) marker))
      (assert-no-result child run))))

(deftest unreadable-result-outcome-test
  ;; Acceptance 5: a child that exits 0 with a last stdout line that does
  ;; not read as EDN gives :read-error, no :result and no throw; each cell
  ;; fails with no result.
  (let [{:keys [baseline]} (config/configuration)
        child (outcome-child baseline {} [:ok '(Object.)])
        [run] (runner/run-jvms [child])]
    (harness/assert-child-stderr child run)
    (testing "unreadable result"
      (harness/assert-run run {:exit 0})
      (is (string? (:read-error run)))
      (is (not (contains? run :result)))
      (assert-no-result child run))))

(def ^:private late-cell-ms
  "Run time of the cell child after the index-0 child in late-cell-throw."
  3000)

(defn- late-cell-throw
  "Run the child of first-child (a function of a file in a temp directory)
   at index 0 and, after it, a baseline cell child that sleeps late-cell-ms,
   then writes the file. Return {:late-child :late-run :thrown}: the late
   child, its result (from the return value of run-jvms, or from the
   :results of the ex-data), and ::no-throw when run-jvms does not throw,
   else {:data :written? :cause} of the ex-info, with :written? the file
   state at the throw. The file exists at the throw only when the throw
   comes after the later child returns, independent of the JVM start time."
  [baseline first-child]
  (temp/with-temp-dir
    "vt-late-cell-"
    (fn [dir]
      (let [file (io/file dir "done")
            late-child (outcome-child baseline {}
                                      [`(do (Thread/sleep ~late-cell-ms)
                                            (spit ~(str file) "done"))])]
        (try (let [results (runner/run-jvms [(first-child file) late-child])]
               {:late-child late-child :late-run (get results 1)
                :thrown ::no-throw})
             (catch clojure.lang.ExceptionInfo e
               {:late-child late-child
                :late-run (get-in (ex-data e) [:results 1])
                :thrown {:data (ex-data e) :written? (.exists file)
                         :cause (ex-cause e)}}))))))

(deftest unreadable-producer-result-test
  ;; plan "Runner": a producer child with an unreadable last stdout line
  ;; throws ex-info after all children of the call return (late-cell-throw:
  ;; the producer at index 0).
  (let [{:keys [baseline]} (config/configuration)
        {:keys [late-child late-run thrown]}
        (late-cell-throw
         baseline
         (fn [_] (assoc baseline :policy "silent" :kind :producer
                        :form "(Object.)")))
        {:keys [data written?]} thrown]
    (testing "unreadable producer result"
      (is (not= ::no-throw thrown))
      (is (true? written?))
      (harness/assert-run late-run {:exit 0})
      (is (= {:index 0 :exit 0 :err ""} (select-keys data [:index :exit :err])))
      (is (string? (:read-error data)))
      (is (str/includes? (str (:out data)) "#object[java.lang.Object")))
    (harness/assert-child-stderr late-child late-run)))

(deftest failed-start-child-test
  ;; plan "Runner": run-jvms throws only after all children of the call
  ;; return, also when a child throws (late-cell-throw). The child at index
  ;; 0 names no Java executable, so its start throws IOException.
  (let [{:keys [baseline]} (config/configuration)
        {:keys [late-child late-run thrown]}
        (late-cell-throw
         baseline
         (fn [file]
           (outcome-child (assoc baseline :java (str file ".missing-java"))
                          {} [:ok])))
        {:keys [data written? cause]} thrown]
    (testing "failed child start"
      (is (not= ::no-throw thrown))
      (is (true? written?))
      (harness/assert-run late-run {:exit 0})
      (is (= {:index 0 :results [nil late-run]} data))
      (is (instance? java.io.IOException cause)))
    (harness/assert-child-stderr late-child late-run)))

(def ^:private late-marker "cell-runner-late-marker")

(def ^:private past-timeout-ms
  "Sleep of the timed-out cell before late-marker: above :timeout-ms
   (2000) from cell start, below the wait (12000) from process start."
  4000)

(deftest timed-out-child-outcome-test
  ;; Acceptance 5: a child past 2000 + 10000 × (2 − 1) ms is destroyed and
  ;; gives no result for any cell. The cell prints late-marker after
  ;; past-timeout-ms, so late-marker shows that the wait is wait-ms, not
  ;; :timeout-ms. The cell writes file after its last sleep, so a file that
  ;; does not exist at the return shows that run-jvms stops the child
  ;; before it returns.
  (let [{:keys [baseline]} (config/configuration)
        [child run written?]
        (temp/with-temp-dir
          "vt-timed-out-cell-"
          (fn [dir]
            (let [file (io/file dir "done")
                  child (outcome-child
                         baseline {:timeout-ms 2000}
                         [`(do (.println System/out ~marker) (.flush System/out)
                               (Thread/sleep ~past-timeout-ms)
                               (.println System/out ~late-marker) (.flush System/out)
                               (Thread/sleep 60000)
                               (spit ~(str file) "done"))
                          :ok])
                  [run] (runner/run-jvms [child])]
              [child run (.exists file)])))]
    (harness/assert-child-stderr child run)
    (testing "timed-out child"
      (harness/assert-run run {:exit :timeout})
      (is (false? written?))
      (is (str/includes? (str (:out run)) marker))
      (is (str/includes? (str (:out run)) late-marker))
      (assert-no-result child run))))

(def ^:private sleep-ms
  "Run time of each limit child after its start."
  6000)

(def ^:private start-ms
  "Upper bound of the JVM start of a limit child (about 3000 ms alone)."
  6000)

(def ^:private limit-form
  "Sleep sleep-ms, then print the [start end] wall-clock ms of the sleep."
  (pr-str `(let [start# (System/currentTimeMillis)]
             (Thread/sleep ~sleep-ms)
             (prn [start# (System/currentTimeMillis)]))))

(defn- max-overlap
  "Return the maximum number of [start end] intervals that contain one
   instant."
  [intervals]
  (apply max 0 (for [[s] intervals]
                 (count (filter (fn [[a b]] (and (<= a s) (< s b))) intervals)))))

(def ^:private limit-count
  "Number of children of the second run-jvms call of child-limit-test: the
   limit, but not below the default limit. Thus the child count, and the
   stderr assertion count, is the same at each limit not above the
   default."
  (max runner/limit (runner/read-limit nil)))

(deftest child-limit-test
  ;; Design Scope 4: limit children run at one time, the limit applies
  ;; across run-jvms calls, and the time that a child waits for a permit
  ;; does not count toward its timeout. Two calls on two threads start one
  ;; child and limit-count children; the overlap of all results is limit
  ;; (a serial runner gives 1, a limit for each call gives more than
  ;; limit). Acceptance 3: limit-count is independent of the limit at the
  ;; default limit or less, so the labels are the same at limit 1 and 4.
  ;; A child that waits for a permit has a timeout above sleep-ms plus JVM
  ;; start, but below its wait plus its own run (start-ms is not above
  ;; sleep-ms plus JVM start); the wait does not count.
  ;; Kind: each child is a :cell child, as a child that compiles as its
  ;; checked case is: its run is the checked case and it returns one
  ;; result. It makes no transfer expansions, so it is not a producer. The
  ;; evidence is concurrency, so the test needs limit-count + 1 children
  ;; of one configuration: an isolation exception (implementation.md
  ;; table, check_record.clj rule-5-exceptions).
  (let [{:keys [baseline]} (config/configuration)
        child (assoc baseline :form limit-form
                     :timeout-ms (+ sleep-ms start-ms))
        calls (mapv #(future (runner/run-jvms (vec (repeat % child))))
                    [1 limit-count])
        results (into [] (mapcat deref) calls)]
    (doseq [run results]
      (harness/assert-child-stderr child run))
    (testing "child limit"
      (doseq [run results]
        (harness/assert-run run {:exit 0}))
      (is (every? vector? (map :result results)))
      (is (= runner/limit (max-overlap (map :result results)))))))

(def ^:private limit-variable
  ;; Design Scope 4 contract: the documented variable name. Keep the
  ;; literal; value-type.jvm-runner/limit-variable is the code under test.
  "VALUE_TYPE_TEST_JVM_LIMIT")

(defn- limit-load-form
  "Return a cell form that starts a grandchild JVM (the
   value-type.jvm-config/current-runtime of the cell child) with
   value-type.jvm-runner/run-command:
   limit-variable set to value and no
   value-type.label-record/record-variable. The grandchild requires
   value-type.jvm-runner and prints its limit. The cell value is the :exit,
   the trimmed :out and the :err of the grandchild. :exit is :timeout when
   the grandchild does not end in 60 s (then it is stopped)."
  [value]
  `(do
     (require 'clojure.string 'value-type.jvm-config 'value-type.jvm-runner
              'value-type.label-record)
     (let [{java# :java classpath# :classpath}
           (value-type.jvm-config/current-runtime)]
       (update (value-type.jvm-runner/run-command
                [java# "-cp" classpath# "clojure.main" "-e"
                 ~(pr-str '(do (require 'value-type.jvm-runner)
                               (prn value-type.jvm-runner/limit)))]
                {~limit-variable ~value
                 value-type.label-record/record-variable nil}
                60000)
               :out clojure.string/trim))))

(deftest limit-variable-load-test
  ;; Design Scope 4: VALUE_TYPE_TEST_JVM_LIMIT sets the limit when
  ;; value-type.jvm-runner loads, and an invalid value makes the load throw
  ;; an exception whose message names the variable and the value. One
  ;; baseline cell child (run-jvms: the limit and the record apply); each
  ;; cell starts a grandchild JVM with the variable set.
  (let [{:keys [baseline]} (config/configuration)
        valid "2"
        invalid "0"
        texts [limit-variable (pr-str invalid)]
        child (harness/cell-child
               (assoc baseline :policy "silent")
               [{:context [(pr-str [:limit-load valid])]
                 :form (limit-load-form valid)
                 :expected {:exit 0 :out valid :err ""}}
                {:context [(pr-str [:limit-load invalid])]
                 :form (limit-load-form invalid)
                 :expected
                 (harness/described
                  (harness/keyed
                   {:exit (harness/detail #(and (integer? %) (not= 0 %)))
                    :out (harness/fixed "")
                    :err (harness/contains texts)}))}])]
    (harness/run-cells [child])))
