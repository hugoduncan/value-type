(ns value-type.fields-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.impl.fields :as fields]
            [value-type.jvm-config :as config]
            [value-type.message-checks :refer [message-names?]]
            [value-type.core]))

(deftest metadata-normalization-test
  ;; Selection preserves order and all metadata; it does not resolve tags.
  (let [declarations '[plain ^String hinted
                       ^{:non-nil false :tag long} primitive
                       ^{:non-nil nil} nullable
                       ^Point ^:non-nil start
                       ^{:tag Point :non-nil true :custom :kept} end]
        selected (fields/enabled-fields "example.Segment" declarations)]
    (is (= '[start end] selected))
    (is (= (mapv meta (subvec declarations 4)) (mapv meta selected)))
    (is (= {:tag 'Point :non-nil true :custom :kept}
           (meta (second selected))))))

(deftest invalid-metadata-test
  ;; Truthy values and false-like values other than false/nil are errors.
  (doseq [value [0 1 "true" :true 'Point [] {}]]
    (testing (pr-str value)
      (let [field (with-meta 'point {:non-nil value})
            data (try
                   (fields/enabled-fields "example.Holder" [field])
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= {:value-type/error :invalid-field
                :class-name "example.Holder"
                :field 'point
                :reason :invalid-non-nil
                :value value}
               data))))))

(deftest declaration-boundary-test
  ;; deftype remains responsible for malformed field syntax.
  (doseq [declarations [nil '(x) 'x [42 :x "x"]]]
    (is (= [] (fields/enabled-fields "example.Holder" declarations))))
  (testing "the first invalid field in declaration order is reported"
    (let [data (try
                 (fields/enabled-fields
                  "example.Holder"
                  '[^{:non-nil :bad} first
                    ^{:non-nil :bad} second])
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
                           (with-meta 'item {:tag tag :non-nil true}))))))
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
                    (with-meta 'item {:tag tag :non-nil true}))
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
      (let [field (with-meta 'item {:tag tag :non-nil true})
            data (try
                   (fields/validate-native-type "example.Holder" field tag)
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
  (let [field (with-meta 'item {:tag 'String :non-nil true})
        resolved (fields/resolve-tag (the-ns 'value-type.fields-test)
                                     "java.lang.String" field)
        data (try
               (fields/validate-native-type "java.lang.String" field resolved)
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
            [['[^:non-nil missing ^{:non-nil :bad} invalid]
              :invalid-non-nil 'invalid]
             ['[^long ^:non-nil primitive ^:non-nil missing]
              :primitive-type 'primitive]
             ['[^:non-nil missing ^long ^:non-nil primitive]
              :missing-tag 'missing]]]
      (let [data (try
                   (fields/specifications defining-ns "example.Holder"
                                          declarations)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= [reason field] ((juxt :reason :field) data))))))
  (testing "disabled fields do not resolve or validate their tags"
    (is (= [] (fields/specifications
               (the-ns 'value-type.fields-test) "example.Holder"
               '[^long primitive ^{:tag Missing :non-nil false} disabled
                 ^{:tag String :non-nil nil} nullable])))))

(deftest mutable-declaration-error-test
  ;; Mutable rejection precedes restriction validation and keeps compiler wrapping.
  (doseq [mutable-key [:volatile-mutable :unsynchronized-mutable]
          restriction [true false nil :invalid]
          expand [macroexpand-1 macroexpand]]
    (let [field (with-meta 'item {mutable-key true :non-nil restriction})
          type-name (gensym "MutableRestricted")
          failure (binding [*ns* (the-ns 'value-type.fields-test)]
                    (try
                      (expand
                       (list 'value-type.core/defvaluetype type-name [field]))
                      (catch clojure.lang.Compiler$CompilerException e e)))]
      (is (instance? clojure.lang.Compiler$CompilerException failure))
      (is (instance? clojure.lang.ExceptionInfo (ex-cause failure)))
      (is (= {:value-type/error :mutable-field
              :class-name (str "value_type.fields_test." type-name)
              :fields [field]}
             (ex-data (ex-cause failure))))
      (is (nil? (ns-resolve 'value-type.fields-test type-name)))
      (is (nil? (ns-resolve 'value-type.fields-test
                            (symbol (str "->" type-name))))))))

(deftest disabled-declaration-behavior-test
  ;; Disabled constraints and plain hints retain ordinary constructor behavior.
  (doseq [restriction [{} {:non-nil false} {:non-nil nil}]]
    (let [type-name (gensym "DisabledRestriction")
          declarations [(with-meta 'number (assoc restriction :tag 'long))
                        (with-meta 'text (assoc restriction :tag 'String
                                                :custom :kept))]
          cls (binding [*ns* (the-ns 'value-type.fields-test)]
                (eval (list 'value-type.core/defvaluetype type-name declarations)))
          constructor (.getConstructor ^Class cls (into-array Class [Long/TYPE Object]))
          factory (ns-resolve 'value-type.fields-test (symbol (str "->" type-name)))]
      (is (= [] (fields/specifications
                 (the-ns 'value-type.fields-test) (.getName ^Class cls)
                 declarations)))
      (is (= Long/TYPE (.getType (.getField ^Class cls "number"))))
      (is (= Object (.getType (.getField ^Class cls "text"))))
      (doseq [text [nil "valid" 42]
              instance [(.newInstance constructor (object-array [17 text]))
                        (factory 17 text)]]
        (is (= 17 (.get (.getField ^Class cls "number") instance)))
        (is (= text (.get (.getField ^Class cls "text") instance)))))))

(deftest unrelated-field-metadata-test
  ;; Macro selection must preserve unrelated field metadata for deftype analysis.
  (let [field (with-meta 'item {:tag 'String :non-nil false :custom :kept})
        expansion (binding [*ns* (the-ns 'value-type.fields-test)]
                    (macroexpand-1
                     (list 'value-type.core/defvaluetype
                           (gensym "MetadataControl") [field])))
        retained (filter #(and (symbol? %) (= 'item %))
                         (tree-seq coll? seq expansion))]
    (is (seq retained))
    (is (every? #(= (meta field) (meta %)) retained))))

(deftest removed-metadata-test
  ;; Presence rejects the removed key even when disabled or combined with the new key.
  (doseq [old-value [true false nil :invalid]
          additional [{} {:non-nil true} {:non-nil false} {:non-nil nil}]]
    (let [field (with-meta 'item (assoc additional :nil-restricted old-value))
          type-name (gensym "RemovedMetadata")
          failure (binding [*ns* (the-ns 'value-type.fields-test)]
                    (try
                      (macroexpand-1
                       (list 'value-type.core/defvaluetype type-name [field]))
                      (catch clojure.lang.Compiler$CompilerException e e)))]
      (is (instance? clojure.lang.Compiler$CompilerException failure))
      (is (= {:value-type/error :invalid-field
              :class-name (str "value_type.fields_test." type-name)
              :field field
              :reason :removed-nil-restricted
              :value old-value}
             (ex-data (ex-cause failure)))))))

(deftest concrete-class-eligibility-test
  ;; Concrete eligibility keeps exact Classes without requiring native support.
  (let [defining-ns (create-ns (gensym "concrete-fields-"))]
    (try
      (.importClass defining-ns java.util.Date)
      (doseq [tag ['String 'Date 'java.util.Date java.util.Date]]
        (let [field (with-meta 'item {:tag tag :non-nil true :custom :kept})
              resolved (fields/resolve-tag defining-ns "example.Holder" field)]
          (is (identical? resolved
                          (fields/validate-concrete-type
                           "example.Holder" field resolved)))))
      (finally (remove-ns (ns-name defining-ns)))))
  (testing "an ordinary fallen-back declaration is a concrete field type"
    (let [cls (binding [*ns* (the-ns 'value-type.fields-test)]
                (eval (list 'value-type.core/defvaluetype
                            (gensym "ConcreteNested") '[nullable])))
          field (with-meta 'nested {:tag cls :non-nil true})]
      (is (identical? cls (fields/validate-concrete-type
                           "example.Holder" field cls))))))

(deftest concrete-class-rejection-test
  ;; Declaration errors do not depend on representation or native capability.
  (doseq [[type-name tag reason]
          [["example.Holder" Long/TYPE :primitive-type]
           ["example.Holder" Runnable :interface-type]
           ["example.Holder" java.util.AbstractList :abstract-type]
           ["example.Holder" (class (object-array 0)) :array-type]
           ["java.lang.String" String :enclosing-type]]]
    (let [field (with-meta 'item {:tag tag :non-nil true})
          data (try
                 (fields/validate-concrete-type type-name field tag)
                 nil
                 (catch clojure.lang.ExceptionInfo e (ex-data e)))]
      (is (= {:value-type/error :invalid-field
              :class-name type-name :field field :reason reason :tag tag}
             data)))))

(deftest portable-specifications-test
  ;; Baseline selection preserves concrete storage, skips disabled tags and
  ;; does not probe native capability without a value-class field.
  (let [declarations '[^String ^:non-nil text
                       ^{:tag Missing :non-nil false} disabled
                       ^java.util.Date ^{:non-nil true :custom :kept} date]]
    (doseq [capable? [false true]]
      (let [probes (atom 0)
            specs (fields/select-enforcement
                   (fields/specifications
                    (the-ns 'value-type.fields-test) "example.Holder"
                    declarations)
                   #(do (swap! probes inc) capable?))]
        (is (zero? @probes))
        (is (= [:generated :generated] (mapv :enforcement specs)))
        (is (= [String java.util.Date] (mapv :type specs)))
        (is (= (meta (last declarations)) (meta (:field (last specs)))))))))

(deftest portable-specification-errors-test
  ;; Portable selection validates declarations before choosing either enforcement mode.
  (doseq [[declarations reason field]
          [['[^:non-nil missing] :missing-tag 'missing]
           ['[^NotYetDefined ^:non-nil forward] :unresolved-tag 'forward]
           ['[^long ^:non-nil primitive] :primitive-type 'primitive]
           ['[^Runnable ^:non-nil interface] :interface-type 'interface]
           ['[^java.util.AbstractList ^:non-nil abstract] :abstract-type 'abstract]
           ['[^String/1 ^:non-nil array] :array-type 'array]
           ['[^String ^:non-nil enclosing] :enclosing-type 'enclosing]
           ['[^:non-nil missing ^{:non-nil :bad} invalid]
            :invalid-non-nil 'invalid]
           ['[^String ^:non-nil valid ^{:nil-restricted nil} removed]
            :removed-nil-restricted 'removed]]]
    (testing (str reason)
      (let [data (try
                   (fields/specifications
                    (the-ns 'value-type.fields-test) "java.lang.String"
                    declarations)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= {:value-type/error :invalid-field
                :class-name "java.lang.String" :reason reason :field field}
               (select-keys data [:value-type/error :class-name :reason :field])))))))

(deftest portable-fallback-class-specification-test
  ;; Real defvaluetype Classes retain exact identity in either representation.
  (let [value-capable? (:available? (config/configuration))
        defining-ns (the-ns 'value-type.fields-test)
        cls (binding [*ns* defining-ns]
              (eval (list 'value-type.core/defvaluetype
                          (gensym "PortableNested") '[nullable])))
        field (with-meta 'nested {:tag cls :non-nil true :custom :kept})
        declarations [field
                      (with-meta 'disabled {:tag 'Missing :non-nil false})
                      (with-meta 'nullable {:tag 'Missing :non-nil nil})
                      (with-meta 'hinted {:tag 'Missing})]]
    (doseq [capable? [false true]]
      (let [specs (fields/select-enforcement
                   (fields/specifications defining-ns "example.Holder"
                                          declarations)
                   (constantly capable?))
            spec (first specs)]
        (is (= 1 (count specs)))
        (is (identical? cls (:type spec)))
        (is (= (if (and value-capable? capable?) :native :generated)
               (:enforcement spec)))
        (is (= (meta field) (meta (:field spec))))))))

(deftest receiving-specifications-test
  ;; Receiving analysis resolves real local Classes without reselecting modes.
  (let [receiver (create-ns (gensym "receiving-fields-"))
        declarations '[^Date ^{:non-nil true :custom :kept} date
                       ^{:tag Missing :non-nil false} disabled]]
    (try
      (.importClass receiver java.util.Date)
      (let [specs (fields/receiving-specifications
                   receiver "example.Holder" declarations [:generated]
                   #{:native :generated})
            spec (first specs)]
        (is (= 1 (count specs)))
        (is (identical? java.util.Date (:type spec)))
        (is (= :generated (:enforcement spec)))
        (is (= (meta (first declarations)) (meta (:field spec)))))
      (testing "invalid mode transport is rejected"
        (doseq [modes [nil '(:generated) [] [:generated :generated] [:unknown]]]
          (let [e (try
                    (fields/receiving-specifications
                     receiver "example.Holder" declarations modes
                     #{:native :generated})
                    nil
                    (catch clojure.lang.ExceptionInfo e e))
                data (ex-data e)]
            (is (= :invalid-field-enforcement (:value-type/error data)))
            (is (= modes (:modes data)))
            (is (message-names? e ["example.Holder" (pr-str modes)
                                   ":generated" ":native"
                                   "evaluate the defvaluetype form directly"])
                (ex-message e)))))
      (testing "identity output permits only generated modes"
        (let [e (try
                  (fields/receiving-specifications
                   receiver "example.Holder" declarations [:native]
                   #{:generated})
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
          (is (= {:value-type/error :invalid-field-enforcement
                  :class-name "example.Holder"
                  :modes [:native]}
                 (ex-data e)))
          (is (message-names? e ["example.Holder" "[:native]" ":generated"
                                 "evaluate the defvaluetype form directly"])
              (ex-message e))))
      (testing "invalid modes are rejected before tag resolution"
        (let [unresolved '[^{:tag Missing :non-nil true} missing]]
          (doseq [[modes permitted] [[[:unknown] #{:native :generated}]
                                     [[:native] #{:generated}]]]
            (let [data (try
                         (fields/receiving-specifications
                          receiver "example.Holder" unresolved modes permitted)
                         nil
                         (catch clojure.lang.ExceptionInfo e (ex-data e)))]
              (is (= {:value-type/error :invalid-field-enforcement
                      :class-name "example.Holder"
                      :modes modes}
                     data))))
          (testing "valid modes reach tag resolution"
            (let [data (try
                         (fields/receiving-specifications
                          receiver "example.Holder" unresolved [:generated]
                          #{:native :generated})
                         nil
                         (catch clojure.lang.ExceptionInfo e (ex-data e)))]
              (is (= [:invalid-field :unresolved-tag]
                     ((juxt :value-type/error :reason) data)))))))
      (testing "a native selection cannot become generated for an identity tag"
        (let [data (try
                     (fields/receiving-specifications
                      receiver "example.Holder" declarations [:native]
                      #{:native :generated})
                     nil
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :identity-type (:reason data)))))
      (finally (remove-ns (ns-name receiver))))))

(deftest public-portable-declaration-errors-test
  ;; Public validation rejects invalid tags before representation selection.
  (doseq [[tag reason] [[nil :missing-tag]
                        ['NotYetDefinedPortableField :unresolved-tag]
                        ['example.missing.Field :unresolved-tag]
                        ['long :primitive-type]
                        ['Runnable :interface-type]
                        ['java.util.AbstractList :abstract-type]
                        ['objects :array-type]]
          expand [macroexpand-1 macroexpand]]
    (testing (pr-str [tag reason])
      (let [type-name (gensym "InvalidPortableField")
            field (with-meta 'item {:tag tag :non-nil true})
            failure (binding [*ns* (the-ns 'value-type.fields-test)]
                      (try
                        (expand (list 'value-type.core/defvaluetype type-name [field]))
                        (catch clojure.lang.Compiler$CompilerException error error)))]
        (is (instance? clojure.lang.Compiler$CompilerException failure))
        (is (= {:value-type/error :invalid-field
                :class-name (str "value_type.fields_test." type-name)
                :field 'item
                :reason reason}
               (select-keys (ex-data (ex-cause failure))
                            [:value-type/error :class-name :field :reason])))
        (is (nil? (ns-resolve 'value-type.fields-test type-name)))
        (is (nil? (ns-resolve 'value-type.fields-test
                              (symbol (str "->" type-name)))))))))

(deftest public-portable-metadata-errors-test
  ;; Public metadata errors must not install a class or factory on any JVM.
  (doseq [[metadata reason value]
          (into (mapv (fn [value]
                        [{:tag 'String :non-nil value} :invalid-non-nil value])
                      [0 1 "true" :true 'Point [] {}])
                (mapv (fn [value]
                        [{:tag 'String :non-nil true :nil-restricted value}
                         :removed-nil-restricted value])
                      [true false nil]))
          expand [macroexpand-1 macroexpand]]
    (testing (pr-str metadata)
      (let [type-name (gensym "InvalidPortableMetadata")
            field (with-meta 'item metadata)
            failure (binding [*ns* (the-ns 'value-type.fields-test)]
                      (try
                        (expand (list 'value-type.core/defvaluetype type-name [field]))
                        (catch clojure.lang.Compiler$CompilerException error error)))]
        (is (instance? clojure.lang.Compiler$CompilerException failure))
        (is (= {:value-type/error :invalid-field
                :class-name (str "value_type.fields_test." type-name)
                :field 'item :reason reason :value value}
               (select-keys (ex-data (ex-cause failure))
                            [:value-type/error :class-name :field :reason :value])))
        (is (nil? (ns-resolve 'value-type.fields-test type-name)))
        (is (nil? (ns-resolve 'value-type.fields-test
                              (symbol (str "->" type-name)))))))))

(deftest public-enclosing-type-error-test
  ;; Reject recursive replacement without disturbing the existing public definition.
  (let [defining-ns (the-ns 'value-type.fields-test)
        type-name (gensym "ExistingPortableOwner")
        cls (binding [*ns* defining-ns]
              (eval (list 'value-type.core/defvaluetype type-name '[item])))
        factory-symbol (symbol (str "->" type-name))
        factory (var-get (ns-resolve defining-ns factory-symbol))]
    (doseq [expand [macroexpand-1 macroexpand]]
      (let [field (with-meta 'item {:tag type-name :non-nil true})
            failure (binding [*ns* defining-ns]
                      (try
                        (expand (list 'value-type.core/defvaluetype type-name [field]))
                        (catch clojure.lang.Compiler$CompilerException error error)))]
        (is (instance? clojure.lang.Compiler$CompilerException failure))
        (is (= {:value-type/error :invalid-field
                :class-name (.getName ^Class cls)
                :field 'item :reason :enclosing-type :tag type-name}
               (ex-data (ex-cause failure))))
        (is (identical? cls (ns-resolve defining-ns type-name)))
        (is (identical? factory (var-get (ns-resolve defining-ns factory-symbol))))
        (let [instance (factory nil)]
          (is (identical? cls (class instance)))
          (is (nil? (.get (.getField ^Class cls "item") instance))))))))

(deftest public-imported-concrete-field-test
  ;; Public receiving compilation retains exact external and nested child Classes.
  (let [defining-ns (create-ns (gensym "imported-fields-"))
        child-name (gensym "NullableChild")
        child-class (binding [*ns* defining-ns]
                      (eval (list 'value-type.core/defvaluetype child-name '[nullable])))
        child-factory (ns-resolve defining-ns (symbol (str "->" child-name)))
        child (child-factory nil)
        samples [[java.util.Date (java.util.Date. 123)
                  ['Date 'java.util.Date 'ExternalAlias]]
                 [java.util.AbstractMap$SimpleEntry
                  (java.util.AbstractMap$SimpleEntry. "key" "value")
                  ['AbstractMap$SimpleEntry 'java.util.AbstractMap$SimpleEntry]]
                 [child-class child [child-name (symbol (.getName ^Class child-class))
                                     'ChildAlias]]]]
    (try
      (is (nil? (.get (.getField ^Class child-class "nullable") child)))
      (.importClass defining-ns 'ExternalAlias java.util.Date)
      (.importClass defining-ns 'ChildAlias child-class)
      (doseq [[field-class _ _] samples]
        (.importClass defining-ns field-class))
      (doseq [[field-class sample tags] samples
              tag tags
              expand [macroexpand-1 macroexpand]]
        (testing (pr-str tag)
          (let [type-name (gensym "ImportedOwner")
                field (with-meta 'item {:tag tag :non-nil true :custom :kept})
                cls (binding [*ns* defining-ns]
                      (eval (expand (list 'value-type.core/defvaluetype
                                          type-name [field]))))
                storage (.getField ^Class cls "item")
                constructor (.getConstructor ^Class cls (into-array Class [Object]))
                factory (ns-resolve defining-ns (symbol (str "->" type-name)))]
            (is (identical? field-class (.getType storage)))
            (is (java.lang.reflect.Modifier/isFinal (.getModifiers storage)))
            (doseq [instance [(factory sample)
                              (.newInstance constructor (object-array [sample]))]]
              (is (identical? sample (.get storage instance))))
            (is (thrown? NullPointerException (factory nil)))
            (is (thrown? ClassCastException (factory "wrong")))
            (doseq [[argument expected] [[nil NullPointerException]
                                         ["wrong" ClassCastException]]]
              (let [failure (try
                              (.newInstance constructor (object-array [argument]))
                              (catch java.lang.reflect.InvocationTargetException error
                                (.getCause error)))]
                (is (instance? expected failure)))))))
      (finally (remove-ns (ns-name defining-ns))))))
