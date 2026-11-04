(ns value-type.deftype-star-test
  "Tests for `value-type.core/defvaluetype*` declaration validation and
  value-expansion receiving."
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.core :as vt]
   [value-type.impl.runtime :as runtime]
   [value-type.test-support :as support :refer [record-tail]]))

(set! *warn-on-reflection* true)

(def ^:private class-name "value_type.deftype_star_test.T")

(defn- expansion-error
  "Macroexpand `(defvaluetype* T value_type.deftype_star_test.T fields)`.
  Return :no-error, or the `ex-data` of the thrown error."
  [fields]
  (support/thrown-data
   #(macroexpand-1 (list `vt/defvaluetype* 'T (symbol class-name) fields))))

(defn- evaluation-error
  "Evaluate a `defvaluetype*` form of a new class with `fields`. Return the
  Throwable, or nil."
  [fields]
  (let [n (gensym "Unchecked")]
    (try (support/eval-in 'value-type.deftype-star-test
                          (list `vt/defvaluetype* n
                                (symbol (str "value_type.deftype_star_test." n))
                                fields))
         nil
         (catch Throwable t t))))

(defn- shape-error [field]
  {:value-type/error :invalid-field
   :class-name       class-name
   :field            field
   :reason           :record-shape})

(defn- tagged [sym tag] (with-meta sym {:tag tag}))

(deftest accepted-shapes-test
  (testing "plain shape"
    (is (= :no-error (expansion-error '[a b]))))
  (testing "record shape with zero declared fields"
    (is (= :no-error (expansion-error record-tail))))
  (testing "record shape with declared fields"
    (is (= :no-error (expansion-error (into '[a b] record-tail)))))
  (testing "record shape with nil tags on __meta and __extmap"
    (is (= :no-error
           (expansion-error
            (-> record-tail
                (assoc 0 (with-meta '__meta {:tag nil}))
                (assoc 1 (with-meta '__extmap {:tag nil}))))))))

(deftest record-shape-rejection-test
  (testing "hidden field not trailing"
    (is (= (shape-error '__meta) (expansion-error '[__meta x]))))
  (testing "trailing subset"
    (let [h (tagged '__hash 'int)]
      (is (= (shape-error h)
             (expansion-error ['x h (tagged '__hasheq 'int)])))))
  (testing "fewer than four fields with all hidden names"
    (is (= (shape-error '__meta) (expansion-error '[__meta]))))
  (testing "tag on __meta"
    (let [m (tagged '__meta 'Object)]
      (is (= (shape-error m)
             (expansion-error (assoc record-tail 0 m))))))
  (testing "wrong tags on hash fields"
    (doseq [tag ['long 'Integer "int" ''int nil 'java.lang/int]]
      (let [h (tagged '__hash tag)]
        (is (= (shape-error h) (expansion-error (assoc record-tail 2 h)))
            (pr-str tag)))))
  (testing "qualified hidden name"
    (let [m 'x/__meta]
      (is (= (shape-error m) (expansion-error (assoc record-tail 0 m))))))
  (testing "wrong order reports the first misplaced field"
    (is (= (shape-error '__extmap)
           (expansion-error ['__extmap '__meta (record-tail 2)
                             (record-tail 3)]))))
  (testing "non-symbol elements have no hidden name"
    (is (= (shape-error '__meta)
           (expansion-error ['__meta "__extmap" (record-tail 2)
                             (record-tail 3)])))))

(deftest field-metadata-rejection-test
  (testing ":non-nil by presence, any value"
    (doseq [v [true false nil]]
      (let [f (with-meta 'a {:non-nil v})]
        (is (= {:value-type/error :invalid-field :class-name class-name
                :field f :reason :non-nil-unsupported}
               (expansion-error [f]))))))
  (testing ":nil-restricted by presence, before :non-nil"
    (let [f (with-meta 'a {:nil-restricted nil :non-nil true})]
      (is (= {:value-type/error :invalid-field :class-name class-name
              :field f :reason :removed-nil-restricted :value nil}
             (expansion-error [f])))))
  (testing "first faulty field in field order"
    (let [a (with-meta 'a {:non-nil true})
          b (with-meta 'b {:nil-restricted true})]
      (is (= :non-nil-unsupported (:reason (expansion-error [a b]))))))
  (testing "hidden fields are checked"
    (let [m (with-meta '__meta {:non-nil true})]
      (is (= :non-nil-unsupported
             (:reason (expansion-error (assoc record-tail 0 m))))))))

(deftest validation-order-test
  (let [mutable-hash (with-meta '__hash {:tag 'int :unsynchronized-mutable true})
        non-nil (with-meta 'a {:non-nil true})]
    (testing "mutable field first, including hash fields"
      (is (= {:value-type/error :mutable-field :class-name class-name
              :fields [mutable-hash]}
             (expansion-error (into [non-nil '__meta]
                                    (assoc record-tail 2 mutable-hash))))))
    (testing "record shape before field metadata"
      (is (= :record-shape
             (:reason (expansion-error [non-nil '__meta '__extmap])))))))

(deftest unchecked-shapes-test
  ;; Non-symbol elements and non-vector fields pass the checks; deftype*
  ;; rejects them, with no value-type error data.
  (doseq [[label fields] [["non-symbol element with metadata"
                           [(with-meta [] {:non-nil true})]]
                          ["non-vector fields" '(__meta x)]]]
    (testing label
      (is (= :no-error (expansion-error fields)))
      (let [t (evaluation-error fields)]
        (is (some? t))
        (is (nil? (support/ex-info-data t)))))))

;;; Receiving: a value expansion evaluated apart from its macroexpansion
;;; fails; it never gives an identity class and never selects fallback.

(defn- star-form
  "A `defvaluetype*` form of simple name n in this namespace; record shape
  when record? is true."
  [n record?]
  (list `vt/defvaluetype* n
        (symbol (str "value_type.deftype_star_test." n))
        (if record? (into '[a] record-tail) '[a])))

(deftest value-expansion-receiving-test
  (when (:available? (runtime/capability))
    (doseq [record? [false true]]
      (testing (if record? "record shape" "plain shape")
        (testing "expansion with no compiler loader gives :not-installed"
          (let [n         (gensym "StarNoInstall")
                expansion (support/on-raw-thread
                           #(binding [*ns* (the-ns 'value-type.deftype-star-test)]
                              (macroexpand (star-form n record?))))]
            (is (seq? expansion) (str "expansion: " expansion))
            (is (= {:value-type/error :not-installed
                    :class-name       (str "value_type.deftype_star_test." n)}
                   (support/thrown-data #(binding [*ns* (the-ns 'value-type.deftype-star-test)]
                                           (eval expansion)))))))
        (testing "expansion evaluated by a later eval gives :not-value-class"
          (let [n (gensym "StarMacroExpanded")]
            (is (= {:value-type/error :not-value-class
                    :class-name       (str "value_type.deftype_star_test." n)}
                   (support/thrown-data
                    #(binding [*ns* (the-ns 'value-type.deftype-star-test)]
                       (eval (list 'eval (list 'macroexpand
                                               (list 'quote (star-form n record?)))))))))))))))
