(ns portable-fields.fixture
  (:require [portable-fields.experiment :refer [checked-identity]]))

(definterface Reads
  (snapshot [])
  (captured []))

(checked-identity Mixed [^long count ^String ^:non-nil label
                         ^double weight nullable]
                  Reads
                  (snapshot [_] [count label weight nullable])
                  (captured [this] (fn [] (.-label this))))
