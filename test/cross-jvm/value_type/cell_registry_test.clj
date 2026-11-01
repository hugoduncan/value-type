(ns value-type.cell-registry-test
  "In-process tests of the false results of the cell-registry isolation
   checks. Each test defines types in a fresh namespace of the test JVM."
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.cell-registry :as registry]))

(defn- fresh-ns
  "Return a new namespace with clojure.core referred."
  []
  (let [n (create-ns (gensym "value-type.cell-registry-test.ns"))]
    (binding [*ns* n] (refer 'clojure.core))
    n))

(defn- define
  "Evaluate form in namespace n; return its value."
  [n form]
  (binding [*ns* n] (eval form)))

(deftest new-class-test
  (let [c (define (fresh-ns) '(deftype Sample [x]))]
    (is (true? (registry/new-class! c)))
    (testing "class of an earlier call"
      (is (false? (registry/new-class! c))))
    (testing "not a class"
      (is (false? (registry/new-class! 'Sample)))
      (is (false? (registry/new-class! nil))))))

(deftest absent-test
  (testing "nothing defined"
    (is (true? (registry/absent? (fresh-ns) 'Sample))))
  (testing "import"
    (let [n (fresh-ns)]
      (.importClass ^clojure.lang.Namespace n 'Sample String)
      (is (false? (registry/absent? n 'Sample "no.such.Sample")))))
  (testing "factory"
    (let [n (fresh-ns)]
      (define n '(defn ->Sample [] nil))
      (is (false? (registry/absent? n 'Sample "no.such.Sample")))))
  (testing "class name"
    (is (false? (registry/absent? (fresh-ns) 'Sample "java.lang.String"))))
  (testing "default class name of a namespace name with -"
    ;; A cell binds LOADER; RT/baseLoader then finds eval-defined classes.
    (with-bindings {clojure.lang.Compiler/LOADER (clojure.lang.DynamicClassLoader.)}
      (let [n (fresh-ns)]
        (define n '(deftype Sample [x]))
        (ns-unmap n 'Sample)
        (ns-unmap n '->Sample)
        (is (false? (registry/absent? n 'Sample)))
        (testing "unmunged name"
          (is (true? (registry/absent? n 'Sample (str (ns-name n) ".Sample")))))))))

(deftest defined-test
  (testing "factory of the class"
    (let [n (fresh-ns)
          c (define n '(deftype Sample [x]))]
      (is (= {:new-class? true :factory? true} (registry/defined n 'Sample c 1)))))
  (testing "factory gives an instance of another class"
    (let [n (fresh-ns)
          c (define n '(deftype Sample [x]))]
      (define n '(defrecord Other [x]))
      (define n '(defn ->Sample [x] (->Other x)))
      (is (= {:new-class? true :factory? false} (registry/defined n 'Sample c 1)))))
  (testing "import gives another class"
    (let [n (fresh-ns)
          c (define n '(deftype Sample [x]))
          factory @(ns-resolve n '->Sample)]
      (define n '(deftype Sample [x]))
      ;; Keep the factory of the first class, so only the import differs.
      (intern n '->Sample factory)
      (is (not (identical? c (ns-resolve n 'Sample))))
      (is (identical? c (class (factory 1))))
      (is (= {:new-class? true :factory? false} (registry/defined n 'Sample c 1))))))
