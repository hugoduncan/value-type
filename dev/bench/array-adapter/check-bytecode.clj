#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns value-type.array-adapter.check-bytecode)

(require '[babashka.process :as process]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[value-type.jvm-executable :as executable]
         '[value-type.jvm-facts :as facts])

(def targets
  {"array_allocation$value_class_QMARK_" ["java/lang/Class.isValue:()Z"]
   "array_allocation$allocate" ["jdk/internal/value/ValueClass.newNullRestrictedAtomicArray:(Ljava/lang/Class;ILjava/lang/Object;)[Ljava/lang/Object;"]
   "array_inspection$value_class_QMARK_" ["java/lang/Class.isValue:()Z"]
   "array_inspection$inspect_array" ["jdk/internal/value/ValueClass.isNullRestrictedArray:(Ljava/lang/Object;)Z"
                                     "jdk/internal/value/ValueClass.isAtomicArray:(Ljava/lang/Object;)Z"
                                     "jdk/internal/value/ValueClass.isFlatArray:(Ljava/lang/Object;)Z"]
   "array$value_class_QMARK_" []
   "array$allocate" []
   "array$inspect_array" []})

(defn checked
  "Run a real tool with a finite bound and retain its combined output."
  [command log]
  (let [child (process/process command {:out (io/file log) :err :out})
        result (deref child 120000 ::timeout)]
    (when (= ::timeout result)
      (process/destroy-tree child)
      (throw (ex-info "Bytecode tool timed out" {:command command})))
    (when-not (zero? (:exit result))
      (throw (ex-info "Bytecode tool failed" {:command command :log log})))
    (slurp log)))

(defn verify-disassembly
  "Check real compiled operation classes, including their forwarding methods."
  [class-name text expected]
  (assert (str/includes? text "invokeStatic(") class-name)
  (assert (not (re-find #"java/lang/reflect/Method\.invoke|clojure/lang/Reflector|java/lang/invoke/MethodHandle[^ ]*\.invoke" text))
          (str "Indirect JVM dispatch in " class-name))
  (doseq [target expected]
    (assert (some #(and (re-find #"\b(invokevirtual|invokestatic)\b" %) (str/includes? % target)) (str/split-lines text)) (str class-name " lacks direct call to " target)))
  ;; The only adapter helper is the inspection component predicate. Check its
  ;; compiled class above and retain this Var target as part of the path evidence.
  (when (= class-name "array_inspection$inspect_array")
    (assert (str/includes? text "String value-type.impl.array-inspection"))
    (assert (str/includes? text "String value-class?"))))

(let [[output] *command-line-args*
      _ (assert output "Supply a new evidence directory")
      directory (io/file output)
      _ (assert (not (.exists directory)) "Do not overwrite evidence")
      _ (.mkdirs directory)
      classes (.toFile (java.nio.file.Files/createTempDirectory "array-bytecode-" (make-array java.nio.file.attribute.FileAttribute 0)))
      platform (executable/platform (System/getProperty "os.name") (System/getProperty "os.arch"))
      selection (facts/select (facts/read-facts) :valhalla platform :export)
      java (executable/resolve! selection (System/getenv)
                                (executable/default-root (System/getProperty "user.home")))
      javap (str (io/file (.getParentFile (io/file java)) "javap"))
      classpath (str/trim (:out (process/shell {:out :string} "clojure -Spath")))
      expression (pr-str `(binding [*compile-path* ~(.getPath classes)]
                            (doseq [namespace# '[value-type.impl.array
                                                 value-type.impl.array-allocation
                                                 value-type.impl.array-inspection]]
                              (clojure.core/compile namespace#))))
      compile-command (into [java] (concat (:jvm-opts selection)
                                           ["-cp" classpath "clojure.main" "-e" expression]))
      commands (atom [compile-command])]
  (try
    (spit (io/file directory "environment.edn")
          (pr-str {:runtime selection :java java :classpath classpath
                   :revision (str/trim (:out (process/shell {:out :string} "git rev-parse HEAD")))
                   :host (:out (process/shell {:out :string} "uname -a"))}))
    (let [compiler-output (checked compile-command (io/file directory "compile.log"))]
      (assert (not (str/includes? compiler-output "Reflection warning")) compiler-output))
    (doseq [[class-name expected] (sort-by key targets)]
      (let [command [javap "-classpath" (.getPath classes) "-c" "-p" "-s"
                     (str "value_type.impl." class-name)]
            text (checked command (io/file directory (str class-name ".javap")))]
        (swap! commands conj command)
        (verify-disassembly class-name text expected)))
    (spit (io/file directory "result.edn")
          (pr-str {:pass? true :classes (count targets) :profile :export
                   :targets targets}))
    (println "Verified" (count targets) "operation classes without preview")
    (finally
      (spit (io/file directory "commands.edn") (pr-str @commands))
      (doseq [file (reverse (file-seq classes))] (io/delete-file file)))))
