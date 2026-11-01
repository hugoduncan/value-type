(ns value-type.cell-harness
  "Run more than one isolated cell in one child JVM and assert each cell.

   A cell is one checked case: its own namespace, LOADER frame, dynamic
   binding frame and *out*/*err* writers. Uses Java 8 APIs only."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [value-type.cell-registry :as registry]
            [value-type.jvm-runner :as runner]
            [value-type.label-record :as record])
  (:import (java.util.regex Pattern)))

(def warning-prefix
  "Text before the context of each fallback warning line and strict policy
   rejection message."
  "value-type: identity deftype fallback: ")

(def unavailable-detail
  "Fallback context of a source definition on Java 8: the missing ClassFile
   API class."
  "java/lang/classfile/ClassFile")

(defn rejection-context
  "Return the fallback context of a source definition without preview on
   configuration (a jvm-config/configuration map): unavailable-detail on
   Java 8, else :any (the context holds a random probe class name)."
  [{:keys [baseline?]}]
  (if baseline? unavailable-detail :any))

(defn policy-rejection
  "Return the expected root failure of strict policy for context."
  [context]
  {:class "java.lang.Error"
   :message (str warning-prefix context)})

(def fallback-context
  "Fallback context of a fixed fallback representation."
  "fixed fallback representation")

(def fallback-rejection
  "Expected root failure of strict policy for a fixed fallback representation."
  (policy-rejection fallback-context))

(def source-rejection-chain
  "Cause chain of a strict policy rejection in source definition: the
   compiler wrapper, then the root java.lang.Error."
  ["clojure.lang.Compiler$CompilerException" "java.lang.Error"])

(def direct-rejection-chain
  "Cause chain of a strict policy rejection of a direct call: the root
   java.lang.Error only."
  [(:class (policy-rejection fallback-context))])

(defn controlled-absence
  "Return a form that loads value-type.impl.runtime and the namespaces of
   requires (symbols), then evaluates body under controlled native absence:
   with-redefs replaces only the native capability accessor
   (value-type.impl.runtime/field-capability) with an unavailable result of
   reason :controlled-absence. The require is a separate top-level form, so
   the namespaces are loaded before body is compiled."
  [requires body]
  (list 'do
        (list* 'require (map #(list 'quote %)
                             (cons 'value-type.impl.runtime requires)))
        (list 'with-redefs
              '[value-type.impl.runtime/field-capability
                (constantly {:available? false :reason :controlled-absence})]
              body)))

(defn described
  "Return cell :expected predicate f of the pair [data f] with
   description data, which a failure report gives as :expected in place
   of f. Each [description predicate] function of this namespace gives
   such a pair.

   A description is the expected value; a map is a map with exactly its
   keys. These forms describe a part that is not one fixed value:
     <detail>          a runtime value or text that the description does
                       not fix (for example a fallback context)
     (contains x ...)  a collection that has each x as an item, or a
                       string that has each x as a substring
     (repeat n x)      a sequence of n items, each x; n :+ is one or more;
                       in a string, n occurrences of x
     (starts-with s)   a string that starts with s
     (including m)     a map that has each key of m, with a value that
                       the description of the key gives; other keys are
                       permitted
     #{x ...}          one of the items
     (warning-line <detail>)
                       the *err* line of a fallback warning of any
                       context (see warning-line)"
  [[data f]]
  (vary-meta f assoc ::description data))

(defn fixed
  "Return [description predicate] (see described) of the fixed value x."
  [x]
  [x #(= x %)])

(defn entries
  "Return [description predicate] (see described) of a sequence of
   [description predicate] pairs: the vector of the descriptions, and a
   predicate that is true when its argument has one item for each pair, in
   order, and each item passes the predicate of its pair."
  [pairs]
  [(mapv first pairs)
   (fn [xs]
     (boolean (and (= (count pairs) (count xs))
                   (every? true? (map (fn [[_ item?] x] (item? x)) pairs xs)))))])

(defn- entries-pass?
  "Return true when x has each key of pairs (a map of key to [description
   predicate]) and the value of each key passes the predicate of its pair."
  [pairs x]
  (every? (fn [[k [_ value?]]] (and (contains? x k) (value? (get x k)))) pairs))

(defn keyed
  "Return [description predicate] (see described) of a map of key to
   [description predicate]: the map of key to description, and a predicate
   that is true when its argument is a map with exactly the keys of pairs,
   and the value of each key passes the predicate of its pair."
  [pairs]
  [(update-vals pairs first)
   (fn [x]
     (boolean (and (map? x)
                   (= (set (keys pairs)) (set (keys x)))
                   (entries-pass? pairs x))))])

(defn repeated
  "Return [description predicate] of the form (repeat n x) (see described)
   of [description predicate] of x: the predicate is true when its argument
   has n items (n :+: one or more) that each pass the predicate of x."
  [n [description item?]]
  [(list 'repeat n description)
   (fn [xs]
     (boolean (and (if (= :+ n) (seq xs) (= n (count xs)))
                   (every? item? xs))))])

(defn- occurrences
  "Return the number of occurrences of text in s (nil as \"\")."
  [text s]
  (count (re-seq (re-pattern (Pattern/quote text)) (or s ""))))

(defn repeated-text
  "Return [description predicate] of the form (repeat n text) in a string
   (see described): the predicate is true when its argument (nil as \"\")
   has n occurrences of text (n :+: one or more)."
  [n text]
  [(list 'repeat n text)
   (fn [s]
     (let [c (occurrences text s)]
       (if (= :+ n) (pos? c) (= n c))))])

(defn contains
  "Return [description predicate] of the form (contains x ...) (see
   described) of xs: the predicate is true when its argument is a string
   that has each x as a substring, or a collection that has each x as an
   item."
  [xs]
  [(list* 'contains xs)
   (fn [x]
     (boolean
      (cond
        (string? x) (every? #(str/includes? x %) xs)
        (coll? x) (every? (fn [item] (some #(= item %) x)) xs)
        :else false)))])

(defn starts-with
  "Return [description predicate] of the form (starts-with s) (see
   described): the predicate is true when its argument is a string that
   starts with s."
  [s]
  [(list 'starts-with s)
   #(boolean (and (string? %) (str/starts-with? % s)))])

(defn including
  "Return [description predicate] of the form (including m) (see
   described) of a map of key to [description predicate]: m is the map of
   key to description, and the predicate is true when its argument is a
   map that has each key of pairs (other keys are permitted), and the
   value of each key passes the predicate of its pair."
  [pairs]
  [(list 'including (update-vals pairs first))
   #(boolean (and (map? %) (entries-pass? pairs %)))])

(defn with-entry
  "Return (keyed pairs) of the map m of fixed values with the key k, whose
   value passes the pair entry: a map with exactly the keys of m and k."
  [m k entry]
  (keyed (assoc (update-vals m fixed) k entry)))

(defn detail?
  "Return true when x is a present runtime <detail> (see described): a
   non-blank string."
  [x]
  (and (string? x) (not (str/blank? x))))

(defn detail
  "Return [description predicate] of a runtime <detail> (see described):
   the predicate is value? (default detail?)."
  ([] (detail detail?))
  ([value?] ['<detail> value?]))

(defn observed
  "Return [description predicate] (see described) of a value whose
   expectation depends on the observed value at key path ks. pair-of gives
   the [description predicate] of the whole value for one value at ks. The
   description gives description (of [description value?]) to pair-of; the
   predicate is true when the observed value passes value? and the whole
   value passes the predicate that pair-of gives for the observed value."
  [ks [description value?] pair-of]
  [(first (pair-of description))
   (fn [x]
     (let [v (get-in x ks)]
       (boolean (and (value? v) ((second (pair-of v)) x)))))])

(defn detailed
  "Return [description predicate] (see described) of a map with :detail,
   the runtime capability detail (see observed). With present?, :detail is
   a runtime <detail> (see detail); else nil. The map without :detail
   equals (expected-of detail)."
  [present? expected-of]
  (observed [:detail]
            (if present? (detail) (fixed nil))
            #(fixed (assoc (expected-of %) :detail %))))

(defn- rejection-pairs
  "Return the map of key to [description predicate] (see described) of the
   :class :message :chain of a cell-registry/thrown map of a strict policy
   rejection with cause chain: root java.lang.Error with message
   warning-prefix + context. When context is :any (a context with a random
   probe class name), the message only starts with warning-prefix."
  [chain context]
  (let [root (policy-rejection context)]
    {:class (fixed (:class root))
     :message (if (= :any context) (starts-with warning-prefix) (fixed (:message root)))
     :chain (fixed chain)}))

(defn rejected
  "Return [description predicate] (see described) of a map of a strict
   policy rejection with cause chain and context (see rejection-pairs) and
   the other entries of pairs (a map of key to [description predicate]): a
   map with exactly :class :message :chain and the keys of pairs."
  [chain context pairs]
  (keyed (merge (rejection-pairs chain context) pairs)))

(defn failed-definition
  "Return [description predicate] (see described) of a
   cell-registry/failed-definition map of a source definition under strict
   policy: rejected of source-rejection-chain and context, with the type
   absent and the other entries of pairs."
  [context pairs]
  (rejected source-rejection-chain context (assoc pairs :absent? (fixed true))))

(defn description
  "Return the :expected value of a check report: the description of a
   described predicate (see described), else expected."
  [expected]
  (if (fn? expected)
    (get (meta expected) ::description expected)
    expected))

(defn runtime-context
  "Return the context label (pr-str [java jvm-opts & parts]) of runtime, a
   map with :java and :jvm-opts."
  [runtime & parts]
  (pr-str (into [(:java runtime) (:jvm-opts runtime)] parts)))

(defn cell-specs
  "Return the producer specs [(registry/cell-ns-name i) x] for each
   item x at index i of xs. Cell i runs in namespace cell.i, which is the
   producer namespace of its expansion."
  [xs]
  (vec (map-indexed (fn [i x] [(registry/cell-ns-name i) x]) xs)))

(defn- source [form]
  (if (string? form)
    form
    (binding [*print-meta* true] (pr-str form))))

(defn child-form
  "Return the child source that runs cell sources in input order and prints
   the EDN vector of cell result entries as the last stdout line."
  [sources]
  (pr-str
   `(do
      (require 'value-type.cell-registry)
      (let [top-loader# (let [v# clojure.lang.Compiler/LOADER]
                          (if (.isBound v#) (deref v#) (clojure.lang.RT/baseLoader)))
            top-bindings# (get-thread-bindings)
            run-cell#
            (fn [index# source#]
              (let [cell-ns# (create-ns (symbol (value-type.cell-registry/cell-ns-name index#)))
                    out# (java.io.StringWriter.)
                    err# (java.io.StringWriter.)]
                (binding [*ns* cell-ns#] (refer 'clojure.core))
                (let [outcome#
                      (with-bindings
                        (assoc top-bindings#
                               clojure.lang.Compiler/LOADER
                               (clojure.lang.DynamicClassLoader. top-loader#)
                               #'*ns* cell-ns#
                               #'*out* out#
                               #'*err* err#)
                        (try
                          {:value (load-string source#)}
                          (catch Throwable thrown#
                            {:thrown (value-type.cell-registry/thrown thrown#)})
                          (finally
                            (.flush ^java.io.Writer *out*)
                            (.flush ^java.io.Writer *err*))))]
                  (assoc outcome# :out (str out#) :err (str err#)))))
            entries# (vec (map-indexed run-cell# ~(vec sources)))]
        (binding [*print-meta* false *print-length* nil *print-level* nil
                  *print-namespace-maps* false]
          (prn entries#))
        (flush)
        (.flush System/out)
        (shutdown-agents)))))

(defn- check-expected!
  "Throw when the :expected of cell is a predicate without a description
   (see described): its failure report would hide the expectation."
  [{:keys [context expected]}]
  (when (and (fn? expected) (not (contains? (meta expected) ::description)))
    (throw (ex-info (str "cell :expected predicate has no description: "
                         (pr-str context))
                    {:context context}))))

(defn cell-child
  "Return a run-jvm option map (:kind :cell) that runs cells in one child.

   configuration holds :java, :classpath, :jvm-opts, :policy and optional
   :timeout-ms and :fixture-ns (label only). Each cell holds :context,
   :form (data or source string; its value is the cell result), :expected
   (a value, or a predicate made with described), :fallback? and :warning.
   Throws when an :expected predicate has no description. The map keeps
   :cells and :configuration for cell-outcomes and assert-child."
  [{:keys [java classpath jvm-opts policy timeout-ms] :as configuration} cells]
  (run! check-expected! cells)
  (cond-> {:java java
           :classpath classpath
           :jvm-opts (vec jvm-opts)
           :policy policy
           :kind :cell
           :cell-count (count cells)
           :form (child-form (mapv (comp source :form) cells))
           :cells (vec cells)
           :configuration configuration}
    timeout-ms (assoc :timeout-ms timeout-ms)))

(defn warning-line
  "Return the *err* line of the fallback warning of context warning."
  [warning]
  (str warning-prefix warning \newline))

(defn- any-warning-line
  "Return [description predicate] (see described) of the *err* line of the
   fallback warning of any context: the form (warning-line <detail>)."
  []
  [(list 'warning-line '<detail>)
   #(boolean (and (string? %)
                  (re-matches (re-pattern (str (Pattern/quote warning-prefix)
                                               "[^\\r\\n]*\\r?\\n"))
                              %)))])

(defn- expected-err
  "Return the described expected *err* of the cell at index (see
   described): the warning line of the first fallback cell of a warn
   child (any-warning-line for warning :any), else \"\"."
  [{:keys [policy cells]} index]
  (let [first-fallback (first (keep-indexed (fn [i c] (when (:fallback? c) i)) cells))]
    (described
     (if (and (= "warn" policy) (= index first-fallback))
       (let [{:keys [warning]} (nth cells index)]
         (if (= :any warning) (any-warning-line) (fixed (warning-line warning))))
       (fixed "")))))

(def ^:private expected-out
  "Described expected *out* of each cell."
  (described (fixed "")))

(defn- result-matches? [expected actual]
  (if (fn? expected)
    (boolean (try (expected actual) (catch Throwable _ false)))
    (= expected actual)))

(defn- check-result [k expected actual pass?]
  {:check k :expected expected :actual actual :pass? (boolean pass?)})

(defn cell-outcomes
  "Return one outcome for each cell of child (a cell-child map), in input
   order, from its run-jvm result: {:context :pass? :checks :failure}."
  [{:keys [cells] :as child} {:keys [exit result read-error] :as run}]
  (let [entries (when (and (= 0 exit) (not read-error) (vector? result)) result)]
    (vec
     (map-indexed
      (fn [index {:keys [context expected]}]
        (let [entry (get entries index)
              checks
              (if (map? entry)
                (let [actual (if (contains? entry :thrown)
                               (select-keys entry [:thrown])
                               (:value entry))
                      err-expected (expected-err child index)]
                  [(check-result :result expected actual (result-matches? expected actual))
                   (check-result :out expected-out (:out entry)
                                 (result-matches? expected-out (:out entry)))
                   (check-result :err err-expected (:err entry)
                                 (result-matches? err-expected (:err entry)))])
                [(check-result :result expected :no-result false)])
              pass? (every? :pass? checks)]
          {:context (vec context)
           :pass? pass?
           :checks checks
           :failure (when-not pass?
                      (if (map? entry)
                        (cond-> {:exit exit :out (:out entry) :err (:err entry)}
                          (:thrown entry) (assoc :thrown (:thrown entry)))
                        (runner/run-facts run)))}))
      cells))))

(defn- child-context [label {:keys [java jvm-opts policy configuration]}]
  (pr-str [label java jvm-opts policy (:fixture-ns configuration)]))

(defn assert-run
  "Assert each value of expected (a map of run keys) against run: one is
   for each key, in map order, with the run-facts of run as the message.
   Return true when all pass."
  [run expected]
  (let [facts (pr-str (runner/run-facts run))]
    (every? true? (mapv (fn [[k v]] (t/is (= v (get run k)) facts)) expected))))

(defn assert-child-stderr
  "Assert empty child-level stderr of child (a cell-child map) with
   assert-run."
  [child run]
  (t/testing (child-context "child stderr" child)
    (assert-run run {:err ""})))

(defn- report-check [{:keys [check expected actual pass?]} failure]
  (t/do-report
   (cond-> {:type (if pass? :pass :fail)
            :expected (description expected)
            :actual actual}
     (not pass?) (assoc :message (str (name check) " check failed: "
                                      (pr-str failure))))))

(defn assert-child
  "Make the support and cell assertions of child (a cell-child map) from
   its run-jvm result, on the calling thread, in input order."
  [{:keys [cells] :as child} {:keys [result] :as run}]
  (assert-child-stderr child run)
  (when (< 1 (count cells))
    (t/testing (child-context "child" child)
      (assert-run run {:exit 0})
      (t/is (= (count cells) (when (vector? result) (count result)))
            (pr-str (runner/run-facts run)))))
  (doseq [{:keys [context checks failure]} (cell-outcomes child run)]
    (letfn [(cell [contexts]
              (if (seq contexts)
                (t/testing (first contexts) (cell (rest contexts)))
                (do
                  (record/cell!)
                  (doseq [c checks] (report-check c failure)))))]
      (cell context))))

(defn run-cells
  "Run cell children with run-jvms and assert each with assert-child.
   Return the run-jvm results."
  [children]
  (let [results (runner/run-jvms (vec children))]
    (doseq [[child run] (map vector children results)]
      (assert-child child run))
    results))

(defn produce
  "Run one producer child (run-jvm, :kind :producer) from runtime that
   evaluates form, printed with metadata. Assert exit 0 and empty stderr
   under the support label \"producer\". Return the child :result when
   both pass, else nil."
  [runtime form]
  (let [producer (runner/run-jvm (assoc runtime :kind :producer :form (source form)))]
    (when (t/testing "producer" (assert-run producer {:exit 0 :err ""}))
      (:result producer))))
