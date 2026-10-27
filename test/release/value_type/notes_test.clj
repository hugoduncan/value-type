(ns value-type.notes-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.notes :as notes])
  (:import [java.time LocalDate]))

(def unreleased "Preamble\n\n## [Unreleased]\n\nNew notes\n")
(def prior "\n## [0.1.2] - 2024-02-29\n\nOld notes\n")

(deftest validation-test
  ;; Every entry is validated, not only the selected version.
  (testing "exact section syntax and unique canonical versions"
    (doseq [heading ["## Other" "##[Unreleased]" "## [Unreleased] "
                     "## [0.1.0] - 2024-01-01" "## [0.1.01] - 2024-01-01"
                     "## [1.0.1] - 2024-01-01" "## [0.1.3] - 2023-02-29"
                     "## [0.1.3] - 2024-13-01" "## [0.1.3] - 2024-01-01 "
                     "## [0.1.3] - 2024-1-01" "## [Unreleased]"]]
      (is (thrown? Exception (notes/parse (str unreleased heading "\nBody"))) heading))
    (is (thrown? Exception (notes/parse (str unreleased prior "\n## [0.1.2] - 2025-01-01\nOther")))))
  (testing "Unreleased is required first"
    (doseq [text ["" "Preamble" prior (str prior unreleased)]]
      (is (thrown? Exception (notes/parse text)))))
  (testing "all dated bodies must contain content"
    (doseq [body ["" "\n \t\n"]]
      (is (thrown? Exception (notes/parse (str unreleased "## [0.1.3] - 2024-01-01\n" body))))))
  (testing "empty Unreleased and no dated sections are valid"
    (is (= ["Unreleased"] (mapv :version (:sections (notes/parse "## [Unreleased]\n"))))))
  (testing "Markdown bodies are opaque"
    (doseq [body ["### Subheading" "<!-- comment -->"]]
      (is (= body (-> (notes/parse (str "## [Unreleased]\n" body)) :sections first :body)))))
  (testing "fences and comments do not hide headings"
    (doseq [prefix ["```" "<!--"]]
      (is (thrown? Exception (notes/parse (str unreleased prefix "\n## Unknown\n")))))))

(deftest selection-test
  ;; Fallback is allowed only for absent versions in preview mode.
  (testing "matching notes take priority"
    (is (= {:body "\nOld notes\n" :source "0.1.2" :preparation-required? false}
           (notes/select-notes (str unreleased prior) "0.1.2" true))))
  (testing "preview requires preparation"
    (is (= {:body "\nNew notes\n" :source "Unreleased" :preparation-required? true}
           (notes/select-notes unreleased "0.1.3" true)))
    (is (thrown? Exception (notes/select-notes unreleased "0.1.3" false))))
  (testing "invalid notes never fall back"
    (doseq [text ["## [Unreleased]\n "
                  (str unreleased "## [0.1.3] - 2024-01-01\n")
                  (str unreleased "## [0.1.3] - invalid\nContent")
                  (str unreleased prior "\n## [0.1.4] - 2024-01-01\n")]]
      (is (thrown? Exception (notes/select-notes text "0.1.3" true))))))

(deftest stamping-test
  ;; Stamping preserves the preamble and prior notes and uses the supplied date.
  (let [date (LocalDate/of 2026 10 23)
        original (str unreleased prior)
        stamped (notes/stamp original "0.1.3" date)]
    (testing "new version and empty Unreleased"
      (is (= (str "Preamble\n\n## [Unreleased]\n\n## [0.1.3] - 2026-10-23\n\nNew notes\n" prior)
             stamped))
      (is (= "" (-> (notes/parse stamped) :sections first :body)))
      (is (= "0.1.3" (:source (notes/select-notes stamped "0.1.3" false)))))
    (testing "reject duplicates and empty notes before producing a result"
      (is (thrown? Exception (notes/stamp original "0.1.2" date)))
      (is (thrown? Exception (notes/stamp "## [Unreleased]\n" "0.1.3" date))))))

(deftest section-boundaries-test
  ;; Only column-one section headings delimit opaque bodies; order is unrestricted.
  (testing "indented headings and subheadings remain body text"
    (let [body "  ## Not a section\n### Details\nText"]
      (is (= body (-> (notes/parse (str "## [Unreleased]\n" body))
                      :sections first :body)))))
  (testing "versions and dates need not increase"
    (let [text (str unreleased
                    "## [0.1.9] - 2025-01-01\nNewer\n"
                    "## [0.1.2] - 2024-02-29\nOlder")]
      (is (= ["Unreleased" "0.1.9" "0.1.2"]
             (mapv :version (:sections (notes/parse text)))))
      (is (= "Older" (:body (notes/select-notes text "0.1.2" false)))))))

(deftest stamp-validation-test
  ;; Stamping validates both the proposed heading and all existing entries.
  (doseq [[text version date]
          [[unreleased "0.1.01" "2026-10-23"]
           [unreleased "0.1.3" "2026-02-29"]
           [(str unreleased "## [0.1.2] - 2024-01-01\n ")
            "0.1.3" "2026-10-23"]]]
    (is (thrown? Exception (notes/stamp text version date)))))
