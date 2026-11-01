(ns value-type.label-record-test
  "In-process test of label-record/summary, the measurement of the
   child-count bound, the label mapping and the namespace time. It reads a
   hand-made record file; no child JVM starts."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [value-type.label-record :as record]
            [value-type.temp-dir :as temp]))

(def ^:private record-data
  "Two test Vars in fixture.one, one in fixture.two.
   t1: two :cell starts, one configuration; the first start has nested
   assertion labels, the second has only its own label.
   t2: one :producer and two :cell starts, two configurations."
  (let [t1 'fixture.one/t1
        t2 'fixture.one/t2
        t3 'fixture.two/t3]
    {:vars [{:var t1 :ms 10} {:var t2 :ms 5} {:var t3 :ms 7}]
     :assertions [{:label [t1 ["a" "x"]] :type :pass}
                  {:label [t1 ["a" "y"]] :type :pass}
                  {:label [t1 ["a"]] :type :pass}
                  {:label [t2 ["producer"]] :type :pass}
                  {:label [t2 ["b"]] :type :fail}
                  {:label [t3 []] :type :pass}]
     :children [{:label [t1 ["a"]] :kind :cell :config 1 :assertion-index 0}
                {:label [t1 ["a"]] :kind :cell :config 1 :assertion-index 2}
                {:label [t2 ["producer"]] :kind :producer :config 3 :assertion-index 3}
                {:label [t2 ["b"]] :kind :cell :config 4 :assertion-index 4}
                {:label [t2 ["b"]] :kind :cell :config 5 :assertion-index 4}]
     :cells [{:label [t1 ["a" "x"]]}
             {:label [t1 ["a" "x"]]}
             {:label [t2 ["b"]]}]}))

(defn- summary [method]
  (temp/with-temp-dir
    "vt-label-record-"
    (fn [dir]
      (let [f (io/file dir "record.edn")]
        (spit f (pr-str record-data))
        (record/summary (str f) method)))))

(deftest summary-test
  (let [s (summary :cells)]
    (testing "namespaces"
      (is (= ['fixture.one 'fixture.two] (keys s))))
    (testing "children for each [test kind]"
      (is (= {['fixture.one/t1 :cell] {:count 2 :configurations 1}
              ['fixture.one/t2 :producer] {:count 1 :configurations 1}
              ['fixture.one/t2 :cell] {:count 2 :configurations 2}}
             (get-in s ['fixture.one :children])))
      (is (= {} (get-in s ['fixture.two :children]))))
    (testing "namespace time is the sum of its Var times"
      (is (= [15 7] (mapv :ms (vals s)))))
    (testing "cells method"
      (is (= {['fixture.one/t1 ["a" "x"]] 2
              ['fixture.one/t1 ["a" "y"]] 0
              ['fixture.one/t1 ["a"]] 0
              ['fixture.one/t2 ["producer"]] 0
              ['fixture.one/t2 ["b"]] 1}
             (get-in s ['fixture.one :labels])))
      (is (= {['fixture.two/t3 []] 0} (get-in s ['fixture.two :labels])))))
  (testing "start-labels method"
    ;; A start gives its cell to each nested non-start label with assertion
    ;; events before the next start, else to the start label.
    (is (= {['fixture.one/t1 ["a" "x"]] 1
            ['fixture.one/t1 ["a" "y"]] 1
            ['fixture.one/t1 ["a"]] 1
            ['fixture.one/t2 ["producer"]] 0
            ['fixture.one/t2 ["b"]] 2}
           (get-in (summary :start-labels) ['fixture.one :labels])))))
