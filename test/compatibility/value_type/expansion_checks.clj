(ns value-type.expansion-checks
  (:require [value-type.core]
            [value-type.impl.runtime :as runtime]))

(defn- absent?
  [type-name]
  (and (nil? (ns-resolve 'value-type.expansion-checks type-name))
       (try
         (Class/forName (str "value_type.expansion_checks." type-name)
                        false (clojure.lang.RT/baseLoader))
         false
         (catch ClassNotFoundException _ true))))

(defn- root
  "Return the root cause of failure."
  [^Throwable failure]
  (if-let [cause (.getCause failure)] (recur cause) failure))

(defn- on-thread
  "Call f on a new thread, with Compiler/LOADER bound when bound?. Return
   :loader-bound?, observed on that thread, and :result. Failures of f
   propagate."
  [bound? f]
  (let [result (promise)
        check #(hash-map :loader-bound? (thread-bound? clojure.lang.Compiler/LOADER)
                         :result (f))
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
                    (catch Throwable error (deliver result error)))))]
    (.start thread)
    (.join thread)
    (when (instance? Throwable @result) (throw @result))
    @result))

(defn- attempt
  [operation type-name]
  (binding [*ns* (the-ns 'value-type.expansion-checks)]
    (try
      (let [expansion (operation (list 'value-type.core/defvaluetype type-name '[x]))]
        {:returned? (some? expansion) :absent? (absent? type-name)})
      (catch Throwable error
        {:chain (loop [cause error names []]
                  (let [names (conj names (.getName (class cause)))]
                    (if-let [next-cause (.getCause cause)]
                      (recur next-cause names)
                      names)))
         :absent? (absent? type-name)}))))

(defn- expansion-attempts
  [bound?]
  (mapv (fn [[operation label]]
          (mapv #(attempt operation (symbol (str label bound? %))) (range 2)))
        [[macroexpand "Full"] [macroexpand-1 "One"]]))

(defn- warning-timing
  [entry-point]
  (let [diagnostics (java.io.StringWriter.)]
    (binding [*err* diagnostics
              *ns* (the-ns 'value-type.expansion-checks)]
      (let [operation ({:full macroexpand :one macroexpand-1} entry-point)
            expansion (operation '(value-type.core/defvaluetype Timing [x]))
            expanded {:returned? (some? expansion) :absent? (absent? 'Timing)}
            after-expansion (str diagnostics)
            evaluated (eval expansion)]
        {:expanded expanded
         :after-expansion after-expansion
         :evaluated {:returned? (some? evaluated) :absent? (absent? 'Timing)}
         :after-evaluation (str diagnostics)}))))

(defn check-expansions
  "Check both expansion entry points with real bound and unbound threads.
   :loader-bound? is the Compiler/LOADER binding observed on each thread."
  ([entry-point bound?]
   (if (:available? (runtime/capability))
     :value-mode
     (let [{:keys [loader-bound? result]} (on-thread bound? #(warning-timing entry-point))]
       (assoc result :loader-bound? loader-bound?))))
  ([]
   (if (:available? (runtime/capability))
     :value-mode
     (let [unbound (on-thread false #(expansion-attempts false))
           bound (on-thread true #(expansion-attempts true))]
       {:loader-bound? {:unbound (:loader-bound? unbound) :bound (:loader-bound? bound)}
        :unbound (:result unbound)
        :bound (:result bound)
        ;; The earlier expansions were discarded. Evaluation shares their warning state.
        :evaluated (binding [*ns* (the-ns 'value-type.expansion-checks)]
                     (mapv #(attempt (fn [form] (eval (macroexpand form)))
                                     (symbol (str "Evaluated" %)))
                           (range 2)))
        :source (mapv #(attempt eval (symbol (str "Source" %))) (range 2))}))))

(defn constrained-discarded
  "Observe constrained expansion policy without evaluating returned forms.
   Return the capability detail and two attempts; a strict outcome is the
   root failure class and message."
  [entry-point]
  (binding [*ns* (the-ns 'value-type.expansion-checks)]
    {:detail (:detail (runtime/capability))
     :attempts
     (mapv
      (fn [index]
        (let [type-name (symbol (str "Discarded" index))
              operation ({:full macroexpand :one macroexpand-1} entry-point)
              outcome (try
                        (operation (list 'value-type.core/defvaluetype type-name
                                         [(with-meta 'text {:tag 'String :non-nil true})]))
                        :returned
                        (catch Throwable error
                          (let [^Throwable cause (root error)]
                            {:class (.getName (class cause)) :message (.getMessage cause)})))]
          {:outcome outcome
           :owner-absent? (absent? type-name)
           :factory-absent? (nil? (ns-resolve *ns* (symbol (str "->" type-name))))}))
      (range 2))}))

(defn constrained-discarded-with-loader
  "Observe discarded constrained forms on an explicitly bound or unbound
   thread. Return constrained-discarded with :loader-bound?."
  [entry-point bound?]
  (let [{:keys [loader-bound? result]} (on-thread bound? #(constrained-discarded entry-point))]
    (assoc result :loader-bound? loader-bound?)))

(defn invalid-constrained-discarded
  "Observe declaration errors before policy, without evaluating expansions."
  [entry-point]
  (binding [*ns* (the-ns 'value-type.expansion-checks)]
    (let [operation ({:full macroexpand :one macroexpand-1} entry-point)]
      (mapv
       (fn [[index metadata]]
         (let [type-name (symbol (str "InvalidDiscarded" index))
               diagnostics (java.io.StringWriter.)
               outcome (binding [*err* diagnostics]
                         (try
                           (operation (list 'value-type.core/defvaluetype type-name
                                            [(with-meta 'text metadata)]))
                           :returned
                           (catch Throwable error
                             (select-keys (ex-data (root error))
                                          [:value-type/error :reason :field]))))]
           {:outcome outcome
            :diagnostics (str diagnostics)
            :owner-absent? (absent? type-name)
            :factory-absent? (nil? (ns-resolve *ns* (symbol (str "->" type-name))))}))
       (map-indexed vector
                    [{:tag 'String :non-nil :invalid}
                     {:tag 'String :nil-restricted false :non-nil true}
                     {:non-nil true}
                     {:tag 'MissingChild :non-nil true}
                     {:tag 'Runnable :non-nil true}])))))
