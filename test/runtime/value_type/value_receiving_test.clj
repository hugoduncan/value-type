(ns value-type.value-receiving-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.core]
            [value-type.test-support :as support])
  (:import (java.lang.reflect InvocationTargetException Modifier)))

(deftest receiving-value-fields-test
  ;; Analyze transported data in a fresh namespace with a receiver-local import.
  (doseq [analysis [:eval :function :source]
          expansion [:internal :macroexpand :macroexpand-1]]
    (testing (str "generated value receiving via " expansion " / " analysis)
      (let [receiver (create-ns (gensym "value.receiver"))
            binary-name (str (ns-name receiver) ".Holder")]
        (try
          (binding [*ns* receiver]
            (clojure.core/refer 'clojure.core)
            (import 'java.util.Date)
            (eval '(defprotocol FieldReads (read-fields [this])))
            (let [fields [(with-meta 'required {:tag 'Date :non-nil true}) 'optional]
                  body (list 'Holder fields 'FieldReads
                             '(read-fields [_] [required optional])
                             'java.util.concurrent.Callable
                             '(call [_] [required optional])
                             'Object
                             '(toString [_] (pr-str [required optional]))
                             'clojure.lang.IDeref
                             '(deref [_]
                                     (fn [select-required?]
                                       (let [selected (if select-required? required optional)]
                                         (fn [] [selected required optional])))))
                  declaration (cons 'value-type.core/defvaluetype body)
                  form (case expansion
                         :internal (apply list 'value-type.core/value-field-deftype
                                          binary-name [:generated] body)
                         :macroexpand (macroexpand declaration)
                         :macroexpand-1 (macroexpand-1 declaration))
                  ^Class owner (case analysis
                                 :function ((eval (list 'fn [] form)))
                                 :source (load-string
                                          (binding [*print-meta* true]
                                            (pr-str form)))
                                 :eval (eval form))
                  field (.getField owner "required")
                  ctor (.getConstructor owner (into-array Class [Object Object]))
                  read-direct (eval '(fn [^Holder holder]
                                       [(.-required holder) (.-optional holder)]))
                  input (java.util.Date. 0)
                  instance (.newInstance ctor (object-array [input nil]))]
              (is (support/value? owner))
              (is (empty? (.getAnnotations field)))
              (is (identical? java.util.Date (.getType field)))
              (is (Modifier/isFinal (.getModifiers field)))
              (is (identical? input (.get field instance)))
              (is (= [input nil] ((ns-resolve receiver 'read-fields) instance)))
              (is (nil? (.get (.getField owner "optional") instance)))
              (doseq [optional-value [nil "nullable"]
                      select-required? [true false]]
                (let [holder (.newInstance ctor (object-array [input optional-value]))
                      read-later ((deref holder) select-required?)]
                  (is (= [input optional-value] (read-direct holder)))
                  (is (= [input optional-value] (.call ^java.util.concurrent.Callable holder)))
                  (is (= (pr-str [input optional-value]) (.toString holder)))
                  (is (= [(if select-required? input optional-value) input optional-value]
                         (read-later)))))
              (doseq [[entry construct]
                      [[:direct (eval '(fn [required optional]
                                         (new Holder required optional)))]
                       [:factory (ns-resolve receiver '->Holder)]
                       [:reflective (fn [required optional]
                                      (try
                                        (.newInstance ctor (object-array [required optional]))
                                        (catch InvocationTargetException error
                                          (throw (.getCause error)))))]]]
                (testing (name entry)
                  (is (identical? input (.get field (construct input nil))))
                  (doseq [[invalid expected] [[nil NullPointerException]
                                              ["wrong" ClassCastException]]]
                    (is (= expected
                           (try
                             (construct invalid nil)
                             nil
                             (catch Exception error
                               (class error))))))))))
          (finally (remove-ns (ns-name receiver))))))))

(deftest receiving-native-and-mixed-fields-test
  ;; Fixed modes must survive receiving analysis, including companion reads.
  (doseq [analysis [:eval :function :source]
          modes [[:native :native] [:native :generated]]
          expansion [:internal :macroexpand :macroexpand-1]]
    (testing (str "fixed " modes " receiving via " expansion " / " analysis)
      (let [receiver (create-ns (gensym "value.mixed.receiver"))
            binary-name (str (ns-name receiver) ".Holder")]
        (try
          (binding [*ns* receiver]
            (clojure.core/refer 'clojure.core)
            (eval '(defprotocol FieldReads (read-fields [this])))
            (let [child (eval '(value-type.core/defvaluetype Child [payload]))
                  input ((ns-resolve receiver '->Child) nil)
                  right-class (if (and (not= :internal expansion)
                                       (= :generated (second modes)))
                                String child)
                  right-input (if (= String right-class) "required" input)
                  fields [(with-meta 'left {:tag 'Child :non-nil true})
                          (with-meta 'right {:tag (if (= String right-class) 'String 'Child)
                                             :non-nil true})
                          'optional
                          (with-meta 'count {:tag 'long})
                          (with-meta 'ratio {:tag 'double})]
                  body (list 'Holder fields
                             'FieldReads
                             '(read-fields [_] [left right optional count ratio])
                             'java.util.concurrent.Callable
                             '(call [_] [left right optional count ratio])
                             'Object
                             '(toString [_] (pr-str [left right optional count ratio]))
                             'clojure.lang.IDeref
                             '(deref [_]
                                     (fn
                                       ([] [left right optional count ratio])
                                       ([select-left?]
                                        (let [selected (if select-left? left optional)]
                                          (fn [] [selected left right optional count ratio]))))))
                  declaration (cons 'value-type.core/defvaluetype body)
                  form (case expansion
                         :internal (apply list 'value-type.core/value-field-deftype
                                          binary-name modes body)
                         :macroexpand (macroexpand declaration)
                         :macroexpand-1 (macroexpand-1 declaration))
                  ^Class owner (case analysis
                                 :function ((eval (list 'fn [] form)))
                                 :source (load-string
                                          (binding [*print-meta* true]
                                            (pr-str form)))
                                 :eval (eval form))
                  ctor (.getConstructor owner (into-array Class [Object Object Object Long/TYPE Double/TYPE]))
                  instance (.newInstance ctor (object-array [input right-input nil 42 2.5]))]
              (is (support/value? owner))
              (let [read-direct (eval '(fn [^Holder holder]
                                         [(.-left holder) (.-right holder)
                                          (.-optional holder) (.-count holder)
                                          (.-ratio holder)]))]
                (is (= [input right-input nil 42 2.5] (read-direct instance))))
              (is (= [input right-input nil 42 2.5]
                     ((ns-resolve receiver 'read-fields) instance)))
              (is (= (pr-str [input right-input nil 42 2.5]) (.toString instance)))
              (doseq [optional-value [nil "nullable"]
                      select-left? [true false]]
                (let [holder (.newInstance ctor (object-array [input right-input optional-value 42 2.5]))
                      read-later ((deref holder) select-left?)]
                  (is (= [input right-input optional-value 42 2.5] ((deref holder))))
                  (is (= [input right-input optional-value 42 2.5]
                         (.call ^java.util.concurrent.Callable holder)))
                  (is (= [(if select-left? input optional-value)
                          input right-input optional-value 42 2.5]
                         (read-later)))))
              (doseq [[field-name mode] (map vector ["left" "right"] modes)]
                (let [field (.getField owner field-name)]
                  (is (identical? (if (= "left" field-name) child right-class)
                                  (.getType field)))
                  (is (Modifier/isFinal (.getModifiers field)))
                  (is (= (= :native mode)
                         (boolean
                          (some #(= "jdk.internal.vm.annotation.NullRestricted"
                                    (.getName (.annotationType %)))
                                (.getAnnotations field)))))))
              (doseq [[entry construct]
                      [[:direct (eval '(fn [left right optional count ratio]
                                         (new Holder left right optional count ratio)))]
                       [:factory (ns-resolve receiver '->Holder)]
                       [:reflective (fn [left right optional count ratio]
                                      (try
                                        (.newInstance ctor (object-array [left right optional count ratio]))
                                        (catch InvocationTargetException error
                                          (throw (.getCause error)))))]]]
                (testing (name entry)
                  (is (= [input right-input nil 42 2.5] ((deref (construct input right-input nil 42 2.5)))))
                  (doseq [position [0 1]
                          [invalid expected] [[nil NullPointerException]
                                              [(Object.) ClassCastException]]]
                    (is (= expected
                           (try
                             (apply construct (assoc [input right-input nil 42 2.5] position invalid))
                             nil
                             (catch Exception error
                               (class error))))))))))
          (finally (remove-ns (ns-name receiver))))))))

(deftest receiving-redefined-child-test
  ;; Reanalysis must use the replacement Class, not a cached same-name Class.
  (doseq [[mode expansion] [[:native :internal]
                            [:generated :internal]
                            [:native :macroexpand]
                            [:native :macroexpand-1]
                            [:generated :macroexpand]
                            [:generated :macroexpand-1]]
          analysis [:eval :function :source]]
    (let [receiver (create-ns (gensym "value.redefinition"))
          binary-name (str (ns-name receiver) ".Holder")]
      (try
        (binding [*ns* receiver]
          (clojure.core/refer 'clojure.core)
          (let [child-form (if (and (= :generated mode)
                                    (not= :internal expansion))
                             '(deftype Child [])
                             '(value-type.core/defvaluetype Child []))
                original (eval child-form)
                old-child (.newInstance ^Class original)
                fields [(with-meta 'required {:tag 'Child :non-nil true})]
                declaration (list 'value-type.core/defvaluetype 'Holder fields)
                form (case expansion
                       :internal (list 'value-type.core/value-field-deftype
                                       binary-name [mode] 'Holder fields)
                       :macroexpand (macroexpand declaration)
                       :macroexpand-1 (macroexpand-1 declaration))
                analyze (fn []
                          (case analysis
                            :eval (eval form)
                            :function ((eval (list 'fn [] form)))
                            :source (load-string
                                     (binding [*print-meta* true]
                                       (pr-str form)))))
                first-owner (analyze)
                replacement (eval child-form)
                new-child (.newInstance ^Class replacement)
                second-owner (analyze)]
            (is (not (identical? original replacement)))
            (is (not (identical? first-owner second-owner)))
            (doseq [[^Class owner expected valid stale]
                    [[first-owner original old-child new-child]
                     [second-owner replacement new-child old-child]]]
              (let [field (.getField owner "required")
                    ctor (.getConstructor owner (into-array Class [Object]))
                    instance (.newInstance ctor (object-array [valid]))]
                (is (identical? expected (.getType field)))
                (is (identical? valid (.get field instance)))
                (doseq [[invalid failure] [[nil NullPointerException]
                                           [stale ClassCastException]]]
                  (is (= failure
                         (try
                           (.newInstance ctor (object-array [invalid]))
                           nil
                           (catch InvocationTargetException error
                             (class (.getCause error)))))))))))
        (finally (remove-ns (ns-name receiver)))))))

;;; defvaluetype* receiving: value-field-deftype*

(def ^:private star-class "value_type.value_receiving_test.StarHolder")

(defn- star-error
  "Macroexpand a `value-field-deftype*` form with `modes`, `fields` and the
  option pairs `opts`; return :no-error or the `ex-data` of the cause."
  [modes fields & opts]
  (support/thrown-data
   #(binding [*ns* (the-ns 'value-type.value-receiving-test)]
      (macroexpand-1 (list* 'value-type.core/value-field-deftype* 'StarHolder
                            (symbol star-class) modes fields opts)))))

(deftest receiving-star-order-test
  (let [nn #(with-meta % {:non-nil true})
        tail support/record-tail]
    (testing ":mutable-field before :record-shape"
      (is (= {:value-type/error :mutable-field}
             (support/error-keys (star-error [] [(with-meta 'a {:volatile-mutable true})
                                                 '__meta])))))
    (testing ":record-shape before metadata keys"
      (is (= {:value-type/error :invalid-field :reason :record-shape}
             (support/error-keys (star-error [] [(with-meta 'a {:non-nil :x}) '__meta])))))
    (testing "metadata keys before :hidden-field, error on a later field"
      (is (= {:value-type/error :invalid-field :reason :invalid-non-nil}
             (support/error-keys (star-error [:generated]
                                             (assoc tail 0 (nn '__meta)
                                                    2 (with-meta '__hash
                                                        {:tag 'int :non-nil :x})))))))
    (testing ":hidden-field before tag and type"
      (is (= {:value-type/error :invalid-field :reason :hidden-field}
             (support/error-keys (star-error [:generated :generated]
                                             (into [(nn 'a)]
                                                   (assoc tail 0 (nn '__meta))))))))))

(deftest receiving-star-invalid-modes-test
  (is (= {:value-type/error :invalid-field-enforcement
          :class-name star-class
          :modes []}
         (star-error [] [(with-meta 'a {:tag 'String :non-nil true})]))))

(deftest receiving-star-closed-option-test
  (let [tail support/record-tail]
    (testing ":invalid-value and the last occurrence"
      (is (= {:value-type/error :invalid-option :class-name star-class
              :option :value-type/closed :value :x :reason :invalid-value}
             (star-error [] (into '[a] tail)
                         :value-type/closed true :value-type/closed :x))))
    (testing ":record-shape-required"
      (is (= {:value-type/error :invalid-option :reason :record-shape-required}
             (support/error-keys
              (star-error [] '[a] :value-type/closed true)))))
    (testing ":record-shape before :invalid-option"
      (is (= {:value-type/error :invalid-field :reason :record-shape}
             (support/error-keys
              (star-error [] '[x __meta] :value-type/closed :x)))))))

(deftest receiving-native-guard-order-test
  ;; The value-class guard is the first form of native-field value output,
  ;; before the native-field guard and the definition.
  (doseq [receiver ['value-type.core/value-field-deftype
                    'value-type.core/value-field-deftype*]]
    (testing (str receiver)
      (let [receiver-ns (create-ns (gensym "value.guard.receiver"))
            binary-name (str (ns-name receiver-ns) ".Holder")]
        (try
          (binding [*ns* receiver-ns]
            (clojure.core/refer 'clojure.core)
            (eval '(value-type.core/defvaluetype Child [payload]))
            (let [fields [(with-meta 'left {:tag 'Child :non-nil true})]
                  form (if (= 'value-type.core/value-field-deftype receiver)
                         (list receiver binary-name [:native] 'Holder fields)
                         (list receiver 'Holder (symbol binary-name) [:native]
                               fields))
                  expansion (with-bindings {clojure.lang.Compiler/LOADER
                                            (clojure.lang.DynamicClassLoader.)}
                              (macroexpand-1 form))]
              (is (= 'clojure.core/let (first expansion)))
              (is (= [`(value-type.impl.capability/require-value-capability!
                        ~binary-name)
                      `(value-type.impl.field-capability/require-field-capability!
                        ~binary-name)]
                     (take 2 (drop 2 expansion))))))
          (finally (remove-ns (ns-name receiver-ns))))))))
