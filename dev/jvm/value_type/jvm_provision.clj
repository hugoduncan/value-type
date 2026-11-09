(ns value-type.jvm-provision
  "Checked JVM archive installation for local development and CI."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts])
  (:import [java.nio.file Files CopyOption]
           [java.security MessageDigest]))

(defn sha256
  "Return the lowercase SHA-256 digest of a file."
  [file]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 65536)]
    (with-open [input (io/input-stream file)]
      (loop []
        (let [size (.read input buffer)]
          (when (pos? size)
            (.update digest buffer 0 size)
            (recur)))))
    (apply str (mapv #(format "%02x" (bit-and 255 %)) (.digest digest)))))

(defn- command!
  [selection & args]
  (let [result (apply shell/sh args)]
    (when-not (zero? (:exit result))
      (throw (ex-info "JVM provisioning command failed"
                      (merge (select-keys selection [:role :platform :build])
                             result {:command (vec args)}))))
    result))

(defn- delete-tree!
  [file]
  ;; Do not follow archive symlinks during cleanup.
  (when-not (Files/isSymbolicLink (.toPath (io/file file)))
    (doseq [child (.listFiles (io/file file))]
      (delete-tree! child)))
  (io/delete-file file true))

(defn install-archive!
  "Check a local archive before extraction, verify its JVM, then install it.

   Archives have one enclosing directory. Existing installations are not replaced.
   A failed checksum never reaches tar. A failed install leaves no destination."
  [selection archive root]
  (let [destination (executable/installation-directory selection root)
        actual (sha256 archive)
        context (select-keys selection [:role :platform :build])]
    (when-not (= (get-in selection [:archive :sha256]) actual)
      (throw (ex-info "JVM archive SHA-256 mismatch"
                      (assoc context :expected (get-in selection [:archive :sha256])
                             :actual actual))))
    (when (.exists destination)
      (throw (ex-info "JVM installation already exists" context)))
    (.mkdirs (.getParentFile destination))
    (let [staging (.toFile (Files/createTempDirectory
                            (.toPath (.getParentFile destination)) "install-"
                            (make-array java.nio.file.attribute.FileAttribute 0)))]
      (try
        (command! selection "tar" "-xzf" (str archive) "--strip-components=1"
                  "-C" (str staging))
        (executable/verify! selection
                            (str (io/file staging (get-in selection [:archive :java]))))
        (Files/move (.toPath staging) (.toPath destination) (make-array CopyOption 0))
        (executable/candidate selection {} root)
        (finally (delete-tree! staging))))))

(defn provision!
  "Download and check a selected JVM, or verify its existing installation.

   curl follows HTTPS redirects only. Downloads and extraction use temporary
   paths; only a verified executable is installed at the role's final path."
  [selection root]
  (let [java (executable/candidate selection {} root)]
    (if (.exists (executable/installation-directory selection root))
      (executable/verify! selection java)
      (let [archive (java.io.File/createTempFile "value-type-jvm-" ".tar.gz")]
        (try
          (command! selection "curl" "--fail" "--location" "--silent" "--show-error"
                    "--proto" "=https" "--proto-redir" "=https"
                    "--connect-timeout" "30" "--max-time" "600"
                    "--output" (str archive) (get-in selection [:archive :url]))
          (install-archive! selection archive root)
          (finally (io/delete-file archive true)))))))

(defn run!
  "Provision ROLE [PLATFORM [ROOT]] from the current facts and print its path."
  [args]
  (when-not (<= 1 (count args) 3)
    (throw (ex-info "Usage: bb jvm:provision ROLE [PLATFORM [ROOT]]" {})))
  (let [[role platform-name root] args
        platform (if platform-name (keyword platform-name)
                     (executable/platform (System/getProperty "os.name")
                                          (System/getProperty "os.arch")))
        selection (facts/select (facts/read-facts) (keyword role) platform :plain)]
    (println (provision! selection
                         (or root (executable/default-root (System/getProperty "user.home")))))))
