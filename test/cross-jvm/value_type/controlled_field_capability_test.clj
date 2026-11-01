(ns value-type.controlled-field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]))

(def ^:private holder-definition
  '(value-type.core/defvaluetype ControlledHolder [^Long ^:non-nil value]))

(def ^:private holder-check
  "Child function: inspect ControlledHolder of the namespace ns-or-name and
   construct it through each entry
   (consumer-aot-checks/construction-results)."
  '(fn [ns-or-name]
     (let [owner (resolve 'ControlledHolder)
           field (.getField owner "value")]
       (merge
        (value-type.consumer-aot-checks/field-facts field)
        {:value? (value-type.consumer-aot-checks/value-class? owner)
         :eligible? (value-type.consumer-aot-checks/value-class? Long)
         :constructions
         (value-type.consumer-aot-checks/construction-results
          ns-or-name owner field (Long/valueOf 7) "wrong")}))))

(def ^:private expected
  {:value? true :eligible? true :type "java.lang.Long"
   :final? true :annotations []
   :constructions (checks/each-construction
                   {:stored 7 :rejections checks/checked-rejections})})

(defn- definition-cell
  "Return a cell form that evaluates definition with native capability
   absent, checks ControlledHolder and returns that map with the
   definition keys :new-class? and :factory?."
  [definition]
  (harness/controlled-absence
   '[value-type.core value-type.cell-registry
     value-type.consumer-aot-checks value-type.transfer-checks]
   (list 'let ['defined definition
               'owner '(if (class? defined) defined (resolve 'ControlledHolder))
               'check holder-check]
         '(merge (check *ns*)
                 (value-type.cell-registry/defined
                   *ns* 'ControlledHolder owner (Long/valueOf 7))))))

(def ^:private defined-expected
  (merge expected registry/new-definition))

(deftest controlled-native-absence-construction-test
  ;; Only the native capability accessor is replaced; every constructor is real.
  ;; Policy and execution options are process-wide: one child for each pair,
  ;; one cell for each expansion form.
  (let [{:keys [valhalla]} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies
           execution config/executions]
       (harness/cell-child
        (config/child-configuration valhalla execution policy)
        (for [expand transfer/expands]
          {:context [(pr-str [policy execution]) (name expand)]
           :form (definition-cell
                   (list 'eval (list (list 'value-type.transfer-checks/expanders expand)
                                     (list 'quote holder-definition))))
           :expected defined-expected}))))))

(def ^:private receiving-specs
  "One consumer cell for each mode and expand, in cell order.
   Cell i runs in namespace cell.i, which is also the producer namespace of
   its expansion, so each cell has its own class name."
  (vec (for [mode transfer/modes
             expand transfer/expands]
         [mode expand])))

(def ^:private controlled-expansions
  "Producer form: serialize holder-definition for each receiving spec
   (transfer-checks/produce) with native capability absent."
  (harness/controlled-absence
   '[value-type.core value-type.transfer-checks]
   (list 'prn
         (list 'value-type.transfer-checks/produce
               (list 'quote holder-definition) true
               (harness/cell-specs (map second receiving-specs))))))

(deftest controlled-native-absence-receiving-test
  ;; Serialized expansions cross a process boundary without producer compiler state.
  (let [{:keys [valhalla] :as configuration} (config/configuration)]
    (when-let [expansions (harness/produce
                           (config/value-producer configuration)
                           controlled-expansions)]
      (testing "producer"
        (is (= (count receiving-specs) (count expansions)) expansions))
      (harness/run-cells
       (for [policy config/policies
             execution config/executions]
         (harness/cell-child
          (config/child-configuration valhalla execution policy)
          (map (fn [[mode expand] expansion]
                 {:context [(pr-str [policy execution mode]) (name expand)]
                  :form (definition-cell (list 'value-type.transfer-checks/evaluate expansion mode))
                  :expected defined-expected})
               receiving-specs expansions)))))))

(def ^:private controlled-aot-source
  "(ns checked.controlled (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype ControlledHolder [^Long ^:non-nil value])")

(def ^:private saved-cell
  "Cell form: load the saved ControlledHolder with native capability absent
   and check it. :source-absent? is read before the load. A saved class is
   the same Class on each load: no new-Class check."
  (harness/controlled-absence
   '[value-type.core value-type.consumer-aot-checks]
   (list 'let ['absent? '(value-type.consumer-aot-checks/source-absent?
                          "checked/controlled.clj")
               '_ '(require 'checked.controlled)
               '_ '(eval '(import checked.controlled.ControlledHolder))
               'check holder-check]
         '(assoc (check "checked.controlled")
                 :source-absent? absent?))))

(defn- controlled-aot-consumers
  "AOT compile checked.controlled with native capability absent
   (fixture/source-free-consumers; source deleted), then run one consumer
   child of form for each execution × policy (rules 1, 2; one artifact,
   rule 4). Options are source-free-consumers options (for example :java)."
  [form consumer-expected & options]
  (let [{:keys [valhalla] :as configuration} (config/configuration)
        jvms (config/with-executions valhalla)
        execution-of (zipmap jvms config/executions)]
    (apply fixture/source-free-consumers
           'checked.controlled controlled-aot-source (config/value-producer configuration)
           jvms config/policies
           form (constantly consumer-expected)
           :context-of (fn [jvm policy] [(pr-str [policy (execution-of jvm)])])
           :produce {:wrap (partial harness/controlled-absence [])}
           options)))

(deftest controlled-native-absence-aot-test
  ;; Saved generated enforcement must load without source or native capability.
  (controlled-aot-consumers saved-cell (assoc expected :source-absent? true)))

(deftest controlled-native-absence-java-aot-test
  ;; A separately compiled Java caller uses saved constructors, not Clojure factories.
  (controlled-aot-consumers
   (harness/controlled-absence
    '[value-type.consumer-aot-checks]
    (checks/java-consumer-form
     {:source "checked/controlled.clj"
      :owner "checked.controlled.ControlledHolder"
      :checked ["value"]}
     "fields.ControlledJavaConsumer"))
   (checks/java-consumer-result true [])
   :java ["test/resources/non-nil-fields/ControlledJavaConsumer.java"]))
