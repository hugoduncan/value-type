(ns value-type.field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def restricted-source
  "(ns restricted.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Leaf [payload])
   (defvaluetype Holder [^Leaf ^:non-nil required nullable ^long count ^double weight]
     java.util.function.Supplier
     (get [this] (fn [] (fn [choose] (if choose (.-required this) (.-nullable this))))))")

(defn- compile-producer
  "Compile restricted.aot-fixture on valhalla under strict policy."
  [valhalla paths & options]
  (apply fixture/compile-producer (assoc valhalla :policy "error") paths
         'restricted.aot-fixture options))

(deftest persisted-restricted-field-output-test
  ;; Inspect actual AOT output, including references in generated closure classes.
  (testing "AOT preserves native storage and rewrites persisted companions"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)
              result (compile-producer
                      valhalla paths
                      :after (str "(require '[value-type.field-aot-checks :as c]) "
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
  "Compiled checks of saved Holder; requires restricted.aot-fixture loaded.
   Returns source absence and, per construction entry (direct, factory,
   reflective), the compiled and reflective reads, Supplier results and
   rejection outcomes. The leaf argument is reported as :leaf."
  '(let [leaf (restricted.aot-fixture/->Leaf nil)
         constructor (.getConstructor restricted.aot_fixture.Holder
                                      (into-array Class [Object Object Long/TYPE Double/TYPE]))
         field (.getField restricted.aot_fixture.Holder "required")
         present (fn [value] (if (= leaf value) :leaf value))
         construct [(fn [x] (restricted.aot_fixture.Holder. x nil 42 2.5))
                    (fn [x] (restricted.aot-fixture/->Holder x nil 42 2.5))
                    (fn [x] (.newInstance constructor (object-array [x nil (long 42) (double 2.5)])))]]
     {:source-absent? (nil? (clojure.java.io/resource "restricted/aot_fixture.clj"))
      :constructions
      (mapv (fn [build]
              (let [^restricted.aot_fixture.Holder holder (build leaf)
                    choose ((.get ^java.util.function.Supplier holder))]
                {:read (present (.-required holder))
                 :field (present (.get field holder))
                 :nullable (.-nullable holder)
                 :count (.-count holder)
                 :weight (.-weight holder)
                 :supplier [(present (choose true)) (present (choose false))]
                 :rejections (mapv (fn [value] (value-type.consumer-aot-checks/outcome #(build value))) [nil "wrong"])}))
            construct)}))

(def source-free-result
  "Expected source-free-consumer result."
  {:source-absent? true
   :constructions (vec (repeat 3 {:read :leaf :field :leaf :nullable nil :count 42 :weight 2.5
                                  :supplier [:leaf nil]
                                  :rejections ["java.lang.NullPointerException"
                                               "java.lang.ClassCastException"]}))})

(deftest source-free-restricted-field-loading-test
  ;; Execute saved classes in a fresh JVM after deleting and excluding their source.
  (testing "source-free AOT retains native enforcement and companion reads"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (compile-producer valhalla paths)
            (let [consumer (runner/run-jvm
                            (assoc valhalla :policy "error"
                                   :classpath (fixture/classpath-with valhalla output)
                                   :form (binding [*print-meta* true]
                                           (pr-str '(require 'clojure.java.io 'restricted.aot-fixture
                                                             'value-type.consumer-aot-checks)
                                                   (list 'prn source-free-consumer)))))]
              (is (= 0 (:exit consumer)) consumer)
              (is (= "" (:err consumer)) consumer)
              (is (= source-free-result (:result consumer)) consumer))))))))

(deftest separately-compiled-restricted-field-consumer-test
  ;; Compile consumer access against saved classes, then execute only saved code.
  (testing "a separately compiled Clojure consumer needs neither source file"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{producer-output :output producer-file :fixture :as producer-paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (compile-producer valhalla producer-paths)
            (fixture/with-fixture
              'restricted.consumer
              (str "(ns restricted.consumer (:require [restricted.aot-fixture] [clojure.java.io]"
                   " [value-type.consumer-aot-checks]))\n"
                   (binding [*print-meta* true]
                     (pr-str (list 'defn 'run-checks [] source-free-consumer))))
              (fn [{consumer-output :output :as consumer-paths}]
                (let [artifact-classpath (fixture/classpath-with valhalla producer-output)
                      compiler (fixture/compile-producer
                                (assoc valhalla :policy "error" :classpath artifact-classpath)
                                consumer-paths 'restricted.consumer
                                :after (pr-str '(prn {:source-absent?
                                                      (nil? (clojure.java.io/resource
                                                             "restricted/aot_fixture.clj"))})))]
                  (when compiler
                    (is (= {:source-absent? true} (:result compiler)) compiler)
                    (is (not (.exists producer-file)))
                    (let [consumer (runner/run-jvm
                                    (assoc valhalla :policy "error"
                                           :classpath (fixture/classpath-with
                                                       {:classpath artifact-classpath} consumer-output)
                                           :form (pr-str
                                                  '(do (require 'clojure.java.io 'restricted.consumer)
                                                       (prn (assoc (restricted.consumer/run-checks)
                                                                   :consumer-source-absent?
                                                                   (nil? (clojure.java.io/resource
                                                                          "restricted/consumer.clj"))))))))]
                      (is (= 0 (:exit consumer)) consumer)
                      (is (= "" (:err consumer)) consumer)
                      (is (= (assoc source-free-result :consumer-source-absent? true)
                             (:result consumer)) consumer))))))))))))

(deftest java-restricted-field-consumer-test
  ;; Compile real Java calls against saved producer classes without producer source.
  (testing "matching EA3 javac preserves direct native enforcement without exports"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (compile-producer valhalla paths)
            (let [classpath (fixture/classpath-with valhalla output)
                  compiler (fixture/compile-java valhalla classpath output
                                                 "test/resources/non-nil-fields/JavaConsumer.java")]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (let [consumer (runner/run-jvm
                                (assoc valhalla :policy "error" :classpath classpath
                                       :form (pr-str (checks/java-consumer-form
                                                      {:source "restricted/aot_fixture.clj"
                                                       :owner "restricted.aot_fixture.Holder"
                                                       :checked ["required"]}
                                                      "fields.JavaConsumer"))))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= {:source-absent? true :value? true
                          :annotations ["jdk.internal.vm.annotation.NullRestricted"]
                          :checks :passed}
                         (:result consumer)) consumer))))))))))

(deftest incompatible-restricted-field-consumer-test
  ;; Real incompatible receivers must reject saved storage, never convert it.
  (testing "source-free native artifacts reject every fallback policy"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (compile-producer valhalla paths)
            (doseq [receiver [baseline (assoc valhalla :jvm-opts [])]
                    policy ["silent" "warn" "error"]]
              (testing (str (:java receiver) " / " policy)
                (let [consumer (runner/run-jvm
                                (assoc receiver :policy policy
                                       :classpath (fixture/classpath-with receiver output)
                                       :form (pr-str (checks/incompatible-consumer-form
                                                      {:source "restricted/aot_fixture.clj"
                                                       :owner "restricted.aot_fixture.Holder"}))))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= checks/incompatible-result (:result consumer)) consumer))))))))))
