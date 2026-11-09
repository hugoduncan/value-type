(ns value-type.jvm-config-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-facts :as facts]))

(deftest selected-profile-test
  ;; Use the real parent executable; changed facts must control launch data.
  (let [java (str (System/getProperty "java.home") "/bin/java")
        entries (mapv #(assoc % :build (System/getProperty "java.runtime.version"))
                      (facts/read-facts))
        environment {"VALUE_TYPE_BASELINE_JAVA" java
                     "VALUE_TYPE_VALHALLA_JAVA" java}
        runtime (fn [role profile]
                  (config/role-runtime entries role :linux-x64 profile
                                       environment "/unused" "isolated-classpath"))]
    (testing "profiles do not inherit preview or exports"
      (is (= [] (:jvm-opts (runtime :baseline :plain))))
      (is (= [] (:jvm-opts (runtime :valhalla :plain))))
      (is (= ["--enable-preview"] (:jvm-opts (runtime :valhalla :preview))))
      (is (= ["--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"]
             (:jvm-opts (runtime :valhalla :export)))))
    (testing "selection preserves the isolated classpath"
      (is (= "isolated-classpath" (:classpath (runtime :baseline :plain)))))
    (testing "changed build facts reject a stale executable override"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"build identity mismatch"
                            (config/role-runtime
                             (mapv #(assoc % :build "different-build") entries)
                             :baseline :linux-x64 :plain environment "/unused" "cp"))))))

(deftest primary-matrix-test
  ;; Smoke roles do not add receivers or execution modes.
  (let [matrix {:baseline :baseline :preview-off :off :valhalla :on
                :ordinary :smoke}]
    (is (= [:baseline :off :on] (config/receivers matrix)))
    (is (= [:baseline :off] (config/incompatible-receivers matrix)))
    (is (= [[] ["-Xint"]] config/executions))))

(deftest parent-role-test
  ;; Facts identify primary roles by name, independent of row order or EA prefix.
  (let [entries (mapv #(if (= :valhalla (:role %))
                         (assoc % :build "28-new-ea+2") %)
                      (facts/read-facts))
        by-role (into {} (map (juxt :role identity)) entries)
        build (fn [role] (:build (get by-role role)))]
    (doseq [rows [entries (mapv by-role [:ordinary :valhalla :baseline])]]
      (testing (str "role order " (mapv :role rows))
        (is (= :baseline (config/parent-role rows (build :baseline) [])))
        (is (= :valhalla (config/parent-role rows (build :valhalla) [])))
        (is (= :valhalla (config/parent-role rows (build :valhalla) ["--enable-preview"])))
        (is (thrown? clojure.lang.ExceptionInfo
                     (config/parent-role rows "27-jep401ea-stale" [])))
        (is (thrown? clojure.lang.ExceptionInfo
                     (config/parent-role rows (build :ordinary) [])))
        (is (thrown? clojure.lang.ExceptionInfo
                     (config/parent-role rows (build :baseline) ["--enable-preview"])))))))
