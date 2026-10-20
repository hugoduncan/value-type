(ns value-type.environment-test
  "Check that the JVM can run value-type."
  (:require
   [clojure.test :refer [deftest is testing]])
  (:import
   (java.lang.management ManagementFactory)))

(defn- value-class-support?
  "Return true when `Class::isValue` exists. Use reflection, so this
  namespace loads on a JDK without value classes."
  []
  (try
    (.getMethod Class "isValue" (make-array Class 0))
    true
    (catch NoSuchMethodException _ false)))

(defn- preview-enabled?
  "Return true when the JVM input arguments contain `--enable-preview`."
  []
  (.contains (.getInputArguments (ManagementFactory/getRuntimeMXBean))
             "--enable-preview"))

(deftest jvm-has-value-classes-test
  (testing "the JVM supports JEP 401 value classes"
    (is (value-class-support?)
        (str "The JVM has no value classes (Class::isValue is missing). "
             "Java " (System/getProperty "java.version") " is in use. "
             "Fix: use the Valhalla JDK (https://jdk.java.net/valhalla/): "
             "export JAVA_CMD=<valhalla-jdk-home>/bin/java"))))

(deftest jvm-has-preview-enabled-test
  (testing "the JVM runs with --enable-preview"
    (is (preview-enabled?)
        (str "The JVM runs without --enable-preview. "
             "Fix: start the JVM with the :test alias (clojure -M:test ...)."))))

(deftest core-loads-test
  (testing "value-type.core loads"
    (is (nil? (require 'value-type.core))
        "value-type.core does not load. Fix: make sure src is on the classpath.")))
