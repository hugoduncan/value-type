(ns value-type.cell-outcomes-test
  "In-process tests of cell-harness: the failure side of cell-outcomes,
   the report translation of assert-child, the cell-child options and the
   rejection rule rejected. Outcome cases use a
   cell-child map and a hand-made run map; no child JVM starts."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.cell-harness :as harness]
            [value-type.jvm-runner :as runner]))

(def ^:private configuration
  {:java "java" :classpath "" :jvm-opts [] :policy "silent"})

(defn- outcomes
  "Return the cell-outcomes of a child of policy with cells, from run.
   Each cell gets :form :ok and the defaults :context and :expected :ok."
  [policy cells run]
  (harness/cell-outcomes
   (harness/cell-child
    (assoc configuration :policy policy)
    (vec (map-indexed (fn [i cell]
                        (merge {:context [(pr-str [:case i])] :expected :ok}
                               cell
                               {:form :ok}))
                      cells)))
   run))

(defn- entry
  "Return a passing cell result entry with overrides."
  [& {:as overrides}]
  (merge {:value :ok :out "" :err ""} overrides))

(defn- run [& entries]
  {:exit 0 :out "" :err "" :result (vec entries)})

(defn- failed
  "Return [pass? failing-check-keys] of outcome."
  [{:keys [pass? checks]}]
  [pass? (vec (keep #(when-not (:pass? %) (:check %)) checks))])

(def ^:private warning-line
  (str harness/warning-prefix "w" \newline))

(deftest warn-err-check-test
  (let [cells [{:fallback? true :warning "w"} {:fallback? true :warning "w"}]]
    (testing "first fallback cell without warning"
      (is (= [false [:err]]
             (failed (first (outcomes "warn" cells (run (entry) (entry))))))))
    (testing "first fallback cell with two warning lines"
      (is (= [false [:err]]
             (failed (first (outcomes "warn" cells
                                      (run (entry :err (str warning-line warning-line))
                                           (entry))))))))
    (testing "warning on a later cell"
      (is (= [[true []] [false [:err]]]
             (mapv failed (outcomes "warn" cells
                                    (run (entry :err warning-line)
                                         (entry :err warning-line)))))))
    (testing "any warning, one warning line"
      (is (= [true []]
             (failed (first (outcomes "warn" [{:fallback? true :warning :any}]
                                      (run (entry :err warning-line))))))))
    (testing "any warning, two warning lines"
      (is (= [false [:err]]
             (failed (first (outcomes "warn" [{:fallback? true :warning :any}]
                                      (run (entry :err (str warning-line warning-line)))))))))
    (testing "any warning without warning prefix"
      (is (= [false [:err]]
             (failed (first (outcomes "warn" [{:fallback? true :warning :any}]
                                      (run (entry :err "other: w\n")))))))))
  (let [cells [{} {:fallback? true :warning "w"}]]
    (testing "first fallback cell is not the first cell"
      (is (= [[true []] [true []]]
             (mapv failed (outcomes "warn" cells
                                    (run (entry) (entry :err warning-line)))))))
    (testing "warning on the first cell, not the first fallback cell"
      (is (= [[false [:err]] [false [:err]]]
             (mapv failed (outcomes "warn" cells
                                    (run (entry :err warning-line) (entry)))))))))

(deftest err-and-out-check-test
  (doseq [policy ["error" "silent"]]
    (testing (pr-str [:err policy])
      (is (= [false [:err]]
             (failed (first (outcomes policy [{:fallback? true :warning "w"}]
                                      (run (entry :err warning-line)))))))))
  (testing "out"
    (is (= [false [:out]]
           (failed (first (outcomes "silent" [{}] (run (entry :out "x")))))))))

(deftest result-check-test
  (let [expected (harness/described
                  (harness/keyed
                   {:thrown (harness/including {:class (harness/fixed "java.lang.Error")
                                                :message (harness/fixed "m")})}))]
    (testing "thrown with other message"
      (is (= [false [:result]]
             (failed (first (outcomes "silent" [{:expected expected}]
                                      (run {:thrown {:class "java.lang.Error"
                                                     :message "n"
                                                     :chain ["java.lang.Error"]}
                                            :out "" :err ""})))))))
    (testing "value in place of thrown"
      (is (= [false [:result]]
             (failed (first (outcomes "silent" [{:expected expected}]
                                      (run (entry)))))))))
  (testing "throwing predicate"
    (is (= [false [:result]]
           (failed (first (outcomes "silent"
                                    [{:expected (harness/described [:throws (fn [_] (throw (ex-info "p" {})))])}]
                                    (run (entry)))))))))

(deftest map-pair-test
  ;; keyed is closed (exactly its keys); including is open (each of its
  ;; keys, other keys permitted). A bare map description is an exact map.
  (let [pairs {:a (harness/fixed 1) :b (harness/fixed nil)}
        [keyed-description keyed?] (harness/keyed pairs)
        [including-description including?] (harness/including pairs)]
    (testing "descriptions"
      (is (= {:a 1 :b nil} keyed-description))
      (is (= '(including {:a 1 :b nil}) including-description)))
    (testing "exact keys"
      (is (true? (keyed? {:a 1 :b nil})))
      (is (true? (including? {:a 1 :b nil}))))
    (testing "other key"
      (is (false? (keyed? {:a 1 :b nil :c 2})))
      (is (true? (including? {:a 1 :b nil :c 2}))))
    (testing "absent key whose pair accepts nil"
      (is (false? (keyed? {:a 1})))
      (is (false? (including? {:a 1}))))
    (testing "not a map"
      (is (false? (keyed? nil)))
      (is (false? (including? [[:a 1] [:b nil]]))))))

(deftest no-result-test
  ;; plan "Cell outcomes": a cell with no entry gets one :result check
  ;; :actual :no-result.
  (testing "exit not 0 with an entry"
    ;; A crash (exit not 0) gives only :no-result.
    (let [[outcome] (outcomes "silent" [{}] (assoc (run (entry)) :exit 1))]
      (is (false? (:pass? outcome)))
      (is (= [[:result :no-result]] (map (juxt :check :actual) (:checks outcome))))
      (is (= 1 (get-in outcome [:failure :exit])))))
  (testing "result shorter than the cell count"
    (let [[a b] (outcomes "silent" [{} {}]
                          (assoc (run (entry)) :out "process out" :err "process err"))]
      (is (= [true []] (failed a)))
      (is (nil? (:failure a)))
      (is (false? (:pass? b)))
      (is (= [[:result :no-result]] (map (juxt :check :actual) (:checks b))))
      (is (= {:exit 0 :out "process out" :err "process err"} (:failure b)))))
  (testing "result that is not a vector"
    (let [result-outcomes (outcomes "silent" [{} {}]
                                    {:exit 0 :out "process out" :err ""
                                     :result {:value :ok}})]
      (is (= [[[:result :no-result]] [[:result :no-result]]]
             (mapv #(mapv (juxt :check :actual) (:checks %)) result-outcomes)))
      (is (= [{:exit 0 :out "process out" :err ""}
              {:exit 0 :out "process out" :err ""}]
             (mapv :failure result-outcomes))))))

(deftest rejection-test
  ;; With no pairs, rejected is exactly the :class :message :chain map.
  (let [chain harness/source-rejection-chain
        thrown {:class "java.lang.Error"
                :message (str harness/warning-prefix "x")
                :chain chain}
        rejection? (fn [context x] ((second (harness/rejected chain context {})) x))]
    (is (true? (rejection? :any thrown)))
    (testing "message without warning prefix"
      (is (false? (rejection? :any (assoc thrown :message "x")))))
    (testing "other chain"
      (is (false? (rejection? :any (assoc thrown :chain ["java.lang.Error"])))))
    (testing "exact context"
      (is (true? (rejection? "x" thrown)))
      (is (false? (rejection? "y" thrown))))))

(deftest rejected-test
  ;; A strict rejection map with other entries: the rejection keys pass
  ;; rejected without entries; the other keys pass their pairs; no other key.
  (let [chain harness/source-rejection-chain
        thrown {:class "java.lang.Error"
                :message (str harness/warning-prefix "x")
                :chain chain}
        [description rejected?] (harness/rejected chain :any {:n (harness/fixed 1)})]
    (testing "rejection with entries"
      (is (true? (rejected? (assoc thrown :n 1)))))
    (testing "message without warning prefix"
      (is (false? (rejected? (assoc thrown :n 1 :message "x")))))
    (testing "other chain"
      (is (false? (rejected? (assoc thrown :n 1 :chain ["java.lang.Error"])))))
    (testing "other entry value"
      (is (false? (rejected? (assoc thrown :n 2)))))
    (testing "other key"
      (is (false? (rejected? (assoc thrown :n 1 :m 1)))))
    (testing "description"
      (is (= (assoc (first (harness/rejected chain :any {})) :n 1) description)))))

(defn- collect-reports
  "Return the clojure.test reports of (assert-child child run), in order,
   each with the innermost :context of the report."
  [child run]
  (let [reports (atom [])]
    (binding [clojure.test/report
              #(swap! reports conj (assoc % :context (first clojure.test/*testing-contexts*)))]
      (harness/assert-child child run))
    @reports))

(deftest assert-child-report-test
  ;; All matrix cell assertions pass through assert-child; these cases show
  ;; its translation of outcomes to clojure.test reports.
  (testing "failing check"
    (let [child (harness/cell-child configuration
                                    [{:context ["report failing"] :form :ok :expected :ok}])
          reports (collect-reports child (run (entry :value :other)))
          fails (filter #(= :fail (:type %)) reports)]
      (is (= 1 (count fails)))
      (is (= {:expected :ok :actual :other}
             (select-keys (first fails) [:expected :actual])))
      (is (str/starts-with? (:message (first fails)) "result check failed: "))
      (is (str/includes? (:message (first fails)) ":out \"\""))))
  (testing "any warning description"
    (let [child (harness/cell-child (assoc configuration :policy "warn")
                                    [{:context ["report any warning"] :form :ok
                                      :expected :ok :fallback? true :warning :any}])
          reports (collect-reports child (run (entry)))
          fails (filter #(= :fail (:type %)) reports)]
      (is (= [{:expected '(warning-line <detail>) :actual ""}]
             (mapv #(select-keys % [:expected :actual]) fails)))
      (is (str/starts-with? (:message (first fails)) "err check failed: "))))
  (testing "fixed warning description"
    (let [child (harness/cell-child (assoc configuration :policy "warn")
                                    [{:context ["report warning"] :form :ok
                                      :expected :ok :fallback? true :warning "w"}])
          reports (collect-reports child (run (entry)))
          fails (filter #(= :fail (:type %)) reports)]
      (is (= [{:expected warning-line :actual ""}]
             (mapv #(select-keys % [:expected :actual]) fails)))))
  (testing "described predicate"
    (let [child (harness/cell-child
                 configuration
                 [{:context ["report described"] :form :ok
                   :expected (harness/described [{:value :ok} #(= :ok %)])}])
          reports (collect-reports child (run (entry :value :other)))
          [fail] (filter #(= :fail (:type %)) reports)]
      (is (= {:value :ok} (:expected fail)))))
  (testing "child result count"
    (let [child (harness/cell-child configuration
                                    [{:context ["report count 0"] :form :ok :expected :ok}
                                     {:context ["report count 1"] :form :ok :expected :ok}])
          reports (collect-reports child (run (entry)))
          fails (filter #(= :fail (:type %)) reports)]
      (is (= [(pr-str ["child" "java" [] "silent" nil]) "report count 1"]
             (mapv :context fails)))
      (is (= {:expected :ok :actual :no-result}
             (select-keys (second fails) [:expected :actual])))))
  (testing "child stderr"
    (let [child (harness/cell-child configuration
                                    [{:context ["report stderr"] :form :ok :expected :ok}])
          reports (collect-reports child (assoc (run (entry)) :err "x"))
          fails (filter #(= :fail (:type %)) reports)]
      (is (= [(pr-str ["child stderr" "java" [] "silent" nil])]
             (mapv :context fails)))
      (is (str/includes? (:message (first fails)) ":err \"x\""))))
  (testing "child exit"
    (let [child (harness/cell-child configuration
                                    [{:context ["report exit 0"] :form :ok :expected :ok}
                                     {:context ["report exit 1"] :form :ok :expected :ok}])
          reports (collect-reports child (assoc (run (entry) (entry)) :exit 1))
          fails (filter #(= :fail (:type %)) reports)]
      ;; cell-outcomes gives :no-result for each cell of a nonzero exit;
      ;; the grouped exit assertion is the one "child" failure.
      (is (= [(pr-str ["child" "java" [] "silent" nil]) "report exit 0" "report exit 1"]
             (mapv :context fails))))))

(deftest undescribed-predicate-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no description"
                        (harness/cell-child configuration
                                            [{:context ["undescribed"] :form :ok
                                              :expected keyword?}]))))

(deftest cell-child-wait-test
  ;; plan "Runner": cell-child gives the runner :cell-count and keeps
  ;; :timeout-ms, so the wait is :timeout-ms + 10000 x (cells - 1).
  (let [cell (fn [i] {:context [(pr-str [:wait i])] :form :ok :expected :ok})]
    (testing "timeout-ms and three cells"
      (is (= 22000
             (runner/wait-ms (harness/cell-child (assoc configuration :timeout-ms 2000)
                                                 (mapv cell (range 3)))))))
    (testing "default timeout and one cell"
      (is (= 120000
             (runner/wait-ms (harness/cell-child configuration [(cell 0)])))))))
