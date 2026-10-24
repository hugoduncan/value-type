(ns value-type.warning-checks
  (:require [clojure.walk :as walk]
            [value-type.core])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(defn- define-types
  [serialized index]
  (binding [*ns* (create-ns (symbol (str "warning.worker" index)))]
    (clojure.core/refer 'clojure.core)
    (mapv (fn [type-name]
            (let [form (walk/postwalk-replace
                        {'Transferred type-name} (read-string serialized))
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
