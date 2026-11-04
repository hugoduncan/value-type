(ns value-type.identity-loader-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.core]
            [value-type.identity-loader-checks :as loader-checks]
            [value-type.impl.loader :as loader]
            [value-type.impl.fields :as fields]
            [value-type.message-checks :refer [message-names?]])
  (:import (clojure.lang DynamicClassLoader)))

(deftest stale-identity-specification-test
  ;; Real sibling loaders must not make equal binary names interchangeable.
  (testing "identity capture rejects a stale exact Class before reading owner bytes"
    (let [binary-name (str "identity.loader.Nested" (gensym))
          original (loader-checks/named-class binary-name)
          replacement (loader-checks/named-class binary-name)
          target (str "identity.loader.Holder" (gensym))
          capture (#'loader/capture-loader
                   target (.getClassLoader ^Class replacement)
                   [{:field 'required :type original :enforcement :generated}]
                   :identity :plain)]
      (is (not (identical? original replacement)))
      (is (identical? replacement
                      (Class/forName binary-name false
                                     (.getClassLoader ^Class replacement))))
      (let [error (try
                    (.defineClass ^DynamicClassLoader capture target (byte-array 0) nil)
                    nil
                    (catch clojure.lang.ExceptionInfo error error))]
        (is (= {:value-type/error :invalid-field :class-name target
                :field 'required :reason :field-class-mismatch}
               (ex-data error)))
        (is (message-names? error [target "required" binary-name
                                   "redefine the field type and the dependent definition"])
            (ex-message error))))))

(deftest receiving-redefined-class-test
  ;; Fixed modes carry no producer Class; each receiver resolves its own import.
  (let [binary-name (str "identity.loader.Child" (gensym))
        original (loader-checks/named-class binary-name)
        replacement (loader-checks/named-class binary-name)
        receiver (create-ns (gensym "identity.receiver"))
        simple-name (symbol (.getSimpleName ^Class original))
        declarations [(with-meta 'required {:tag simple-name :non-nil true})]]
    (try
      (doseq [expected [original replacement]]
        (ns-unmap receiver simple-name)
        (.importClass receiver expected)
        (let [spec (first (fields/receiving-specifications
                           receiver "example.Holder" declarations [:generated]
                           #{:generated}))]
          (is (identical? expected (:type spec)))
          (is (= :generated (:enforcement spec)))))
      (is (not (identical? original replacement)))
      (finally (remove-ns (ns-name receiver))))))

(deftest public-stale-sibling-class-test
  ;; A public receiver must reject a mismatched import, not weaken typed storage.
  (let [results (loader-checks/public-stale-sibling)]
    (is (= (set loader-checks/cases) (set (keys results))))
    (doseq [[case-key result] results]
      (testing (pr-str case-key)
        (is (= loader-checks/public-stale-sibling-expected result))))))
