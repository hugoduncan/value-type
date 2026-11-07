(ns value-type.runtime-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.expansion-checks :as expansion]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]
            [value-type.validation-checks :as validation]))

;; Policy is process-wide (isolation rule 2): one cell child for each policy.
;; Cells of one test and policy share that child.

(defn- child
  "Return the cell child of policy on the selected parent configuration."
  [policy cells]
  (harness/cell-child (assoc (:selected (config/configuration)) :policy policy) cells))

(def ^:private optional-loaded-form
  "A child form: true when an optional implementation namespace is loaded."
  (list 'boolean
        (list 'some 'find-ns
              (list 'quote '[value-type.impl.loader value-type.impl.transform]))))

(defn- policy-context [policy]
  (str "policy " policy))

(deftest capability-test
  ;; Use actual JVM APIs and class definition, not simulated capability results.
  ;; Isolation rule 5: first capability detection in a fresh library runtime.
  (let [{:keys [available? unavailable-reason]} (config/configuration)]
    (harness/run-cells
     [(child nil
             [{:context []
               :form (list 'do '(require '[value-type.impl.capability :as capability])
                           (list 'let '[a (capability/capability)]
                                 {:capability '(dissoc a :probe-class)
                                  :cached '(identical? a (capability/capability))
                                  :optional-loaded optional-loaded-form}))
               :expected (harness/described
                          (harness/keyed
                           {:cached (harness/fixed true)
                            :optional-loaded (harness/fixed false)
                            :capability (if available?
                                          (harness/fixed {:available? true})
                                          (harness/keyed
                                           {:available? (harness/fixed false)
                                            :reason (harness/fixed unavailable-reason)
                                            :detail (harness/detail)}))}))}])])))

(deftest policy-test
  ;; Independent processes own configuration; property changes after the
  ;; first read have no effect.
  ;; The direct enforce-fallback! call is a fallback cell with context "test".
  (let [context "test"]
    (harness/run-cells
     (for [policy (cons nil config/policies)]
       (child policy
              [{:context [(policy-context policy)]
                :form `(do (require 'value-type.impl.runtime 'value-type.cell-registry)
                           (value-type.impl.runtime/fallback-policy)
                           (System/setProperty value-type.cell-registry/policy-property
                                               "invalid-later")
                           (value-type.cell-registry/twice
                            (fn []
                              (try (value-type.impl.runtime/enforce-fallback! ~context)
                                   :allowed
                                   (catch Error e#
                                     (value-type.cell-registry/failure e#))))))
                :expected (registry/each-attempt
                           (if (= "error" policy)
                             (harness/policy-rejection context)
                             :allowed))
                :fallback? true
                :warning context}])))))

(deftest invalid-policy-test
  ;; Invalid launch values do not fail at load: the requires succeed.
  ;; invalid-policy-report-test shows the throw at the first read.
  (harness/run-cells
   (for [policy ["" "WARN" "unknown"]]
     (child policy
            [{:context [(policy-context (pr-str policy))]
              :form '(do (require 'value-type.core 'value-type.capability
                                  'value-type.impl.runtime)
                         :loaded)
              :expected :loaded}]))))

(deftest public-require-test
  ;; Public loading must not load optional implementation APIs or warn.
  (harness/run-cells
   (for [policy config/policies]
     (child policy
            [{:context [(policy-context policy)]
              :form (list 'do '(require 'value-type.core) optional-loaded-form)
              :expected false}]))))

(deftest public-definition-test
  ;; Real source compilation selects representation without weakening strict policy.
  (let [{:keys [available?] :as configuration} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies]
       (child policy
              [{:context [(policy-context policy)]
                :form '(do (require 'value-type.core 'value-type.impl.capability
                                    'value-type.cell-registry)
                           (let [available? (:available? (value-type.impl.capability/capability))
                                 cell-ns *ns*]
                             {:available? available?
                              :outcome
                              (value-type.cell-registry/outcome
                               #(value-type.cell-registry/failed-definition % cell-ns 'Sample)
                               #(eval '(value-type.core/defvaluetype Sample [x]))
                               (fn [c]
                                 (let [[field equal?] (eval '(let [a (->Sample 42) b (->Sample 42)]
                                                               [(.-x a) (= a b)]))]
                                   (merge {:stored field :equal? equal?}
                                          (value-type.cell-registry/defined cell-ns 'Sample c 42)))))}))
                :expected (harness/described
                           (harness/keyed
                            {:available? (harness/fixed available?)
                             :outcome (if (and (not available?) (= "error" policy))
                                        (harness/failed-definition (harness/rejection-context configuration) {})
                                        (harness/fixed
                                         (merge {:stored 42 :equal? available?}
                                                registry/new-definition)))}))
                :fallback? (not available?)
                :warning :any}])))))

(defn- root-class
  "A child form: the root class name of the cause chain of the failure of
   call (a form that `eval` runs), through failed-call."
  [call]
  (list 'value-type.cell-registry/failed-call
        '#(:class (value-type.cell-registry/thrown %))
        (list 'fn* [] (list 'eval call))))

(defn- non-nil-checks
  "A child form map: the root class names of (new cn nil) and (new cn \"s\"),
   and for record? the same checks through create, with :create-stored."
  [record?]
  (merge
   {:nil (root-class '(list 'new cn nil))
    :wrong-type (root-class '(list 'new cn "s"))}
   (when record?
     {:create-stored '(eval (list '.-x (list '. cn 'create {:x 42})))
      :create-nil (root-class '(list '. cn 'create {:x nil}))
      :create-wrong-type (root-class '(list '. cn 'create {:x "s"}))})))

(defn- deftype*-form
  "A child form: evaluate defvaluetype* of simple-name with fields in the
   cell namespace. Give {:named? :new-class? :value? :hash-fields :stored}
   (hash-fields is the count of declared __hash/__hasheq fields; stored is
   field x of (new cn 42), the declared-fields constructor), merged with
   the child form map checks, or the failed-definition map."
  [simple-name fields checks]
  (list 'do '(require 'value-type.core 'value-type.impl.capability
                      'value-type.cell-registry)
        (list 'let ['cell-ns '*ns*
                    'cn (list 'symbol (list 'str '(namespace-munge cell-ns)
                                            (str "." simple-name)))]
              (list 'value-type.cell-registry/outcome
                    (list 'fn '[e] (list 'value-type.cell-registry/failed-definition
                                         'e 'cell-ns (list 'quote simple-name)))
                    (list 'fn '[] (list 'eval (list 'list ''value-type.core/defvaluetype*
                                                    (list 'quote simple-name) 'cn
                                                    (list 'quote fields))))
                    (list 'fn '[^Class c]
                          (list 'merge
                                '{:named? (= (str cn) (.getName c))
                                  :new-class? (value-type.cell-registry/new-class! c)
                                  :value? (value-type.impl.capability/value-class? c)
                                  :hash-fields (value-type.cell-registry/hash-field-count c)
                                  :stored (eval (list '.-x (list 'new cn 42)))}
                                checks))))))

(def ^:private non-nil-field
  "The enabled field of the non-nil cells."
  (with-meta 'x {:tag 'Long :non-nil true}))

(def ^:private rejected-checks
  "The expected :nil and :wrong-type outcomes."
  {:nil "java.lang.NullPointerException"
   :wrong-type "java.lang.ClassCastException"})

(deftest deftype*-policy-test
  ;; Both shapes follow policy; warn emits once per runtime, shared with
  ;; defvaluetype; declaration errors come before policy.
  (let [{:keys [available?] :as configuration} (config/configuration)
        context (harness/rejection-context configuration)
        fallback (fn [policy expected]
                   (if (and (not available?) (= "error" policy))
                     (harness/described (harness/failed-definition context {}))
                     expected))
        definition (fn [policy label class-name fields record? checks extra]
                     {:context [(policy-context policy) label]
                      :form (deftype*-form class-name
                              (cond-> fields
                                record? (into registry/record-tail))
                              checks)
                      :expected (fallback policy
                                          (merge {:named? true :new-class? true
                                                  :value? available?
                                                  :hash-fields (if (and record?
                                                                        (not available?))
                                                                 2
                                                                 0)
                                                  :stored 42}
                                                 extra))
                      :fallback? (not available?)
                      :warning context})]
    (harness/run-cells
     (for [policy config/policies]
       (child policy
              [(definition policy "plain" 'PolicyPlain '[x] false {} {})
               (definition policy "record" 'PolicyRecord '[x] true {} {})
               (definition policy "plain non-nil" 'PolicyPlainNonNil
                 [non-nil-field] false (non-nil-checks false)
                 rejected-checks)
               (definition policy "record non-nil" 'PolicyRecordNonNil
                 [non-nil-field] true (non-nil-checks true)
                 (merge {:create-stored 42}
                        rejected-checks
                        (set/rename-keys
                         rejected-checks
                         {:nil :create-nil
                          :wrong-type :create-wrong-type})))
               {:context [(policy-context policy) "defvaluetype after"]
                :form '(do (require 'value-type.core)
                           (try (eval '(value-type.core/defvaluetype PolicyLater [x]))
                                :defined
                                (catch Throwable _ :rejected)))
                :expected (if (and (not available?) (= "error" policy))
                            :rejected
                            :defined)
                :fallback? (not available?)
                :warning context}
               {:context [(policy-context policy) "declaration error"]
                :form '(do (require 'value-type.core 'value-type.cell-registry)
                           (value-type.cell-registry/outcome
                            value-type.cell-registry/root-data
                            #(eval '(value-type.core/defvaluetype*
                                      PolicyBad cell.PolicyBad [^:non-nil x]))))
                :expected {:value-type/error :invalid-field
                           :class-name "cell.PolicyBad"
                           :field 'x
                           :reason :missing-tag
                           :tag nil}
                :fallback? false}])))))

(deftest deftype*-removed-field-check-test
  ;; Value output rejects hash-field reads; identity fallback does not check.
  (let [{:keys [available?]} (config/configuration)]
    (harness/run-cells
     (for [policy ["silent" "warn"]]
       (child policy
              [{:context [(policy-context policy)]
                :form '(do (require 'value-type.core 'value-type.cell-registry)
                           (value-type.cell-registry/outcome
                            value-type.cell-registry/root-data
                            #(eval (list 'value-type.core/defvaluetype*
                                         'HashRead 'cell.HashRead
                                         ['a '__meta '__extmap
                                          (with-meta '__hash {:tag 'int})
                                          (with-meta '__hasheq {:tag 'int})]
                                         :implements ['clojure.lang.IHashEq]
                                         '(hasheq [this] __hash)))))
                :expected (if available?
                            {:value-type/error :invalid-field
                             :class-name "cell.HashRead"
                             :field '__hash
                             :reason :removed-field-access}
                            :accepted)
                :fallback? (not available?)
                :warning :any}])))))

(deftest loader-require-test
  ;; Requiring the loader loads no optional value-output namespace.
  (harness/run-cells
   [(child nil
           [{:context []
             :form '(do (require 'value-type.impl.loader)
                        (boolean
                         (some find-ns '[value-type.impl.value-backend
                                         value-type.impl.transform
                                         value-type.impl.field-access])))
             :expected false}])]))

(deftest fallback-source-compatibility-test
  ;; Compare real identity types; capable JVMs must not be forced into fallback.
  (let [{:keys [available?]} (config/configuration)
        expected (merge registry/new-definition
                        {:fields ["long" "java.lang.Object"]
                         :protocol 42 :interface "sample" :method "sample:42"
                         :same true :distinct true :unequal true :hash true
                         :clojure-hash true :lookup :found :other-lookup :absent})]
    (harness/run-cells
     [(child "silent"
             [{:context []
               :form '(do (require '[value-type.impl.capability :as capability])
                          (if (:available? (capability/capability))
                            :value-mode
                            (do (require 'value-type.source-checks)
                                ((resolve 'value-type.source-checks/compatibility)))))
               :expected (if available?
                           :value-mode
                           {:ordinary expected :portable expected
                            :hinted-field 42 :constructor true :import true})}])])))

(defn- failed-entry
  "Return [description predicate] of a failed definition (cell-registry
   failed-definition) with the fixed keys of fixed, and a :message that
   names each of texts. When class-name? is true, the root :data also has
   a :class-name (a runtime <detail>) that the message names (see
   harness/observed)."
  [fixed texts class-name?]
  (let [entry (fn [class-name]
                (harness/with-entry
                  (cond-> fixed class-name? (assoc-in [:data :class-name] class-name))
                  :message
                  (harness/contains (cond->> texts class-name? (cons class-name)))))]
    (if class-name?
      (harness/observed [:data :class-name] (harness/detail) entry)
      (entry nil))))

(deftest source-field-validation-test
  ;; Mutable fields fail in either representation; false metadata remains legal.
  (let [defined (assoc registry/new-definition :final? true)
        mutable #(failed-entry {:key % :enabled? true :class "clojure.lang.ExceptionInfo"
                                :data {:value-type/error :mutable-field :fields '[x]}
                                :absent? true}
                               ["mutable fields [x]"] true)
        fields (harness/entries
                [(mutable :volatile-mutable)
                 (harness/fixed (merge {:key :volatile-mutable :enabled? false} defined))
                 (mutable :unsynchronized-mutable)
                 (harness/fixed (merge {:key :unsynchronized-mutable :enabled? false} defined))])
        invalid (harness/entries
                 (mapv (fn [[_ text]]
                         (failed-entry {:class "java.lang.AssertionError" :absent? true}
                                       [text] false))
                       validation/invalid-fields))]
    (harness/run-cells
     [(child "silent"
             [{:context []
               :form '(do (require '[value-type.validation-checks :as validation])
                          {:fields (validation/check-fields) :invalid (validation/invalid-forms)})
               :expected (harness/described
                          (harness/keyed {:fields fields :invalid invalid}))}])])))

(def ^:private discarded-expansion
  "Expected result of a discarded fallback expansion."
  {:returned? true :absent? true})

(def ^:private returned-definition
  "Expected definition map of an evaluated fallback expansion."
  (assoc registry/new-definition :returned? true))

(deftest fallback-expansion-policy-test
  ;; Discarded expansions consume warning state; caught strict errors grant no permission.
  ;; The warning is on the unbound thread of the first expansion; that thread
  ;; binds the cell *err*.
  (let [{:keys [available?] :as configuration} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies
           :let [strict? (= "error" policy)
                 rejected (harness/failed-definition (harness/rejection-context configuration) {})
                 expansions (harness/entries
                             (transfer/each-expand
                              (harness/entries
                               (registry/each-attempt
                                (if strict? rejected (harness/fixed discarded-expansion))))))
                 definitions (harness/entries
                              (registry/each-attempt
                               (if strict? rejected (harness/fixed returned-definition))))]]
       (child policy
              [{:context [policy]
                :form (registry/check-form 'value-type.expansion-checks/check-expansions)
                :expected
                (if available?
                  :value-mode
                  (harness/described
                   (harness/keyed
                    {:loader-bound? (harness/fixed {:unbound false :bound true})
                     :unbound expansions
                     :bound expansions
                     :evaluated definitions
                     :source definitions})))
                :fallback? (not available?)
                :warning :any}])))))

(deftest fallback-expansion-warning-timing-test
  ;; Fresh warning state prevents earlier expansions from masking delayed warnings.
  ;; Isolation rule 5: each cell needs the first warning of a fresh library
  ;; runtime, so each cell has its own child. The check captures the warning
  ;; in its own *err* binding; thus the cell *err* is empty (not a fallback
  ;; cell for the harness text rule).
  (let [{:keys [available?]} (config/configuration)]
    (harness/run-cells
     (for [expand transfer/expands
           bound? [false true]]
       (child "warn"
              [{:context [(str expand " loader-bound=" bound?)]
                :form (registry/check-form 'value-type.expansion-checks/check-expansions
                                           expand bound?)
                :expected
                (if available?
                  :value-mode
                  (let [warning (harness/repeated-text 1 harness/warning-prefix)
                        [description keyed?]
                        (harness/keyed {:loader-bound? (harness/fixed bound?)
                                        :expanded (harness/fixed discarded-expansion)
                                        :evaluated (harness/fixed returned-definition)
                                        :after-expansion warning
                                        :after-evaluation warning})]
                    (harness/described
                     [description
                      (fn [{:keys [after-expansion after-evaluation] :as observed}]
                        (and (keyed? observed)
                             (= after-expansion after-evaluation)))])))}])))))

(defn- discarded
  "Return [description predicate] (harness/detailed) of a discarded result
   {:detail :attempts} merged with extra: detail is present when the
   capability is not available; two discarded attempts for detail."
  [available? rejected? extra]
  (harness/detailed
   (not available?)
   (fn [detail]
     (assoc extra :attempts
            (registry/each-attempt
             {:outcome (if rejected? (harness/policy-rejection detail) :accepted)
              :absent? true})))))

(deftest constrained-discarded-expansion-test
  ;; Discarding a constrained form must not defer policy or install its owner.
  (let [{:keys [available?]} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies
           :let [rejected? (and (not available?) (= "error" policy))]]
       (child policy
              (for [expand transfer/expands
                    bound? [false true]]
                {:context [(str expand " " policy " loader-bound=" bound?)]
                 :form (registry/check-form 'value-type.expansion-checks/constrained-discarded-with-loader
                                            expand bound?)
                 :expected (harness/described
                            (discarded available? rejected? {:loader-bound? bound?}))
                 :fallback? (not available?)
                 :warning :any}))))))

(deftest constrained-validation-before-policy-test
  ;; Invalid discarded declarations neither warn nor become strict-policy errors.
  (let [{:keys [available?]} (config/configuration)
        invalid (mapv (fn [[_ reason]]
                        {:outcome {:value-type/error :invalid-field
                                   :reason reason :field 'text}
                         :diagnostics ""
                         :absent? true})
                      expansion/invalid-declarations)]
    (harness/run-cells
     (for [policy config/policies
           :let [rejected? (and (not available?) (= "error" policy))]]
       (child policy
              (for [expand transfer/expands]
                {:context [(str expand " " policy)]
                 :form (list 'do
                             '(require '[value-type.expansion-checks :as expansion])
                             {:invalid (list 'expansion/invalid-constrained-discarded expand)
                              :valid (list 'expansion/constrained-discarded expand)})
                 :expected (harness/described
                            (harness/keyed
                             {:invalid (harness/fixed invalid)
                              :valid (discarded available? rejected? {})}))
                 :fallback? (not available?)
                 :warning :any}))))))

(deftest invalid-policy-report-test
  ;; report reads the policy first: an invalid value throws plain
  ;; IllegalArgumentException before any probe; report does not load core.
  (harness/run-cells
   (for [policy ["" "WARN" "unknown"]]
     (child policy
            [{:context [(policy-context (pr-str policy))]
              :form '(do (require 'value-type.capability 'value-type.cell-registry)
                         {:thrown (value-type.cell-registry/failed-call
                                   value-type.cell-registry/thrown
                                   value-type.capability/report)
                          :probes (mapv #(realized? @(find-var %))
                                        '[value-type.impl.capability/detected-capability
                                          value-type.impl.field-capability/detected-field-capability
                                          value-type.impl.array/detected-allocation-capability
                                          value-type.impl.array/detected-inspection-capability])
                          :core-loaded? (some? (find-ns 'value-type.core))})
              :expected {:thrown {:class "java.lang.IllegalArgumentException"
                                  :message (registry/invalid-policy-message policy)
                                  :chain ["java.lang.IllegalArgumentException"]}
                         :probes [false false false false]
                         :core-loaded? false}}]))))
