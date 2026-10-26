(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [semantic-checks]}}}} allocation
  (:require [clojure.pprint :as pprint]
            [value-type.core :refer [defvaluetype]])
  (:import [java.lang.management ManagementFactory]))

(load-file "spike/value-array-semantics/semantic_checks.clj")

(defvaluetype EmptyValue [])
(defvaluetype LongValue [^long x])
(defvaluetype PairValue [^long x ^long y])
(defvaluetype ReferenceValue [x])
(deftype IdentityValue [^long x])

(defn observe
  "Retain returned data or the full exception chain from the real JVM."
  [id inputs f]
  (merge {:id id :operation :atomic-allocation :inputs inputs}
         (try {:status :pass :value (f)}
              (catch Throwable error
                {:status :rejection
                 :causes (loop [current error result []]
                           (if current
                             (recur (.getCause ^Throwable current)
                                    (conj result {:class (.getName (class current))
                                                  :message (.getMessage ^Throwable current)}))
                             result))}))))

(def fixtures
  [{:name :empty :component EmptyValue :seed (EmptyValue.)}
   {:name :long :component LongValue :seed (LongValue. 17)}
   {:name :pair :component PairValue :seed (PairValue. 17 29)}
   {:name :reference-null :component ReferenceValue :seed (ReferenceValue. nil)}
   {:name :reference :component ReferenceValue :seed (ReferenceValue. "payload")}])

(defn allocate
  "Invoke the selected internal atomic allocator without an adapter."
  [component length seed]
  (let [method (.getMethod (Class/forName "jdk.internal.value.ValueClass")
                           "newNullRestrictedAtomicArray"
                           (into-array Class [Class Integer/TYPE Object]))]
    (.invoke method nil (object-array [component (int length) seed]))))

(defn allocation-case
  "Observe allocation, actual length and all initial slots."
  [{:keys [name component seed]} length seed-kind]
  (let [initial (case seed-kind :valid seed :null nil :wrong "wrong component")]
    (observe (keyword "allocation" (str (clojure.core/name name) "-" length "-" (clojure.core/name seed-kind)))
             {:component (.getName ^Class component) :length length :seed seed-kind}
             #(let [^objects array (allocate component length initial)]
                {:length (alength array)
                 :component (.getName (.getComponentType (class array)))
                 :all-slots-match-seed? (every? (fn [index] (= initial (aget array index)))
                                                (range (alength array)))}))))

(defn run-cases
  "Run bounded value cases and identity/primitive rejection controls."
  []
  (into []
        cat
        [(mapv (fn [{:keys [name component]}]
                 (assoc (observe (keyword "component" (clojure.core/name name))
                                 {:component (.getName ^Class component)}
                                 #(.invoke (.getMethod Class "isValue" (make-array Class 0))
                                           component (object-array 0)))
                        :operation :value-status))
               (conj fixtures
                     {:name :identity :component IdentityValue}
                     {:name :primitive :component Long/TYPE}))
         (into [] (for [fixture fixtures length [0 1 3] seed-kind [:valid :null :wrong]]
                    (allocation-case fixture length seed-kind)))
         (mapv #(allocation-case % -1 :valid) fixtures)
         (mapv #(allocation-case % 1 :valid)
               [{:name :identity :component IdentityValue :seed (IdentityValue. 17)}
                {:name :primitive :component Long/TYPE :seed (long 17)}])]))

(defn run-probe!
  "Write and validate allocation evidence at the explicit output path."
  [output-path]
  (let [cases (run-cases)]
  ;; Save observations before assertions so unexpected outcomes remain inspectable.
    (spit output-path
          (with-out-str
            (pprint/pprint
             {:schema-version 1 :phase :allocation
              :environment {:java-version (System/getProperty "java.runtime.version")
                            :java-home (System/getProperty "java.home")
                            :os (System/getProperty "os.name")
                            :arch (System/getProperty "os.arch")
                            :clojure-version (clojure-version)
                            :jvm-flags (vec (.getInputArguments (ManagementFactory/getRuntimeMXBean)))}
              :cases cases})))
    (semantic-checks/allocation! cases)))
