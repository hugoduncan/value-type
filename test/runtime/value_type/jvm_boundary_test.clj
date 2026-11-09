(ns value-type.jvm-boundary-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.test-support :as support])
  (:import (java.lang.management ManagementFactory)))

(deftest runtime-child-compilation-boundary-test
  ;; Observe real JVM arguments; parent compilation must not leak to consumers.
  (let [parent (set (.getInputArguments (ManagementFactory/getRuntimeMXBean)))
        compilation #{"-Xcomp" "-Xbatch" "-XX:-TieredCompilation"}]
    (testing "forced compilation uses the complete parent configuration"
      (when (some parent compilation)
        (is (every? parent compilation))
        (is (contains? parent "--enable-preview"))
        (is (contains? parent "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"))
        (is (= "auto" (System/getProperty "value-type.value-classes")))))
    (testing "runtime consumers retain only preview, in normal and forced-JIT rows"
      (support/with-temp-dir [directory]
        (let [child (support/run-in-new-jvm
                     directory
                     "(prn (vec (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))))")]
          (is (= 0 (:exit child)) (pr-str child))
          (is (= ["--enable-preview"] (:result child)) (pr-str child))
          (is (= "" (:err child))))))))
