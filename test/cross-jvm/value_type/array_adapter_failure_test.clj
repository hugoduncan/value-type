(ns value-type.array-adapter-failure-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.array-capability :as capability]
            [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]
            [value-type.temp-dir :as temp])
  (:import (java.io File RandomAccessFile)))

(defn- isolated-runtime
  [runtime root]
  (let [source (.getCanonicalFile (io/file "src"))]
    (doseq [^File file (file-seq source) :when (.isFile file)]
      (let [target (io/file root (str (.relativize (.toPath source) (.toPath file))))]
        (io/make-parents target)
        (io/copy file target)))
    (assoc runtime :classpath
           (str/join File/pathSeparator
                     (mapv #(if (= source (.getCanonicalFile (io/file %)))
                              (.getPath ^File root) %)
                           (str/split (:classpath runtime)
                                      (re-pattern File/pathSeparator)))))))

(defn- break-artifact!
  [runtime root adapter fault]
  (let [stem (str "value_type/impl/array_" (name adapter))
        source (io/file root (str stem ".clj"))]
    (case fault
      :missing (io/delete-file source)
      :compiler (spit source "\n(def broken-adapter unresolved-adapter-symbol)\n" :append true)
      :linkage
      (let [adapter-ns (symbol (str "value-type.impl.array-" (name adapter)))
            result (runner/run-jvm
                    (assoc runtime :form
                           (pr-str `(prn ~(fixture/compile-form root adapter-ns)))))]
        (when (harness/assert-run result {:exit 0 :result adapter-ns :err ""})
          (io/delete-file source)
          ;; A real compiled adapter with an unsupported class-file version.
          (with-open [file (RandomAccessFile. (io/file root (str stem "__init.class")) "rw")]
            (.seek file 6)
            (.writeShort file 65535)))))))

(defn- failure-form
  [adapter entry fault]
  (let [allocation? (= adapter :allocation)
        access-check (if allocation? 'value-type.impl.array/check-allocation-access
                         'value-type.impl.array/check-inspection-access)
        operation (if (= entry :capability)
                    (if allocation? '(value-type.impl.array/allocation-capability)
                        '(value-type.impl.array/inspection-capability))
                    (if allocation? '(value-type.array/make-array nil 0 nil)
                        '(value-type.array/array-info (object-array 0))))
        expected (case fault
                   :missing 'java.io.FileNotFoundException
                   :compiler 'clojure.lang.Compiler$CompilerException
                   :linkage 'java.lang.UnsupportedClassVersionError)]
    `(do
       (require 'value-type.array)
       (let [prerequisites# (and (:available? ((deref (resolve '~access-check))))
                                 ~(if allocation?
                                    '(:available? (value-type.impl.capability/capability))
                                    true))]
         (prn
          {:prerequisites prerequisites#
           :outcome
           (try
             ~operation
             :returned
             (catch Throwable error#
               (let [chain# (loop [current# error# errors# []]
                              (if current#
                                (recur (.getCause current#) (conj errors# current#))
                                errors#))]
                 {:expected-failure? (boolean (some #(instance? ~expected %) chain#))
                  :capability-error? (boolean (some #(contains? (ex-data %) :value-type/error)
                                                    chain#))})))})))))

(deftest lazy-adapter-failures-escape-test
  ;; Damaged artifacts are negative controls, not supported-JVM evidence.
  (let [runtime (assoc (capability/runtime (config/configuration) :export) :policy "silent")]
    (doseq [adapter [:allocation :inspection]
            fault [:missing :compiler :linkage]]
      (testing (pr-str [adapter fault])
        (temp/with-temp-dir
          "vt-adapter-failure-"
          (fn [root]
            (let [isolated (isolated-runtime runtime root)]
              (is (not= (:classpath runtime) (:classpath isolated)))
              (break-artifact! isolated root adapter fault)
              ;; Each entry point must encounter first discovery in a fresh JVM.
              (doseq [entry [:capability :public]]
                (testing (name entry)
                  (harness/assert-run
                   (runner/run-jvm (assoc isolated :form (pr-str (failure-form adapter entry fault))))
                   {:exit 0 :err ""
                    :result {:prerequisites true
                             :outcome {:expected-failure? true :capability-error? false}}}))))))))))
