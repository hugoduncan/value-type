(ns value-type.jvm-config
  "Baseline-safe configuration for the documented fallback test matrix."
  (:require [clojure.string :as str]))

(defn configuration
  "Return explicit runner configurations and independent host expectations.

   Only Java 8 without preview and Valhalla 27 EA with preview off/on are
   test matrix entries. Producer executable overrides retain their roles."
  []
  (let [version (System/getProperty "java.version")
        options (filterv #{"--enable-preview"}
                         (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean)))
        baseline? (str/starts-with? version "1.8.")
        valhalla? (str/starts-with? version "27-jep401ea")
        classpath (System/getProperty "java.class.path")]
    (when-not (or (and baseline? (empty? options)) valhalla?)
      (throw (ex-info "Unsupported fallback test configuration: use Java 8 or Valhalla 27 JEP 401 EA"
                      {:version version :jvm-opts options})))
    {:available? (and valhalla? (boolean (seq options)))
     :baseline? baseline?
     :unavailable-reason (if baseline? :missing-classfile-api :unsupported-class-version)
     :selected {:java (str (System/getProperty "java.home") "/bin/java")
                :classpath classpath :jvm-opts options}
     :baseline {:java (or (System/getenv "VALUE_TYPE_BASELINE_JAVA")
                          "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java")
                :classpath classpath :jvm-opts []}
     :valhalla {:java (or (System/getenv "VALUE_TYPE_VALHALLA_JAVA")
                          (str (System/getProperty "user.home")
                               "/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"))
                :classpath classpath :jvm-opts ["--enable-preview"]}}))
