(ns value-type.warning-checks
  (:require [clojure.walk :as walk]
            [value-type.cell-registry :as registry]
            [value-type.core])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(defn- worker-ns
  "Return the worker namespace index under the namespace prefix."
  [prefix kind index]
  (create-ns (symbol (str prefix "." kind index))))

(defn- nil-construction
  "Return :accepted when factory takes nil, else the root failure class."
  [factory]
  (registry/outcome (comp :class registry/thrown) #(factory nil)))

(def identity-types
  "The identity type names that define-types defines in each worker."
  '[FirstIdentity SecondIdentity])

(defn- define-types
  "Define each identity type from serialized in worker namespace index.
   Return per type its definition map with :stored, :identity? and
   :nil-construction (factory call with nil)."
  [serialized class-name prefix index]
  (binding [*ns* (worker-ns prefix "warning.worker" index)]
    (clojure.core/refer 'clojure.core)
    (mapv (fn [type-name]
            (let [n *ns*
                  form (walk/postwalk-replace
                        {'Transferred type-name
                         class-name (registry/class-name n type-name)}
                        (read-string serialized))
                  type-class (eval form)
                  factory (ns-resolve n (registry/factory-symbol type-name))
                  a (factory 42)]
              (assoc (registry/defined n type-name type-class 42)
                     :stored (clojure.lang.Reflector/getInstanceField a "x")
                     :identity? (registry/identity? factory 42)
                     :nil-construction (nil-construction factory))))
          identity-types)))

(def worker-count
  "The number of concurrent namespace workers of each check."
  8)

(defn- run-workers
  "Run (f index) for each index below n, each on its own thread with the
   current bindings. All workers wait at one start barrier. Return the
   results in index order; rethrow the first worker failure."
  [n f]
  (let [ready (CountDownLatch. n)
        start (CountDownLatch. 1)
        workers (mapv
                 (fn [index]
                   (let [result (promise)
                         thread (Thread.
                                 (bound-fn []
                                   (.countDown ready)
                                   (try
                                     (.await start)
                                     (deliver result (f index))
                                     (catch Throwable thrown
                                       (deliver result thrown)))))]
                     (.start thread)
                     {:thread thread :result result}))
                 (range n))]
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

(defn concurrent-definitions
  "Evaluate fixed fallback expansions in worker-count synchronized namespace
   workers. class-name is the class name that the producer fixed in
   serialized. Worker namespaces have the current namespace name as prefix."
  [serialized class-name]
  (let [prefix (str (ns-name *ns*))]
    (run-workers worker-count
                 #(define-types serialized class-name prefix %))))

(defn concurrent-rejections
  "Evaluate each fixed constrained expansion twice in worker-count
   synchronized namespace workers. class-name is the class name that the
   producer fixed in serialized. Worker namespaces have the current
   namespace name as prefix. Per evaluation, return :accepted or the root
   failure, and :absent? of Transferred (cell-registry/absent?)."
  [serialized class-name]
  (let [prefix (str (ns-name *ns*))]
    (run-workers
     worker-count
     (fn [index]
       (binding [*ns* (worker-ns prefix "strict.worker" index)]
         (clojure.core/refer 'clojure.core)
         (let [n *ns*
               form (walk/postwalk-replace
                     {class-name (registry/class-name n 'Transferred)}
                     (read-string serialized))]
           (registry/twice (fn []
                             {:outcome (registry/outcome registry/failure #(eval form))
                              :absent? (registry/absent? n 'Transferred)}))))))))
