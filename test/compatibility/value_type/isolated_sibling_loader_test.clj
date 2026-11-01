(ns value-type.isolated-sibling-loader-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.identity-loader-checks :as loader-checks]
            [value-type.temp-dir :as temp]
            [value-type.transfer-checks :as transfer])
  (:import (clojure.lang Compiler)
           (java.net URL URLClassLoader)))

(defn- verify-owner
  [receiver child sibling]
  (let [owner (ns-resolve receiver 'Holder)
        field (.getDeclaredField ^Class owner "required")
        valid (loader-checks/instance child)
        wrong (loader-checks/instance sibling)]
    (is (identical? child (.getType field)))
    (is (java.lang.reflect.Modifier/isFinal (.getModifiers field)))
    (is (empty? (.getDeclaredAnnotations field)))
    (is (= (checks/each-construction
            {:stored-input? true :rejections checks/checked-rejections})
           (checks/input-results receiver owner field valid wrong)))))

(deftest public-isolated-sibling-construction-test
  ;; Real URL loaders bypass DynamicClassLoader's shared binary-name cache.
  (temp/with-temp-dir
    "vt-siblings-"
    (fn [root]
      (let [internal-name (str "isolated/Child" (gensym))
            binary-name (.replace internal-name \/ \.)
            class-file (io/file root (str internal-name ".class"))
            parent (.getContextClassLoader (Thread/currentThread))]
        (io/make-parents class-file)
        (with-open [output (io/output-stream class-file)]
          (.write output ^bytes (loader-checks/class-bytes binary-name)))
        (with-open [left (URLClassLoader. (into-array URL [(.toURL (.toURI root))]) parent)
                    right (URLClassLoader. (into-array URL [(.toURL (.toURI root))]) parent)]
          (let [left-child (Class/forName binary-name true left)
                right-child (Class/forName binary-name true right)]
            (is (not (identical? left-child right-child)))
            (doseq [[loader-name local-loader child sibling]
                    [["left" left left-child right-child]
                     ["right" right right-child left-child]]
                    expand transfer/expands
                    mode transfer/modes]
              (testing (str "isolated sibling through " mode)
                (testing (str loader-name " " (pr-str expand))
                  (let [receiver (create-ns (gensym "isolated.receiver"))]
                    (try
                      (with-bindings {Compiler/LOADER local-loader}
                        (binding [*ns* receiver]
                          (clojure.core/refer 'clojure.core)
                          (.importClass receiver child)
                          (is (identical? child (Class/forName binary-name false local-loader)))
                          (transfer/evaluate-form
                           ((transfer/expanders expand)
                            (list 'value-type.core/defvaluetype 'Holder
                                  [(with-meta 'required
                                     {:tag (symbol (.getSimpleName ^Class child))
                                      :non-nil true})]))
                           mode)
                          (verify-owner receiver child sibling)))
                      (finally (remove-ns (ns-name receiver))))))))))))))
