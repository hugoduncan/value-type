(ns value-type.core-test
  "Tests for `value-type.core`: `defvaluetype` and `check-value-class`."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [value-type.core :as vt :refer [defvaluetype]]
   [value-type.test-support :as support :refer [value? with-temp-dir]])
  (:import
   (clojure.lang Compiler ExceptionInfo IDeref RT)
   (java.util List)
   (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

;;; Helpers

(def ^:private test-ns (the-ns 'value-type.core-test))

(def ^:private eval-here (partial support/eval-in test-ns))

(defn- class-name
  "Binary name of the class `name` in the test namespace."
  [name]
  (support/binary-name test-ns name))

(defn- error-info
  "Call `f`. Return the first `ExceptionInfo` in the cause chain of the
  thrown exception, or nil."
  [f]
  (try (f) nil
       (catch Throwable t
         (some #(when (instance? ExceptionInfo %) %)
               (take-while some? (iterate ex-cause t))))))

(defn- error-data
  "Call `f`. Return the `ex-data` of the `error-info` exception, or nil."
  [f]
  (ex-data (error-info f)))

(defn- message-names?
  "True when the message of `e` contains each string in `parts`."
  [e parts]
  (let [m (str (ex-message e))]
    (every? #(str/includes? m %) parts)))

(def ^:private direct-fix "evaluate the defvaluetype form directly")

(deftype IdentityType [a])

;;; check-value-class

(deftest check-value-class-test
  (testing "an identity class"
    (is (= {:value-type/error :not-value-class
            :class-name       (.getName IdentityType)}
           (error-data #(vt/check-value-class IdentityType true)))
        "installed true gives :not-value-class")
    (is (= {:value-type/error :not-installed
            :class-name       (.getName IdentityType)}
           (error-data #(vt/check-value-class IdentityType false)))
        "installed false gives :not-installed"))
  (testing "a value class gives nil"
    (let [cls (eval-here `(support/vt-type ~(gensym "LoaderVT") [~'a]))]
      (is (value? cls))
      (is (nil? (vt/check-value-class cls true))))))

;;; defvaluetype basics

(defvaluetype Point [^long x y])

(deftest basic-test
  (testing "the result is a value class"
    (is (value? Point)))
  (testing "constructors give instances with the field values"
    (let [^Point p (->Point 1 :a)
          ^Point q (Point. 1 :a)]
      (is (= [1 :a] [(.x p) (.y p)]))
      (is (= [1 :a] [(.x q) (.y q)]))))
  (testing "same values are = and identical?"
    (is (= (->Point 1 :a) (->Point 1 :a)))
    (is (identical? (->Point 1 :a) (->Point 1 :a))))
  (testing "different values are not ="
    (is (not= (->Point 1 :a) (->Point 2 :a)))))

(defn- is-the-class
  "Assert that `cls` is a value class, and that it is the class `n` in
  the test namespace."
  [cls n]
  (is (value? cls) (str cls " is a value class"))
  (is (identical? (ns-resolve test-ns n) cls)
      (str cls " is the class " n)))

(deftest compile-path-test
  (testing "eval of one form"
    (let [n (gensym "EvalVT")]
      (is-the-class (eval-here `(defvaluetype ~n [~'a])) n)))
  (testing "load-string of a source string"
    (let [n (gensym "LoadVT")]
      (is-the-class (binding [*ns* test-ns]
                      (load-string (str "(value-type.core/defvaluetype "
                                        n " [a])")))
                    n)))
  (testing "a call inside a defn body"
    (let [f (gensym "make-defn-vt")
          n (gensym "DefnVT")]
      (eval-here `(defn ~f [] (defvaluetype ~n [~'a])))
      ;; the deftype import runs at the call, in *ns*
      (is-the-class (eval-here (list f)) n))))

;;; Specs

(defprotocol Area (area [this]))

(definterface Scaled (^long scaled [^long k]))

(defvaluetype Rect [^long w ^long h]
  Area
  (area [_] (* w h))
  Scaled
  (scaled [_ k] (* k w))
  Object
  (toString [_] (str "Rect " w "x" h))
  (equals [_ o] (and (instance? Rect o) (= w (.w ^Rect o))))
  (hashCode [_] (int w))
  IDeref
  (deref [this] (fn [] this)))

(deftest spec-test
  (let [r (->Rect 2 3)]
    (testing "protocol and interface methods"
      (is (= 6 (area r)))
      (is (= 10 (.scaled ^Rect r 5))))
    (testing "Object overrides are used"
      (is (= "Rect 2x3" (str r)))
      (is (.equals ^Object r (->Rect 2 9)) "equals compares only w")
      (is (= 2 (.hashCode ^Object r))))
    (testing "an inner fn that captures this"
      (is (identical? r ((deref r)))))))

;;; Fields

(defvaluetype Empty [])

(defvaluetype Typed [^long l ^double d ^String s])

(defvaluetype NotMutable [^{:volatile-mutable false} a])

(defn- field-type [^Class cls ^String field]
  (.getType (.getDeclaredField cls field)))

(deftest field-test
  (testing "zero fields"
    (is (value? Empty))
    (is (identical? (->Empty) (->Empty))))
  (testing "hinted fields have the deftype field types"
    (is (value? Typed))
    ;; as deftype: primitive hints give primitive fields, a class hint
    ;; gives an Object field
    (is (= [Long/TYPE Double/TYPE Object]
           (map #(field-type Typed %) ["l" "d" "s"]))))
  (testing ":volatile-mutable false is accepted"
    (is (value? NotMutable))))

;;; Options

(defvaluetype WithLoadNs [a] :load-ns true)

(deftest options-test
  (is (value? WithLoadNs)))

;;; Redefinition

(deftest redefinition-test
  (let [n    (gensym "Redef")
        c1   (eval-here `(defvaluetype ~n [~'a]))
        c2   (eval-here `(defvaluetype ~n [~'a ~'b]))
        ctor (ns-resolve test-ns (symbol (str "->" n)))]
    (is (value? c2))
    (is (not (identical? c1 c2)))
    (is (instance? ^Class c2 (ctor 1 2)))))

;;; Errors

(deftest mutable-field-test
  (testing "one :volatile-mutable field"
    (let [n (gensym "M1")
          e (error-info
             #(eval-here `(defvaluetype ~n
                            [~'a ~(with-meta 'mut-b {:volatile-mutable true})])))]
      (is (= {:value-type/error :mutable-field
              :class-name       (class-name n)
              :fields           '[mut-b]}
             (ex-data e)))
      (is (message-names? e [(class-name n) "mut-b"]) (ex-message e))))
  (testing "two :unsynchronized-mutable fields"
    (let [n (gensym "M2")
          e (error-info
             #(eval-here
               `(defvaluetype ~n
                  [~(with-meta 'mut-a {:unsynchronized-mutable true})
                   ~(with-meta 'mut-b {:unsynchronized-mutable true})])))]
      (is (= {:value-type/error :mutable-field
              :class-name       (class-name n)
              :fields           '[mut-a mut-b]}
             (ex-data e)))
      (is (message-names? e [(class-name n) "mut-a" "mut-b"]) (ex-message e))))
  (testing "LOADER is not changed"
    (let [l (RT/makeClassLoader)]
      (with-bindings {Compiler/LOADER l}
        (binding [*ns* test-ns]
          (is (= :mutable-field
                 (:value-type/error
                  (error-data
                   #(macroexpand-1
                     `(defvaluetype ~(gensym "M3")
                        [~(with-meta 'a {:volatile-mutable true})])))))))
        (is (identical? l @Compiler/LOADER))))))

(deftest reserved-field-test
  (let [cause (support/compile-cause
               #(eval-here `(defvaluetype ~(gensym "Reserved") [~'__hash])))]
    (is (instance? AssertionError cause) (str "cause: " cause))))

(deftest not-installed-test
  (let [n         (gensym "NoInstall")
        form      `(defvaluetype ~n [~'a])
        expansion (support/on-raw-thread
                   #(binding [*ns* test-ns] (macroexpand form)))]
    (is (seq? expansion) (str "expansion: " expansion))
    (let [e (error-info #(eval-here expansion))]
      (is (= {:value-type/error :not-installed
              :class-name       (class-name n)}
             (ex-data e)))
      (is (message-names? e [(class-name n) direct-fix]) (ex-message e)))))

(deftest not-value-class-test
  (let [n (gensym "MacroExpanded")
        e (error-info
           #(eval-here `(eval (macroexpand '(defvaluetype ~n [~'a])))))]
    (is (= {:value-type/error :not-value-class
            :class-name       (class-name n)}
           (ex-data e)))
    (is (message-names? e [(class-name n) direct-fix]) (ex-message e))))

;;; AOT

(defn- run-java
  "Run `java --enable-preview -cp <cp> clojure.main -e <form>`. Return
  `{:exit :out :err}`, or `{:exit ::timeout}`."
  [^String cp ^String form]
  (let [java (str (System/getProperty "java.home") "/bin/java")
        pb   (ProcessBuilder.
              ^List (vector java "--enable-preview" "-cp" cp
                               "clojure.main" "-e" form))
        p    (.start pb)
        out  (future (slurp (.getInputStream p)))
        err  (future (slurp (.getErrorStream p)))]
    (if (.waitFor p 120 TimeUnit/SECONDS)
      {:exit (.exitValue p) :out @out :err @err}
      (do (.destroyForcibly p) {:exit ::timeout :err (deref err 1000 "")}))))

(deftest aot-new-jvm-test
  (with-temp-dir [dir]
    (let [ns-sym 'value-type.aot-fixture
          n      (gensym "Pt")]
      (is (nil? (support/aot-compile
                 dir ns-sym
                 (str "(ns " ns-sym " (:require value-type.core))\n"
                      "(value-type.core/defvaluetype " n " [x])\n"))))
      (let [cp   (str dir (System/getProperty "path.separator")
                      (System/getProperty "java.class.path"))
            cls  (support/binary-name ns-sym n)
            form (str "(require '" ns-sym ")"
                      "(prn [(.isValue ^Class " cls ")"
                      " (.-x ^" cls " (" ns-sym "/->" n " 42))])")
            {:keys [exit out err]} (run-java cp form)]
        (is (= 0 exit) (str "stderr: " err))
        (is (= [true 42] (edn/read-string (last (str/split-lines (str out)))))
            (str "stderr: " err))))))
