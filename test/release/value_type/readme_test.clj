(ns value-type.readme-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.readme :as readme]))

(def initial
  (str "Introduction\n<!-- release-status:start -->\nNo release is published.\n"
       "<!-- release-status:end -->\nInstallation\n<!-- release-install:start -->\n"
       "No coordinates yet.\n<!-- release-install:end -->\n"
       "Development only: clone and :local/root\n"))

(deftest prepared-coordinates-test
  ;; First preparation and replacement preserve unrelated text and state publication limits.
  (doseq [input [initial (readme/render initial "0.1.2")]]
    (let [result (readme/render input "0.1.3")]
      (is (str/includes? result "org.hugoduncan/value-type {:mvn/version \"0.1.3\"}"))
      (is (str/includes? result "Use them only after this\nversion is published"))
      (is (str/includes? result "After version 0.1.3 is published"))
      (is (not (str/includes? result "0.1.2")))
      (is (str/starts-with? result "Introduction\n"))
      (is (str/includes? result "<!-- release-status:end -->\nInstallation\n"))
      (is (str/ends-with? result "Development only: clone and :local/root\n"))
      (is (= result (readme/render result "0.1.3"))))))

(deftest invalid-regions-test
  ;; Missing, duplicate, reversed and overlapping markers cannot produce a prepared document.
  (doseq [label ["status" "install"]
          edge ["start" "end"]]
    (let [marker (str "<!-- release-" label ":" edge " -->")]
      (testing marker
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"marker pair"
                              (readme/render (str/replace initial marker "") "0.1.3")))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"marker pair"
                              (readme/render (str initial marker) "0.1.3"))))))
  (doseq [markers [["status:end" "status:start" "install:start" "install:end"]
                   ["status:start" "install:start" "install:end" "status:end"]
                   ["status:start" "install:start" "status:end" "install:end"]]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (readme/render (apply str (mapv #(str "<!-- release-" % " -->") markers))
                                "0.1.3"))))
  (doseq [version [nil "" "0.1.0" "1.2.3" "0.1.2\n"]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid release version"
                          (readme/render initial version)))))
