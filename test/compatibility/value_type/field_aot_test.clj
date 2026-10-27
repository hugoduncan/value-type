(ns value-type.field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def restricted-source
  "(ns restricted.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Leaf [payload])
   (defvaluetype Holder [^Leaf ^:nil-restricted required nullable ^long count ^double weight]
     java.util.function.Supplier
     (get [this] (fn [] (fn [choose] (if choose (.-required this) (.-nullable this))))))")

(defn- compile-producer
  [valhalla source output after-compile]
  (let [result (runner/run-jvm
                (assoc valhalla :policy "error"
                       :classpath (str (:classpath valhalla) java.io.File/pathSeparator source)
                       :form (str "(binding [*compile-path* " (pr-str (.getPath output))
                                  "] (compile 'restricted.aot-fixture)) " after-compile)))]
    (is (= 0 (:exit result)) result)
    (is (= "" (:err result)) result)
    (when (and (= 0 (:exit result)) (= "" (:err result)))
      result)))

(deftest persisted-restricted-field-output-test
  ;; Inspect actual AOT output, including references in generated closure classes.
  (testing "AOT preserves native storage and rewrites persisted companions"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [source output]}]
        (let [{:keys [valhalla]} (config/configuration)
              result (compile-producer
                      valhalla source output
                      (str "(require '[value-type.field-aot-checks :as c]) "
                           "(prn (c/inspect-output " (pr-str (.getPath output)) "))"))]
          (when result
            (is (= {:fields {"required" "Lrestricted/aot_fixture/Leaf;"
                             "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
                    :annotations ["Ljdk/internal/vm/annotation/NullRestricted;"]
                    :constructors ["(Ljava/lang/Object;Ljava/lang/Object;JD)V"]}
                   (dissoc (:result result) :companion-references)) result)
            (is (seq (get-in result [:result :companion-references])) result)
            (is (every? #{"Lrestricted/aot_fixture/Leaf;"}
                        (get-in result [:result :companion-references])) result)))))))

(def source-free-consumer
  '(do
     (require '[clojure.java.io :as io])
     (assert (nil? (io/resource "restricted/aot_fixture.clj")))
     (require '[restricted.aot-fixture :as a])
     (let [leaf (a/->Leaf nil)
           direct (restricted.aot_fixture.Holder. leaf nil 42 2.5)
           factory (a/->Holder leaf nil 42 2.5)
           constructor (.getConstructor restricted.aot_fixture.Holder
                                        (into-array Class [Object Object Long/TYPE Double/TYPE]))
           reflected (.newInstance constructor (object-array [leaf nil (long 42) (double 2.5)]))
           construct [(fn [x] (restricted.aot_fixture.Holder. x nil 42 2.5))
                      (fn [x] (a/->Holder x nil 42 2.5))
                      (fn [x] (.newInstance constructor (object-array [x nil (long 42) (double 2.5)])))]]
       (doseq [holder [direct factory reflected]]
         (assert (= leaf (.-required ^restricted.aot_fixture.Holder holder)))
         (assert (= leaf (.get (.getField restricted.aot_fixture.Holder "required") holder)))
         (assert (nil? (.-nullable ^restricted.aot_fixture.Holder holder)))
         (assert (= 42 (.-count ^restricted.aot_fixture.Holder holder)))
         (assert (= 2.5 (.-weight ^restricted.aot_fixture.Holder holder)))
         (let [choose ((.get ^java.util.function.Supplier holder))]
           (assert (= leaf (choose true)))
           (assert (nil? (choose false)))))
       (prn {:source-absent? true
             :reads? true
             :rejections
             (mapv (fn [build]
                     (mapv (fn [value]
                             (try
                               (build value)
                               :accepted
                               (catch Throwable error
                                 (.getName (class (if (instance? java.lang.reflect.InvocationTargetException error)
                                                    (.getCause error)
                                                    error))))))
                           [nil "wrong"]))
                   construct)}))))

(deftest source-free-restricted-field-loading-test
  ;; Execute saved classes in a fresh JVM after deleting and excluding their source.
  (testing "source-free AOT retains native enforcement and companion reads"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [source output fixture]}]
        (let [{:keys [valhalla]} (config/configuration)
              producer (compile-producer valhalla source output "(prn :compiled)")]
          (when producer
            (java.nio.file.Files/delete (.toPath fixture))
            (is (not (.exists fixture)))
            (let [consumer (runner/run-jvm
                            (assoc valhalla :policy "error"
                                   :classpath (str (:classpath valhalla) java.io.File/pathSeparator output)
                                   :form (binding [*print-meta* true] (pr-str source-free-consumer))))]
              (is (= 0 (:exit consumer)) consumer)
              (is (= "" (:err consumer)) consumer)
              (is (= {:source-absent? true :reads? true
                      :rejections (vec (repeat 3 ["java.lang.NullPointerException"
                                                  "java.lang.ClassCastException"]))}
                     (:result consumer)) consumer))))))))

(deftest separately-compiled-restricted-field-consumer-test
  ;; Compile consumer access against saved classes, then execute only saved code.
  (testing "a separately compiled Clojure consumer needs neither source file"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{producer-source :source producer-output :output producer-file :fixture}]
        (let [{:keys [valhalla]} (config/configuration)
              producer (compile-producer valhalla producer-source producer-output "(prn :compiled)")]
          (when producer
            (java.nio.file.Files/delete (.toPath producer-file))
            (fixture/with-fixture
              'restricted.consumer
              (str "(ns restricted.consumer (:require [restricted.aot-fixture :as a] [clojure.java.io :as io]))\n"
                   (binding [*print-meta* true]
                     (pr-str (list 'defn 'run-checks [] source-free-consumer))))
              (fn [{consumer-source :source consumer-output :output consumer-file :fixture}]
                (let [artifact-classpath (str (:classpath valhalla) java.io.File/pathSeparator producer-output)
                      compiler (runner/run-jvm
                                (assoc valhalla :policy "error"
                                       :classpath (str artifact-classpath java.io.File/pathSeparator consumer-source)
                                       :form (str "(require '[clojure.java.io :as io]) "
                                                  "(assert (nil? (io/resource \"restricted/aot_fixture.clj\"))) "
                                                  "(binding [*compile-path* " (pr-str (.getPath consumer-output))
                                                  "] (compile 'restricted.consumer)) (prn :compiled)")))]
                  (is (= 0 (:exit compiler)) compiler)
                  (is (= "" (:err compiler)) compiler)
                  (when (= 0 (:exit compiler))
                    (java.nio.file.Files/delete (.toPath consumer-file))
                    (is (and (not (.exists producer-file)) (not (.exists consumer-file))))
                    (let [consumer (runner/run-jvm
                                    (assoc valhalla :policy "error"
                                           :classpath (str artifact-classpath java.io.File/pathSeparator consumer-output)
                                           :form "(require '[clojure.java.io :as io])
                                                  (assert (nil? (io/resource \"restricted/consumer.clj\")))
                                                  (require 'restricted.consumer)
                                                  (restricted.consumer/run-checks)"))]
                      (is (= 0 (:exit consumer)) consumer)
                      (is (= "" (:err consumer)) consumer)
                      (is (= {:source-absent? true :reads? true
                              :rejections (vec (repeat 3 ["java.lang.NullPointerException"
                                                          "java.lang.ClassCastException"]))}
                             (:result consumer)) consumer))))))))))))

(deftest java-restricted-field-consumer-test
  ;; Compile real Java calls against saved producer classes without producer source.
  (testing "matching EA3 javac preserves direct native enforcement without exports"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [source output fixture]}]
        (let [{:keys [valhalla]} (config/configuration)
              producer (compile-producer valhalla source output "(prn :compiled)")]
          (when producer
            (java.nio.file.Files/delete (.toPath fixture))
            (is (not (.exists fixture)))
            (let [classpath (str (:classpath valhalla) java.io.File/pathSeparator output)
                  compiler (runner/run-jvm
                            (assoc valhalla :classpath classpath
                                   :form (str "(prn (.run (javax.tools.ToolProvider/getSystemJavaCompiler) nil nil nil "
                                              "(into-array String [\"--enable-preview\" \"--release\" \"27\" "
                                              "\"-classpath\" " (pr-str classpath)
                                              " \"-d\" " (pr-str (.getPath output))
                                              " \"test/resources/nil-restricted-fields/JavaConsumer.java\"])))")))]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (let [consumer (runner/run-jvm
                                (assoc valhalla :policy "error" :classpath classpath
                                       :form "(require '[clojure.java.io :as io])
                                              (assert (nil? (io/resource \"restricted/aot_fixture.clj\")))
                                              (fields.JavaConsumer/runChecks)
                                              (prn :java-checks-passed)"))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= :java-checks-passed (:result consumer)) consumer))))))))))

(def incompatible-consumer
  '(do
     (require '[clojure.java.io :as io] 'value-type.core)
     (assert (nil? (io/resource "restricted/aot_fixture.clj")))
     (prn
      (mapv (fn [_]
              (try
                (Class/forName "restricted.aot_fixture.Holder")
                :accepted
                (catch UnsupportedClassVersionError _
                  :unsupported-class-version)))
            (range 2)))))

(deftest incompatible-restricted-field-consumer-test
  ;; Real incompatible receivers must reject saved storage, never convert it.
  (testing "source-free native artifacts reject every fallback policy"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [source output fixture]}]
        (let [{:keys [baseline valhalla]} (config/configuration)
              producer (compile-producer valhalla source output "(prn :compiled)")]
          (when producer
            (java.nio.file.Files/delete (.toPath fixture))
            (is (not (.exists fixture)))
            (doseq [receiver [baseline (assoc valhalla :jvm-opts [])]
                    policy ["silent" "warn" "error"]]
              (testing (str (:java receiver) " / " policy)
                (let [consumer (runner/run-jvm
                                (assoc receiver :policy policy
                                       :classpath (str (:classpath receiver) java.io.File/pathSeparator output)
                                       :form (pr-str incompatible-consumer)))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= [:unsupported-class-version :unsupported-class-version]
                         (:result consumer)) consumer))))))))))
