;; Acceptance 2 and 3 checks of a task-018 after record.
;;
;; Run from the repository root:
;;
;;   clojure -Sdeps '{:paths ["test/compatibility"]}' -M \
;;     munera/closed/018-fast-compatibility-matrix/check_record.clj \
;;     <raw-record-dir> [<limit-1-raw-record-dir>]
;;
;; <raw-record-dir> holds the VALUE_TYPE_TEST_RECORD files of the four
;; commands at limit 4: fallback-java8.edn, fallback-preview-off.edn,
;; fallback-preview-on.edn, cross-jvm.edn. <limit-1-raw-record-dir> holds
;; fallback-java8.edn and cross-jvm.edn at limit 1.
;;
;; The script writes the summaries (label-record/summary :cells) to
;; after-record/<command>.edn and prints each finding. It checks:
;; - no cell label has a cell count above 1;
;; - each [test kind] has count <= distinct configurations, except the
;;   rule-5 exceptions below (count <= the stated limit);
;; - label mapping: each first-record label of a command (first-record/)
;;   has targets in the after labels of that command and cross-jvm: the
;;   same label, or a label of the same Var whose contexts extend it. A
;;   first label is mapped before the match: moved-vars gives the new Var
;;   of the 5 Vars moved from identity-receiving-test; context-renames
;;   gives the functions applied to each context of a Var (the 5 Vars of
;;   the mode rename, code-shaper passes 15, 17, 18, 22, give :analysis for
;;   :function; public-imported-concrete-field-test gives the fixed child
;;   tags for the gensym names; 3 runtime-test Vars give the expand keyword
;;   for :full and :one, code-shaper pass 25; receiving-identity-fields-test
;;   gives the construction keyword for direct, factory and reflective,
;;   implementation test review pass 35). inserted-contexts gives the
;;   expand contexts that 2 after Vars insert above an inner context
;;   (implementation test review pass 32); the match ignores them. The
;;   target cell counts sum to at least the first count. Thus a finding is a new deviation only;
;; - limit 1 and limit 4 give equal :assertions label vectors in each test
;;   namespace, and the same namespaces. Scry 0.1.61 orders namespaces by
;;   a hash map keyed by Namespace (identity hash), so the namespace order
;;   can differ in each JVM run; it is not a runner property.
;; Time (acceptance 2) comes from /usr/bin/time -p, not from this script.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pp]
         '[clojure.string :as str]
         '[value-type.label-record :as record])

(def task-dir "munera/closed/018-fast-compatibility-matrix")

(def commands ["fallback-java8" "fallback-preview-off" "fallback-preview-on" "cross-jvm"])

(def rule-5-exceptions
  "[test kind] → allowed child count (implementation.md exception table)."
  {['value-type.array-info-boundary-test/safe-inspection-boundary-test :cell] 36
   ['value-type.array-matrix-test/configuration-matrix-test :cell] 36
   ['value-type.array-sequence-boundary-test/sequence-configuration-matrix-test :cell] 36
   ['value-type.runtime-test/fallback-expansion-warning-timing-test :cell] 4
   ['value-type.transfer-test/concurrent-warning-scope-test :cell] 2
   ['value-type.transfer-test/constrained-concurrent-warning-scope-test :cell] 8
   ['value-type.cell-runner-test/child-limit-test :cell] 5})

(def moved-vars
  "First Var → after Var (moved from identity-receiving-test)."
  (into {}
        (for [n ["receiving-identity-policy-test" "incompatible-value-receiving-test"
                 "generated-value-receiving-policy-test"
                 "strict-nested-public-declaration-test"
                 "nested-public-warning-scope-test"]]
          [(symbol "value-type.identity-receiving-test" n)
           (symbol "value-type.identity-receiving-cross-jvm-test" n)])))

(defn- analysis-mode
  "Context with the mode part :function as :analysis (code-shaper passes
  15, 17, 18, 22)."
  [context]
  (str/replace context ":function" ":analysis"))

(def fixed-tags
  "Gensym child tag pattern → fixed context."
  {#"^imported_fields_\d+\.NullableChild\d+$" "imported_fields.NullableChild"
   #"^NullableChild\d+$" "NullableChild"})

(defn- fixed-tag
  "Context with a gensym child tag as its fixed context (implementation
  review pass 12)."
  [context]
  (or (some (fn [[re fixed]] (when (re-find re context) fixed)) fixed-tags)
      context))

(defn- expand-keyword
  "Context with the entry point part :full or :one as its expand keyword of
  transfer-checks/expands (code-shaper pass 25)."
  [context]
  (str/replace context #"^:(?:full|one)\b"
               #(if (= ":full" %) ":macroexpand" ":macroexpand-1")))

(defn- construction-keyword
  "Context direct, factory or reflective as the printed construction keyword
  of consumer-aot-checks/constructions (implementation test review pass
  35)."
  [context]
  (if (#{"direct" "factory" "reflective"} context)
    (pr-str (keyword context))
    context))

(def context-renames
  "First Var → functions of one context, applied in order."
  {'value-type.runtime-test/fallback-expansion-warning-timing-test [expand-keyword]
   'value-type.runtime-test/constrained-discarded-expansion-test [expand-keyword]
   'value-type.runtime-test/constrained-validation-before-policy-test [expand-keyword]
   'value-type.controlled-field-capability-test/controlled-native-absence-receiving-test
   [analysis-mode]
   'value-type.loader-policy-test/fresh-policy-loader-rejection-test [analysis-mode]
   'value-type.identity-loader-test/public-stale-sibling-class-test [analysis-mode]
   'value-type.identity-receiving-test/receiving-identity-fields-test
   [analysis-mode construction-keyword]
   'value-type.isolated-sibling-loader-test/public-isolated-sibling-construction-test
   [analysis-mode]
   'value-type.fields-test/public-imported-concrete-field-test [fixed-tag]})

(defn- rename [[v contexts]]
  [(get moved-vars v v)
   (reduce (fn [cs f] (mapv f cs)) contexts (context-renames v))])

(def expand-contexts
  "The expand contexts of identity-receiving-test (:internal and
  transfer-checks/expands)."
  #{":internal" ":macroexpand" ":macroexpand-1"})

(def inserted-contexts
  "After Var → contexts that an after label inserts in the contexts of a
  first label (implementation test review pass 32: an inner expand context
  above an existing inner context)."
  {'value-type.identity-receiving-test/receiving-identity-fields-test expand-contexts
   'value-type.public-field-annotations-test/public-field-annotations-test
   expand-contexts})

(defn- without-inserted [[v contexts]]
  [v (into [] (remove (get inserted-contexts v #{})) contexts)])

(defn- labels [summary]
  (apply merge (map :labels (vals summary))))

(defn- label-extends? [[v contexts] [tv tcontexts]]
  (and (= v tv)
       (<= (count contexts) (count tcontexts))
       (= contexts (subvec tcontexts 0 (count contexts)))))

(defn- mapping-findings [first-labels after-labels]
  (for [[label n] first-labels
        :let [l (rename label)
              targets (filter #(label-extends? l (without-inserted (key %)))
                              after-labels)
              total (reduce + (map val targets))]
        :when (or (empty? targets) (< total n))]
    {:label label :first n :targets (count targets) :after total}))

(defn- bound-findings [summary]
  (for [[_ {:keys [children]}] summary
        [k {:keys [count configurations]}] children
        :let [limit (get rule-5-exceptions k configurations)]
        :when (> count limit)]
    {:test k :count count :configurations configurations}))

(defn- assertion-labels
  "Test namespace → :assertions label vector of that namespace, in report order."
  [path]
  (reduce (fn [m {[v :as label] :label}]
            (update m (namespace v) (fnil conj []) label))
          {}
          (:assertions (edn/read-string (slurp path)))))

(let [[raw-dir limit-1-dir] *command-line-args*
      after (into {} (for [c commands]
                       [c (record/summary (str raw-dir "/" c ".edn") :cells)]))
      cross (labels (after "cross-jvm"))]
  (doseq [[c s] after]
    (spit (io/file task-dir "after-record" (str c ".edn"))
          (with-out-str (pp/pprint s))))
  (doseq [c commands
          :let [s (after c)]]
    (println c
             "max-cell-count" (reduce max 0 (vals (labels s)))
             "children" (reduce + (for [[_ n] s [_ {:keys [count]}] (:children n)] count))
             "ms" (reduce + (map :ms (vals s))))
    (doseq [f (bound-findings s)] (println "  bound:" (pr-str f))))
  (doseq [c (butlast commands)
          :let [first-labels (labels (edn/read-string
                                      (slurp (io/file task-dir "first-record" (str c ".edn")))))
                findings (mapping-findings first-labels (merge (labels (after c)) cross))]]
    (println c "first labels" (count first-labels) "mapping findings" (count findings))
    (doseq [f findings] (println "  mapping:" (pr-str f))))
  (when limit-1-dir
    (doseq [c ["fallback-java8" "cross-jvm"]
            :let [a (assertion-labels (str raw-dir "/" c ".edn"))
                  b (assertion-labels (str limit-1-dir "/" c ".edn"))]]
      (println c "limit-1 = limit-4 :assertions labels of each namespace" (= a b)
               "namespaces" (count a) (count b)
               "assertions" (reduce + (map count (vals a))) (reduce + (map count (vals b))))
      (doseq [n (sort (into (set (keys a)) (keys b)))
              :when (not= (a n) (b n))]
        (println "  order:" n (count (a n)) (count (b n)))))))
