(ns isolated-evidence
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn observation
  "Validate retained process evidence and record a fatal outcome separately."
  [root input query]
  (let [dir (io/file root (str input "-" query))
        read-file #(slurp (io/file dir %))
        exit (edn/read-string (read-file "run.exit"))
        id (keyword "diagnostic" (str input "-" query))
        result-file (io/file dir "result.edn")
        log (read-file "run.log")
        environment (:environment (edn/read-string (read-file "allocation.edn")))
        common {:id id :operation :array-diagnostic
                :inputs {:input (keyword input) :query query}
                :exit exit :environment environment
                :command (str/trim (read-file "run.command"))}]
    (assert (seq environment) (str id " missing environment"))
    (assert (str/includes? log (str ":invoking :" input " " query))
            (str id " did not reach query"))
    (if (= "null" input)
      (let [fatal (read-file "hs_err.log")]
        (assert (= 134 exit) (str id " unexpected exit " exit))
        (assert (not (.exists result-file)) (str id " stale result"))
        (assert (str/includes? fatal "SIGSEGV") (str id " missing signal"))
        (assoc common :status :fatal :signal :SIGSEGV
               :fatal-log (str input "-" query "/hs_err.log")))
      (let [cases (:cases (edn/read-string (slurp result-file)))
            result (first cases)]
        (assert (= 0 exit) (str id " unexpected exit " exit))
        (assert (= 1 (count cases)) (str id " incorrect coverage"))
        (assert (= id (:id result)) (str id " incorrect case"))
        (assert (and (= :pass (:status result)) (false? (:value result)))
                (str id " unexpected query result"))
        (assoc common :status :pass :value false)))))

(defn record
  "Derive portable isolated records from raw process evidence."
  [root]
  {:schema-version 1 :phase :isolated-diagnostics
   :cases (into [] (for [input ["null" "non-array"]
                         query ["isFlatArray" "isNullRestrictedArray" "isAtomicArray"]]
                     (observation root input query)))})
