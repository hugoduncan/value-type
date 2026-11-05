(ns value-type.identity-receiving-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.message-checks :refer [message-names?]]
            [value-type.transfer-checks :as transfer])
  (:import (java.lang.reflect Constructor Modifier)))

(deftest receiving-identity-fields-test
  ;; Analyze transported data in a fresh namespace with a receiver-local import.
  (doseq [mode transfer/modes
          expand (cons :internal transfer/expands)
          nested? [false true]]
    (testing (str "fixed generated identity receiving via " mode " nested? " nested?)
      (testing (pr-str expand)
        (let [receiver (create-ns (gensym "identity.receiver"))
              binary-name (registry/class-name receiver 'Holder)]
          (try
            (binding [*ns* receiver]
              (clojure.core/refer 'clojure.core)
              (import 'java.util.Date)
              (eval '(defprotocol FieldReads (read-fields [this])))
              (let [child-class (when nested?
                                  (eval '(value-type.core/defvaluetype Child [optional])))
                    required-class (or child-class java.util.Date)
                    fields [(with-meta 'required {:tag (if nested? 'Child 'Date)
                                                  :non-nil true})
                            (with-meta 'label {:tag 'String :non-nil true})
                            'optional (with-meta 'count {:tag 'long})
                            (with-meta 'ratio {:tag 'double})]
                    non-nil-positions (into [] (keep-indexed
                                                (fn [i field] (when (:non-nil (meta field)) i)))
                                            fields)
                    optional-position (.indexOf ^java.util.List fields 'optional)
                    specs '[FieldReads
                            (read-fields [_] [required label optional count ratio])
                            java.util.concurrent.Callable
                            (call [_] [required label optional count ratio])
                            Object
                            (toString [_] (pr-str [required label optional count ratio]))
                            clojure.lang.IDeref
                            (deref [_]
                                   (fn
                                     ([] [required label optional count ratio])
                                     ([selected?]
                                      (fn [] (if selected? required optional)))))]
                    form (list* 'value-type.core/identity-field-deftype
                                binary-name [:generated :generated] 'Holder fields
                                specs)
                    public-form (list* 'value-type.core/defvaluetype
                                       'Holder fields specs)
                    selected (if (= :internal expand)
                               form
                               ((transfer/expanders expand) public-form))
                    ^Class owner (transfer/evaluate-form selected mode)
                    field (.getField owner "required")
                    ^Constructor ctor (first (.getConstructors owner))
                    input (if nested?
                            ((ns-resolve receiver '->Child) nil)
                            (java.util.Date. 0))
                    args [input "label" nil (long 7) (double 2.5)]
                    instance (.newInstance ctor (object-array args))]
                (is (= args ((ns-resolve receiver 'read-fields) instance)))
                (is (= (pr-str args) (.toString instance)))
                (is (identical? required-class (.getType field)))
                (when nested?
                  (is (nil? (.get (.getField ^Class child-class "optional") input))))
                (is (Modifier/isFinal (.getModifiers field)))
                (is (identical? String (.getType (.getField owner "label"))))
                (is (Modifier/isFinal (.getModifiers (.getField owner "label"))))
                (let [read-direct (eval '(fn [^Holder holder]
                                           [(.-required holder) (.-label holder)
                                            (.-optional holder) (.-count holder)
                                            (.-ratio holder)]))]
                  (is (= args (read-direct instance))))
                (is (identical? input (.get field instance)))
                (is (nil? (.get (.getField owner "optional") instance)))
                (doseq [optional [nil "present"]]
                  (let [values (assoc args optional-position optional)
                        holder (.newInstance ctor (object-array values))
                        read-field (.deref ^clojure.lang.IDeref holder)]
                    (is (= values (read-field)))
                    (is (= values (.call ^java.util.concurrent.Callable holder)))
                    (is (identical? input ((read-field true))))
                    (is (identical? optional ((read-field false))))))
                (doseq [[construction build] (checks/named-builders receiver owner)]
                  (testing (pr-str construction)
                    (is (= args ((.deref ^clojure.lang.IDeref (build args)))))
                    (doseq [position non-nil-positions]
                      (testing (name (nth fields position))
                        (is (= checks/checked-rejections
                               (checks/checked-outcomes
                                (fn [[x]] (build (assoc args position x)))
                                42)))))))))
            (finally (remove-ns (ns-name receiver)))))))))

(deftest receiving-redefined-child-test
  ;; Reanalysis must use the replacement Class, not a cached same-name Class.
  (doseq [expand (cons :internal transfer/expands)
          mode transfer/modes]
    (testing (pr-str expand mode)
      (let [receiver (create-ns (gensym "identity.redefinition"))
            binary-name (registry/class-name receiver 'Holder)]
        (try
          (binding [*ns* receiver]
            (clojure.core/refer 'clojure.core)
            (let [original (eval '(deftype Child []))
                  old-child (.newInstance ^Class original)
                  fields [(with-meta 'required {:tag 'Child :non-nil true})]
                  declaration (list 'value-type.core/defvaluetype 'Holder fields)
                  form (if (= :internal expand)
                         (list 'value-type.core/identity-field-deftype
                               binary-name [:generated] 'Holder fields)
                         ((transfer/expanders expand) declaration))
                  analyze #(transfer/evaluate-form form mode)
                  first-owner (analyze)
                  replacement (eval '(deftype Child []))
                  new-child (.newInstance ^Class replacement)
                  second-owner (analyze)]
              (is (not (identical? original replacement)))
              (is (not (identical? first-owner second-owner)))
              (doseq [[owner-name ^Class owner expected valid stale]
                      [["first-owner" first-owner original old-child new-child]
                       ["second-owner" second-owner replacement new-child old-child]]]
                (testing owner-name
                  (let [field (.getField owner "required")
                        ctor (.getConstructor owner (into-array Class [Object]))
                        instance (.newInstance ctor (object-array [valid]))]
                    (is (identical? expected (.getType field)))
                    (is (identical? valid (.get field instance)))
                    (is (= checks/checked-rejections
                           (checks/checked-outcomes
                            (fn [[x]] (.newInstance ctor (object-array [x])))
                            stale))))))))
          (finally (remove-ns (ns-name receiver))))))))

(deftest receiving-identity-native-modes-test
  ;; Identity output rejects native modes before tag resolution and validation.
  (let [receiver (create-ns (gensym "identity.native"))]
    (try
      (binding [*ns* receiver]
        (clojure.core/refer 'clojure.core)
        (eval '(deftype Child []))
        (doseq [tag '[String Child]]
          (testing (str tag)
            (let [form (list 'value-type.core/identity-field-deftype
                             (registry/class-name receiver 'Holder) [:native] 'Holder
                             [(with-meta 'value {:tag tag :non-nil true})])
                  cause (try (macroexpand-1 form)
                             (catch clojure.lang.Compiler$CompilerException thrown
                               (.getCause thrown)))]
              (is (= {:value-type/error :invalid-field-enforcement
                      :class-name (registry/class-name receiver 'Holder)
                      :modes [:native]}
                     (ex-data cause)))
              (is (message-names? cause [(registry/class-name receiver 'Holder)
                                         "[:native]"
                                         "evaluate the defvaluetype form directly"])
                  (ex-message cause))))))
      (finally (remove-ns (ns-name receiver))))))

;;; defvaluetype* receiving: identity-field-deftype*

(def ^:private star-class "value_type.identity_receiving_test.StarHolder")

(defn- error-keys
  "The `:value-type/error` and `:reason` of the error data `data`; `data`
  itself when it is not a map (for example :no-error)."
  [data]
  (if (map? data) (select-keys data [:value-type/error :reason]) data))

(defn- star-error
  "Macroexpand an `identity-field-deftype*` form with `modes` and `fields`;
  return :no-error or the `ex-data` of the cause."
  [modes fields]
  (try
    (binding [*ns* (the-ns 'value-type.identity-receiving-test)]
      (macroexpand-1 (list 'value-type.core/identity-field-deftype* 'StarHolder
                           (symbol star-class) modes fields)))
    :no-error
    (catch clojure.lang.Compiler$CompilerException thrown
      (ex-data (.getCause thrown)))))

(deftest receiving-star-order-test
  (let [nn #(with-meta % {:non-nil true})]
    (testing ":mutable-field before :record-shape"
      (is (= {:value-type/error :mutable-field}
             (error-keys (star-error [] [(with-meta 'a {:volatile-mutable true})
                                         '__meta])))))
    (testing ":record-shape before metadata keys"
      (is (= {:value-type/error :invalid-field :reason :record-shape}
             (error-keys (star-error [] [(with-meta 'a {:non-nil :x}) '__meta])))))
    (testing "metadata keys before :hidden-field, error on a later field"
      (is (= {:value-type/error :invalid-field :reason :invalid-non-nil}
             (error-keys (star-error [:generated]
                                     (assoc registry/record-tail 0 (nn '__meta)
                                            2 (with-meta '__hash
                                                {:tag 'int :non-nil :x})))))))
    (testing ":hidden-field before tag and type"
      (is (= {:value-type/error :invalid-field :reason :hidden-field}
             (error-keys (star-error [:generated :generated]
                                     (into [(nn 'a)]
                                           (assoc registry/record-tail 0 (nn '__meta))))))))))

(deftest receiving-star-native-modes-test
  (is (= {:value-type/error :invalid-field-enforcement
          :class-name star-class
          :modes [:native]}
         (star-error [:native] [(with-meta 'a {:tag 'String :non-nil true})]))))
