(ns value-type.object-size
  "Manual shallow-size comparison. Run with bash bench/object-size.sh."
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [criterium.agent :as agent]
   [value-type.core :refer [defvaluetype]])
  (:import
   [java.lang.ref Reference]
   [java.lang.reflect Modifier]))

(deftype Identity0 [])
(defvaluetype Value0 [])
(deftype Identity1 [^long f0])
(defvaluetype Value1 [^long f0])
(deftype Identity2 [^long f0 ^long f1])
(defvaluetype Value2 [^long f0 ^long f1])
(deftype Identity3 [^long f0 ^long f1 ^long f2])
(defvaluetype Value3 [^long f0 ^long f1 ^long f2])

(def ^:private cases
  [{:kind :deftype :field-count 0 :class Identity0 :construct ->Identity0}
   {:kind :defvaluetype :field-count 0 :class Value0 :construct ->Value0}
   {:kind :deftype :field-count 1 :class Identity1 :construct ->Identity1}
   {:kind :defvaluetype :field-count 1 :class Value1 :construct ->Value1}
   {:kind :deftype :field-count 2 :class Identity2 :construct ->Identity2}
   {:kind :defvaluetype :field-count 2 :class Value2 :construct ->Value2}
   {:kind :deftype :field-count 3 :class Identity3 :construct ->Identity3}
   {:kind :defvaluetype :field-count 3 :class Value3 :construct ->Value3}])

(defn- require-agent!
  []
  (when-not (agent/attached?)
    (throw (ex-info "Criterium native agent is not attached; run bash bench/object-size.sh"
                    {}))))

(defn- class-evidence
  [{:keys [kind field-count] target-class :class}]
  (let [^Class target target-class
        fields (into (sorted-map)
                     (comp (remove #(Modifier/isStatic (.getModifiers ^java.lang.reflect.Field %)))
                           (map (fn [^java.lang.reflect.Field field]
                                  [(.getName field) (.getName (.getType field))])))
                     (.getDeclaredFields target))
        expected (into (sorted-map)
                       (map (fn [i] [(str "f" i) "long"]))
                       (range field-count))
        evidence {:kind kind :field-count field-count :class (.getName target)
                  :fields fields :value? (.isValue target)}]
    (when-not (and (= expected fields)
                   (= (= kind :defvaluetype) (:value? evidence)))
      (throw (ex-info "Unexpected target class layout" evidence)))
    evidence))

(defn- trace-case
  [{:keys [field-count construct] :as measurement-case}]
  (let [layout (class-evidence measurement-case)
        values (mapv (fn [i] (mapv #(long (+ i %)) (range field-count)))
                     (range 10))
        thread (.threadId (Thread/currentThread))
        [records objects] (agent/with-allocation-tracing
                            (mapv #(apply construct %) values))]
    (try
      (assoc layout :values values :sample-count 10 :thread thread
             :records (vec records))
      (finally
        (Reference/reachabilityFence objects)))))

(defn- validate-case
  [{:keys [class thread records] :as evidence}]
  (let [targets (filterv #(and (= class (:object-type %))
                               (= thread (:thread %))) records)
        sizes (into #{} (map :object_size) targets)
        problems (cond-> []
                   (not= 10 (count targets)) (conj :target-count)
                   (some :freed targets) (conj :freed-target)
                   (not-every? #(and (integer? %) (pos? %)) sizes)
                   (conj :invalid-size)
                   (not= 1 (count sizes)) (conj :size-discrepancy))]
    (when (seq problems)
      (throw (ex-info "Invalid object-size evidence"
                      {:problems problems :sizes sizes :evidence evidence
                       :target-records targets})))
    (assoc (dissoc evidence :records) :target-count (count targets)
           :bytes (first sizes))))

(defn- result-rows
  [validated]
  (mapv (fn [field-count]
          (let [pair (filterv #(= field-count (:field-count %)) validated)
                by-kind (into {} (map (juxt :kind :bytes)) pair)]
            (when-not (and (= 2 (count pair))
                           (= #{:deftype :defvaluetype} (set (keys by-kind))))
              (throw (ex-info "Missing or duplicate type pair"
                              {:field-count field-count :pair pair})))
            (assoc by-kind :field-count field-count
                   :difference (- (:defvaluetype by-kind) (:deftype by-kind)))))
        (range 4)))

(defn- vm-option
  [option-name]
  (let [bean (java.lang.management.ManagementFactory/getPlatformMXBean
              com.sun.management.HotSpotDiagnosticMXBean)]
    (try
      (let [option (.getVMOption bean option-name)]
        {:value (.getValue option) :origin (str (.getOrigin option))})
      (catch IllegalArgumentException _
        {:value :unknown :reason "VM option unavailable"}))))

(def ^:private vm-option-names
  ["ObjectAlignmentInBytes" "UseCompressedOops"
   "UseCompressedClassPointers" "UseCompactObjectHeaders"])

(defn- environment-data
  [state]
  (when-not (= "0.5.251-ALPHA" (:criterium state))
    (throw (ex-info "Unexpected Criterium version" {:version (:criterium state)})))
  (when-not (and (seq (get-in state [:agent :path]))
                 (seq (get-in state [:agent :sha256])))
    (throw (ex-info "Criterium native agent metadata unavailable" {:agent (:agent state)})))
  (update state :vm-options
          (fn [options]
            (into (sorted-map)
                  (map (fn [key]
                         [key (get options key
                                   {:value :unknown :reason "VM option unavailable"})]))
                  vm-option-names))))

(defn- read-environment-state
  []
  (let [properties (java.util.Properties.)
        resource (io/resource "META-INF/maven/org.hugoduncan/criterium/pom.properties")
        runtime (java.lang.management.ManagementFactory/getRuntimeMXBean)
        agent-path (agent/extract-agent!)]
    (when resource
      (with-open [stream (io/input-stream resource)]
        (.load properties stream)))
    {:recorded-at (str (java.time.Instant/now))
     :system (into (sorted-map)
                   (map (fn [key] [key (System/getProperty key "unknown")]))
                   ["java.version" "java.runtime.version" "java.vm.name"
                    "java.vm.version" "java.vendor" "os.name" "os.version" "os.arch"])
     :clojure (clojure-version)
     :criterium (.getProperty properties "version")
     :criterium-resource (str resource)
     :jvm-arguments (vec (.getInputArguments runtime))
     :agent (when agent-path
              {:path agent-path
               :sha256 (.formatHex (java.util.HexFormat/of)
                                   (.digest (java.security.MessageDigest/getInstance "SHA-256")
                                            (java.nio.file.Files/readAllBytes
                                             (.toPath (io/file agent-path)))))})
     :vm-options (into (sorted-map)
                       (map (fn [key] [key (vm-option key)]))
                       vm-option-names)}))

(defn- write-edn!
  [path data]
  (with-open [writer (io/writer path)]
    (pprint/pprint data writer)))

(defn- publish-evidence!
  [directory evidence]
  (let [result-file (io/file directory "results.edn")]
    (io/delete-file result-file true)
    (write-edn! (io/file directory "raw.edn") evidence)
    (let [validated (mapv validate-case (:cases evidence))
          results (assoc evidence :cases validated :rows (result-rows validated))]
      (write-edn! result-file results)
      results)))

(defn -main
  "Write raw evidence before validation; publish results only on success."
  [& _args]
  (require-agent!)
  (run! class-evidence cases)
  (let [environment (environment-data (read-environment-state))
        raw (mapv trace-case cases)
        evidence {:environment environment
                  :selection [:exact-class-name :allocating-thread]
                  :cases raw}]
    (prn (:rows (publish-evidence! "target/object-size" evidence)))))
