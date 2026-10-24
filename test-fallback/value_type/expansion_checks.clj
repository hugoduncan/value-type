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
  (assert (= bound? (thread-bound? clojure.lang.Compiler/LOADER)))
  (mapv (fn [[operation label]]
          (mapv #(attempt operation (symbol (str label bound? %))) (range 2)))
        [[macroexpand "Full"] [macroexpand-1 "One"]]))

(defn- warning-timing
  [entry-point bound?]
  (let [diagnostics (java.io.StringWriter.)
        result (promise)
        check (fn []
                (assert (= bound? (thread-bound? clojure.lang.Compiler/LOADER)))
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
                     :after-evaluation (str diagnostics)})))
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

(defn check-expansions
  "Check both expansion entry points with real bound and unbound threads."
  ([entry-point bound?]
   (if (:available? (runtime/capability))
     :value-mode
     (warning-timing entry-point bound?)))
  ([]
   (if (:available? (runtime/capability))
     :value-mode
     (let [result (promise)
           thread (Thread. ^Runnable
                           (fn []
                             (try (deliver result (expansion-attempts false))
                                  (catch Throwable error (deliver result error)))))]
       (.start thread)
       (.join thread)
       (when (instance? Throwable @result) (throw @result))
       {:unbound @result
        :bound (with-bindings {clojure.lang.Compiler/LOADER
                              (clojure.lang.DynamicClassLoader. (clojure.lang.RT/baseLoader))}
                 (expansion-attempts true))
        ;; The earlier expansions were discarded. Evaluation shares their warning state.
        :evaluated (binding [*ns* (the-ns 'value-type.expansion-checks)]
                     (mapv #(attempt (fn [form] (eval (macroexpand form)))
                                     (symbol (str "Evaluated" %)))
                           (range 2)))
        :source (mapv #(attempt eval (symbol (str "Source" %))) (range 2))}))))
