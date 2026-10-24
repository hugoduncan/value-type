(ns value-type.aot-checks
  (:require [clojure.java.io :as io]))

(defn- cause-chain
  [failure]
  (loop [current failure result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result (.getName (class current))))
      result)))

(defn compile-fixture
  "Compile the production fixture; report output and repeated strict failures."
  [directory strict?]
  (let [fixture 'fallback.aot-fixture
        binary-name "fallback.aot_fixture.AotIdentity"
        class-file (io/file directory "fallback/aot_fixture/AotIdentity.class")]
    (binding [*compile-path* directory]
      (if strict?
        (mapv (fn [_]
                (let [chain (try
                              (compile fixture)
                              []
                              (catch Throwable failure (cause-chain failure)))]
                  {:chain chain
                   :emitted? (.exists class-file)
                   :mapped? (some? (ns-resolve fixture 'AotIdentity))
                   :loadable? (try
                                (Class/forName binary-name false (clojure.lang.RT/baseLoader))
                                true
                                (catch ClassNotFoundException _ false))}))
              (range 2))
        (do
          (compile fixture)
          (let [factory (ns-resolve fixture '->AotIdentity)
                a (factory 42)
                b (factory 42)]
            {:emitted? (.isFile class-file)
             :identity? (not= a b)
             :field (.-x a)
             :factory? (= binary-name (.getName (class a)))}))))))

(defn load-fixture
  "Load compiled fallback output without source; report identity or Error chains."
  [strict?]
  (assert (nil? (io/resource "fallback/aot_fixture.clj")))
  (if strict?
    (mapv (fn [_]
            (try
              (require 'fallback.aot-fixture :reload)
              {:loaded? true}
              (catch Throwable failure
                {:error? (instance? Error failure)
                 :chain (cause-chain failure)})))
          (range 2))
    (do
      (require 'fallback.aot-fixture)
      (require 'fallback.aot-fixture :reload)
      (let [factory (ns-resolve 'fallback.aot-fixture '->AotIdentity)
            a (factory 42)
            b (factory 42)]
        {:field (.-x a)
         :identity? (not= a b)
         :factory? (= "fallback.aot_fixture.AotIdentity" (.getName (class a)))}))))

(defn load-value-fixture
  "Report source-free value artifact loading, or its JVM rejection chain."
  []
  (assert (nil? (io/resource "fallback/aot_fixture.clj")))
  (try
    (require 'fallback.aot-fixture)
    (let [factory (ns-resolve 'fallback.aot-fixture '->AotIdentity)
          instance (factory 42)
          predicate (.getMethod Class "isValue" (make-array Class 0))]
      {:value? (.invoke predicate (class instance) (object-array 0))
       :field (.-x instance)})
    (catch Throwable failure
      {:chain (cause-chain failure)})))

(defn invoke-runtime-fixture
  "Load source-free output, then invoke its guarded function twice."
  []
  (assert (nil? (io/resource "fallback/runtime_fixture.clj")))
  (require 'fallback.runtime-fixture)
  (let [operation (ns-resolve 'fallback.runtime-fixture 'define-type)]
    {:loaded? true
     :calls (mapv (fn [_]
                    (try
                      (let [type-class (operation)
                            factory (ns-resolve 'fallback.runtime-fixture '->RuntimeIdentity)
                            a (factory 42)]
                        {:field (.-x a)
                         :identity? (not= a (factory 42))
                         :factory? (= type-class (class a))})
                      (catch Throwable failure
                        {:chain (cause-chain failure)})))
                  (range 2))}))
