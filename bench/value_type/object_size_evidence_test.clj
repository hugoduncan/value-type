(ns value-type.object-size-evidence-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest native-evidence-test
  ;; Check the real launcher's files independently of its result validation.
  (let [directory (or (System/getenv "OBJECT_SIZE_EVIDENCE_DIR")
                      (throw (ex-info "Run bench/object-size-integration-test.sh" {})))
        raw (edn/read-string (slurp (io/file directory "raw.edn")))
        results (edn/read-string (slurp (io/file directory "results.edn")))
        case-key (juxt :kind :field-count)
        expected-keys (into #{} (for [kind [:deftype :defvaluetype]
                                     n (range 4)]
                                 [kind n]))
        raw-cases (into {} (map (juxt case-key identity)) (:cases raw))
        result-cases (into {} (map (juxt case-key identity)) (:cases results))
        sizes (into {}
                    (map (fn [[key evidence]]
                           [key (:object_size
                                 (first (filterv #(and (= (:class evidence) (:object-type %))
                                                      (= (:thread evidence) (:thread %)))
                                                 (:records evidence))))]))
                    raw-cases)]
    (testing "published measurement environment"
      (let [environment (:environment raw)
            agent-path (get-in environment [:agent :path])
            agent-hash (get-in environment [:agent :sha256])
            arguments (:jvm-arguments environment)
            options (:vm-options environment)]
        (is (map? environment))
        (is (= environment (:environment results)))
        (doseq [property ["java.version" "java.runtime.version" "java.vm.name"
                          "java.vm.version" "java.vendor"
                          "os.name" "os.version" "os.arch"]]
          (let [value (get-in environment [:system property])]
            (is (and (string? value) (not (str/blank? value))
                     (not= "unknown" value))
                (str "Published system property: " property))))
        (is (= (clojure-version) (:clojure environment)))
        (is (= "0.5.251-ALPHA" (:criterium environment)))
        (is (and (string? agent-path) (not (str/blank? agent-path))
                 (.isAbsolute (io/file agent-path))))
        (is (and (string? agent-hash)
                 (re-matches #"[0-9a-f]{64}" agent-hash)))
        (is (vector? arguments))
        (is (some #{"--enable-preview"} arguments))
        (is (some #{(str "-agentpath:" agent-path)} arguments))
        (is (= #{"ObjectAlignmentInBytes" "UseCompressedOops"
                 "UseCompressedClassPointers" "UseCompactObjectHeaders"}
               (set (keys options))))
        (doseq [[option {:keys [value origin reason]}] options]
          (testing option
            (if (= :unknown value)
              (is (and (string? reason) (not (str/blank? reason))))
              (do
                (is (and (string? value) (not (str/blank? value))))
                (is (and (string? origin) (not (str/blank? origin))))))))))
    (doseq [document [raw results]]
      (is (= 8 (count (:cases document))))
      (is (= expected-keys (into #{} (map case-key) (:cases document))))
      (is (= [:exact-class-name :allocating-thread] (:selection document))))
    (doseq [[kind n :as key] expected-keys]
      (testing (str kind " with " n " fields")
        (let [evidence (get raw-cases key)
              target (str "value_type.object_size."
                          (if (= kind :deftype) "Identity" "Value") n)
              targets (filterv #(and (= target (:object-type %))
                                     (= (:thread evidence) (:thread %)))
                               (:records evidence))
              expected-values (mapv (fn [i] (mapv #(long (+ i %)) (range n)))
                                    (range 10))]
          (is (= target (:class evidence)))
          (is (= (into {} (map (fn [i] [(str "f" i) "long"])) (range n))
                 (:fields evidence)))
          (is (= (= kind :defvaluetype) (:value? evidence)))
          (is (= 10 (:sample-count evidence)))
          (is (= expected-values (:values evidence)))
          (is (pos-int? (:thread evidence)))
          (is (= 10 (count targets)))
          (is (not-any? :freed targets))
          (is (every? #(and (integer? (:object_size %))
                           (pos? (:object_size %))) targets))
          (is (= 1 (count (into #{} (map :object_size) targets))))
          (is (= (assoc (dissoc evidence :records)
                        :target-count 10 :bytes (get sizes key))
                 (get result-cases key))))))
    (doseq [n (range 4)]
      (is (= (:values (get raw-cases [:deftype n]))
             (:values (get raw-cases [:defvaluetype n])))))
    (is (= (mapv (fn [n]
                   (let [identity-size (get sizes [:deftype n])
                         value-size (get sizes [:defvaluetype n])]
                     {:field-count n :deftype identity-size
                      :defvaluetype value-size
                      :difference (- value-size identity-size)}))
                 (range 4))
           (:rows results)))))
