(ns value-type.jvm-runner-test
  "In-process tests of the pure runner rules: the child limit, the child
   wait time, the read of child stdout, the throw rule of each kind, the
   first child that throws and the removal of the output files. No child
   JVM starts."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.jvm-runner :as runner]))

(def ^:private limit-variable
  ;; Design Scope 4 contract: the documented variable name. Keep the
  ;; literal; runner/limit-variable is the code under test.
  "VALUE_TYPE_TEST_JVM_LIMIT")

(deftest read-limit-test
  ;; plan "Runner": VALUE_TYPE_TEST_JVM_LIMIT is a positive decimal integer,
  ;; default 4; at most Integer/MAX_VALUE.
  (testing "valid"
    (is (= [4 1 12 2147483647]
           (mapv runner/read-limit [nil "1" "12" "2147483647"]))))
  (doseq [value ["0" "-1" "4x" "" "2147483648" "99999999999999999999"]]
    (testing (pr-str [:invalid value])
      (let [e (try (runner/read-limit value) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (str/includes? (str (ex-message e)) limit-variable))
        (is (str/includes? (str (ex-message e)) (pr-str value)))))))

(deftest wait-ms-test
  ;; plan "Runner": the wait from process start is :timeout-ms plus 10000 ms
  ;; for each cell after the first.
  (is (= 120000 (runner/wait-ms {})))
  (is (= 120000 (runner/wait-ms {:cell-count 1})))
  (is (= 2000 (runner/wait-ms {:timeout-ms 2000 :cell-count 1})))
  (is (= 22000 (runner/wait-ms {:timeout-ms 2000 :cell-count 3}))))

(deftest read-result-test
  ;; plan "Runner": on exit 0 the last stdout line reads as EDN, and empty
  ;; stdout gives :result nil (no :read-error); an unreadable line gives
  ;; :read-error and no :result; a non-zero exit or a timeout gives
  ;; :result nil.
  (is (= {:result nil} (runner/read-result 0 "")))
  (is (= {:result [1]} (runner/read-result 0 "x\n[1]")))
  (let [unreadable (runner/read-result 0 "#object[java.lang.Object 0x1 \"o\"]")]
    (is (string? (:read-error unreadable)))
    (is (not (contains? unreadable :result))))
  (is (= {:result nil} (runner/read-result 3 "[1]")))
  (is (= {:result nil} (runner/read-result :timeout "[1]"))))

(deftest read-failure-test
  ;; plan "Runner": a producer or compile child with :read-error throws; a
  ;; :cell child, and a map with no :kind (default :cell), do not.
  (let [unreadable {:exit 0 :out "#object[x]" :err "" :read-error "bad"}
        readable {:exit 0 :out "[1]" :err "" :result [1]}
        data (assoc unreadable :index 0)]
    (doseq [kind [:producer :aot-compile :java-compile]]
      (testing (pr-str kind)
        (is (= data (runner/read-failure [{:kind kind}] [unreadable])))
        (is (nil? (runner/read-failure [{:kind kind}] [readable])))))
    (testing ":cell"
      (is (nil? (runner/read-failure [{:kind :cell}] [unreadable]))))
    (testing "no :kind"
      (is (nil? (runner/read-failure [{}] [unreadable]))))
    (testing "first of two"
      (is (= 1 (:index (runner/read-failure
                        [{:kind :cell} {:kind :producer} {:kind :aot-compile}]
                        [unreadable unreadable unreadable])))))))

(deftest thrown-failure-test
  ;; run-jvms docstring: when a child throws, throw ex-info of the first
  ;; such child (:index).
  (let [a (Exception. "a")
        b (Exception. "b")]
    (testing "no :thrown"
      (is (nil? (runner/thrown-failure [{:result 1} {:result 2}]))))
    (testing "first of two"
      (is (= {:index 1 :thrown a} (runner/thrown-failure
                                   [{:result 1} {:thrown a} {:thrown b}]))))))

(deftest with-output-files-test
  ;; run-jvm docstring: temporary output files are removed before returning,
  ;; also when the child throws.
  (let [received (atom nil)
        receive (fn [out-file err-file]
                  (reset! received [(.getParentFile ^java.io.File out-file)
                                    out-file err-file])
                  (spit out-file "out")
                  (spit err-file "err"))
        exist? #(mapv (fn [^java.io.File f] (.exists f)) @received)]
    (testing "returns"
      (is (= :value (runner/with-output-files
                      (fn [out-file err-file]
                        (receive out-file err-file)
                        :value))))
      (is (= [false false false] (exist?))))
    (testing "throws"
      (let [e (ex-info "thrown" {})]
        (is (identical? e (try (runner/with-output-files
                                 (fn [out-file err-file]
                                   (receive out-file err-file)
                                   (throw e)))
                               (catch clojure.lang.ExceptionInfo t t)))))
      (is (= [false false false] (exist?))))))
