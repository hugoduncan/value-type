(ns value-type.jvm-provision-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.test :refer [deftest is testing]]
            [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts]
            [value-type.jvm-provision :as provision])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- delete-tree!
  [file]
  (when-not (Files/isSymbolicLink (.toPath file))
    (doseq [child (.listFiles file)] (delete-tree! child)))
  (io/delete-file file true))

(deftest checked-archive-test
  ;; Exercise the ARM64 installation layout with a real archive and parent JVM.
  ;; This checks provisioning, not the architecture of a publisher archive.
  (let [directory (.toFile (Files/createTempDirectory "jvm-provision-test-"
                                                      (make-array FileAttribute 0)))
        bin (io/file directory "source" "jdk" "bin")
        archive (io/file directory "jdk.tar.gz")
        root (str (io/file directory "installed"))
        java (io/file (System/getProperty "java.home") "bin" "java")]
    (try
      (.mkdirs bin)
      (Files/createSymbolicLink (.toPath (io/file bin "java")) (.toPath java)
                                (make-array FileAttribute 0))
      (is (zero? (:exit (shell/sh "tar" "-czf" (str archive)
                                  "-C" (str (io/file directory "source")) "jdk"))))
      (let [selection (-> (facts/select (facts/read-facts) :baseline :linux-aarch64 :plain)
                          (assoc :build (System/getProperty "java.runtime.version"))
                          (assoc-in [:archive :sha256] (provision/sha256 archive)))
            destination (executable/installation-directory selection root)]
        (testing "checksum failure creates no extraction directory"
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"SHA-256 mismatch"
                                (provision/install-archive!
                                 (assoc-in selection [:archive :sha256] (apply str (repeat 64 "0")))
                                 archive root)))
          (is (not (.exists (io/file root)))))
        (testing "build failure removes staging and never installs"
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"build identity mismatch"
                                (provision/install-archive! (assoc selection :build "wrong") archive root)))
          (is (empty? (.listFiles (.getParentFile
                                   (executable/installation-directory (assoc selection :build "wrong") root))))))
        (testing "a verified archive installs and an existing installation is verified"
          (is (= (executable/candidate selection {} root)
                 (provision/install-archive! selection archive root)))
          (is (.isFile (io/file destination "bin" "java")))
          (is (= (executable/candidate selection {} root)
                 (provision/provision! selection root)))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already exists"
                                (provision/install-archive! selection archive root)))))
      (finally (delete-tree! directory)))))

(deftest digest-test
  ;; Check the digest against a standard vector, independent of archive creation.
  (let [file (java.io.File/createTempFile "jvm-digest-" ".txt")]
    (try
      (spit file "abc")
      (is (= "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
             (provision/sha256 file)))
      (finally (io/delete-file file)))))

(deftest unsafe-installation-path-test
  ;; Invalid selections fail before archive access, network access or writes.
  (let [directory (.toFile (Files/createTempDirectory "jvm-path-test-"
                                                      (make-array FileAttribute 0)))
        root (io/file directory "installed")
        missing-archive (io/file directory "missing.tar.gz")
        selection (facts/select (facts/read-facts) :baseline :linux-x64 :plain)]
    (try
      (doseq [component ["." ".." "../../outside" "a/b" "a\\b" "/absolute" "C:outside"]
              field [:role :build :platform]]
        (let [invalid (assoc selection field (if (= :build field) component (keyword component)))]
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"path component"
                                (executable/installation-directory invalid root)))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"path component"
                                (provision/provision! invalid root)))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"path component"
                                (provision/install-archive! invalid missing-archive root)))
          (is (empty? (.listFiles directory)))))
      (testing "an existing role symlink cannot direct installation outside the root"
        (.mkdirs root)
        (Files/createSymbolicLink (.toPath (io/file root "baseline")) (.toPath directory)
                                  (make-array FileAttribute 0))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"escapes root"
                              (provision/provision! selection root)))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"escapes root"
                              (provision/install-archive! selection missing-archive root)))
        (is (= #{"installed"} (set (.list directory))))
        (is (= #{"baseline"} (set (.list root)))))
      (finally (delete-tree! directory)))))
