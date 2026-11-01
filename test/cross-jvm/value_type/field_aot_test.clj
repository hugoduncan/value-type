(ns value-type.field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

(def restricted-source
  "(ns restricted.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Leaf [payload])
   (defvaluetype Holder [^Leaf ^:non-nil required nullable ^long count ^double weight]
     java.util.function.Supplier
     (get [this] (fn [] (fn [choose] (if choose (.-required this) (.-nullable this))))))")

(defn- compile-cell-form
  "Return the cell form that AOT compiles restricted.aot-fixture into
   output, then returns the inspected output and the definition maps of
   Leaf and Holder."
  [output]
  (list 'do
        '(require 'value-type.field-aot-checks 'value-type.cell-registry)
        (fixture/compile-form output 'restricted.aot-fixture)
        (list 'let ['n '(the-ns 'restricted.aot-fixture)
                    'leaf '((ns-resolve n '->Leaf) nil)]
              {:output (list 'value-type.field-aot-checks/inspect-output (.getPath output))
               :definitions
               {'(quote Leaf) '(value-type.cell-registry/defined n "Leaf" (ns-resolve n 'Leaf) nil)
                '(quote Holder) '(value-type.cell-registry/defined
                                   n "Holder" (ns-resolve n 'Holder) leaf nil 42 2.5)}})))

(def ^:private output?
  (fixture/persisted-output?
   {:fields {"required" "Lrestricted/aot_fixture/Leaf;"
             "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
    :annotations (mapv fixture/descriptor checks/native-annotations)
    :constructors ["(Ljava/lang/Object;Ljava/lang/Object;JD)V"]}
   "Lrestricted/aot_fixture/Leaf;"))

(def ^:private persisted-definitions
  {'Leaf registry/new-definition
   'Holder registry/new-definition})

(def ^:private persisted-output?
  "True when the inspected output keeps native storage, rewrites companion
   references to Leaf and both types are new Classes with factories."
  (harness/described
   (harness/keyed {:output [(harness/description output?) output?]
                   :definitions (harness/fixed persisted-definitions)})))

(deftest persisted-restricted-field-output-test
  ;; Inspect actual AOT output, including references in generated closure classes.
  (testing "AOT preserves native storage and rewrites persisted companions"
    (fixture/with-fixture
      'restricted.aot-fixture restricted-source
      (fn [{:keys [source output]}]
        (let [{:keys [valhalla] :as configuration} (config/configuration)]
          (harness/run-cells
           [(harness/cell-child
             (assoc (config/value-producer configuration)
                    :classpath (fixture/classpath-with valhalla source)
                    :fixture-ns 'restricted.aot-fixture)
             [{:context []
               :form (compile-cell-form output)
               :expected persisted-output?}])]))))))

(def source-free-consumer
  "Compiled checks of saved Holder; requires restricted.aot-fixture loaded.
   Returns source absence and, per construction entry (direct, factory,
   reflective), the compiled and reflective reads, Supplier results and
   rejection outcomes (consumer-aot-checks/checked-outcomes). Each
   construction entry takes [x], as consumer-aot-checks/builders entries
   do. The leaf argument is reported as :leaf."
  '(let [leaf (restricted.aot-fixture/->Leaf nil)
         constructor (.getConstructor restricted.aot_fixture.Holder
                                      (into-array Class [Object Object Long/TYPE Double/TYPE]))
         field (.getField restricted.aot_fixture.Holder "required")
         present (fn [value] (if (= leaf value) :leaf value))
         construct [(fn [[x]] (restricted.aot_fixture.Holder. x nil 42 2.5))
                    (fn [[x]] (restricted.aot-fixture/->Holder x nil 42 2.5))
                    (fn [[x]] (.newInstance constructor (object-array [x nil (long 42) (double 2.5)])))]]
     {:source-absent? (value-type.consumer-aot-checks/source-absent? "restricted/aot_fixture.clj")
      :constructions
      (mapv (fn [build]
              (let [^restricted.aot_fixture.Holder holder (build [leaf])
                    choose ((.get ^java.util.function.Supplier holder))]
                {:read (present (.-required holder))
                 :stored (present (.get field holder))
                 :nullable (.-nullable holder)
                 :count (.-count holder)
                 :weight (.-weight holder)
                 :supplier [(present (choose true)) (present (choose false))]
                 :rejections (value-type.consumer-aot-checks/checked-outcomes build "wrong")}))
            construct)}))

(def source-free-result
  "Expected source-free-consumer result."
  {:source-absent? true
   :constructions (checks/each-construction
                   {:read :leaf :stored :leaf :nullable nil :count 42 :weight 2.5
                    :supplier [:leaf nil]
                    :rejections checks/checked-rejections})})

(deftest source-free-restricted-field-loading-test
  ;; Execute saved classes in a fresh JVM after deleting and excluding their source.
  (testing "source-free AOT retains native enforcement and companion reads"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'restricted.aot-fixture restricted-source (config/value-producer configuration)
       [valhalla] ["error"]
       (list 'do
             '(require 'restricted.aot-fixture 'value-type.consumer-aot-checks)
             source-free-consumer)
       (constantly source-free-result)))))

(deftest separately-compiled-restricted-field-consumer-test
  ;; Compile consumer access against saved classes, then execute only saved code.
  (testing "a separately compiled Clojure consumer needs neither source file"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/with-produced
        'restricted.aot-fixture restricted-source (config/value-producer configuration)
        (fn [{producer-output :output producer-file :fixture}]
          (fixture/with-fixture
            'restricted.consumer
            (str "(ns restricted.consumer (:require [restricted.aot-fixture]"
                 " [value-type.consumer-aot-checks]))\n"
                 (binding [*print-meta* true]
                   (pr-str (list 'defn 'run-checks [] source-free-consumer))))
            (fn [{consumer-output :output :as consumer-paths}]
              (let [artifact (assoc (config/value-producer configuration)
                                    :classpath (fixture/classpath-with valhalla producer-output))
                    compiler (fixture/compile-producer
                              artifact
                              consumer-paths 'restricted.consumer
                              :after (pr-str '(prn {:source-absent?
                                                    (value-type.consumer-aot-checks/source-absent?
                                                     "restricted/aot_fixture.clj")})))]
                (when compiler
                  (harness/assert-run compiler {:result {:source-absent? true}})
                  (is (not (.exists producer-file)))
                  (harness/run-cells
                   [(fixture/consumer-child 'restricted.aot-fixture valhalla
                                            (fixture/classpath-with artifact consumer-output)
                                            "error"
                                            '(do (require 'restricted.consumer)
                                                 (assoc (restricted.consumer/run-checks)
                                                        :consumer-source-absent?
                                                        (value-type.consumer-aot-checks/source-absent?
                                                         "restricted/consumer.clj")))
                                            (assoc source-free-result :consumer-source-absent? true))]))))))))))

(deftest java-restricted-field-consumer-test
  ;; Compile real Java calls against saved producer classes without producer source.
  (testing "matching EA3 javac preserves direct native enforcement without exports"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'restricted.aot-fixture restricted-source (config/value-producer configuration)
       [valhalla] ["error"]
       (checks/java-consumer-form
        {:source "restricted/aot_fixture.clj"
         :owner "restricted.aot_fixture.Holder"
         :checked ["required"]}
        "fields.JavaConsumer")
       (constantly (checks/java-consumer-result true checks/native-annotations))
       :java ["test/resources/non-nil-fields/JavaConsumer.java"]))))

(deftest incompatible-restricted-field-consumer-test
  ;; Real incompatible receivers must reject saved storage, never convert it.
  (testing "source-free native artifacts reject every fallback policy"
    (let [configuration (config/configuration)]
      (fixture/incompatible-consumers 'restricted.aot-fixture restricted-source (config/value-producer configuration)
                                      (config/incompatible-receivers configuration)
                                      {:source "restricted/aot_fixture.clj"
                                       :owner "restricted.aot_fixture.Holder"}))))
