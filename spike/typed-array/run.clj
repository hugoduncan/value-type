;; This executable script is not a classpath namespace.
#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns typed-array-probe
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [criterium.bench :as bench]
            [value-type.core :refer [defvaluetype]])
  (:import [investigation Probe]
           [jdk.internal.value ValueClass]
           [java.util.function Supplier]))

(set! *warn-on-reflection* true)

(deftype IdentityLong [^long x])
(defvaluetype ValueLong [^long x])

(defn identity-array
  [^long n]
  (let [a (make-array IdentityLong n)]
    (dotimes [i n] (aset ^objects a i (IdentityLong. (long i))))
    a))

(defn value-array
  [^long n]
  (let [a (make-array ValueLong n)]
    (dotimes [i n] (aset ^objects a i (ValueLong. (long i))))
    a))

(defn restricted-array
  [^long n]
  (let [a (ValueClass/newNullRestrictedAtomicArray ValueLong (int n) (ValueLong. -1))]
    (dotimes [i n] (aset ^objects a i (ValueLong. (long i))))
    a))

(defn identity-sum
  ^long [^objects a]
  (loop [i (int 0) sum (long 0)]
    (if (< i (alength a))
      (recur (unchecked-inc-int i)
             (unchecked-add sum (.-x ^IdentityLong (aget a i))))
      sum)))

(defn value-sum
  ^long [^objects a]
  (loop [i (int 0) sum (long 0)]
    (if (< i (alength a))
      (recur (unchecked-inc-int i)
             (unchecked-add sum (.-x ^ValueLong (aget a i))))
      sum)))

;; Keep flat and reference-array profiles at separate access sites.
(defn restricted-sum
  ^long [^objects a]
  (loop [i (int 0) sum (long 0)]
    (if (< i (alength a))
      (recur (unchecked-inc-int i)
             (unchecked-add sum (.-x ^ValueLong (aget a i))))
      sum)))

(defn measure
  [n [kind factory pass]]
  (let [a (factory n)
        expected (quot (* n (dec n)) 2)
        _ (assert (= expected (pass a)))
        allocated (Probe/allocation
                   (reify Supplier (get [_] (factory n))) 30)
        storage (Probe/storage a)
        limit (parse-long (or (System/getenv "BENCH_SECONDS") "60"))
        _ (bench/bench (pass a) :limit-time-s limit :viewer :none)
        {:keys [batch-size stats]} (-> (bench/last-bench) :data :stats)
        ns-per-pass (/ (double (-> stats :elapsed-time :median)) (long batch-size))]
    (assert (= expected (pass a)))
    (java.lang.ref.Reference/reachabilityFence a)
    {:kind kind :array-class (.getName (class a))
     :component-value? (.isValue (.getComponentType (class a)))
     :flat? (ValueClass/isFlatArray a)
     :null-restricted? (ValueClass/isNullRestrictedArray a)
     :atomic? (ValueClass/isAtomicArray a)
     :sum expected :array-bytes (Probe/shallow a) :storage-bytes storage
     :allocated-bytes-per-build allocated
     :allocated-bytes-per-element (/ allocated n)
     :batch-size batch-size :ns-per-pass ns-per-pass :ns-per-element (/ ns-per-pass n)}))

(let [[size rotation configuration output] *command-line-args*
      n (parse-long size)
      shift (parse-long rotation)
      cases [[:identity identity-array identity-sum]
             [:value value-array value-sum]
             [:restricted restricted-array restricted-sum]]
      ordered (mapv #(nth cases (mod (+ shift %) 3)) (range 3))
      report {:n n :rotation shift :configuration configuration
              :java (System/getProperty "java.runtime.version")
              :os (System/getProperty "os.version")
              :arch (System/getProperty "os.arch")
              :clojure (clojure-version)
              :criterium "0.5.245-ALPHA"
              :benchmark-limit-s (parse-long (or (System/getenv "BENCH_SECONDS") "60"))
              :jvm-arguments (vec (.getInputArguments
                                  (java.lang.management.ManagementFactory/getRuntimeMXBean)))
              :results (mapv #(measure n %) ordered)}]
  (with-open [writer (io/writer output)]
    (pprint/pprint report writer))
  (prn report))
(shutdown-agents)
