(ns value-type.jvm-facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-facts :as facts]))

(deftest selection-test
  ;; Named profiles must not enable unrelated VM features.
  (let [data (facts/read-facts)]
    (testing "all maintained roles have all supported platform archives"
      (doseq [role [:baseline :valhalla :ordinary]
              platform [:linux-x64 :linux-aarch64 :macos-aarch64]]
        (is (= [] (:jvm-opts (facts/select data role platform :plain))))))
    (testing "preview and exports remain separate"
      (is (= ["--enable-preview"]
             (:jvm-opts (facts/select data :valhalla :linux-x64 :preview))))
      (is (= ["--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"]
             (:jvm-opts (facts/select data :valhalla :linux-x64 :export)))))
    (testing "the ordinary role is smoke only"
      (is (= :smoke (:tier (facts/select data :ordinary :linux-x64 :plain)))))))

(deftest invalid-facts-test
  ;; Reject bad data before it can select or download a runtime.
  (let [data (facts/read-facts)]
    (doseq [[label invalid message]
            [["duplicate roles" (conj data (first data)) #"Duplicate JVM roles"]
             ["build" (assoc-in data [0 :build] "") #"build identity"]
             ["tier" (assoc-in data [0 :tier] :unknown) #"test tier"]
             ["profiles" (assoc-in data [0 :profiles :plain] ["--enable-preview"]) #"empty :plain"]
             ["flags" (assoc-in data [0 :profiles :bad] [nil]) #"flag profile"]
             ["platforms" (assoc-in data [0 :platforms] {}) #"platforms"]
             ["checksum" (assoc-in data [0 :platforms :linux-x64 :sha256] "bad") #"SHA-256"]
             ["URL" (assoc-in data [0 :platforms :linux-x64 :url] "http://example.org") #"HTTPS"]
             ["layout" (assoc-in data [0 :platforms :linux-x64 :java] "../bin/java") #"layout"]]]
      (testing label
        (is (thrown-with-msg? clojure.lang.ExceptionInfo message (facts/validate invalid)))))
    (doseq [[role platform profile message]
            [[:unknown :linux-x64 :plain #"Unknown JVM role"]
             [:baseline :unknown :plain #"Unknown JVM platform"]
             [:baseline :linux-x64 :preview #"Unknown JVM flag profile"]]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo message
                            (facts/select data role platform profile))))))

(deftest linux-arm64-selection-test
  ;; Native Linux archives retain the same role, build, tier and profiles.
  (let [data (facts/read-facts)]
    (doseq [role [:baseline :valhalla :ordinary]]
      (let [arm (facts/select data role :linux-aarch64 :plain)
            x64 (facts/select data role :linux-x64 :plain)]
        (is (= :aarch64 (get-in arm [:archive :architecture])))
        (is (= "bin/java" (get-in arm [:archive :java])))
        (is (= (dissoc x64 :platform :archive)
               (dissoc arm :platform :archive)))))))

(deftest installation-component-test
  ;; Facts reject path syntax but preserve exact build punctuation.
  (let [data (facts/read-facts)]
    (is (= data (facts/validate data)))
    (is (= "27-jep401ea3+1-1"
           (:build (first (facts/validate (assoc-in data [0 :build] "27-jep401ea3+1-1"))))))
    (doseq [component ["." ".." "../../outside" "a/b" "a\\b" "/absolute" "C:outside"]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"build identity"
                            (facts/validate (assoc-in data [0 :build] component))))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"JVM role"
                            (facts/validate (assoc-in data [0 :role] (keyword component)))))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"JVM platform"
                            (facts/validate
                             (assoc-in data [0 :platforms]
                                       {(keyword component) (get-in data [0 :platforms :linux-x64])})))))))
