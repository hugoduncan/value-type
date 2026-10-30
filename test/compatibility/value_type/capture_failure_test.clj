(ns value-type.capture-failure-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.impl.loader :as loader]
            [value-type.message-checks :refer [message-names?]])
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader ExceptionInfo)))

(defn- empty-owner-bytes
  [binary-name]
  (let [writer (ClassWriter. 0)]
    (.visit writer Opcodes/V1_8 Opcodes/ACC_PUBLIC
            (.replace ^String binary-name \. \/) nil "java/lang/Object" nil)
    (.visitEnd writer)
    (.toByteArray writer)))

(deftest unsupported-owner-capture-test
  ;; Real capture must not define unchecked output after a transform rejects it.
  (testing "missing selected storage rejects repeatedly without weaker output"
    (let [target (str "capture.failure.Owner" (gensym))
          capture (#'loader/capture-loader
                   target (.getContextClassLoader (Thread/currentThread))
                   [{:field 'required :type String :enforcement :generated}]
                   :identity)
          bytes (empty-owner-bytes target)
          errors (java.io.StringWriter.)]
      (binding [*err* errors]
        (dotimes [_ 2]
          (let [failure (try
                          (.defineClass ^DynamicClassLoader capture target bytes nil)
                          nil
                          (catch ExceptionInfo error error))]
            (is (= :unsupported-shape (:value-type/error (ex-data failure))))
            (is (message-names? failure [target "missing non-nil field storage"
                                         "use Clojure 1.12"])
                (ex-message failure))
            (is (thrown? ClassNotFoundException
                         (Class/forName target false capture))))))
      (is (= "" (str errors))))))
