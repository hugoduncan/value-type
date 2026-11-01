(ns value-type.function-redefinition-test
  (:require [clojure.test :refer [deftest]]
            [clojure.walk :as walk]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

(def ^:private repeated-definition-body
  "Cell body template: evaluate the DECLARATION placeholder twice through
   function analysis. Inspect each owner immediately after its definition:
   value?, field type, final modifier, native annotations, the stored value
   and the nil and wrong-type outcomes of each construction entry (direct,
   factory, reflective) and :new-class?. owner is the Class of the factory result,
   so factory identity holds by construction; :factory? (import identity)
   is not checked, because a redefinition through function analysis leaves
   the namespace import stale. *ns* is read once, on the cell thread."
  '(let [cell-ns *ns*
         define
         (fn []
           ((eval (list 'fn [] 'DECLARATION)))
           (let [instance ((ns-resolve cell-ns '->RepeatedHolder) (Long/valueOf 7))
                 owner (class instance)
                 field (.getField owner "value")]
             {:owner owner
              :check
              (merge
               (value-type.consumer-aot-checks/field-facts field)
               {:value? (value-type.consumer-aot-checks/value-class? owner)
                :constructions
                (value-type.consumer-aot-checks/construction-results
                 cell-ns owner field (Long/valueOf 7) "wrong")
                :new-class? (value-type.cell-registry/new-class! owner)})}))
         definitions (value-type.cell-registry/twice define)]
     {:distinct-classes? (apply distinct? (map :owner definitions))
      :checks (mapv :check definitions)}))

(defn- cell-form
  "Return the cell form for declaration: load the check namespaces, then
   evaluate repeated-definition-body with declaration under controlled
   native absence (cell-harness/controlled-absence)."
  [declaration]
  (harness/controlled-absence
   '[value-type.consumer-aot-checks value-type.core value-type.cell-registry]
   (walk/postwalk-replace {'DECLARATION declaration} repeated-definition-body)))

(defn- expected
  "Return the expected result of one declaration defined twice."
  [check]
  (let [check (assoc check :new-class? true)]
    {:distinct-classes? true :checks (registry/each-attempt check)}))

(deftest controlled-capability-repeated-definition-test
  ;; Controlled capability (acceptance 8): only the native capability accessor
  ;; is replaced. Same-name redefinition keeps value representation and
  ;; generated checks. Fields are read from the constructed Class, not a stale
  ;; namespace import. Policy and execution options are process-wide: one
  ;; child for each pair, one cell for each declaration.
  (let [{:keys [valhalla]} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies
           execution config/executions
           :let [context (pr-str [:controlled-capability policy execution])]]
       (harness/cell-child
        (config/child-configuration valhalla execution policy)
        [{:context [context "deftype"]
          :form (cell-form '(clojure.core/deftype RepeatedHolder [value]))
          :expected (expected {:value? false :type "java.lang.Object"
                               :final? true :annotations []
                               :constructions (checks/each-construction
                                               {:stored 7
                                                :rejections (mapv (constantly :accepted)
                                                                  checks/checked-rejections)})})}
         {:context [context "defvaluetype"]
          :form (cell-form '(value-type.core/defvaluetype RepeatedHolder
                              [^Long ^:non-nil value]))
          :expected (expected {:value? true :type "java.lang.Long"
                               :final? true :annotations []
                               :constructions (checks/each-construction
                                               {:stored 7
                                                :rejections checks/checked-rejections})})}])))))
