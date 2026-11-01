(ns value-type.jvm-config
  "Baseline-safe configuration for the documented fallback test matrix."
  (:require [clojure.string :as str]
            [value-type.cell-registry :as registry]))

(def baseline-version-prefix
  "The java.version prefix of the Java 8 baseline runtime."
  "1.8.")

(def valhalla-version-prefix
  "The java.version prefix of the Valhalla 27 JEP 401 EA runtime."
  "27-jep401ea")

(def preview-option
  "The JVM option that enables preview features."
  "--enable-preview")

(defn current-runtime
  "Return the Java executable (:java, from java.home) and the classpath
   (:classpath, from java.class.path) of the current JVM."
  []
  {:java (str (System/getProperty "java.home") "/bin/java")
   :classpath (System/getProperty "java.class.path")})

(defn configuration
  "Return explicit runner configurations and independent host expectations.

   Only Java 8 without preview and Valhalla 27 EA with preview off/on are
   test matrix entries. :preview-off is the :valhalla runtime with no JVM
   options. :selected is the current-runtime with its preview option.
   Producer executable overrides retain their roles."
  []
  (let [{version :runtime flags :flags} (registry/jvm-facts)
        options (filterv #{preview-option} flags)
        baseline? (str/starts-with? version baseline-version-prefix)
        valhalla? (str/starts-with? version valhalla-version-prefix)
        {:keys [classpath] :as current} (current-runtime)
        valhalla {:java (or (System/getenv "VALUE_TYPE_VALHALLA_JAVA")
                            (str (System/getProperty "user.home")
                                 "/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"))
                  :classpath classpath :jvm-opts [preview-option]}]
    (when-not (or (and baseline? (empty? options)) valhalla?)
      (throw (ex-info "Unsupported fallback test configuration: use Java 8 or Valhalla 27 JEP 401 EA"
                      {:version version :jvm-opts options})))
    {:available? (and valhalla? (boolean (seq options)))
     :baseline? baseline?
     :unavailable-reason (if baseline? :missing-classfile-api :unsupported-class-version)
     :selected (assoc current :jvm-opts options)
     :baseline {:java (or (System/getenv "VALUE_TYPE_BASELINE_JAVA")
                          "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java")
                :classpath classpath :jvm-opts []}
     :valhalla valhalla
     :preview-off (assoc valhalla :jvm-opts [])}))

(defn receivers
  "Return the receiver runtime axis of configuration: baseline, preview off
   and preview on."
  [{:keys [baseline preview-off valhalla]}]
  [baseline preview-off valhalla])

(defn incompatible-receivers
  "Return the receivers of configuration without value capability: baseline
   and preview off."
  [{:keys [baseline preview-off]}]
  [baseline preview-off])

(def policies
  "The fallback policy axis of the test matrix."
  ["silent" "warn" "error"])

(def executions
  "The execution axis of the test matrix: added JVM options for normal and
   interpreter execution."
  [[] ["-Xint"]])

(defn with-execution
  "Return runtime with the execution options added to :jvm-opts."
  [runtime execution]
  (update runtime :jvm-opts into execution))

(defn with-executions
  "Return runtime in each execution of executions."
  [runtime]
  (map #(with-execution runtime %) executions))

(defn child-configuration
  "Return runtime with the execution options added to :jvm-opts and :policy
   policy."
  [runtime execution policy]
  (assoc (with-execution runtime execution) :policy policy))

(defn value-producer
  "Return the producer runtime of value expansions in configuration:
   Valhalla, preview on, policy error."
  [{:keys [valhalla]}]
  (assoc valhalla :policy "error"))

(defn identity-producer
  "Return the producer runtime of identity expansions in configuration:
   baseline, policy silent."
  [{:keys [baseline]}]
  (assoc baseline :policy "silent"))

(defn receiver-jvms
  "Return each receiver of configuration in each execution."
  [configuration]
  (mapcat with-executions (receivers configuration)))

(defn incompatible-receiver-jvms
  "Return each receiver of configuration without value capability in each
   execution."
  [configuration]
  (mapcat with-executions (incompatible-receivers configuration)))
