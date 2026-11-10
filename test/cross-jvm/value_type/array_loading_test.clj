(ns value-type.array-loading-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.array-capability :as capability]
            [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]
            [value-type.jvm-facts :as facts]))

(defn- loading-form
  [require-order]
  `(do
     (run! require '~require-order)
     (require 'value-type.capability)
     (let [loaded# (fn []
                     (mapv #(boolean (find-ns %))
                           '[value-type.impl.array-allocation
                             value-type.impl.array-inspection]))
           before# (loaded#)
           allocation# (select-keys (value-type.impl.array/allocation-capability)
                                    [:available? :reason])
           inspection# (select-keys (value-type.impl.array/inspection-capability)
                                    [:available? :reason])]
       {:runtime (System/getProperty "java.runtime.version")
        :flags (vec (.getInputArguments
                     (java.lang.management.ManagementFactory/getRuntimeMXBean)))
        :before before#
        :allocation allocation#
        :inspection inspection#
        :rejections
        (mapv (fn [[available?# operation#]]
                (when-not available?#
                  (try (operation#)
                       (catch clojure.lang.ExceptionInfo error#
                         (:reason (ex-data error#))))))
              [[(:available? allocation#) #(value-type.array/make-array nil -1 nil)]
               [(:available? inspection#) #(value-type.array/array-info (object-array 0))]])
        :after (loaded#)})))

(deftest lazy-public-require-test
  ;; Each require order and launch profile needs a fresh namespace registry.
  (let [configuration (config/configuration)
        jvm-facts (facts/read-facts)]
    (harness/run-cells
     (for [label [:java8 :no-options :no-export :export-only :export]
           require-order capability/require-orders
           :let [runtime (capability/runtime configuration label)
                 report (rows/report label)
                 allocation (:array-allocation report)
                 inspection (:array-inspection report)
                 build (:build (facts/select jvm-facts
                                             (if (= label :java8) :baseline :valhalla)
                                             :linux-x64 :plain))]]
       (harness/cell-child
        (assoc runtime :policy "silent")
        [{:context [(pr-str [label require-order])]
          :form (loading-form require-order)
          :expected
          (harness/described
           (harness/fixed
            {:runtime build
             :flags (capability/expected-flags runtime "silent")
             :before [false false]
             :allocation allocation
             :inspection inspection
             :rejections [(:reason allocation) (:reason inspection)]
             :after [(:available? allocation) (:available? inspection)]}))}])))))

(def ^:private aot-source
  "(ns array.require-only (:require [value-type.array] [value-type.capability]))")

(def ^:private adapter-state
  '(mapv #(boolean (find-ns %))
         '[value-type.impl.array-allocation value-type.impl.array-inspection]))

(deftest lazy-require-only-aot-test
  ;; Compile and load real saved application output without its source.
  (let [configuration (config/configuration)]
    (doseq [label [:java8 :no-options :no-export :export-only :export]]
      (testing (name label)
        (let [runtime (assoc (capability/runtime configuration label) :policy "silent")]
          (fixture/with-fixture
            'array.require-only aot-source
            (fn [{:keys [output] :as paths}]
              (when-let [compiled (fixture/compile-producer
                                   runtime paths 'array.require-only
                                   :after (pr-str (list 'prn adapter-state)))]
                (is (= [false false] (:result compiled)))
                (is (empty? (filter #(re-find #"array_(allocation|inspection)" (.getName ^java.io.File %))
                                    (file-seq output))))
                (harness/run-cells
                 [(fixture/consumer-child
                   'array.require-only runtime (fixture/classpath-with runtime output)
                   "silent"
                   `(do
                      (require 'clojure.java.io)
                      (require 'array.require-only)
                      (let [adapters# ~adapter-state
                            loading# ~(loading-form [])]
                        {:source-absent? (nil? (clojure.java.io/resource "array/require_only.clj"))
                         :compiled-present? (some? (clojure.java.io/resource "array/require_only__init.class"))
                         :adapters adapters#
                         :loading loading#}))
                   (harness/described
                    (harness/fixed
                     (let [report (rows/report label)
                           allocation (:array-allocation report)
                           inspection (:array-inspection report)]
                       {:source-absent? true
                        :compiled-present? true
                        :adapters [false false]
                        :loading
                        {:runtime (:build (facts/select (facts/read-facts)
                                                        (if (= label :java8) :baseline :valhalla)
                                                        :linux-x64 :plain))
                         :flags (capability/expected-flags runtime "silent")
                         :before [false false]
                         :allocation allocation
                         :inspection inspection
                         :rejections [(:reason allocation) (:reason inspection)]
                         :after [(:available? allocation) (:available? inspection)]}}))))])))))))))

(def ^:private repeated-operation-form
  '(do
     (require 'value-type.core)
     (eval '(value-type.core/defvaluetype ReceiverPoint [^long x]))
     (let [seed ((resolve '->ReceiverPoint) 7)
           component (class seed)]
       (mapv
        (fn [_]
          (let [allocated (value-type.array/make-array component 2 seed)
                explicit (value-type.array/into-array component seed [seed seed])
                inferred (value-type.array/into-array component [seed seed])]
            {:contents (mapv #(= [seed seed] (vec %))
                             [allocated explicit inferred])
             :restricted (mapv #(select-keys (value-type.array/array-info %)
                                             [:component-value? :null-restricted? :atomic?])
                               [allocated explicit inferred])
             :ordinary (select-keys (value-type.array/array-info (object-array 2))
                                    [:component-value? :null-restricted? :atomic?])}))
        (range 3)))))

(deftest receiving-runtime-aot-test
  ;; A plain producer must not fix the capabilities of an exported receiver.
  (let [configuration (config/configuration)
        producer (assoc (capability/runtime configuration :no-options) :policy "silent")
        receiver (capability/runtime configuration :export)]
    (fixture/with-fixture
      'array.require-only aot-source
      (fn [{:keys [output] :as paths}]
        (when-let [compiled (fixture/compile-producer
                             producer paths 'array.require-only
                             :after (pr-str (list 'prn adapter-state)))]
          (is (= [false false] (:result compiled)))
          (harness/run-cells
           [(fixture/consumer-child
             'array.require-only receiver (fixture/classpath-with receiver output)
             "silent"
             `(do
                (require 'clojure.java.io)
                (require 'array.require-only)
                (let [before# ~adapter-state
                      operations# ~repeated-operation-form
                      after# ~adapter-state
                      report# (select-keys (value-type.capability/report)
                                           [:array-allocation :array-inspection])]
                  {:source-absent? (nil? (clojure.java.io/resource "array/require_only.clj"))
                   :compiled-present? (some? (clojure.java.io/resource "array/require_only__init.class"))
                   :before before#
                   :operations operations#
                   :after after#
                   :runtime (System/getProperty "java.runtime.version")
                   :flags (vec (.getInputArguments
                                (java.lang.management.ManagementFactory/getRuntimeMXBean)))
                   :report report#}))
             (harness/described
              (harness/fixed
               {:source-absent? true
                :compiled-present? true
                :before [false false]
                :operations (vec (repeat 3
                                         {:contents [true true true]
                                          :restricted (vec (repeat 3 {:component-value? true
                                                                      :null-restricted? true
                                                                      :atomic? true}))
                                          :ordinary {:component-value? false
                                                     :null-restricted? false
                                                     :atomic? true}}))
                :after [true true]
                :runtime (:build (facts/select (facts/read-facts) :valhalla :linux-x64 :plain))
                :flags (capability/expected-flags receiver "silent")
                :report {:array-allocation {:available? true}
                         :array-inspection {:available? true}}})))]))))))
