(ns value-type.value-class-switch-checks
  "Baseline-safe child checks of the value-type.value-classes switch. Loads
   no value-type namespace at its own load: each check requires the public
   namespaces, then makes the first capability read."
  (:require [clojure.java.io :as io]
            [value-type.capability-matrix-checks :as capability-matrix-checks]
            [value-type.cell-registry :as registry]))

(def ^:private switch-property
  "The JVM property of the value-class switch."
  "value-type.value-classes")

(defn- require-public
  "Require the public namespaces."
  []
  (require 'value-type.core 'value-type.array 'value-type.capability))

(defn- report
  "Call value-type.capability/report."
  []
  ((resolve 'value-type.capability/report)))

(defn allocation-messages
  "Require the public namespaces. Return the registry/rejected-call
   outcome of make-array and of into-array."
  []
  (require-public)
  {:make-array (registry/rejected-call #((resolve 'value-type.array/make-array) Object 1 nil))
   :into-array (registry/rejected-call #((resolve 'value-type.array/into-array) Object [nil]))})

(defn probe-loads
  "Require the public namespaces; call report. Return the :value-class
   reason and whether a class-load line of log-path (written by the child
   JVM option -Xlog:class+load:file=log-path) names the value-class probe
   class CapabilityProbe."
  [log-path]
  (require-public)
  (let [result (report)]
    {:reason (get-in result [:value-class :reason])
     :probe-loaded? (with-open [r (io/reader log-path)]
                      (boolean (some #(.contains ^String % "CapabilityProbe")
                                     (line-seq r))))}))

(defn fixed-read
  "Require the public namespaces; call report; set the switch to auto;
   call report again. Return the :value-class reason of each read."
  []
  (require-public)
  (let [first-reason (get-in (report) [:value-class :reason])]
    (System/setProperty switch-property "auto")
    {:first first-reason
     :second (get-in (report) [:value-class :reason])}))

(defn invalid-value
  "Require the public namespaces (loading succeeds). Call report; set the
   switch to auto; call report again; define an ordinary type; call
   make-array and both into-array arities with a lazy input that counts
   its realizations; call array-info. Return :thrown (the first failure),
   :second-identical?, :make-array-identical? and :into-array-identical?
   (whether each later failure is the first failure), :definition
   (:accepted, or its class and whether its cause is the first failure),
   :effects (the input realization count) and :inspection (the
   capability-matrix-checks/inspection result)."
  []
  (require-public)
  (let [first-failure (registry/caught report)
        _ (System/setProperty switch-property "auto")
        second-failure (registry/caught report)
        effects (atom 0)
        make-input #(lazy-seq (swap! effects inc) [1])
        same? #(boolean (and first-failure (identical? first-failure %)))
        failure-of (fn [^Throwable t]
                     {:class (.getName (class t))
                      :cause-identical? (same? (.getCause t))})
        definition (registry/outcome
                    failure-of
                    #(eval '(value-type.core/defvaluetype SwitchPoint [^long x])))]
    {:thrown (some-> first-failure registry/thrown)
     :second-identical? (same? second-failure)
     :definition definition
     :make-array-identical?
     (same? (registry/caught
             #((resolve 'value-type.array/make-array) nil -1 nil)))
     :into-array-identical?
     [(same? (registry/caught
              #((resolve 'value-type.array/into-array) nil (make-input))))
      (same? (registry/caught
              #((resolve 'value-type.array/into-array) nil nil (make-input))))]
     :effects @effects
     :inspection (capability-matrix-checks/inspection)}))

(defn policy-first
  "Require the public namespaces; call report. Return the thrown map of
   the failure."
  []
  (require-public)
  (registry/failed-call registry/thrown report))

(defn- narrowed-failure
  "Return the outer exception class and registry/root-failure of error
   with :data narrowed to :value-type/error and :reason."
  [error]
  (assoc (update (registry/root-failure error) :data select-keys
                 [:value-type/error :reason])
         :outer-class (.getName (class error))))

(defn aot-load
  "Require the saved namespace ns-sym once (no source on the classpath).
   Return source absence and the load outcome: :accepted, or the
   outer exception class and root failure with :data narrowed to
   :value-type/error and :reason."
  [ns-sym source-path]
  {:source-absent? (nil? (io/resource source-path))
   :load (registry/outcome narrowed-failure #(require ns-sym))})

(defn aot-install
  "aot-load of ns-sym and source-path, then call ns-sym/install for each
   attempt (registry/twice). Return :source-absent?, :load and
   :invocations (each an outcome with the outer class and narrowed root failure)."
  [ns-sym source-path]
  (let [loaded (aot-load ns-sym source-path)
        install (resolve (symbol (name ns-sym) "install"))]
    (assoc loaded :invocations
           (registry/twice #(registry/outcome narrowed-failure install)))))

(defn aot-load-failed-self-check
  "aot-load of ns-sym and source-path while
   value-type.impl.capability/capability returns a :failed-self-check
   result with detail."
  [ns-sym source-path detail]
  (require 'value-type.impl.capability)
  (with-redefs-fn {(resolve 'value-type.impl.capability/capability)
                   (constantly {:available? false
                                :reason :failed-self-check
                                :detail detail})}
    #(aot-load ns-sym source-path)))
