(ns value-type.object-size-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [value-type.object-size :as size]))

(def ^:private record
  {:object-type "target.Class" :thread 7 :object_size 24 :freed false})

(defn- evidence
  [records]
  {:class "target.Class" :thread 7 :records records})

(deftest selection-test
  ;; Only the exact class and allocating thread establish target size.
  (testing "unrelated records do not affect the result"
    (let [records (conj (vec (repeat 10 record))
                        (assoc record :object-type "target.ClassOther" :object_size 99)
                        (assoc record :thread 8 :object_size 99))
          result (#'size/validate-case (evidence records))]
      (is (= 24 (:bytes result)))
      (is (= 10 (:target-count result))))))

(deftest invalid-evidence-test
  ;; Failures retain all evidence and identify the violated contract.
  (doseq [[problem records]
          [[:target-count []]
           [:target-count (vec (repeat 9 record))]
           [:target-count (vec (repeat 11 record))]
           [:freed-target (conj (vec (repeat 9 record)) (assoc record :freed true))]
           [:invalid-size (vec (repeat 10 (dissoc record :object_size)))]]]
    (testing (str problem)
      (let [input (evidence records)
            error (try (#'size/validate-case input)
                       (catch clojure.lang.ExceptionInfo exception
                         (ex-data exception)))]
        (is (= input (:evidence error)))
        (is (some #{problem} (:problems error))))))
  (doseq [invalid [0 -8 24.0 "24"]]
    (testing (str "invalid size " invalid)
      (is (thrown? clojure.lang.ExceptionInfo
                   (#'size/validate-case
                    (evidence (vec (repeat 10 (assoc record :object_size invalid)))))))))
  (testing "discrepancies retain both observed sizes"
    (let [error (try
                  (#'size/validate-case
                   (evidence (conj (vec (repeat 9 record))
                                   (assoc record :object_size 32))))
                  (catch clojure.lang.ExceptionInfo exception
                    (ex-data exception)))]
      (is (= #{24 32} (:sizes error)))
      (is (= [:size-discrepancy] (:problems error))))))

(deftest rows-test
  ;; Row order is independent of input order; differences remain signed.
  (let [validated (into [] (mapcat (fn [[n delta]]
                                     [{:field-count n :kind :deftype :bytes 32}
                                      {:field-count n :kind :defvaluetype
                                       :bytes (+ 32 delta)}]))
                        [[3 8] [1 0] [0 -8] [2 16]])
        rows (#'size/result-rows validated)]
    (is (= [{:field-count 0 :deftype 32 :defvaluetype 24 :difference -8}
            {:field-count 1 :deftype 32 :defvaluetype 32 :difference 0}
            {:field-count 2 :deftype 32 :defvaluetype 48 :difference 16}
            {:field-count 3 :deftype 32 :defvaluetype 40 :difference 8}]
           rows))
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'size/result-rows (pop validated))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'size/result-rows (conj validated (first validated)))))))

(deftype WrongName [^long other])
(deftype WrongType [f0])

(deftest layouts-test
  ;; Layout checks must not depend on the case-vector order.
  (let [layouts (into {} (map (fn [input]
                                [[(:kind input) (:field-count input)]
                                 (#'size/class-evidence input)]))
                      (reverse @#'size/cases))]
    (is (= (set (for [kind [:deftype :defvaluetype] n (range 4)] [kind n]))
           (set (keys layouts))))
    (doseq [[[kind n] layout] layouts]
      (testing (str kind " " n)
        (is (= (into {} (map #(vector (str "f" %) "long")) (range n))
               (:fields layout)))
        (is (= (= kind :defvaluetype) (:value? layout))))))
  (doseq [[label input]
          [["wrong name" {:kind :deftype :field-count 1 :class WrongName}]
           ["wrong type" {:kind :deftype :field-count 1 :class WrongType}]
           ["wrong count" {:kind :deftype :field-count 0 :class WrongName}]
           ["identity as value" {:kind :defvaluetype :field-count 0
                                 :class value_type.object_size.Identity0}]
           ["value as identity" {:kind :deftype :field-count 0
                                 :class value_type.object_size.Value0}]]]
    (testing label
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unexpected target class layout"
                            (#'size/class-evidence input))))))

(deftest environment-test
  ;; Metadata rules consume state, without extracting or reading an agent.
  (let [state {:criterium "0.5.251-ALPHA"
               :agent {:path "/synthetic/agent" :sha256 "synthetic-hash"}
               :system {"java.vm.version" "synthetic-vm"}
               :vm-options {"ObjectAlignmentInBytes" {:value "8" :origin "DEFAULT"}}}
        environment (#'size/environment-data state)]
    (is (= (dissoc state :vm-options) (dissoc environment :vm-options)))
    (is (= {"ObjectAlignmentInBytes" {:value "8" :origin "DEFAULT"}
            "UseCompressedOops" {:value :unknown :reason "VM option unavailable"}
            "UseCompressedClassPointers" {:value :unknown :reason "VM option unavailable"}
            "UseCompactObjectHeaders" {:value :unknown :reason "VM option unavailable"}}
           (:vm-options environment)))
    (testing "all missing VM options are explicitly unknown"
      (is (= 4 (count (:vm-options (#'size/environment-data (dissoc state :vm-options))))))
      (is (every? #(= :unknown (:value %))
                  (vals (:vm-options (#'size/environment-data (dissoc state :vm-options)))))))
    (doseq [version [nil "0.5.245-ALPHA"]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unexpected Criterium version"
                            (#'size/environment-data (assoc state :criterium version)))))
    (doseq [agent [nil {} {:path "/synthetic/agent"} {:sha256 "hash"}]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"agent metadata unavailable"
                            (#'size/environment-data (assoc state :agent agent)))))))

(deftest publication-test
  ;; Real isolated files preserve diagnostics and cannot retain stale success.
  (let [directory (.toFile (java.nio.file.Files/createTempDirectory
                            "object-size-test" (make-array java.nio.file.attribute.FileAttribute 0)))
        raw-file (io/file directory "raw.edn")
        result-file (io/file directory "results.edn")
        unrelated (io/file directory "keep.txt")
        input {:environment {:synthetic true}
               :cases (into [] (for [n (range 4) kind [:deftype :defvaluetype]]
                                 (assoc (evidence (vec (repeat 10 record)))
                                        :field-count n :kind kind)))}]
    (try
      (spit unrelated "keep")
      (let [result (#'size/publish-evidence! directory input)]
        (is (= input (edn/read-string (slurp raw-file))))
        (is (= result (edn/read-string (slurp result-file))))
        (is (= 4 (count (:rows result)))))
      (let [invalid (assoc-in input [:cases 0 :records] [])]
        (is (thrown? clojure.lang.ExceptionInfo
                     (#'size/publish-evidence! directory invalid)))
        (is (= invalid (edn/read-string (slurp raw-file))))
        (is (not (.exists result-file)))
        (is (= "keep" (slurp unrelated))))
      (finally
        (run! #(io/delete-file % true) [raw-file result-file unrelated directory])))))
