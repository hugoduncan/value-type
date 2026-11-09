(ns value-type.jvm-config
  "Baseline-safe configuration for the documented fallback test matrix."
  (:require [value-type.cell-registry :as registry]
            [value-type.jvm-facts :as facts]
            [value-type.jvm-executable :as executable]))

(defn parent-role
  "Select a primary role by exact runtime build, independent of capabilities."
  [entries build flags]
  (or (some (fn [{:keys [role] expected :build}]
              (when (and (#{:baseline :valhalla} role)
                         (= expected build)
                         (or (= role :valhalla)
                             (not (some #{"--enable-preview"} flags))))
                role))
            entries)
      (throw (ex-info "Unsupported fallback test configuration: use the primary pair in dev/jvm/jvms.edn"
                      {:build build :jvm-opts flags}))))

(def preview-option
  "The JVM option that enables preview features."
  "--enable-preview")

(defn current-runtime
  "Return the Java executable (:java, from java.home) and the classpath
   (:classpath, from java.class.path) of the current JVM."
  []
  {:java (str (System/getProperty "java.home") "/bin/java")
   :classpath (System/getProperty "java.class.path")})

(defn role-runtime
  "Resolve one facts role/profile to a verified child runtime."
  [entries role platform profile environment root classpath]
  (let [selection (facts/select entries role platform profile)]
    {:java (executable/resolve! selection environment root)
     :classpath classpath
     :jvm-opts (:jvm-opts selection)}))

(defn configuration
  "Return the primary-pair matrix and independent capability expectations.

   Executables and launch profiles come from facts. The selected parent must
   match a primary build exactly; smoke roles never become matrix entries."
  []
  (let [entries (facts/read-facts)
        platform (executable/platform (System/getProperty "os.name")
                                      (System/getProperty "os.arch"))
        environment (System/getenv)
        root (executable/default-root (System/getProperty "user.home"))
        {flags :flags} (registry/jvm-facts)
        options (filterv #{preview-option} flags)
        role (parent-role entries (System/getProperty "java.runtime.version") options)
        baseline? (= role :baseline)
        valhalla? (= role :valhalla)
        {:keys [classpath] :as current} (current-runtime)
        runtime (fn [role profile]
                  (role-runtime entries role platform profile environment root classpath))]
    (executable/verify! (facts/select entries (if baseline? :baseline :valhalla)
                                      platform :plain)
                        (:java current))
    {:available? (and valhalla? (boolean (seq options)))
     :baseline? baseline?
     :unavailable-reason (if baseline? :missing-classfile-api :unsupported-class-version)
     :selected (assoc current :jvm-opts options)
     :baseline (runtime :baseline :plain)
     :valhalla (runtime :valhalla :preview)
     :preview-off (runtime :valhalla :plain)}))

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
