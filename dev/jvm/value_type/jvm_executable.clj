(ns value-type.jvm-executable
  "Development-only executable selection and build verification."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [value-type.jvm-facts :as facts])
  (:import [java.io File]
           [java.util.concurrent TimeUnit]))

(defn platform
  "Return the supported host platform, or reject an unsupported host."
  [os architecture]
  (or (get {["Linux" "amd64"] :linux-x64
            ["Linux" "x86_64"] :linux-x64
            ["Linux" "aarch64"] :linux-aarch64
            ["Linux" "arm64"] :linux-aarch64
            ;; The pinned macOS baseline runs as x64 under Rosetta.
            ["Mac OS X" "x86_64"] :macos-aarch64
            ["Mac OS X" "aarch64"] :macos-aarch64
            ["Mac OS X" "arm64"] :macos-aarch64}
           [os architecture])
      (throw (ex-info "Unsupported JVM host platform"
                      {:os os :architecture architecture}))))

(defn default-root
  "Return the provisioning root outside the project's build directory."
  [home]
  (str (io/file home ".cache" "value-type" "jvms")))

(defn installation-directory
  "Return the role/build/platform directory for a selected facts entry."
  [selection root]
  (let [{:keys [role build platform]} selection
        context (select-keys selection [:role :build :platform])]
    (when-not (and (keyword? role) (keyword? platform) (string? build)
                   (every? facts/path-component? [role build platform]))
      (throw (ex-info "Invalid JVM installation path component" context)))
    (let [destination (io/file root (name role) build (name platform))
          root-path (.toPath (.getCanonicalFile (io/file root)))
          destination-path (.toPath (.getCanonicalFile destination))]
      ;; Canonical paths also reject existing symlinks that escape the root.
      (when-not (and (.startsWith destination-path root-path)
                     (not= destination-path root-path))
        (throw (ex-info "JVM installation path escapes root" context)))
      destination)))

(defn candidate
  "Select an explicit environment override or the provisioned executable."
  [selection environment root]
  (let [override (get environment (:environment selection))]
    (str (if (and override (not (str/blank? override)))
           (io/file override)
           (io/file (installation-directory selection root)
                    (get-in selection [:archive :java]))))))

(defn- inspect-build!
  [executable timeout-ms context]
  ;; Files avoid pipe backpressure and unbounded stream-reader joins.
  (let [out-file (File/createTempFile "value-type-java-out-" ".log")
        err-file (File/createTempFile "value-type-java-err-" ".log")]
    (try
      (let [process (.start (doto (ProcessBuilder.
                                   ^java.util.List [executable "-XshowSettings:properties" "-version"])
                              (.redirectOutput out-file)
                              (.redirectError err-file)))]
        (try
          (if (.waitFor process timeout-ms TimeUnit/MILLISECONDS)
            {:exit (.exitValue process) :out (slurp out-file) :err (slurp err-file)}
            (do
              (.destroyForcibly process)
              (let [terminated? (.waitFor process 5 TimeUnit/SECONDS)]
                (throw (ex-info "JVM build verification timed out"
                                (assoc context :timeout-ms timeout-ms
                                       :terminated? terminated?
                                       :out (slurp out-file) :err (slurp err-file)))))))
          (finally
            (when (.isAlive process)
              (.destroyForcibly process)
              (.waitFor process 5 TimeUnit/SECONDS)))))
      (finally
        (.delete out-file)
        (.delete err-file)))))

(defn verify!
  "Check executable access and exact java.runtime.version; return its path.

   Verification uses no preview or export flags. Overrides cannot bypass it.
   The default timeout is 30000 ms; each termination wait is at most five seconds."
  ([selection executable]
   (verify! selection executable 30000))
  ([selection executable timeout-ms]
   (when-not (and (integer? timeout-ms) (pos? timeout-ms))
     (throw (ex-info "JVM verification timeout must be positive milliseconds"
                     {:timeout-ms timeout-ms})))
   (let [file (io/file executable)
         context (assoc (select-keys selection [:role :platform :build]) :java executable)]
     (when-not (and (.isFile file) (.canExecute file))
       (throw (ex-info "Missing or non-executable JVM" context)))
     (let [{:keys [exit out err]} (inspect-build! executable timeout-ms context)
           actual (second (re-find #"(?m)^\s*java\.runtime\.version = ([^\r\n]+)" (str out err)))]
       (when-not (and (zero? exit) (= (:build selection) actual))
         (throw (ex-info "JVM build identity mismatch"
                         (assoc context :actual-build actual
                                :exit exit :out out :err err))))
       executable))))

(defn resolve!
  "Select and verify a role executable using explicit environment and root."
  [selection environment root]
  (verify! selection (candidate selection environment root)))
