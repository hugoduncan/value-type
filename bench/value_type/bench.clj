(ns value-type.bench
  "Method-call benchmark: `defvaluetype` against `deftype` with the
  same body. Run with `clojure -M:bench -m value-type.bench [n]`.
  Results: `ramora/defvaluetype-design/benchmark.md`."
  (:require
   [clojure.pprint :as pprint]
   [clojure.walk :as walk]
   [criterium.bench :as bench]
   [value-type.core :as vt]))

(set! *warn-on-reflection* true)

(definterface INorm (^long norm []))

(defprotocol Norm (norm [this]))

(defmacro ^:private defpair
  "Define `dt` with `deftype` and `vt` with `defvaluetype`. Both have
  the fields `^long x` and `^long y`, and the same `specs`."
  [dt vt & specs]
  `(do (deftype ~dt [~'^long x ~'^long y] ~@specs)
       (vt/defvaluetype ~vt [~'^long x ~'^long y] ~@specs)))

(defpair IDt IVt INorm (norm [_] (+ x y)))
(defpair PDt PVt Norm (norm [_] (+ x y)))
(defpair Dt Vt)

(defn- substitute-t
  "Replace `T`, `T.` and the type hint `^T` in `form` with the type
  symbol `t`."
  [t form]
  (walk/postwalk
   (fn [x]
     (let [x (cond (= 'T x) t
                   (= (symbol "T.") x) (symbol (str t "."))
                   :else x)]
       (if (= 'T (:tag (meta x)))
         (vary-meta x assoc :tag t)
         x)))
   form))

(defmacro ^:private defpass
  "Define one pass function for each `[name type]` in `fns`. A pass
  adds `value` (with `T` bound to the type) for each element `e` of
  array `a` to a `long` sum, and returns the sum. If `value` uses
  `e2`, the pass also takes array `b` and binds `e2` to its element.
  The functions differ only by `T`."
  [value & fns]
  (let [pair? (some #{'e2} (tree-seq coll? seq value))]
    `(do
       ~@(for [[fname t] (partition 2 fns)]
           `(defn ~fname ^long
              [~'^objects a ~@(when pair? [(with-meta 'b {:tag 'objects})])]
              (let [~'n (alength ~'a)]
                (loop [~'i 0 ~'s 0]
                  (if (< ~'i ~'n)
                    (let [~'e (aget ~'a ~'i)
                          ~@(when pair? ['e2 '(aget b i)])]
                      (recur (inc ~'i) (+ ~'s ~(substitute-t t value))))
                    ~'s))))))))

(defpass (.norm ^INorm e) interface-dt IDt interface-vt IVt)
(defpass (long (norm e)) protocol-dt PDt protocol-vt PVt)
(defpass (.-x ^T e) field-dt Dt field-vt Vt)
(defpass (if (identical? e e2) 1 0) identical-dt Dt identical-vt Vt)
(defpass (.-x ^T (T. (.-x ^T e) (.-y ^T e))) allocate-dt Dt allocate-vt Vt)

(defn- instances
  "An `Object` array of `n` instances from `ctor`; element `i` has
  `x = i` and `y = 2i`."
  ^objects [ctor ^long n]
  (let [a (object-array n)]
    (dotimes [i n]
      (aset a i (ctor i (* 2 i))))
    a))

(def ^:private cases
  "Case name, criterion flag, two-array flag, then pass function and
  constructor for each kind."
  [[:interface true false interface-dt ->IDt interface-vt ->IVt]
   [:protocol true false protocol-dt ->PDt protocol-vt ->PVt]
   [:field false false field-dt ->Dt field-vt ->Vt]
   [:identical false true identical-dt ->Dt identical-vt ->Vt]
   [:allocate false false allocate-dt ->Dt allocate-vt ->Vt]])

(def ^:private limit-time-s 10)

(defn- median-ns
  "Median elapsed time (ns) of one evaluation in the last criterium
  run. A sample is a batch of evaluations, so divide the sample median
  by the batch size. The paths are internal to criterium
  0.5.245-ALPHA."
  ^double []
  (let [{:keys [batch-size stats]} (-> (bench/last-bench) :data :stats)]
    (/ (double (-> stats :elapsed-time :median)) (long batch-size))))

(defn- measure
  "Median time (ns) of one pass of `pass` over a new array of `n`
  instances from `ctor`, and a second new array if `pair?`."
  ^double [pass pair? ctor n]
  (let [a (instances ctor n)]
    (if pair?
      (let [b (instances ctor n)]
        (bench/bench (pass a b) :limit-time-s limit-time-s :viewer :none))
      (bench/bench (pass a) :limit-time-s limit-time-s :viewer :none))
    (median-ns)))

(defn- validate-n
  [n]
  (when-not (and (integer? n) (<= 1 n Integer/MAX_VALUE))
    (throw (ex-info
            (str "Array length n must be an integer in [1, 2147483647]; got "
                 (pr-str n))
            {:n n :min 1 :max Integer/MAX_VALUE})))
  n)

(defn run
  "Run all cases over `n` instances. Return a vector of result maps.
  `n` must be an integer in [1, 2147483647] (the JVM array-length
  range). Available memory can impose a lower limit. Invalid input
  throws ExceptionInfo before arrays are built or measurement starts."
  [n]
  (validate-n n)
  (mapv
   (fn [[case-name criterion? pair? dt-pass dt-ctor vt-pass vt-ctor]]
     (let [dt (/ (measure dt-pass pair? dt-ctor n) n)
           vt (/ (measure vt-pass pair? vt-ctor n) n)]
       {:case case-name
        :criterion? criterion?
        :deftype-ns dt
        :defvaluetype-ns vt
        :ratio (/ vt dt)}))
   cases))

(defn- print-results [n results]
  (println "n =" n)
  (println)
  (println "| Case | Kind | deftype (ns/call) | defvaluetype (ns/call) | Ratio |")
  (println "|---|---|---:|---:|---:|")
  (doseq [{:keys [case criterion? deftype-ns defvaluetype-ns ratio]} results]
    (println (format "| `%s` | %s | %.3f | %.3f | %.2f |"
                     (name case)
                     (if criterion? "criterion" "information")
                     deftype-ns defvaluetype-ns ratio)))
  (println)
  (pprint/pprint {:n n :results results}))

(defn -main [& [n]]
  (let [n (if n (or (parse-long n) n) 1024)]
    (print-results n (run n))
    (shutdown-agents)))
