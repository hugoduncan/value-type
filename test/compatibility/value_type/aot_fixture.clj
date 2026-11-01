(ns value-type.aot-fixture
  "Baseline-safe temporary source and output lifecycle for AOT scenarios."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]
            [value-type.temp-dir :as temp])
  (:import (java.io File)
           (java.nio.file Files)))

(def top-level-source
  "(ns fallback.aot-fixture (:require [value-type.core :refer [defvaluetype]]))\n(defvaluetype AotIdentity [x])\n")

(defn with-fixture
  "Write namespace source, call f with explicit paths, and remove all files.

   The callback owns compilation, source deletion and consumer classpaths."
  [fixture-ns source-text f]
  (temp/with-temp-dir
    "vt-aot-"
    (fn [root]
      (let [source (io/file root "source")
            output (io/file root "classes")
            fixture (io/file source (str (-> (str fixture-ns)
                                             (str/replace "." "/")
                                             (str/replace "-" "_"))
                                         ".clj"))]
        (io/make-parents fixture)
        (.mkdirs output)
        (spit fixture source-text)
        (f {:root root :source source :output output :fixture fixture})))))

(def ^:private compiled
  "The default result that a compile child prints."
  :compiled)

(defn classpath-with
  "Append dir to the classpath of jvm."
  [jvm ^File dir]
  (str (:classpath jvm) File/pathSeparator (.getPath dir)))

(defn compile-form
  "Return the form that AOT compiles fixture-ns into output."
  [^File output fixture-ns]
  (list 'binding ['*compile-path* (.getPath output)]
        (list 'compile (list 'quote fixture-ns))))

(defn compile-clojure-child
  "Return the run-jvm option map that AOT compiles fixture-ns from source
   into output in a child jvm.

   Optional :wrap maps the compile form to the evaluated form; :after is
   source evaluated next and defaults to printing :compiled. :kind is the
   recorder child kind (default :aot-compile)."
  [jvm source ^File output fixture-ns
   & {:keys [wrap after kind]
      :or {wrap identity after (pr-str (list 'prn compiled)) kind :aot-compile}}]
  (assoc jvm
         :kind kind
         :classpath (classpath-with jvm source)
         :form (str (pr-str (wrap (compile-form output fixture-ns)))
                    " " after)))

(defn compile-clojure
  "Run compile-clojure-child (same arguments) and return the run-jvm result."
  [jvm source output fixture-ns & options]
  (runner/run-jvm (apply compile-clojure-child jvm source output fixture-ns options)))

(defn compile-producer
  "AOT compile fixture-ns with compile-clojure, then delete the fixture
   source. paths is the with-fixture path map; :wrap, :after and :kind are
   as for compile-clojure.

   Assert exit 0, empty stderr and, without :after, the :compiled result; with
   :after the caller checks :result. Return the run-jvm result when these pass
   and the source is deleted, else nil."
  [jvm {:keys [source output ^File fixture]} fixture-ns & {:keys [after] :as options}]
  (let [result (compile-clojure jvm source output fixture-ns
                                (select-keys options [:wrap :after :kind]))
        compiled? (harness/assert-run result (cond-> {:exit 0 :err ""}
                                               (nil? after) (assoc :result compiled)))]
    (when compiled?
      (Files/delete (.toPath fixture))
      (is (not (.exists fixture)))
      result)))

(defn produce
  "Run compile-producer (same arguments and options) under the support label
   \"producer\"; return true when the compile passes."
  [jvm paths fixture-ns & options]
  (testing "producer"
    (boolean (apply compile-producer jvm paths fixture-ns options))))

(defn with-produced
  "Write fixture-ns source-text (with-fixture), AOT compile it on producer
   (produce, with options), and call f with the with-fixture path map only
   when the compile passes. Remove all files."
  [fixture-ns source-text producer f & options]
  (with-fixture
    fixture-ns source-text
    (fn [paths]
      (when (apply produce producer paths fixture-ns options)
        (f paths)))))

(defn descriptor
  "Return the field descriptor of class-name, for example
   Ljava/lang/Long; of java.lang.Long."
  [class-name]
  (str "L" (.replace ^String class-name "." "/") ";"))

(defn persisted-output?
  "Return a described cell predicate of a field-aot-checks/inspect-output
   result: true when the result without :companion-references equals
   storage, and :companion-references has one or more items, each
   descriptor."
  [storage descriptor]
  (harness/described
   (harness/with-entry storage :companion-references
     (harness/repeated :+ (harness/fixed descriptor)))))

(defn inspection-cell
  "Return the cell with context that inspects field of the saved
   checked.aot-fixture Holder in output (field-aot-checks/inspect-output)
   and expects expected."
  [context ^File output field expected]
  {:context context
   :form (registry/check-form 'value-type.field-aot-checks/inspect-output (.getPath output)
                              "checked/aot_fixture/Holder" field)
   :expected expected})

(defn inspection-child
  "Return the child on jvm (policy error) with one inspection-cell of
   output for each [context field expected] in cells."
  [jvm ^File output cells]
  (harness/cell-child
   (assoc jvm :policy "error" :fixture-ns 'checked.aot-fixture)
   (for [[context field expected] cells]
     (inspection-cell context output field expected))))

(defn consumer-child
  "Return a one-cell child for a source-free AOT consumer (isolation rule 4:
   one artifact; a first load in the cell is rule 5). The cell evaluates
   form on jvm under policy (nil: unset) with classpath; expected is the
   cell expectation.
   fixture-ns (or nil) is for the child label only.

   Options: :context (default [(pr-str [java jvm-opts policy])], policy
   omitted when nil); :fallback? and :warning as for cell-harness cells."
  [fixture-ns jvm classpath policy form expected & {:keys [context fallback? warning]}]
  (harness/cell-child
   (cond-> (assoc jvm :policy policy :classpath classpath)
     fixture-ns (assoc :fixture-ns fixture-ns))
   [(cond-> {:context (or context
                          [(apply harness/runtime-context jvm (when policy [policy]))])
             :form form
             :expected expected}
      fallback? (assoc :fallback? true :warning warning))]))

(defn compile-java-child
  "Return the run-jvm option map that compiles java-source against classpath
   into output with the javac of jvm.

   By default preview features are enabled and the release is the child JVM
   feature version. With :baseline? true, no preview or release option is
   passed (Java 8 javac rejects both). The child prints the javac exit code."
  [jvm classpath ^File output java-source & {:keys [baseline?]}]
  (assoc jvm
         :kind :java-compile
         :classpath classpath
         :form (pr-str
                (list 'prn
                      (list '.run '(javax.tools.ToolProvider/getSystemJavaCompiler)
                            nil nil nil
                            (list 'into-array 'String
                                  (into (if baseline?
                                          []
                                          [config/preview-option
                                           "--release" '(str (.feature (Runtime/version)))])
                                        ["-classpath" classpath
                                         "-d" (.getPath output)
                                         java-source])))))))

(defn compile-java
  "Run compile-java-child (same arguments) and return the run-jvm result;
   :result is the javac exit code."
  [jvm classpath output java-source & options]
  (runner/run-jvm (apply compile-java-child jvm classpath output java-source options)))

(defn javac
  "Run compile-java (same arguments) under the support label \"javac\";
   assert exit 0 and javac exit 0; return true when both pass."
  [jvm classpath output java-source & options]
  (testing "javac"
    (harness/assert-run (apply compile-java jvm classpath output java-source options)
                        {:exit 0 :result 0})))

(defn source-free-consumers
  "AOT compile fixture-ns from source-text on producer (with-produced),
   then run one consumer-child of form for each jvm and policy (jvm outer),
   with the expected value (expected policy). Each child uses the classpath
   (classpath-with jvm output).

   Options: :java [java-source & javac-options] compiles java-source first
   (javac on producer without :policy, classpath (classpath-with producer
   output)); the cells run only when it passes, and each child uses that
   classpath. :produce is a map of produce options (for example :wrap).
   :context-of is a function of jvm and policy that gives the cell context.
   The other options are consumer-child options."
  [fixture-ns source-text producer jvms policies form expected
   & {:keys [java context-of] :as options}]
  (with-produced
    fixture-ns source-text producer
    (fn [{:keys [output]}]
      (let [compiler (dissoc producer :policy)
            java-classpath (classpath-with compiler output)]
        (when (or (nil? java)
                  (apply javac compiler java-classpath output java))
          (harness/run-cells
           (for [jvm jvms
                 policy policies]
             (consumer-child fixture-ns jvm
                             (if java java-classpath (classpath-with jvm output))
                             policy form (expected policy)
                             (cond-> (dissoc options :java :produce :context-of)
                               context-of (assoc :context (context-of jvm policy)))))))))
    (:produce options)))

(defn incompatible-consumers
  "Run source-free-consumers of fixture-ns from source-text on producer for
   each jvm of jvms and each policy (config/policies): each cell loads the
   saved class of spec (consumer-aot-checks/incompatible-consumer-form) and
   expects consumer-aot-checks/incompatible-result. The context is the default
   consumer-child context with the prefix \"<java> / <policy>\"."
  [fixture-ns source-text producer jvms spec]
  (source-free-consumers
   fixture-ns source-text producer jvms config/policies
   (checks/incompatible-consumer-form spec)
   (constantly checks/incompatible-result)
   :context-of (fn [jvm policy]
                 [(str (:java jvm) " / " policy)
                  (harness/runtime-context jvm policy)])))

(defn reload-form
  "Return a child form whose value is {:before v :after v}: v is the value
   of consumer-form before and after a reload of fixture-ns."
  [fixture-ns consumer-form]
  `(let [before# (eval '~consumer-form)]
     (require '~fixture-ns :reload)
     {:before before# :after (eval '~consumer-form)}))
