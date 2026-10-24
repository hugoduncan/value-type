;; Run with the runtime-only classpath; no optional namespace is required.
(require '[clojure.edn :as edn])

(defn class-present? [class-name]
  (try
    (Class/forName class-name false (.getContextClassLoader (Thread/currentThread)))
    true
    (catch ClassNotFoundException _ false)))

(defn value-method-present? []
  (try
    (.getMethod Class "isValue" (make-array Class 0))
    true
    (catch NoSuchMethodException _ false)))

(let [result {:java-version (System/getProperty "java.runtime.version")
              :clojure-version (clojure-version)
              :classfile-api (class-present? "java.lang.classfile.ClassFile")
              :is-value-method (value-method-present?)}
      expected (edn/read-string (first *command-line-args*))]
  (prn result)
  (assert (= expected (select-keys result [:classfile-api :is-value-method]))))
