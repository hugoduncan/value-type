(ns value-type.array-info-boundary-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private export-option
  "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")

(defn- control-form
  [require-order]
  (str "(run! require '" (pr-str require-order) ")"
       "(prn {:runtime (System/getProperty \"java.version\")
               :policy (System/getProperty \"value-type.fallback\")
               :flags (vec (.getInputArguments
                             (java.lang.management.ManagementFactory/getRuntimeMXBean)))
               :require-order '" (pr-str require-order)
       " :native (do (require 'value-type.array-info-observation)
                       ((resolve 'value-type.array-info-observation/observe)))
          :outcomes
          (mapv (fn [input]
                  (mapv (fn [_]
                          (try
                            {:info (update (value-type.array/array-info input)
                                           :component-class #(.getName ^Class %))}
                            (catch clojure.lang.ExceptionInfo e
                              {:exception (.getName (class e))
                               :data (ex-data e)
                               :message (ex-message e)
                               :cause (some-> e ex-cause class .getName)})))
                        (range 2)))
                [nil \"not an array\" (long-array 1) (object-array 1)])})"))

(deftest safe-inspection-boundary-test
  ;; Crash-risk public inputs stay in disposable JVMs, including supported rows.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[label configuration supported? reason cause]
            [[:java8 baseline false :missing-array-api "java.lang.ClassNotFoundException"]
             [:no-options (assoc valhalla :jvm-opts []) false :array-access-denied "java.lang.IllegalAccessException"]
             [:no-export valhalla false :array-access-denied "java.lang.IllegalAccessException"]
             [:export (update valhalla :jvm-opts conj export-option) true nil nil]
             [:export-only (assoc valhalla :jvm-opts [export-option]) true nil nil]
             [:no-flattening (update valhalla :jvm-opts into
                                     [export-option "-XX:-UseArrayFlattening"]) true nil nil]]
            policy ["silent" "warn" "error"]
            require-order [['value-type.array 'value-type.core]
                           ['value-type.core 'value-type.array]]]
      (testing (pr-str [label policy require-order])
        (let [{:keys [exit result err]}
              (runner/run-jvm (assoc configuration :policy policy
                                     :form (control-form require-order)
                                     :timeout-ms 120000))
              outcomes (:outcomes result)]
          (is (= 0 exit) err)
          (is (re-find (if (= label :java8) #"^1\.8\." #"^27-jep401ea")
                       (or (:runtime result) "")))
          (is (= policy (:policy result)))
          (is (= require-order (:require-order result)))
          (is (= (conj (:jvm-opts configuration)
                       (str "-Dvalue-type.fallback=" policy))
                 (:flags result)))
          (is (= (if (= label :java8)
                   {:exception "java.lang.NoSuchMethodException"}
                   {:parameters [] :return "boolean" :static? false
                    :invocation {:value false}})
                 (get-in result [:native :component-value])))
          (is (= (if (= label :java8)
                   {:exception "java.lang.ClassNotFoundException"}
                   (into {}
                         (map (fn [[method-name value]]
                                [method-name
                                 {:parameters ["java.lang.Object"]
                                  :return "boolean" :static? true
                                  :invocation
                                  (if supported?
                                    {:value value}
                                    {:exception "java.lang.IllegalAccessException"})}]))
                         {"isNullRestrictedArray" false
                          "isAtomicArray" true
                          "isFlatArray" false}))
                 (get-in result [:native :array])))
          (is (= 4 (count outcomes)))
          (doseq [repeated outcomes]
            (is (= 2 (count repeated)))
            (is (= (first repeated) (second repeated))))
          (doseq [outcome (into [] cat (take 3 outcomes))]
            (is (= "clojure.lang.ExceptionInfo" (:exception outcome)))
            (is (= {:value-type/error :invalid-array :reason :not-reference-array}
                   (:data outcome)))
            (is (nil? (:cause outcome))))
          (doseq [outcome (nth outcomes 3 nil)]
            (if supported?
              (is (= {:info {:component-class "java.lang.Object"
                             :component-value? false :null-restricted? false
                             :atomic? true :flat? false}}
                     outcome))
              (do
                (is (= "clojure.lang.ExceptionInfo" (:exception outcome)))
                (is (= {:value-type/error :array-inspection-unavailable :reason reason}
                       (:data outcome)))
                (is (= cause (:cause outcome)))
                (is (re-find #"--add-exports" (:message outcome))))))
          (is (not (re-find #"(?i)fallback" err))))))))

(deftest export-only-capabilities-test
  ;; Inspection must not inherit allocation's preview prerequisite.
  (let [{:keys [valhalla]} (config/configuration)]
    (doseq [policy ["silent" "warn" "error"]]
      (testing policy
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc valhalla
                      :jvm-opts [export-option]
                      :policy policy
                      :timeout-ms 120000
                      :form
                      "(require 'value-type.array)
                       (let [input (object-array 1)
                             inspect #(update (value-type.array/array-info input)
                                              :component-class (fn [c] (.getName ^Class c)))]
                         (prn {:before (inspect)
                               :allocation
                               (mapv (fn [_]
                                       (try
                                         (value-type.array/make-array nil -1 nil)
                                         :unexpected-success
                                         (catch clojure.lang.ExceptionInfo e
                                           (select-keys (ex-data e) [:value-type/error :reason]))))
                                     (range 2))
                               :after (inspect)}))"))]
          (is (= 0 exit) err)
          (is (= {:component-class "java.lang.Object"
                  :component-value? false :null-restricted? false
                  :atomic? true :flat? false}
                 (:before result)))
          (is (= [{:value-type/error :array-unavailable
                   :reason :unsupported-class-version}
                  {:value-type/error :array-unavailable
                   :reason :unsupported-class-version}]
                 (:allocation result)))
          (is (= (:before result) (:after result)))
          (is (not (re-find #"(?i)fallback" err))))))))
