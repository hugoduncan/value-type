;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; The forms and assertions below are copied from the Clojure 1.12.6
;; test suite (tag `clojure-1.12.6`, `test/clojure/`), with `deftype`
;; changed to `defvaluetype`. See task munera 005 for the change rules.

(ns value-type.deftype-compat-test
  "`deftype` compatibility corpus: the `deftype` forms of the Clojure
  1.12.6 test suite, as `defvaluetype`."
  (:require
   [clojure.test :refer [assert-expr deftest do-report is report testing]]
   [value-type.core :refer [defvaluetype]]
   [value-type.test-support :as support :refer [value? with-temp-dir]])
  (:import
   (java.lang.annotation Annotation Retention RetentionPolicy)))

;;; Supporting definitions

;; test_helper.clj: `causes`, `fails-with-cause?`, `thrown-with-cause-msg?`

(defn causes
  [^Throwable throwable]
  (loop [causes []
         t throwable]
    (if t (recur (conj causes t) (.getCause t)) causes)))

;; this is how I wish clojure.test/thrown? worked...
;; Does body throw expected exception, anywhere in the .getCause chain?
(defmethod assert-expr 'fails-with-cause?
  [msg [_ exception-class msg-re & body :as form]]
  `(try
     ~@body
     (report {:type :fail, :message ~msg, :expected '~form, :actual nil})
     (catch Throwable t#
       (if (some (fn [cause#]
                   (and
                    (= ~exception-class (class cause#))
                    (re-find ~msg-re (.getMessage cause#))))
                 (causes t#))
         (report {:type :pass, :message ~msg,
                  :expected '~form, :actual t#})
         (report {:type :fail, :message ~msg,
                  :expected '~form, :actual t#})))))

(defmethod clojure.test/assert-expr 'thrown-with-cause-msg? [msg form]
  ;; (is (thrown-with-cause-msg? c re expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the message string of the *cause* exception matches
  ;; (with re-find) the regular expression re.
  (let [klass (nth form 1)
        re (nth form 2)
        body (nthnext form 3)]
    `(try ~@body
          (do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
          (catch ~klass e#
            (let [m# (if (.getCause e#) (.. e# getCause getMessage) (.getMessage e#))]
              (if (re-find ~re m#)
                (do-report {:type :pass, :message ~msg,
                            :expected '~form, :actual e#})
                (do-report {:type :fail, :message ~msg,
                            :expected '~form, :actual e#})))
            e#))))

;; protocols/examples.clj

(defprotocol ExampleProtocol
  "example protocol used by clojure tests"

  (foo [a] "method with one arg")
  (bar [a b] "method with two args")
  (^String baz [a] [a b] "method with multiple arities")
  (with-quux [a] "method name with a hyphen"))

(defprotocol MarkerProtocol
  "a protocol with no methods")

(defprotocol MarkerProtocol2)

;;; Markers
;; protocols.clj: marker-tests

;; Upstream deliberately implements only part of ExampleProtocol.
#_{:clj-kondo/ignore [:missing-protocol-method]}
(defvaluetype HasMarkers []
  ExampleProtocol
  ;; clj-kondo associates this method with the following marker protocol.
  #_{:clj-kondo/ignore [:unresolved-protocol-method]}
  (foo [_this] "foo")
  MarkerProtocol
  MarkerProtocol2)

;; Upstream deliberately implements only part of ExampleProtocol.
#_{:clj-kondo/ignore [:missing-protocol-method]}
(defvaluetype WillGetMarker []
  ExampleProtocol
  (foo [_this] "foo"))

(extend-type WillGetMarker MarkerProtocol)

(deftest marker-tests
  (testing "That types with markers are reportedly satifying them."
    (let [hm (HasMarkers.)
          wgm (WillGetMarker.)]
      (is (satisfies? MarkerProtocol hm))
      (is (satisfies? MarkerProtocol2 hm))
      (is (satisfies? MarkerProtocol wgm))))
  (is (value? HasMarkers))
  (is (value? WillGetMarker)))

;;; Extend and Illegal extend
;; protocols.clj: extend-test, illegal-extending

(defvaluetype ExtendTestWidget [name])
;; Upstream deliberately implements only part of ExampleProtocol.
#_{:clj-kondo/ignore [:missing-protocol-method]}
(defvaluetype HasProtocolInline []
  ExampleProtocol
  (foo [_this] :inline))
(deftest extend-test
  (testing "you can extend deftype types"
    (extend
     ExtendTestWidget
      ExampleProtocol
      {:foo (fn [this] (str "widget " (.name this)))})
    (is (= "widget z" (foo (ExtendTestWidget. "z")))))
  (is (value? ExtendTestWidget)))

(deftest illegal-extending
  (testing "you cannot extend a protocol to a type that implements the protocol inline"
    (is (fails-with-cause? IllegalArgumentException #".*HasProtocolInline already directly implements interface"
                           (eval '(extend value_type.deftype_compat_test.HasProtocolInline
                                    value-type.deftype-compat-test/ExampleProtocol
                                    {:foo (fn [_] :extended)})))))
  (is (value? HasProtocolInline)))

;;; Disabled upstream
;; protocols.clj: extends?-test, extenders-test, satisifies?-test,
;; re-extending-test (all disabled with #_ in Clojure)

;; Upstream deliberately implements only part of ExampleProtocol.
#_{:clj-kondo/ignore [:missing-protocol-method]}
(defvaluetype ExtendsTestWidget []
  ExampleProtocol)

(defvaluetype ExtendersTestWidget [])

;; Upstream deliberately implements only part of ExampleProtocol.
#_{:clj-kondo/ignore [:missing-protocol-method]}
(defvaluetype SatisfiesTestWidget []
  ExampleProtocol)

(defvaluetype ReExtendingTestWidget [])

(deftest disabled-upstream-test
  (is (value? ExtendsTestWidget))
  (is (value? ExtendersTestWidget))
  (is (value? SatisfiesTestWidget))
  (is (value? ReExtendingTestWidget)))

;;; Factory and literals
;; protocols.clj: deftype-factory-fn, test-ctor-literals, exercise-literals

(defvaluetype TypeToTestFactory [a])

(deftest deftype-factory-fn
  (testing "that the ->T factory is gen'd for a deftype and that it works"
    (is (= (.a (TypeToTestFactory. 42)) (.a (->TypeToTestFactory 42)))))
  (is (value? TypeToTestFactory)))

(deftest test-ctor-literals
  (testing "that constructor literals work for deftypes too"
    (is (= (.a (TypeToTestFactory. 42)) (.a #value_type.deftype_compat_test.TypeToTestFactory[42])))))

(defvaluetype TypeToTestLiterals [a])
(deftest exercise-literals
  (testing "that ctor literals can be used in common 'places'"
    (is (= (.a (TypeToTestLiterals. ())) (.a #value_type.deftype_compat_test.TypeToTestLiterals[()]))))
  (testing "that records and types are evalable"
    (is (= 42 (.a (eval #value_type.deftype_compat_test.TypeToTestLiterals[42])))))
  (is (value? TypeToTestLiterals)))

;;; Hints
;; protocols.clj: hinting-test

(defvaluetype TypeToTestLongHint [^long a])
(defvaluetype TypeToTestByteHint [^byte a])

(deftest hinting-test
  (testing "that primitive hinting requiring no coercion works as expected"
    (is (= (.a (TypeToTestLongHint. 42)) (.a (->TypeToTestLongHint (long 42)))))
    (testing "that invalid primitive types on hinted defrecord fields fails"
      (is (thrown?
           IllegalArgumentException
           (read-string "#value_type.deftype_compat_test.TypeToTestLongHint[\"\"]")))))
  (testing "that primitive hinting requiring coercion works as expected"
    (is (= (.a (TypeToTestByteHint. 42)) (.a (->TypeToTestByteHint (byte 42))))))
  (is (value? TypeToTestLongHint))
  (is (value? TypeToTestByteHint)))

;;; Leading dashes
;; protocols.clj: test-leading-dashes

(defprotocol Dasherizer
  (-do-dashed [this]))
(defvaluetype Dashed []
  Dasherizer
  (-do-dashed [_this] 10))

(deftest test-leading-dashes
  (is (= 10 (-do-dashed (Dashed.))))
  (is (= [10] (map -do-dashed [(Dashed.)])))
  (is (value? Dashed)))

;;; Hash collisions
;; protocols/hash_collisions.clj: protocols-with-hash-collisions,
;; protocols-with-no-min-hash-in-13-bits

(defprotocol TestProtocolA
  (method-a [this] "Test method A"))

(defprotocol TestProtocolB
  (method-b [this] "Test method B"))

(defvaluetype TestType1 [])
(defvaluetype TestType2 [])
(defvaluetype TestType3 [])
(defvaluetype TestType4 [])
(defvaluetype TestType5 [])
(defvaluetype TestType6 [])
(defvaluetype TestType7 [])
(defvaluetype TestType8 [])
(defvaluetype TestType9 [])
(defvaluetype TestType10 [])
(defvaluetype TestType11 [])
(defvaluetype TestType12 [])
(defvaluetype TestType13 [])
(defvaluetype TestType14 [])
(defvaluetype TestType15 [])

(def original-hash hash)

(defn colliding-hash
  "Mock hash function which returns identical hash codes for the
  classes TestType1 and TestType2, normal hashes for everything else."
  [x]
  (if (or (= x TestType1) (= x TestType2))
    999 ;; artificial hash code, to cause a collision
    (original-hash x)))

(deftest protocols-with-hash-collisions
  (with-redefs [hash colliding-hash]
    (extend TestType1 TestProtocolA {:method-a (constantly 1)})
    (extend TestType2 TestProtocolA {:method-a (constantly 2)})
    (is (= 1 (method-a (TestType1.))))
    (is (= 2 (method-a (TestType2.))))))

(defn no-min-hash-in-13-bits
  "Mock hash function which returns hash codes for the classes
  TestType1 through TestType15 such that they cannot be compressed
  into a 13-bit min-hash table. Returns normal hashes for everything
  else."
  [x]
  (cond
    (= x TestType1) 1
    (= x TestType2) 2
    (= x TestType3) 4
    (= x TestType4) 8
    (= x TestType5) 16
    (= x TestType6) 32
    (= x TestType7) 64
    (= x TestType8) 128
    (= x TestType9) 256
    (= x TestType10) 512
    (= x TestType11) 1024
    (= x TestType12) 2048
    (= x TestType13) 4096
    (= x TestType14) 8192
    (= x TestType15) 16384
    :else (original-hash x)))

(deftest protocols-with-no-min-hash-in-13-bits
  (with-redefs [hash no-min-hash-in-13-bits]
    (extend TestType1 TestProtocolB {:method-b (constantly 1)})
    (extend TestType2 TestProtocolB {:method-b (constantly 2)})
    (extend TestType3 TestProtocolB {:method-b (constantly 3)})
    (extend TestType4 TestProtocolB {:method-b (constantly 4)})
    (extend TestType5 TestProtocolB {:method-b (constantly 5)})
    (extend TestType6 TestProtocolB {:method-b (constantly 6)})
    (extend TestType7 TestProtocolB {:method-b (constantly 7)})
    (extend TestType8 TestProtocolB {:method-b (constantly 8)})
    (extend TestType9 TestProtocolB {:method-b (constantly 9)})
    (extend TestType10 TestProtocolB {:method-b (constantly 10)})
    (extend TestType11 TestProtocolB {:method-b (constantly 11)})
    (extend TestType12 TestProtocolB {:method-b (constantly 12)})
    (extend TestType13 TestProtocolB {:method-b (constantly 13)})
    (extend TestType14 TestProtocolB {:method-b (constantly 14)})
    (extend TestType15 TestProtocolB {:method-b (constantly 15)})
    (is (= 1 (method-b (TestType1.))))
    (is (= 2 (method-b (TestType2.))))
    (is (= 3 (method-b (TestType3.))))
    (is (= 4 (method-b (TestType4.))))
    (is (= 5 (method-b (TestType5.))))
    (is (= 6 (method-b (TestType6.))))
    (is (= 7 (method-b (TestType7.))))
    (is (= 8 (method-b (TestType8.))))
    (is (= 9 (method-b (TestType9.))))
    (is (= 10 (method-b (TestType10.))))
    (is (= 11 (method-b (TestType11.))))
    (is (= 12 (method-b (TestType12.))))
    (is (= 13 (method-b (TestType13.))))
    (is (= 14 (method-b (TestType14.))))
    (is (= 15 (method-b (TestType15.))))))

(deftest hash-collision-value-classes-test
  (is (value? TestType1))
  (is (value? TestType2))
  (is (value? TestType3))
  (is (value? TestType4))
  (is (value? TestType5))
  (is (value? TestType6))
  (is (value? TestType7))
  (is (value? TestType8))
  (is (value? TestType9))
  (is (value? TestType10))
  (is (value? TestType11))
  (is (value? TestType12))
  (is (value? TestType13))
  (is (value? TestType14))
  (is (value? TestType15)))

;;; Name ambiguity
;; java_interop.clj: test-reflective-field-name-ambiguous

(definterface I (a []))
(defvaluetype T [a] I (a [_] "method"))

(deftest test-reflective-field-name-ambiguous
  (let [t (->T "field")]
    (is (= "method" (. ^T t a)))
    (is (= "field" (. ^T t -a)))
    (is (= "method" (. t a)))
    (is (= "field" (. t -a)))
    (is (thrown? IllegalArgumentException (. t -BOGUS))))
  (is (value? T)))

;;; Embedded instance
;; compilation.clj: clj-1399

(defvaluetype CLJ1399 [munged-field-name])

(deftest clj-1399
  ;; throws an exception on failure
  (is (eval `(fn [] ~(CLJ1399. 1))))
  (is (value? CLJ1399)))

;;; Errors
;; ns_libs.clj: naming-types, test-defrecord-deftype-err-msg
;; array_symbols.clj: test-array-symbols

(deftest naming-types
  (testing "you cannot use a name already referred from another namespace"
    (is (thrown-with-msg? IllegalStateException
                          #"StringBuffer already refers to: class java.lang.StringBuffer"
                          (defvaluetype StringBuffer [])))))

(deftest test-defrecord-deftype-err-msg
  (is (thrown-with-cause-msg? clojure.lang.Compiler$CompilerException
                              #"defrecord and deftype fields must be symbols, value-type\.deftype-compat-test\.MyType had: :key1"
                              (support/eval-in 'value-type.deftype-compat-test
                                               '(defvaluetype MyType [:key1])))))

(deftest test-array-symbols
  (testing "error conditions"
    (is (thrown? Exception
                 (support/eval-in 'value-type.deftype-compat-test
                                  '(defvaluetype Foo/2 [a]))))
    (is (thrown-with-cause-msg? Exception #"IllegalName: .*Foo/2"
                                (support/eval-in 'value-type.deftype-compat-test
                                                 '(defvaluetype Foo/2 [a]))))))

;;; Annotations
;; annotations/java_8.clj, annotations/java_9.clj:
;; test-annotations-on-type, test-annotations-on-field,
;; test-annotations-on-method.
;; One `Bar` stands for the java_8.clj and java_9.clj forms (the same
;; form). The javax.xml.ws annotation types are not in JDK 11 and later,
;; so they are removed from the form and from `expected-annotations`.

(definterface Foo (foo []))

(defvaluetype #^{Deprecated true
                 Retention RetentionPolicy/RUNTIME
                 javax.annotation.processing.SupportedOptions ["foo" "bar" "baz"]}
                Bar [#^int a
                     #^{:tag int
                        Deprecated true
                        Retention RetentionPolicy/RUNTIME
                        javax.annotation.processing.SupportedOptions ["foo" "bar" "baz"]}
                       b]
  Foo (#^{Deprecated true
          Retention RetentionPolicy/RUNTIME
          javax.annotation.processing.SupportedOptions ["foo" "bar" "baz"]}
         foo [_this] 42))

(defn annotation->map
  "Converts a Java annotation (which conceals data)
   into a map (which makes is usable). Not lazy.
   Works recursively. Returns non-annotations unscathed."
  [#^java.lang.annotation.Annotation o]
  (cond
    (instance? Annotation o)
    (let [type (.annotationType o)
          itfs (-> (into #{type} (supers type)) (disj java.lang.annotation.Annotation))
          data-methods (into #{} (mapcat #(.getDeclaredMethods %) itfs))]
      (into
       {:annotationType (.annotationType o)}
       (map
        (fn [m] [(keyword (.getName m)) (annotation->map (.invoke m o nil))])
        data-methods)))
    (or (sequential? o) (.isArray (class o)))
    (map annotation->map o)
    :else o))

(def expected-annotations
  #{{:annotationType java.lang.annotation.Retention, :value RetentionPolicy/RUNTIME}
    {:annotationType javax.annotation.processing.SupportedOptions, :value ["foo" "bar" "baz"]}
    {:annotationType java.lang.Deprecated, :forRemoval false, :since ""}})

(deftest test-annotations-on-type
  (is (=
       expected-annotations
       (into #{} (map annotation->map (.getAnnotations Bar)))))
  (is (value? Bar)))

(deftest test-annotations-on-field
  (is (=
       expected-annotations
       (into #{} (map annotation->map (.getAnnotations (.getField Bar "b")))))))

(deftest test-annotations-on-method
  (is (=
       expected-annotations
       (into #{} (map annotation->map (.getAnnotations (.getMethod Bar "foo" nil)))))))

;;; AOT in a new JVM
;; compilation.clj: CLJ-979, clj-1208
;; compilation/examples.clj (X, T), compilation/load_ns.clj (x)

(def ^:private aot-ns 'value-type.deftype-compat-aot)

(def ^:private aot-source
  (str "(ns " aot-ns " (:require [value-type.core :refer [defvaluetype]]))\n"
       ;; compilation/examples.clj
       "(eval '(defvaluetype X []))\n"
       "(defvaluetype T [])\n"
       ;; compilation/load_ns.clj
       "(defn a [] 1)\n"
       "(defprotocol p (f [_]))\n"
       "(defvaluetype x []\n"
       "  :load-ns true\n"
       "  p (f [_] (a)))\n"))

(defn- value-form
  "Form that tests in the new JVM that `cls` is a value class."
  [cls]
  (str "(.isValue ^Class " cls ")"))

(deftest aot-clj-979-clj-1208-test
  (with-temp-dir [dir]
    (is (nil? (support/aot-compile dir aot-ns aot-source)))
    (let [x-cls (support/binary-name aot-ns 'x)
          X-cls (support/binary-name aot-ns 'X)
          T-cls (support/binary-name aot-ns 'T)
          form  (str
                 ;; 1. the namespace is not loaded
                 "(def r1 {:ns-unloaded-before (nil? (find-ns '" aot-ns "))})\n"
                 ;; 2. clj-1208
                 "(def r2 {:clj-1208-f (.f (" x-cls ".))})\n"
                 ;; 3. the namespace is loaded; x is a value class
                 "(def r3 {:ns-loaded-after (some? (find-ns '" aot-ns "))"
                 " :x-value " (value-form x-cls) "})\n"
                 ;; 4. CLJ-979
                 "(def r4 {:X-value " (value-form X-cls)
                 " :T-value " (value-form T-cls)
                 " :X-ctor-class (= " X-cls " (class (" aot-ns "/->X)))"
                 " :T-ctor-class (= " T-cls " (class (" T-cls ".)) (class (" aot-ns "/->T)))})\n"
                 "(prn (merge r1 r2 r3 r4))")
          {:keys [exit result err]} (support/run-in-new-jvm dir form)]
      (is (= 0 exit) (str "stderr: " err))
      (is (= {:ns-unloaded-before true
              :clj-1208-f         1
              :ns-loaded-after    true
              :x-value            true
              :X-value            true
              :T-value            true
              :X-ctor-class       true
              :T-ctor-class       true}
             result)
          (str "stderr: " err)))))
