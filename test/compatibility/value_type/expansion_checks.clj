(ns value-type.expansion-checks
  "Expansion policy checks. Each public function reads *ns* once, on the
   cell thread, and defines or expands in that namespace."
  (:require [value-type.cell-registry :as registry]
            [value-type.core]
            [value-type.impl.runtime :as runtime]
            [value-type.transfer-checks :as transfer]))

(defn- on-thread
  "Call f on a new thread, with Compiler/LOADER bound when bound? and only
   *out*/*err* bound to the caller values (the cell writers). Return
   :loader-bound?, observed on that thread, and :result. Failures of f
   propagate."
  [bound? f]
  (let [result (promise)
        out *out*
        err *err*
        check #(binding [*out* out *err* err]
                 (hash-map :loader-bound? (thread-bound? clojure.lang.Compiler/LOADER)
                           :result (f)))
        thread (Thread. ^Runnable
                (fn []
                  (try
                    (deliver result
                             (if bound?
                               (with-bindings {clojure.lang.Compiler/LOADER
                                               (clojure.lang.DynamicClassLoader.
                                                (clojure.lang.RT/baseLoader))}
                                 (check))
                               (check)))
                    (catch Throwable thrown (deliver result thrown)))))]
    (.start thread)
    (.join thread)
    (when (instance? Throwable @result) (throw @result))
    @result))

(defn- definition
  "Return the definition map of an evaluation of type-name in cell-ns that
   returned value."
  [cell-ns type-name value]
  (assoc (registry/defined cell-ns type-name value 1) :returned? (some? value)))

(defn- discarded-expansion
  "Return the result of a discarded expansion of type-name in cell-ns:
   :returned? (expansion is not nil) and :absent?."
  [cell-ns type-name expansion]
  {:returned? (some? expansion) :absent? (registry/absent? cell-ns type-name)})

(defn- attempt
  "Apply expander to a defvaluetype form of type-name in cell-ns. A
   discarded expansion gives :returned? and :absent?; an evaluation
   (evaluate? true) gives its definition map; a failure gives
   registry/failed-definition."
  [cell-ns expander evaluate? type-name]
  (binding [*ns* cell-ns]
    (registry/outcome
     #(registry/failed-definition % cell-ns type-name)
     #(expander (list 'value-type.core/defvaluetype type-name '[x]))
     (if evaluate?
       #(definition cell-ns type-name %)
       #(discarded-expansion cell-ns type-name %)))))

(defn- expansion-attempts
  "Return the discarded attempts of each expand of transfer/expands, in
   order. The type name has the expand index, bound? and the attempt index."
  [cell-ns bound?]
  (vec (map-indexed
        (fn [expand-index expand]
          (registry/attempts #(attempt cell-ns (transfer/expanders expand) false
                                       (symbol (str "Expand" expand-index bound? %)))))
        transfer/expands)))

(defn- warning-timing
  [cell-ns expand]
  (let [diagnostics (java.io.StringWriter.)]
    (binding [*err* diagnostics
              *ns* cell-ns]
      (let [expansion ((transfer/expanders expand) '(value-type.core/defvaluetype Timing [x]))
            expanded (discarded-expansion cell-ns 'Timing expansion)
            after-expansion (str diagnostics)
            evaluated (eval expansion)]
        {:expanded expanded
         :after-expansion after-expansion
         :evaluated (definition cell-ns 'Timing evaluated)
         :after-evaluation (str diagnostics)}))))

(defn check-expansions
  "Check each expand of transfer/expands with real bound and unbound threads.
   :loader-bound? is the Compiler/LOADER binding observed on each thread."
  ([expand bound?]
   (if (:available? (runtime/capability))
     :value-mode
     (let [cell-ns *ns*
           {:keys [loader-bound? result]}
           (on-thread bound? #(warning-timing cell-ns expand))]
       (assoc result :loader-bound? loader-bound?))))
  ([]
   (if (:available? (runtime/capability))
     :value-mode
     (let [cell-ns *ns*
           unbound (on-thread false #(expansion-attempts cell-ns false))
           bound (on-thread true #(expansion-attempts cell-ns true))]
       {:loader-bound? {:unbound (:loader-bound? unbound) :bound (:loader-bound? bound)}
        :unbound (:result unbound)
        :bound (:result bound)
        ;; The earlier expansions were discarded. Evaluation shares their warning state.
        :evaluated (registry/attempts #(attempt cell-ns (fn [form] (eval (macroexpand form)))
                                                true (symbol (str "Evaluated" %))))
        :source (registry/attempts #(attempt cell-ns eval true (symbol (str "Source" %))))}))))

(defn- discarded
  [cell-ns expand]
  (binding [*ns* cell-ns]
    {:detail (:detail (runtime/capability))
     :attempts
     (registry/attempts
      (fn [index]
        (let [type-name (symbol (str "Discarded" index))
              outcome (registry/outcome
                       registry/failure
                       #((transfer/expanders expand)
                         (list 'value-type.core/defvaluetype type-name
                               [(with-meta 'text {:tag 'String :non-nil true})])))]
          {:outcome outcome
           :absent? (registry/absent? cell-ns type-name)})))}))

(defn constrained-discarded
  "Observe constrained expansion policy of expand (transfer/expands) without
   evaluating returned forms, in *ns*. Return the capability detail and
   registry/attempt-count attempts; an outcome is :accepted, or the root
   failure class and message (strict policy)."
  [expand]
  (discarded *ns* expand))

(defn constrained-discarded-with-loader
  "Observe discarded constrained forms on an explicitly bound or unbound
   thread. Return constrained-discarded with :loader-bound?."
  [expand bound?]
  (let [cell-ns *ns*
        {:keys [loader-bound? result]} (on-thread bound? #(discarded cell-ns expand))]
    (assoc result :loader-bound? loader-bound?)))

(def invalid-declarations
  "Invalid constrained declarations, in check order: pairs of field metadata
   and the expected :reason of the declaration error."
  [[{:tag 'String :non-nil :invalid} :invalid-non-nil]
   [{:tag 'String :nil-restricted false :non-nil true} :removed-nil-restricted]
   [{:non-nil true} :missing-tag]
   [{:tag 'MissingChild :non-nil true} :unresolved-tag]
   [{:tag 'Runnable :non-nil true} :interface-type]])

(defn invalid-constrained-discarded
  "Observe declaration errors of expand (transfer/expands) before policy,
   without evaluating expansions: one result for each declaration of
   invalid-declarations."
  [expand]
  (let [cell-ns *ns*
        expander (transfer/expanders expand)]
    (mapv
     (fn [[index metadata]]
       (let [type-name (symbol (str "InvalidDiscarded" index))
             diagnostics (java.io.StringWriter.)
             outcome (binding [*err* diagnostics]
                       (registry/outcome
                        #(select-keys (registry/root-data %)
                                      [:value-type/error :reason :field])
                        #(expander (list 'value-type.core/defvaluetype type-name
                                         [(with-meta 'text metadata)]))))]
         {:outcome outcome
          :diagnostics (str diagnostics)
          :absent? (registry/absent? cell-ns type-name)}))
     (map-indexed vector (map first invalid-declarations)))))
