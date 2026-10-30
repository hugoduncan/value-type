(ns value-type.isolated-sibling-loader-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [value-type.core])
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang Compiler)
           (java.net URL URLClassLoader)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- child-bytes
  [internal-name]
  (let [writer (ClassWriter. 0)]
    (.visit writer Opcodes/V1_8 Opcodes/ACC_PUBLIC
            internal-name nil "java/lang/Object" nil)
    (let [constructor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>" "()V" nil nil)]
      (.visitCode constructor)
      (.visitVarInsn constructor Opcodes/ALOAD 0)
      (.visitMethodInsn constructor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn constructor Opcodes/RETURN)
      (.visitMaxs constructor 1 1)
      (.visitEnd constructor))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn- verify-owner
  [receiver child sibling]
  (let [owner (ns-resolve receiver 'Holder)
        field (.getDeclaredField ^Class owner "required")
        constructor (.getConstructor ^Class owner (into-array Class [Object]))
        reflective (fn [value]
                     (try
                       (.newInstance constructor (object-array [value]))
                       (catch java.lang.reflect.InvocationTargetException error
                         (throw (.getCause error)))))
        valid (.newInstance (.getConstructor ^Class child (make-array Class 0))
                            (object-array 0))
        wrong (.newInstance (.getConstructor ^Class sibling (make-array Class 0))
                            (object-array 0))]
    (is (identical? child (.getType field)))
    (is (java.lang.reflect.Modifier/isFinal (.getModifiers field)))
    (is (empty? (.getDeclaredAnnotations field)))
    (doseq [construct [@(ns-resolve receiver '->Holder)
                       (eval '(fn [value] (new Holder value)))
                       reflective]]
      (is (identical? valid (.get field (construct valid))))
      (is (thrown? NullPointerException (construct nil)))
      (is (thrown? ClassCastException (construct wrong))))))

(deftest public-isolated-sibling-construction-test
  ;; Real URL loaders bypass DynamicClassLoader's shared binary-name cache.
  (let [root (.toFile (Files/createTempDirectory "value-type-siblings"
                                                 (make-array FileAttribute 0)))
        internal-name (str "isolated/Child" (gensym))
        binary-name (.replace internal-name \/ \.)
        class-file (io/file root (str internal-name ".class"))
        parent (.getContextClassLoader (Thread/currentThread))]
    (try
      (io/make-parents class-file)
      (with-open [output (io/output-stream class-file)]
        (.write output ^bytes (child-bytes internal-name)))
      (with-open [left (URLClassLoader. (into-array URL [(.toURL (.toURI root))]) parent)
                  right (URLClassLoader. (into-array URL [(.toURL (.toURI root))]) parent)]
        (let [left-child (Class/forName binary-name true left)
              right-child (Class/forName binary-name true right)]
          (is (not (identical? left-child right-child)))
          (doseq [[local-loader child sibling] [[left left-child right-child] [right right-child left-child]] expand [macroexpand macroexpand-1] analysis [:eval :function :source]]
            (testing (str "isolated sibling through " analysis)
              (let [receiver (create-ns (gensym "isolated.receiver"))]
                (try
                  (with-bindings {Compiler/LOADER local-loader}
                    (binding [*ns* receiver]
                      (clojure.core/refer 'clojure.core)
                      (.importClass receiver child)
                      (is (identical? child (Class/forName binary-name false local-loader)))
                      (let [selected (expand
                                      (list 'value-type.core/defvaluetype 'Holder
                                            [(with-meta 'required
                                               {:tag (symbol (.getSimpleName ^Class child))
                                                :non-nil true})]))]
                        (case analysis
                          :eval (eval selected)
                          :function ((eval (list 'fn [] selected)))
                          :source (load-string (binding [*print-meta* true]
                                                 (pr-str selected)))))
                      (verify-owner receiver child sibling)))
                  (finally (remove-ns (ns-name receiver)))))))))
      (finally
        (doseq [file (reverse (file-seq root))]
          (io/delete-file file))))))
