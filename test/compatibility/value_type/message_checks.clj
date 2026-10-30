(ns value-type.message-checks
  "Error message assertions. Error text is not contractual: tests assert
   only the parts that a message must name."
  (:require [clojure.string :as str]))

(defn message-names?
  "True when the message of `x` (a Throwable or a string) contains each
   string in `parts`."
  [x parts]
  (let [m (str (if (instance? Throwable x) (ex-message x) x))]
    (every? #(str/includes? m %) parts)))
