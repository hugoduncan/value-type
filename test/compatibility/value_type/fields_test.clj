(ns value-type.fields-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.impl.fields :as fields]
            [value-type.core]))

(deftest metadata-normalization-test
  ;; Selection preserves order and all metadata; it does not resolve tags.
  (let [declarations '[plain ^String hinted
                       ^{:nil-restricted false :tag long} primitive
                       ^{:nil-restricted nil} nullable
                       ^Point ^:nil-restricted start
                       ^{:tag Point :nil-restricted true :custom :kept} end]
        selected (fields/restricted-fields "example.Segment" declarations)]
    (is (= '[start end] selected))
    (is (= (mapv meta (subvec declarations 4)) (mapv meta selected)))
    (is (= {:tag 'Point :nil-restricted true :custom :kept}
           (meta (second selected))))))

(deftest invalid-metadata-test
  ;; Truthy values and false-like values other than false/nil are errors.
  (doseq [value [0 1 "true" :true 'Point [] {}]]
    (testing (pr-str value)
      (let [field (with-meta 'point {:nil-restricted value})
            data (try
                   (fields/restricted-fields "example.Holder" [field])
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= {:value-type/error :invalid-field
                :class-name "example.Holder"
                :field 'point
                :reason :invalid-nil-restricted
                :value value}
               data))))))

(deftest declaration-boundary-test
  ;; deftype remains responsible for malformed field syntax.
  (doseq [declarations [nil '(x) 'x [42 :x "x"]]]
    (is (= [] (fields/restricted-fields "example.Holder" declarations))))
  (testing "the first invalid field in declaration order is reported"
    (let [data (try
                 (fields/restricted-fields
                  "example.Holder"
                  '[^{:nil-restricted :bad} first
                    ^{:nil-restricted :bad} second])
                 nil
                 (catch clojure.lang.ExceptionInfo e (ex-data e)))]
      (is (= 'first (:field data))))))

(deftest explicit-tag-resolution-test
  ;; Resolve real classes in the supplied namespace, not the caller's namespace.
  (let [defining-ns (create-ns (gensym "field-resolution-"))]
    (try
      (.importClass defining-ns java.util.Date)
      (doseq [[tag expected] [['Date java.util.Date]
                              ['java.util.Date java.util.Date]
                              ["java.util.Date" java.util.Date]
                              [java.util.Date java.util.Date]
                              ['long Long/TYPE]
                              ['longs (class (long-array 0))]
                              ['String/1 (class (make-array String 0))]]]
        (testing (pr-str tag)
          (is (identical? expected
                          (fields/resolve-tag
                           defining-ns "example.Holder"
                           (with-meta 'item {:tag tag :nil-restricted true}))))))
      (finally (remove-ns (ns-name defining-ns))))))

(deftest unresolved-tag-test
  ;; Missing, malformed and forward tags fail with stable field context.
  (doseq [[tag reason] [[nil :missing-tag]
                        ['NotYetDefined :unresolved-tag]
                        ['example.missing.Point :unresolved-tag]
                        ['unknown/Point :unresolved-tag]
                        [:Point :unresolved-tag]
                        [42 :unresolved-tag]]]
    (testing (pr-str tag)
      (let [data (try
                   (fields/resolve-tag
                    (the-ns 'value-type.fields-test) "example.Holder"
                    (with-meta 'item {:tag tag :nil-restricted true}))
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= {:value-type/error :invalid-field
                :class-name "example.Holder"
                :field 'item
                :reason reason
                :tag tag}
               data))))))

(deftest ineligible-type-test
  ;; Real Classes exercise each rejection independently of native capability.
  (doseq [[tag reason] [[Long/TYPE :primitive-type]
                        [String :identity-type]
                        [Runnable :interface-type]
                        [java.util.AbstractList :abstract-type]
                        [(class (object-array 0)) :array-type]
                        [(class (long-array 0)) :array-type]]]
    (testing (.getName ^Class tag)
      (let [field (with-meta 'item {:tag tag :nil-restricted true})
            data (try
                   (fields/validate-type "example.Holder" field tag)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= {:value-type/error :invalid-field
                :class-name "example.Holder"
                :field 'item
                :reason reason
                :tag tag}
               data))))))

(deftest enclosing-type-test
  ;; A resolved existing definition must not permit a recursive replacement.
  (let [field (with-meta 'item {:tag 'String :nil-restricted true})
        resolved (fields/resolve-tag (the-ns 'value-type.fields-test)
                                     "java.lang.String" field)
        data (try
               (fields/validate-type "java.lang.String" field resolved)
               nil
               (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (= {:value-type/error :invalid-field
            :class-name "java.lang.String"
            :field 'item
            :reason :enclosing-type
            :tag 'String}
           data))))

(deftest specification-validation-order-test
  ;; Metadata errors precede type errors; then each enabled type is checked in order.
  (let [defining-ns (the-ns 'value-type.fields-test)]
    (doseq [[declarations reason field]
            [['[^:nil-restricted missing ^{:nil-restricted :bad} invalid]
              :invalid-nil-restricted 'invalid]
             ['[^String ^:nil-restricted identity ^:nil-restricted missing]
              :identity-type 'identity]
             ['[^:nil-restricted missing ^String ^:nil-restricted identity]
              :missing-tag 'missing]]]
      (let [data (try
                   (fields/specifications defining-ns "example.Holder" declarations)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= [reason field] ((juxt :reason :field) data))))))
  (testing "disabled fields do not resolve or validate their tags"
    (is (= [] (fields/specifications
               (the-ns 'value-type.fields-test) "example.Holder"
               '[^long primitive ^{:tag Missing :nil-restricted false} disabled
                 ^{:tag String :nil-restricted nil} nullable])))))

(deftest mutable-declaration-error-test
  ;; Mutable rejection precedes restriction validation and keeps compiler wrapping.
  (doseq [mutable-key [:volatile-mutable :unsynchronized-mutable]
          restriction [true false nil :invalid]]
    (let [field (with-meta 'item {mutable-key true :nil-restricted restriction})
          type-name (gensym "MutableRestricted")
          failure (binding [*ns* (the-ns 'value-type.fields-test)]
                    (try
                      (macroexpand
                       (list 'value-type.core/defvaluetype type-name [field]))
                      (catch clojure.lang.Compiler$CompilerException e e)))]
      (is (instance? clojure.lang.Compiler$CompilerException failure))
      (is (instance? clojure.lang.ExceptionInfo (ex-cause failure)))
      (is (= {:value-type/error :mutable-field
              :class-name (str "value_type.fields_test." type-name)
              :fields [field]}
             (ex-data (ex-cause failure)))))))

(deftest disabled-declaration-behavior-test
  ;; Disabled metadata retains primitive storage, nullable hints and custom metadata.
  (doseq [restriction [false nil]]
    (let [type-name (gensym "DisabledRestriction")
          declarations [(with-meta 'number {:tag 'long :nil-restricted restriction})
                        (with-meta 'text {:tag 'String :nil-restricted restriction
                                          :custom :kept})]
          cls (binding [*ns* (the-ns 'value-type.fields-test)]
                (eval (list 'value-type.core/defvaluetype type-name declarations)))
          constructor (.getConstructor ^Class cls (into-array Class [Long/TYPE Object]))
          instance (.newInstance constructor (object-array [17 nil]))]
      (is (= [] (fields/specifications
                 (the-ns 'value-type.fields-test) (.getName ^Class cls) declarations)))
      (is (= Long/TYPE (.getType (.getField ^Class cls "number"))))
      (is (= Object (.getType (.getField ^Class cls "text"))))
      (is (= 17 (.get (.getField ^Class cls "number") instance)))
      (is (nil? (.get (.getField ^Class cls "text") instance))))))

(deftest unrelated-field-metadata-test
  ;; Macro selection must preserve unrelated field metadata for deftype analysis.
  (let [field (with-meta 'item {:tag 'String :nil-restricted false :custom :kept})
        expansion (binding [*ns* (the-ns 'value-type.fields-test)]
                    (macroexpand-1
                     (list 'value-type.core/defvaluetype
                           (gensym "MetadataControl") [field])))
        retained (filter #(and (symbol? %) (= 'item %))
                         (tree-seq coll? seq expansion))]
    (is (seq retained))
    (is (every? #(= (meta field) (meta %)) retained))))
