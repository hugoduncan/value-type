(ns value-type.aot-fixture
  "Baseline-safe temporary source and output lifecycle for AOT scenarios."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.nio.file Files)
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
