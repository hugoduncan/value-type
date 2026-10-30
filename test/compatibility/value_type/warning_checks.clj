(ns value-type.warning-checks
  (:require [clojure.walk :as walk]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(defn- define-types
  [serialized index]
  (binding [*ns* (create-ns (symbol (str "warning.worker" index)))]
    (clojure.core/refer 'clojure.core)
    (mapv (fn [type-name]
            (let [form (walk/postwalk-replace
                        {'Transferred type-name
                         "user.Transferred" (str (munge (str (ns-name *ns*))) "." type-name)}
                        (read-string serialized))
                  type-class (eval form)
                  factory (ns-resolve *ns* (symbol (str "->" type-name)))
                  a (factory 42)
                  b (factory 42)]
              {:field (clojure.lang.Reflector/getInstanceField a "x")
               :identity? (not= a b)
               :factory? (= type-class (class a))}))
          '[FirstIdentity SecondIdentity])))

(defn concurrent-definitions
  "Evaluate fixed fallback expansions in eight synchronized namespace workers."
  [serialized]
  (let [ready (CountDownLatch. 8)
        start (CountDownLatch. 1)
        workers (mapv
                 (fn [index]
                   (let [result (promise)
                         thread (Thread.
                                 (bound-fn []
                                   (.countDown ready)
                                   (try
                                     (.await start)
                                     (deliver result (define-types serialized index))
                                     (catch Throwable error
                                       (deliver result error)))))]
                     (.start thread)
                     {:thread thread :result result}))
                 (range 8))]
    (try
      (when-not (.await ready 10 TimeUnit/SECONDS)
        (throw (ex-info "Warning workers did not reach the barrier" {})))
      (finally (.countDown start)))
    (mapv (fn [{:keys [thread result]}]
            (.join ^Thread thread)
            (let [outcome @result]
              (if (instance? Throwable outcome)
                (throw outcome)
                outcome)))
          workers)))

(defn concurrent-rejections
  "Evaluate each fixed constrained expansion twice in concurrent namespaces.
   Per evaluation, return :accepted or the root failure, and owner and factory
   absence."
  [serialized]
  (let [start (promise)
        workers
        (mapv (fn [index]
                (future
                  @start
                  (binding [*ns* (create-ns (symbol (str "strict.worker" index)))]
                    (clojure.core/refer 'clojure.core)
                    (let [owner (str (munge (str (ns-name *ns*))) ".Transferred")
                          form (walk/postwalk-replace {"user.Transferred" owner}
                                                      (read-string serialized))]
                      (mapv (fn [_]
                              {:outcome (try (eval form) :accepted
                                             (catch Throwable thrown (checks/failure thrown)))
                               :factory-absent? (nil? (ns-resolve *ns* '->Transferred))
                               :owner-absent?
                               (try
                                 (Class/forName owner false (clojure.lang.RT/baseLoader))
                                 false
                                 (catch ClassNotFoundException _ true))})
                            (range 2))))))
              (range 8))]
    (deliver start true)
    (mapv deref workers)))
