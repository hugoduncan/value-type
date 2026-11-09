(ns value-type.smoke-test
  "Bounded smoke checks on the facts-selected ordinary JDK."
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as aot]
            [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts]
            [value-type.jvm-runner :as runner]))

(defn- selection []
  (facts/select (facts/read-facts) :ordinary
                (executable/platform (System/getProperty "os.name")
                                     (System/getProperty "os.arch"))
                :plain))

(defn- runtime [selected]
  {:java (executable/resolve! selected (System/getenv)
                              (executable/default-root (System/getProperty "user.home")))
   :classpath (System/getProperty "java.class.path")
   :jvm-opts (conj (:jvm-opts selected) "-Dvalue-type.value-classes=auto")})

(def ^:private startup-form
  '(do
     (require 'value-type.core 'value-type.array 'value-type.capability)
     (prn {:build (System/getProperty "java.runtime.version")
           :classfile? (some? (Class/forName "java.lang.classfile.ClassFile"))
           :value-predicate? (boolean (some #(= "isValue" (.getName %))
                                            (.getMethods Class)))
           :flags (vec (.getInputArguments
                        (java.lang.management.ManagementFactory/getRuntimeMXBean)))
           :value-class (:value-class ((resolve 'value-type.capability/report)))})))

(deftest ordinary-startup-test
  ;; Load under every policy before any definition can trigger fallback.
  (let [selected (selection)
        jvm (runtime selected)]
    (doseq [policy ["silent" "warn" "error"]]
      (testing (str "ordinary startup under " policy)
        (let [run (runner/run-jvm (assoc jvm :policy policy :kind :smoke-startup
                                         :form (pr-str startup-form)))
              result (:result run)]
          (is (= 0 (:exit run)) (pr-str (runner/run-facts run)))
          (is (= "" (:err run)))
          (is (= (:build selected) (:build result)))
          (is (true? (:classfile? result)))
          (is (false? (:value-predicate? result)))
          (is (= {:available? false :reason :missing-value-predicate}
                 (:value-class result)))
          (is (= #{"-Dvalue-type.value-classes=auto"
                   (str "-Dvalue-type.fallback=" policy)}
                 (set (:flags result)))))))))

(def ^:private definition-form
  '(do
     (require 'value-type.core)
     (prn
      (mapv
       (fn [type-name]
         (let [failure (try
                         (eval (list 'value-type.core/defvaluetype type-name '[x]))
                         nil
                         (catch Throwable thrown
                           (loop [cause thrown]
                             (if-let [nested (.getCause cause)]
                               (recur nested)
                               {:class (.getName (class cause))
                                :message (.getMessage cause)}))))]
           (if failure
             {:failure failure
              :class-defined? (try
                                (Class/forName (str "user." type-name) false
                                               (clojure.lang.RT/baseLoader))
                                true
                                (catch ClassNotFoundException _ false))}
             (let [factory (resolve (symbol (str "->" type-name)))
                   left (factory 42)
                   right (factory 42)]
               {:field (.x left)
                :other-field (.x right)
                :distinct? (not (identical? left right))
                :equal? (= left right)
                :identity-class? (locking left true)}))))
       '[SmokeFirst SmokeSecond]))))

(deftest ordinary-definition-policy-test
  ;; Two definitions in each fresh process check policy and warning lifetime.
  (let [jvm (runtime (selection))]
    (doseq [policy ["silent" "warn" "error"]]
      (testing (str "ordinary definitions under " policy)
        (let [run (runner/run-jvm (assoc jvm :policy policy :kind :smoke-definition
                                         :form (pr-str definition-form)))
              results (:result run)]
          (is (= 0 (:exit run)) (pr-str (runner/run-facts run)))
          (is (= 2 (count results)))
          (if (= "error" policy)
            (doseq [result results]
              (is (= "java.lang.Error" (get-in result [:failure :class])))
              (is (re-find #"value-type: identity deftype fallback:"
                           (or (get-in result [:failure :message]) "")))
              (is (false? (:class-defined? result))))
            (is (= (vec (repeat 2 {:field 42 :other-field 42 :distinct? true
                                   :equal? false :identity-class? true}))
                   results)))
          (if (= "warn" policy)
            (is (= 1 (count (re-seq #"value-type: identity deftype fallback:"
                                    (:err run)))))
            (is (= "" (:err run)))))))))

(def ^:private aot-consumer-form
  '(do
     (require 'fallback.aot-fixture)
     (let [factory (resolve 'fallback.aot-fixture/->AotIdentity)
           left (factory 42)
           right (factory 42)]
       (prn {:source-absent? (nil? (clojure.java.io/resource "fallback/aot_fixture.clj"))
             :field (.x left)
             :other-field (.x right)
             :distinct? (not (identical? left right))
             :equal? (= left right)
             :identity-class? (locking left true)}))))

(deftest ordinary-aot-test
  ;; A fresh consumer must use compiled output, not recompile fixture source.
  (let [jvm (assoc (runtime (selection)) :policy "silent")]
    (aot/with-produced
      'fallback.aot-fixture aot/top-level-source jvm
      (fn [{:keys [output]}]
        (testing "source-free identity output on the same ordinary JDK"
          (let [run (runner/run-jvm
                     (assoc jvm :kind :smoke-aot-consumer
                            :classpath (aot/classpath-with jvm output)
                            :form (pr-str aot-consumer-form)))]
            (is (= 0 (:exit run)) (pr-str (runner/run-facts run)))
            (is (= "" (:err run)))
            (is (= {:source-absent? true :field 42 :other-field 42
                    :distinct? true :equal? false :identity-class? true}
                   (:result run)))))))))
