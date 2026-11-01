(ns value-type.temp-dir
  "Baseline-safe temporary directory support for tests. It does not load
   value-type.jvm-runner, so an in-process run does not read
   VALUE_TYPE_TEST_JVM_LIMIT."
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn with-temp-dir
  "Create a temporary directory with prefix, call f of it (a File), and
   remove the directory tree when f returns or throws. Return the value of
   f."
  [^String prefix f]
  (let [dir (.toFile (Files/createTempDirectory prefix (make-array FileAttribute 0)))]
    (try
      (f dir)
      (finally
        (run! #(Files/deleteIfExists (.toPath ^File %))
              (reverse (file-seq dir)))))))
