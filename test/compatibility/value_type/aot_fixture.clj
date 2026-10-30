(ns value-type.aot-fixture
  "Baseline-safe temporary source and output lifecycle for AOT scenarios."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [is]]
            [value-type.jvm-runner :as runner])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(def top-level-source
  "(ns fallback.aot-fixture (:require [value-type.core :refer [defvaluetype]]))\n(defvaluetype AotIdentity [x])\n")

(defn with-fixture
  "Write namespace source, call f with explicit paths, and remove all files.

   The callback owns compilation, source deletion and consumer classpaths."
  [fixture-ns source-text f]
  (let [root (.toFile (Files/createTempDirectory
                       "vt-aot-" (make-array FileAttribute 0)))
        source (io/file root "source")
        output (io/file root "classes")
        fixture (io/file source (str (-> (str fixture-ns)
                                         (str/replace "." "/")
                                         (str/replace "-" "_"))
                                     ".clj"))]
    (try
      (io/make-parents fixture)
      (.mkdirs output)
      (spit fixture source-text)
      (f {:root root :source source :output output :fixture fixture})
      (finally
        (run! #(Files/deleteIfExists (.toPath ^java.io.File %))
              (reverse (file-seq root)))))))

(defn classpath-with
  "Append dir to the classpath of jvm."
  [jvm ^File dir]
  (str (:classpath jvm) File/pathSeparator (.getPath dir)))

(defn compile-clojure
  "AOT compile fixture-ns from source into output in a child jvm.

   Optional :wrap maps the compile form to the evaluated form; :after is
   source evaluated next and defaults to printing :compiled. Return the
   run-jvm result."
  [jvm source ^File output fixture-ns
   & {:keys [wrap after] :or {wrap identity after "(prn :compiled)"}}]
  (runner/run-jvm
   (assoc jvm
          :classpath (classpath-with jvm source)
          :form (str (pr-str (wrap (list 'binding ['*compile-path* (.getPath output)]
                                         (list 'compile (list 'quote fixture-ns)))))
                     " " after))))

(defn compile-producer
  "AOT compile fixture-ns with compile-clojure, then delete the fixture
   source. paths is the with-fixture path map; :wrap and :after are as for
   compile-clojure.

   Assert exit 0, empty stderr and, without :after, the :compiled result; with
   :after the caller checks :result. Return the run-jvm result when these pass
   and the source is deleted, else nil."
  [jvm {:keys [source output ^File fixture]} fixture-ns & {:keys [wrap after]}]
  (let [result (compile-clojure jvm source output fixture-ns
                                :wrap (or wrap identity)
                                :after (or after "(prn :compiled)"))
        compiled? (and (= 0 (:exit result))
                       (= "" (:err result))
                       (or after (= :compiled (:result result))))]
    (is (= 0 (:exit result)) result)
    (is (= "" (:err result)) result)
    (when-not after
      (is (= :compiled (:result result)) result))
    (when compiled?
      (Files/delete (.toPath fixture))
      (is (not (.exists fixture)))
      result)))

(defn compile-java
  "Compile java-source against classpath into output with the javac of jvm.

   By default preview features are enabled and the release is the child JVM
   feature version. With :baseline? true, no preview or release option is
   passed (Java 8 javac rejects both). Return the run-jvm result; :result is
   the javac exit code."
  [jvm classpath ^File output java-source & {:keys [baseline?]}]
  (runner/run-jvm
   (assoc jvm
          :classpath classpath
          :form (pr-str
                 (list 'prn
                       (list '.run '(javax.tools.ToolProvider/getSystemJavaCompiler)
                             nil nil nil
                             (list 'into-array 'String
                                   (into (if baseline?
                                           []
                                           ["--enable-preview"
                                            "--release" '(str (.feature (Runtime/version)))])
                                         ["-classpath" classpath
                                          "-d" (.getPath output)
                                          java-source]))))))))

(defn reload-form
  "Return a child program that evaluates consumer-form before and after a
   reload of fixture-ns.

   consumer-form must print its result as the last stdout line. The program
   prints {:before result :after result}, so that the parent compares both
   results with the expected value."
  [fixture-ns consumer-form]
  (binding [*print-meta* true]
    (pr-str
     `(do
        (require 'clojure.edn 'clojure.string)
        (let [result# (fn []
                        (-> (with-out-str (eval '~consumer-form))
                            clojure.string/split-lines
                            last
                            clojure.edn/read-string))
              before# (result#)]
          (require '~fixture-ns :reload)
          (prn {:before before# :after (result#)}))))))
