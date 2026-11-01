(ns value-type.label-record
  "Label, child-start and cell recorder for compatibility test runs.

   Active only when VALUE_TYPE_TEST_RECORD names an output file. Then it
   wraps the root of clojure.test/test-var once, records each :pass, :fail
   and :error event with its label (Var symbol and testing contexts), the
   wall-clock time of each Var, each child start and each cell, and writes
   the full record as EDN after each Var. Uses Java 8 APIs only."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :as t]))

(def record-variable
  "The name of the environment variable that names the record file."
  "VALUE_TYPE_TEST_RECORD")

(def ^:private record-path (System/getenv record-variable))

(def ^:dynamic *var*
  "The test Var that runs on this thread, bound by the test-var wrapper."
  nil)

(defonce ^:private state
  (atom {:vars [] :assertions [] :children [] :cells []}))

(defn- var-symbol [v]
  (when v
    (let [{:keys [ns name]} (meta v)]
      (symbol (str (ns-name ns)) (str name)))))

(defn- label []
  [(var-symbol *var*) (vec (reverse t/*testing-contexts*))])

(defn- write! []
  (let [f (io/file record-path)]
    (some-> (.getParentFile f) .mkdirs)
    (spit f (pr-str @state))))

(defn- record-event! [m]
  (when (#{:pass :fail :error} (:type m))
    (swap! state update :assertions conj
           {:label (label) :type (:type m)})))

(defn- wrap-test-var [test-var]
  (fn recorded-test-var [v]
    (let [previous t/report
          start (System/nanoTime)]
      (binding [*var* v
                t/report (fn [m] (record-event! m) (previous m))]
        (try
          (test-var v)
          (finally
            (swap! state update :vars conj
                   {:var (var-symbol v)
                    :ms (quot (- (System/nanoTime) start) 1000000)})
            (write!)))))))

(defonce ^:private installed
  (when record-path
    (alter-var-root #'t/test-var wrap-test-var)
    true))

(defn active?
  "Return true when the recorder records this run."
  []
  (boolean installed))

(defn child-start!
  "Record one child start with the current label, kind and a hash of
   the child configuration (:java, :jvm-opts, :policy, :classpath)."
  [kind {:keys [java jvm-opts policy classpath]}]
  (when (active?)
    (swap! state
           (fn [s]
             (update s :children conj
                     {:label (label)
                      :kind kind
                      :config (hash [java (vec jvm-opts) policy classpath])
                      :assertion-index (count (:assertions s))})))))

(defn cell!
  "Record one cell result under the current label."
  []
  (when (active?)
    (swap! state update :cells conj {:label (label)})))

(defn- var-ns [v] (some-> v namespace symbol))

(defn- label-ns [[v]] (var-ns v))

(defn- nested-label? [[v contexts] [sv scontexts]]
  (and (= v sv)
       (> (count contexts) (count scontexts))
       (= scontexts (subvec contexts 0 (count scontexts)))))

(defn- start-label-cells [{:keys [assertions children]}]
  (let [starts (set (map :label (filter #(= :cell (:kind %)) children)))
        bounds (concat (map :assertion-index (rest children))
                       [(count assertions)])]
    (reduce
     (fn [counts [{:keys [label kind assertion-index]} end]]
       (if (= :cell kind)
         (let [nested (->> (subvec assertions assertion-index end)
                           (map :label)
                           (filter #(and (nested-label? % label)
                                         (not (starts %))))
                           distinct)]
           (if (seq nested)
             (reduce #(update %1 %2 (fnil inc 0)) counts nested)
             (update counts label (fnil inc 0))))
         counts))
     {}
     (map vector children bounds))))

(defn- cell-counts [{:keys [cells]}]
  (frequencies (map :label cells)))

(defn summary
  "Read one record file and return a map from namespace symbol to
   {:children {[test kind] {:count n :configurations n}}
    :ms namespace-time :labels {label cell-count}}.
   method is :start-labels (first record) or :cells (after record)."
  [path method]
  (let [record (edn/read-string (slurp path))
        counts (case method
                 :start-labels (start-label-cells record)
                 :cells (cell-counts record))
        labels (distinct (concat (map :label (:assertions record))
                                 (map :label (:children record))
                                 (keys counts)))
        nss (distinct (concat (keep (comp var-ns :var) (:vars record))
                              (keep label-ns labels)))]
    (into (sorted-map)
          (for [n nss]
            [n {:ms (reduce + (for [{:keys [var ms]} (:vars record)
                                    :when (= n (var-ns var))]
                                ms))
                :children
                (into {}
                      (for [[k cs] (group-by (juxt (comp first :label) :kind)
                                             (filter #(= n (label-ns (:label %)))
                                                     (:children record)))]
                        [k {:count (count cs)
                            :configurations (count (distinct (map :config cs)))}]))
                :labels (into {}
                              (for [l labels :when (= n (label-ns l))]
                                [l (get counts l 0)]))}]))))
