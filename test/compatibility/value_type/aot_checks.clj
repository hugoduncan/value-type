(ns value-type.aot-checks
  (:require [clojure.java.io :as io]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]))

(defn compile-fixture
  "Compile the production fixture. Strict: return one map for each of two
   attempts, registry/thrown (or {} without a throw) with :emitted? and
   :absent?. Else return {:emitted? :identity?
   :stored :new-class? :factory?}; it calls registry/new-class! once."
  [directory strict?]
  (let [fixture 'fallback.aot-fixture
        binary-name "fallback.aot_fixture.AotIdentity"
        class-file (io/file directory "fallback/aot_fixture/AotIdentity.class")]
    (binding [*compile-path* directory]
      (if strict?
        (registry/twice (fn []
                          (let [result (registry/outcome registry/thrown
                                                         #(compile fixture)
                                                         (constantly {}))]
                            (assoc result
                                   :emitted? (.exists class-file)
                                   :absent? (registry/absent? (create-ns fixture) "AotIdentity" binary-name)))))
        (do
          (compile fixture)
          (let [factory (ns-resolve fixture '->AotIdentity)]
            (merge {:emitted? (.isFile class-file)
                    :identity? (registry/identity? factory 42)
                    :stored (.-x (factory 42))}
                   (registry/defined fixture "AotIdentity"
                     (ns-resolve fixture 'AotIdentity) 42))))))))

(defn load-fixture
  "Load compiled fallback output without source; report :stored,
   :identity? and :binary-name? (the instance class has the fixture binary
   name), or registry/thrown with :error? for each strict attempt."
  [strict?]
  (assert (checks/source-absent? "fallback/aot_fixture.clj"))
  (if strict?
    (registry/twice (fn []
                      (registry/outcome #(assoc (registry/thrown %) :error? (instance? Error %))
                                        #(require 'fallback.aot-fixture :reload)
                                        (constantly {:loaded? true}))))
    (do
      (require 'fallback.aot-fixture)
      (require 'fallback.aot-fixture :reload)
      (let [factory (ns-resolve 'fallback.aot-fixture '->AotIdentity)
            a (factory 42)]
        {:stored (.-x a)
         :identity? (registry/identity? factory 42)
         :binary-name? (= "fallback.aot_fixture.AotIdentity" (.getName (class a)))}))))

(defn load-value-fixture
  "Report source-free value artifact loading, or registry/thrown of its JVM
   rejection."
  []
  (assert (checks/source-absent? "fallback/aot_fixture.clj"))
  (registry/outcome registry/thrown
                    (fn []
                      (require 'fallback.aot-fixture)
                      (let [factory (ns-resolve 'fallback.aot-fixture '->AotIdentity)
                            instance (factory 42)]
                        {:value? (checks/value-class? (class instance))
                         :stored (.-x instance)}))
                    identity))

(defn invoke-runtime-fixture
  "Load source-free output, then invoke its guarded function twice. Each
   call gives :stored, :identity? and :factory-class? (the defined Class is
   the instance class), or registry/thrown."
  []
  (assert (checks/source-absent? "fallback/runtime_fixture.clj"))
  (require 'fallback.runtime-fixture)
  (let [operation (ns-resolve 'fallback.runtime-fixture 'define-type)]
    {:loaded? true
     :calls (registry/twice
             #(registry/outcome registry/thrown
                                operation
                                (fn [type-class]
                                  (let [factory (ns-resolve 'fallback.runtime-fixture '->RuntimeIdentity)
                                        a (factory 42)]
                                    {:stored (.-x a)
                                     :identity? (registry/identity? factory 42)
                                     :factory-class? (= type-class (class a))}))))}))
