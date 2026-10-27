(ns build-test
  (:require
   [build]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [clojure.tools.deps :as deps]
   [clojure.xml :as xml])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [java.util Arrays Properties]
   [java.util.jar JarFile]
   [java.util.concurrent TimeUnit]))

(defn- with-project
  [f]
  (let [directory (.toFile (Files/createTempDirectory
                            "value-type-build-" (make-array FileAttribute 0)))]
    (try
      (f directory)
      (finally
        (doseq [file (reverse (vec (file-seq directory)))]
          (io/delete-file file))))))

(defn- run-process
  [directory command environment]
  (let [output (io/file directory "process-output.txt")
        builder (doto (ProcessBuilder. ^java.util.List command)
                  (.directory directory)
                  (.redirectErrorStream true)
                  (.redirectOutput output))]
    (doseq [[key value] environment]
      (if (nil? value)
        (.remove (.environment builder) key)
        (.put (.environment builder) key value)))
    (let [process (.start builder)]
      (try
        (when-not (.waitFor process 60 TimeUnit/SECONDS)
          (throw (ex-info "Child process timed out" {:command command})))
        {:exit (.exitValue process) :out (slurp output)}
        (finally
          (when (.isAlive process)
            (.destroyForcibly process)
            (.waitFor process 10 TimeUnit/SECONDS)))))))

(defn- build-process
  [directory expression environment]
  ;; Absolute paths keep the child independent of its working directory.
  (let [classpath (str/join
                   java.io.File/pathSeparator
                   (mapv #(.getCanonicalPath (io/file %))
                         (str/split (System/getProperty "java.class.path")
                                    (re-pattern java.io.File/pathSeparator))))]
    (run-process directory
                 [(str (System/getProperty "java.home") "/bin/java")
                  "-cp" classpath "clojure.main" "-e"
                  (str "(require 'build 'clojure.tools.deps) (try " expression
                       " (catch Exception e (println (ex-message e)) (System/exit 1)))")]
                 environment)))

(deftest version-result-test
  ;; Version parsing is pure; result values need no process substitute.
  (testing "trimmed decimal counts form the version"
    (is (= "0.1.42" (#'build/version-from-result {:exit 0 :out " 42\n"}))))
  (testing "Git errors cannot produce a fallback version"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"git rev-list.*failed"
                          (#'build/version-from-result
                           {:exit 128 :out "42" :err "not a repository"}))))
  (testing "invalid counts are rejected"
    (doseq [output [nil "" " \n" "-1" "abc" "1.5" "1\n2"]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid commit count"
                            (#'build/version-from-result {:exit 0 :out output}))
          (pr-str output)))))

(deftest git-failure-test
  ;; Real failed Git invocations must stop before producing an artifact.
  (with-project
    (fn [directory]
      (doseq [[environment diagnostic]
              [[{} #"git rev-list --count HEAD failed"]
               [{"PATH" (.getAbsolutePath directory)} #"could not launch Git"]]]
        (let [result (build-process directory "(build/jar nil)" environment)]
          (is (not= 0 (:exit result)) result)
          (is (re-find diagnostic (:out result)) result)
          (is (not (.exists (io/file directory "target")))))))))

(deftest clean-without-git-test
  ;; Clean works without Git, removes old output, and is idempotent.
  (with-project
    (fn [directory]
      (let [stale (io/file directory "target/classes/stale.txt")]
        (io/make-parents stale)
        (spit stale "stale")
        (dotimes [_ 2]
          (let [result (build-process directory "(build/clean nil)"
                                      {"PATH" (.getAbsolutePath directory)})]
            (is (= 0 (:exit result)) result)
            (is (not (.exists (io/file directory "target"))))))))))

(defn- source-files
  []
  (into {} (comp (filter #(.isFile %))
                 (map (fn [file]
                        [(str (.relativize (.toPath (io/file "src"))
                                           (.toPath file))) file])))
        (file-seq (io/file "src"))))

(defn- prepare-project!
  [directory]
  (doseq [[path source] (merge (into {} (map (fn [[path file]] [(str "src/" path) file]))
                                     (source-files))
                               {"deps.edn" (io/file "deps.edn")
                                "LICENSE" (io/file "LICENSE")
                                "build.clj" (io/file "build.clj")})]
    (let [destination (io/file directory path)]
      (io/make-parents destination)
      (io/copy source destination)))
  (doseq [command [["git" "init" "--quiet"]
                   ["git" "add" "."]
                   ["git" "-c" "user.name=Build Test"
                    "-c" "user.email=build@example.invalid"
                    "-c" "commit.gpgsign=false" "commit" "--quiet" "-m" "fixture"]]]
    (let [result (run-process directory command {})]
      (when-not (zero? (:exit result))
        (throw (ex-info "Cannot prepare Git fixture" result)))))
  (doseq [path ["target/stale.txt" "target/classes/stale.class"
                "test/development.clj" "bench/development.clj"
                "test-resources/value_type/ArrayAccess.java"
                "dev/release/excluded.clj" "test-release/excluded_test.clj"]]
    (let [file (io/file directory path)]
      (io/make-parents file)
      (spit file "must not be packaged"))))

(defn- fixture-inputs
  [directory]
  ;; Value vectors compare bytes by content and retain the complete source paths.
  (into {}
        (comp (filter #(.isFile %))
              (map (fn [file]
                     [(str (.relativize (.toPath directory) (.toPath file)))
                      (vec (Files/readAllBytes (.toPath file)))])))
        (into [(io/file directory "deps.edn")]
              (file-seq (io/file directory "src")))))

(deftest cli-build-commands-test
  ;; Exercise tool alias resolution without the build-test classpath.
  (with-project
    (fn [directory]
      (prepare-project! directory)
      (let [config (io/file directory "cli-config")
            _ (.mkdirs config)
            _ (spit (io/file config "deps.edn") "{}")
            environment {"CLJ_CONFIG" (.getAbsolutePath config)
                         "JAVA_CMD" (str (System/getProperty "java.home") "/bin/java")}
            before-jar (fixture-inputs directory)
            result (run-process directory ["clojure" "-T:build" "jar"] environment)
            artifact (io/file directory "target/value-type-0.1.1.jar")]
        (testing "the documented jar command builds the expected artifact"
          (is (= 0 (:exit result)) result)
          (is (.isFile artifact))
          (when (.isFile artifact)
            (with-open [archive (JarFile. artifact)]
              (is (some? (.getJarEntry archive "value_type/core.clj"))))))
        (testing "jar preserves source paths, source bytes and deps.edn"
          (is (= before-jar (fixture-inputs directory))))
        ;; Clean must also work without repository metadata.
        (doseq [file (reverse (vec (file-seq (io/file directory ".git"))))]
          (io/delete-file file))
        (testing "the documented clean command removes target and is idempotent"
          (dotimes [_ 2]
            (let [before-clean (fixture-inputs directory)
                  cleaned (run-process directory ["clojure" "-T:build" "clean"] environment)]
              (is (= before-clean (fixture-inputs directory))
                  "clean preserves source paths, source bytes and deps.edn")
              (is (= 0 (:exit cleaned)) cleaned)
              (is (not (.exists (io/file directory "target")))))))))))

(defn- xml-nodes
  [node tag]
  (filterv #(and (map? %) (= tag (:tag %))) (:content node)))

(defn- xml-text
  [node & path]
  (apply str (:content (reduce #(first (xml-nodes %1 %2)) node path))))

(defn- pom-dependencies
  [pom]
  (into {} (map (fn [dependency]
                  [(symbol (xml-text dependency :groupId)
                           (xml-text dependency :artifactId))
                   {:mvn/version (xml-text dependency :version)}]))
        (xml-nodes (first (xml-nodes pom :dependencies)) :dependency)))

(defn- check-metadata
  [^JarFile archive]
  (let [prefix "META-INF/maven/org.hugoduncan/value-type/"
        pom (with-open [stream (.getInputStream archive (.getJarEntry archive (str prefix "pom.xml")))]
              (xml/parse stream))
        properties (with-open [stream (.getInputStream archive (.getJarEntry archive (str prefix "pom.properties")))]
                     (doto (Properties.) (.load stream)))]
    (testing "Maven coordinates and version agree with the artifact"
      (doseq [[tag expected] {:groupId "org.hugoduncan" :artifactId "value-type" :version "0.1.1"}]
        (is (= expected (xml-text pom tag)))
        (is (= expected (.getProperty properties (name tag))))))
    (testing "project, SCM and license metadata are complete"
      (doseq [[path expected]
              [[[:description] "A Clojure library for JEP 401 value objects."]
               [[:url] "https://github.com/hugoduncan/value-type"]
               [[:scm :url] "https://github.com/hugoduncan/value-type"]
               [[:scm :connection] "scm:git:https://github.com/hugoduncan/value-type.git"]
               [[:scm :developerConnection] "scm:git:ssh://git@github.com/hugoduncan/value-type.git"]
               [[:licenses :license :name] "Eclipse Public License 2.0"]
               [[:licenses :license :url] "https://www.eclipse.org/legal/epl-2.0/"]
               [[:licenses :license :distribution] "repo"]]]
        (is (= expected (apply xml-text pom path)) (pr-str path))))
    (testing "only project runtime dependencies are declared"
      (is (= (:deps (edn/read-string (slurp "deps.edn")))
             (pom-dependencies pom))))))

(deftest source-jar-contract-test
  ;; Inspect a real artifact from known Git history, not build interactions.
  (with-project
    (fn [directory]
      (prepare-project! directory)
      (let [user-config (io/file directory "user-config")
            user-deps (io/file user-config "deps.edn")]
        (io/make-parents user-deps)
        (spit user-deps "{:deps {unwanted/user-dependency {:mvn/version \"99.0\"}}}")
        (let [result (build-process
                      directory
                      (str "(require 'clojure.tools.deps) (assert (contains? (:deps (clojure.edn/read-string "
                           "(slurp (clojure.tools.deps/user-deps-path)))) "
                           "'unwanted/user-dependency)) (prn (build/jar nil))")
                      {"CLJ_CONFIG" (.getAbsolutePath user-config)})
              artifact (io/file directory "target/value-type-0.1.1.jar")]
          (is (= 0 (:exit result)) result)
          (when (zero? (:exit result))
            (is (= {:jar-file "target/value-type-0.1.1.jar"
                    :pom-file "target/classes/META-INF/maven/org.hugoduncan/value-type/pom.xml"
                    :version "0.1.1"}
                   (edn/read-string (str/trim (:out result))))))
          (is (.isFile artifact))
          (is (not (.exists (io/file directory "target/stale.txt"))))
          (is (not (.exists (io/file directory "target/classes/stale.class"))))
          (when (.isFile artifact)
            (with-open [archive (JarFile. artifact)]
              (let [payload (assoc (source-files) "LICENSE" (io/file "LICENSE"))
                    expected (into (set (keys payload))
                                   ["META-INF/MANIFEST.MF"
                                    "META-INF/maven/org.hugoduncan/value-type/pom.xml"
                                    "META-INF/maven/org.hugoduncan/value-type/pom.properties"])
                    actual (into #{} (comp (remove #(.isDirectory %))
                                           (map #(.getName %)))
                                 (enumeration-seq (.entries archive)))]
                (testing "the archive contains only sources, license and metadata"
                  (is (= expected actual))
                  (is (every? actual ["value_type/array.clj"
                                      "value_type/impl/array.clj"])))
                (testing "all source and license bytes are preserved"
                  (doseq [[path file] payload]
                    (when-let [entry (.getJarEntry archive path)]
                      (with-open [stream (.getInputStream archive entry)]
                        (is (Arrays/equals (Files/readAllBytes (.toPath file))
                                           (.readAllBytes stream)) path)))))
                (testing "the returned POM is the packaged POM"
                  (with-open [stream (.getInputStream archive
                                                      (.getJarEntry archive "META-INF/maven/org.hugoduncan/value-type/pom.xml"))]
                    (is (Arrays/equals
                         (Files/readAllBytes (.toPath (io/file directory "target/classes/META-INF/maven/org.hugoduncan/value-type/pom.xml")))
                         (.readAllBytes stream)))))
                (check-metadata archive)))))))))

(deftest isolated-consumer-test
  ;; Resolve the packaged contract, never the build-test JVM classpath.
  (with-project
    (fn [directory]
      (prepare-project! directory)
      (let [built (build-process directory "(prn (build/jar nil))" {})
            artifact (io/file directory "target/value-type-0.1.1.jar")]
        (is (= 0 (:exit built)) built)
        (when (zero? (:exit built))
          (let [runtime-deps
                (with-open [archive (JarFile. artifact)
                            stream (.getInputStream
                                    archive
                                    (.getJarEntry archive
                                                  "META-INF/maven/org.hugoduncan/value-type/pom.xml"))]
                  (pom-dependencies (xml/parse stream)))
                consumer-deps (io/file directory "consumer-deps.edn")
                _ (spit consumer-deps (pr-str {:paths [] :deps runtime-deps}))
                basis (deps/create-basis {:root nil :user nil
                                          :project (.getAbsolutePath consumer-deps)
                                          :aliases []})
                runtime-paths (:classpath-roots basis)
                classpath (into [(.getCanonicalPath artifact)] runtime-paths)
                expression
                (pr-str
                 '(do
                    (require '[clojure.java.io :as io])
                    (doseq [path ["value_type/core.clj" "value_type/array.clj"
                                  "value_type/impl/array.clj"]
                            :let [resource (io/resource path)]]
                      (assert (= "jar" (.getProtocol resource)))
                      (assert (= (.getCanonicalFile (io/file (System/getProperty "consumer.jar")))
                                 (.getCanonicalFile
                                  (io/file (.toURI (.getJarFileURL (.openConnection resource))))))))
                    (require '[value-type.core :refer [defvaluetype]]
                             '[value-type.array :as va])
                    ;; Controls call allocation before defining any fallback fixture.
                    (def array-result
                      (when-not (Boolean/getBoolean "consumer.arrays")
                        (mapv (fn [_]
                                (try
                                  (va/make-array nil -1 nil)
                                  :unexpected-success
                                  (catch clojure.lang.ExceptionInfo e
                                    (:value-type/error (ex-data e)))))
                              (range 2))))
                    (defvaluetype Point [^long x ^long y])
                    (let [a (Point. 3 7)
                          b (Point. 11 13)]
                      (prn {:value-class? (when-let [predicate (first (filterv #(= "isValue" (.getName %))
                                                                               (.getMethods Class)))]
                                            (.invoke predicate (class a) (object-array 0)))
                            :equal-copy? (= a (->Point 3 7))
                            :factory-class? (= Point (class (->Point 3 7)))
                            :fields [(.-x a) (.-y a) (.-x b) (.-y b)]
                            :arrays
                            (if (Boolean/getBoolean "consumer.arrays")
                              (let [values (va/make-array Point 3 a)
                                    initial? (= [a a a] (vec values))
                                    _ (aset values 1 b)
                                    rejected (mapv
                                              (fn [value]
                                                (try
                                                  (aset values 0 value)
                                                  :unexpected-success
                                                  (catch NullPointerException _ :null)
                                                  (catch ArrayStoreException _ :type)))
                                              [nil "wrong"])]
                                {:component? (= Point (.getComponentType (class values)))
                                 :length (alength values)
                                 :initial? initial?
                                 :stored? (= b (aget values 1)
                                             (java.lang.reflect.Array/get values 1))
                                 :rejected rejected
                                 :contents? (= [a b a] (vec values))})
                              array-result)}))))]
            (testing "the consumer classpath contains only artifact and runtime JARs"
              (is (seq runtime-paths))
              (is (every? #(and (.isFile (io/file %)) (str/ends-with? % ".jar"))
                          classpath)))
            (doseq [[java options expected-value?]
                    [[(str (System/getProperty "java.home") "/bin/java")
                      ["--enable-preview"
                       "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"
                       "-Dconsumer.arrays=true"] true]
                     [(str (System/getProperty "java.home") "/bin/java")
                      [] false]
                     [(or (System/getenv "VALUE_TYPE_BASELINE_JAVA")
                          "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java")
                      [] nil]]]
              (testing (str "the artifact supplies a working macro on " java)
                (let [result (run-process
                              directory
                              (into [java]
                                    (concat options
                                            ["-Dvalue-type.fallback=silent"
                                             (str "-Dconsumer.jar=" (.getCanonicalPath artifact))
                                             "-cp" (str/join java.io.File/pathSeparator classpath)
                                             "clojure.main" "-e" expression]))
                              {})]
                  (is (= 0 (:exit result)) result)
                  (when (zero? (:exit result))
                    (is (= {:value-class? expected-value?
                            :equal-copy? (boolean expected-value?)
                            :factory-class? true :fields [3 7 11 13]
                            :arrays (if expected-value?
                                      {:component? true :length 3 :initial? true
                                       :stored? true :rejected [:null :type]
                                       :contents? true}
                                      [:array-unavailable :array-unavailable])}
                           (edn/read-string (last (str/split-lines (:out result)))))
                        result)))))))))))

(deftest deployment-output-test
  ;; Step outputs append artifact data without replacing earlier outputs.
  (let [file (java.io.File/createTempFile "deploy-output-" ".txt")]
    (try
      (spit file "prior=value\n")
      (#'build/write-github-output! file
                                    {:version "0.1.9" :jar-file "target/library.jar" :pom-file "target/pom.xml"})
      (is (= "prior=value\nversion=0.1.9\njar-file=target/library.jar\npom-file=target/pom.xml\n"
             (slurp file)))
      (finally (.delete file)))))

(deftest valid-deployment-credentials-test
  ;; Nonblank fixture credentials pass validation without building or deploying.
  (testing "both deployment credentials are present"
    (is (nil? (#'build/assert-deploy-credentials!
               {"CLOJARS_USERNAME" "fixture-user"
                "CLOJARS_PASSWORD" "fixture-password"})))))

(deftest deployment-credentials-test
  ;; Real processes reject missing credentials before build or output writes.
  (with-project
    (fn [directory]
      (let [sentinel (io/file directory "target/keep")
            output (io/file directory "github-output")
            secret "fixture-secret-never-print"]
        (io/make-parents sentinel)
        (spit sentinel "unchanged")
        (spit output "prior=value\n")
        (doseq [missing ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"]
                value [nil "" " \t"]]
          (testing (str missing " rejects " (pr-str value))
            (let [result (build-process
                          directory
                          (str "(build/deploy {:github-output "
                               (pr-str (str output)) "})")
                          (assoc {"CLOJARS_USERNAME" secret
                                  "CLOJARS_PASSWORD" secret}
                                 missing value))]
              (is (not= 0 (:exit result)) result)
              (is (str/includes? (:out result) missing) result)
              (is (not (str/includes? (:out result) secret)))
              (is (= "unchanged" (slurp sentinel)))
              (is (= "prior=value\n" (slurp output))))))))))
