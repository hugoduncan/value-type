(ns value-type.impl.removed-field-test
  "Tests for the removed-field access check of record-shaped value output
  (`value-type.impl.value-backend`, `:record` shape)."
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.core :as vt]
   [value-type.impl.capability :as capability]
   [value-type.impl.loader :as loader]
   [value-type.test-support :as support
    :refer [binary-name record-tail with-temp-dir]])
  (:import
   (clojure.lang DynamicClassLoader)
   (java.io File)
   (java.lang.classfile ClassBuilder ClassFile ClassModel ClassTransform
                        CodeBuilder FieldModel)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.nio.file Files)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private test-ns (the-ns 'value-type.impl.removed-field-test))

(def ^:private enabled-a
  "A non-nil `String` field `a`: it gives a non-empty storage."
  (with-meta 'a {:tag 'String :non-nil true}))

(defn- record-form
  "A record-shaped `defvaluetype*` form for `n` in the namespace `ns`
  (default `test-ns`) whose first field is `a` (default untagged) and
  whose `hasheq` body is `body`, built by `head` (`vt/defvaluetype*` or
  `deftype*`)."
  ([head n body] (record-form test-ns head n body))
  ([ns head n body] (record-form ns head n 'a body))
  ([ns head n a body]
   (list head n (symbol (binary-name ns n)) (into [a] record-tail)
         :implements ['clojure.lang.IHashEq]
         (list 'hasheq '[this] body))))

(defn- class-file
  "The class file of the class `n` of the namespace `ns` under `dir`."
  ^File [^File dir ns n]
  (File. dir (str (.replace ^String (binary-name ns n) \. \/) ".class")))

(defn- removed-access [n field]
  {:value-type/error :invalid-field
   :class-name (binary-name test-ns n)
   :field field
   :reason :removed-field-access})

(defn- eval-here [form] (support/eval-in test-ns form))

(defn- field-names [^bytes bs]
  (set (map #(.stringValue (.fieldName ^FieldModel %))
            (.fields ^ClassModel (.parse (ClassFile/of) bs)))))

(def ^:private value-output? #(:available? (capability/capability)))

;;; Compiler path

(deftest owner-access-test
  (when (value-output?)
    (testing "an owner method that reads __hash"
      (let [n (gensym "Owner")]
        (is (= (removed-access n '__hash)
               (support/thrown-data #(eval-here (record-form `vt/defvaluetype* n '__hash)))))))
    (testing "an owner method that reads __hasheq"
      (let [n (gensym "Owner")]
        (is (= (removed-access n '__hasheq)
               (support/thrown-data #(eval-here (record-form `vt/defvaluetype* n '__hasheq)))))))
    (testing "both fields give __hash"
      (let [n (gensym "Owner")]
        (is (= (removed-access n '__hash)
               (support/thrown-data #(eval-here (record-form `vt/defvaluetype* n
                                                             '(+ __hasheq __hash))))))))
    (testing "no access is accepted"
      (let [n (gensym "Owner")]
        (is (class? (eval-here (record-form `vt/defvaluetype* n 0))))))
    (testing "an owner method that reads __hash, with an enabled field"
      (let [n (gensym "Owner")]
        (is (= (removed-access n '__hash)
               (support/thrown-data
                #(eval-here (record-form test-ns `vt/defvaluetype* n enabled-a
                                         '__hash)))))))))

(defn- companion-body
  "A `hasheq` body that calls a closure that reads `__hasheq` of the
  target `n`."
  [n]
  (list (list 'fn [] (list '.-__hasheq (with-meta 'this {:tag n})))))

(deftest companion-access-test
  (when (value-output?)
    (testing "a fn class that reads __hasheq of the target"
      (let [n (gensym "Companion")]
        (is (= (removed-access n '__hasheq)
               (support/thrown-data
                #(eval-here (record-form `vt/defvaluetype* n (companion-body n))))))))
    (testing "a fn class that reads __hasheq, with an enabled field"
      (let [n (gensym "Companion")]
        (is (= (removed-access n '__hasheq)
               (support/thrown-data
                #(eval-here (record-form test-ns `vt/defvaluetype* n enabled-a
                                         (companion-body n))))))))))

(deftest aot-removed-field-test
  (when (value-output?)
    (testing "AOT: the error, and the compiler file holds untransformed bytes"
      (with-temp-dir [dir]
        (let [ns-sym 'probe.removed-field
              n (gensym "Aot")
              form (record-form ns-sym `vt/defvaluetype* n '__hash)
              e (support/aot-compile
                 dir ns-sym
                 (str "(ns " ns-sym ")\n"
                      (binding [*print-meta* true] (pr-str form))))
              f (class-file dir ns-sym n)]
          (is (= {:value-type/error :invalid-field
                  :class-name (binary-name ns-sym n)
                  :field '__hash
                  :reason :removed-field-access}
                 (ex-data e)))
          (is (.isFile f))
          (when (.isFile f)
            (is (every? (field-names (Files/readAllBytes (.toPath f)))
                        ["__hash" "__hasheq"]))))))))

;;; Closed shape

(defn- closed-form
  "As `record-form` with `vt/defvaluetype*`, with `:value-type/closed true`."
  [n body]
  (let [[head n' cname fields & more] (record-form `vt/defvaluetype* n body)]
    (list* head n' cname fields :value-type/closed true more)))

(deftest closed-owner-access-test
  (when (value-output?)
    (doseq [[body field] [['__meta '__meta]
                          ['__extmap '__extmap]
                          ['__hash '__hash]
                          ['(do __hasheq __extmap) '__extmap]
                          ['(do __hash __extmap __meta) '__meta]]]
      (testing (str "an owner body " body " gives " field)
        (let [n (gensym "Closed")]
          (is (= (removed-access n field)
                 (support/thrown-data #(eval-here (closed-form n body))))))))
    (testing "a fn class that reads __meta of the target"
      (let [n (gensym "Closed")]
        (is (= (removed-access n '__meta)
               (support/thrown-data
                #(eval-here
                  (closed-form n (list (list 'fn []
                                             (list '.-__meta
                                                   (with-meta 'this {:tag n})))))))))))
    (testing "no access is accepted; the class has only the declared field"
      (let [n (gensym "Closed")
            ^Class c (eval-here (closed-form n 0))]
        (is (= ["a"] (map #(.getName ^java.lang.reflect.Field %)
                          (remove #(java.lang.reflect.Modifier/isStatic
                                    (.getModifiers ^java.lang.reflect.Field %))
                                  (.getDeclaredFields c)))))))))

;;; Companion bytes

(defn- companion-bytes
  "Bytes of a class `name` with one static method `f(Object)V` that
  casts its argument to `owner` and runs `access!` on it, given the
  `CodeBuilder` and the owner `ClassDesc`."
  ^bytes [^String name ^String owner access!]
  (let [owner-desc (ClassDesc/of owner)]
    (.build (ClassFile/of) (ClassDesc/of name)
            (reify Consumer
              (accept [_ cb]
                (.withMethodBody
                 ^ClassBuilder cb "f" (MethodTypeDesc/ofDescriptor "(Ljava/lang/Object;)V")
                 (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_STATIC))
                 (reify Consumer
                   (accept [_ c]
                     (let [^CodeBuilder c c]
                       (.aload c 0)
                       (.checkcast c owner-desc)
                       (access! c owner-desc)
                       (.return_ c))))))))))

(defn- define-companion
  "Define companion bytes `bs` named `cname$fn` through a record-shaped
  value capture loader for `cname`."
  [^String cname ^bytes bs]
  (let [^DynamicClassLoader cl (#'loader/capture-loader
                                cname (DynamicClassLoader.) [] :value :record)]
    (.defineClass cl (str cname "$fn") bs nil)))

(deftest companion-bytes-test
  (when (value-output?)
    (doseq [field ["__hash" "__hasheq"]]
      (testing (str "a companion putfield of " field " of the target")
        (let [n (gensym "Put")
              cname (binary-name test-ns n)
              bs (companion-bytes (str cname "$fn") cname
                                  (fn [^CodeBuilder c ^ClassDesc owner]
                                    (.iconst_0 c)
                                    (.putfield c owner ^String field
                                               ConstantDescs/CD_int)))]
          (is (= (removed-access n (symbol field))
                 (support/thrown-data #(define-companion cname bs)))))))
    (testing "a companion getfield of __hash on another class is accepted"
      (let [n (gensym "Other")
            cname (binary-name test-ns n)
            bs (companion-bytes (str cname "$fn") "vt.built.Other"
                                (fn [^CodeBuilder c ^ClassDesc owner]
                                  (.getfield c owner "__hash" ConstantDescs/CD_int)
                                  (.pop c)))]
        (is (class? (define-companion cname bs)))))))

;;; Error order

(defn- with-extra-constructor
  "Return `bs` with one extra `(I)V` constructor."
  ^bytes [^bytes bs]
  (let [cf (ClassFile/of)]
    (.transformClass
     cf (.parse cf bs)
     (.andThen ClassTransform/ACCEPT_ALL
               (ClassTransform/endHandler
                (reify Consumer
                  (accept [_ cb]
                    (.withMethodBody
                     ^ClassBuilder cb "<init>" (MethodTypeDesc/ofDescriptor "(I)V")
                     (int ClassFile/ACC_PUBLIC)
                     (reify Consumer
                       (accept [_ c]
                         (let [^CodeBuilder c c]
                           (.aload c 0)
                           (.invokespecial c ConstantDescs/CD_Object "<init>"
                                           ConstantDescs/MTD_void)
                           (.return_ c))))))))))))

(defn- record-bytes
  "Identity bytes of a record-shaped `deftype*` class `n` whose `hasheq`
  reads `__hash`."
  ^bytes [n]
  (with-temp-dir [dir]
    (binding [*compile-files* true
              *compile-path* (str dir)]
      (eval-here (list 'do (record-form 'deftype* n '__hash)
                       (symbol (binary-name test-ns n)))))
    (Files/readAllBytes (.toPath (class-file dir test-ns n)))))

(deftest error-order-test
  (when (value-output?)
    (testing "unexpected-constructor before removed-field-access; no file"
      (let [n (gensym "Order")
            cname (binary-name test-ns n)
            bs (with-extra-constructor (record-bytes n))
            ^DynamicClassLoader cl (#'loader/capture-loader
                                    cname (DynamicClassLoader.) [] :value :record)]
        (with-temp-dir [dir]
          (is (= {:value-type/error :unexpected-constructor :class-name cname}
                 (support/thrown-data #(binding [*compile-files* true
                                                 *compile-path* (str dir)]
                                         (.defineClass cl cname bs nil)))))
          (is (empty? (rest (file-seq dir)))))))))
