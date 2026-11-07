(ns value-type.test-support
  "Helpers and fixtures shared by the test namespaces."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [value-type.impl.loader :as loader])
  (:import
   (clojure.lang Compiler Compiler$CompilerException Reflector)
   (java.io File StringReader)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util List)
   (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

(defn binary-name
  "Binary name of the class `name` in the namespace `ns` (a namespace or
  a symbol), as `deftype` computes it."
  [ns name]
  (str (namespace-munge ns) "." name))

(defn message-names?
  "True when the message of `x` (a Throwable or a string) contains each
  string in `parts`. Error text is not contractual: tests assert only the
  parts that a message must name."
  [x parts]
  (let [m (str (if (instance? Throwable x) (ex-message x) x))]
    (every? #(str/includes? m %) parts)))

(def record-tail
  "The four hidden fields that end a record-shaped `deftype*` field vector."
  ['__meta '__extmap (with-meta '__hash {:tag 'int})
   (with-meta '__hasheq {:tag 'int})])

(defn ex-info-data
  "Return the `ex-data` of the first `ExceptionInfo` in the cause chain of
  `t`, or nil."
  [^Throwable t]
  (some #(when (instance? clojure.lang.ExceptionInfo %) (ex-data %))
        (take-while some? (iterate ex-cause t))))

(defn- cause-chain-classes
  "Classes of the cause chain of the exception that `f` throws, or
  `:returned`."
  [f]
  (try (f) :returned
       (catch Throwable t
         (set (map class (take-while some? (iterate ex-cause t)))))))

(defn rejection
  "`expected` if the cause chain of the exception that `f` throws has it,
  else `:returned` or the classes of the cause chain."
  [expected f]
  (let [r (cause-chain-classes f)]
    (if (and (set? r) (contains? r expected)) expected r)))

(defn thrown-data
  "Call `f`. Return :no-error, or the `ex-data` of the first
  `ExceptionInfo` in the cause chain of the thrown exception. Rethrow when
  the chain has no `ExceptionInfo`."
  [f]
  (try (f) :no-error
       (catch Throwable t
         (or (ex-info-data t) (throw t)))))

(defn error-keys
  "The `:value-type/error` and `:reason` of the error data `data`; `data`
  itself when it is not a map (for example :no-error)."
  [data]
  (if (map? data) (select-keys data [:value-type/error :reason]) data))

(defmacro vt-type
  "Install the capture loader for `name` (no `defvaluetype`), then
  expand to the `deftype` form and the class."
  [name fields & specs]
  (let [cname (binary-name *ns* name)]
    (loader/install! cname [] :value :plain)
    `(let [] (deftype ~name ~fields ~@specs) ~(symbol cname))))

(defn value?
  "True when `cls` is a value class."
  [^Class cls]
  (boolean (Reflector/invokeInstanceMethod cls "isValue" (object-array 0))))

(defn on-raw-thread
  "Call `f` on a new raw `Thread` (no conveyed bindings). Return the
  result, the `Throwable` that `f` throws, or `::timeout` after
  `timeout-ms` (default 10000)."
  ([f] (on-raw-thread f 10000))
  ([f timeout-ms]
   (let [result (promise)
         thread (Thread. ^Runnable
                 (fn [] (deliver result (try (f) (catch Throwable t t)))))]
     (.start thread)
     (deref result timeout-ms ::timeout))))

(defn eval-in
  "Eval `form` with `*ns*` bound to `ns`."
  [ns form]
  (binding [*ns* (the-ns ns)] (eval form)))

(defn temp-dir
  "A new temporary directory."
  ^File []
  (.toFile (Files/createTempDirectory "vt-test" (make-array FileAttribute 0))))

(defn delete-tree
  "Delete `dir` and its contents."
  [^File dir]
  (run! #(.delete ^File %) (reverse (file-seq dir))))

(defmacro with-temp-dir
  "Bind `sym` to a new temporary directory for `body`, then delete it."
  [[sym] & body]
  `(let [~sym (temp-dir)]
     (try ~@body (finally (delete-tree ~sym)))))

(defn compile-cause
  "Call `f`. Return the cause of the `CompilerException`, or nil."
  [f]
  (try (f) nil
       (catch Compiler$CompilerException e (ex-cause e))))

(defn aot-compile
  "AOT compile `source` (the source string of namespace `ns-sym`) into
  `dir`. Return the cause of the `CompilerException`, or nil."
  [^File dir ns-sym ^String source]
  (let [path (str (.replace ^String (namespace-munge ns-sym) \. \/) ".clj")]
    (binding [*compile-files* true
              *compile-path*  (str dir)]
      (compile-cause
       #(Compiler/compile (StringReader. source) path (.getName (File. path)))))))

(defn- run-java
  "Run `java --enable-preview -cp <cp> clojure.main -e <form>`. Return
  `{:exit :out :err}`, or `{:exit ::timeout}`."
  [^String cp ^String form]
  (let [java (str (System/getProperty "java.home") "/bin/java")
        pb   (ProcessBuilder.
              ^List (vector java "--enable-preview" "-cp" cp
                            "clojure.main" "-e" form))
        p    (.start pb)
        out  (future (slurp (.getInputStream p)))
        err  (future (slurp (.getErrorStream p)))]
    (if (.waitFor p 120 TimeUnit/SECONDS)
      {:exit (.exitValue p) :out @out :err @err}
      (do (.destroyForcibly p) {:exit ::timeout :err (deref err 1000 "")}))))

(defn run-in-new-jvm
  "Run `form` (a source string) in a new JVM with `dir` on the test
  classpath. `form` must `prn` the result as its last output. Return
  `{:exit :result :err}`, with `:result` the EDN of the last line of
  stdout when `:exit` is 0, else nil. On a non-zero exit the last line
  can be the output of an earlier form; `:err` holds the cause. When
  the JVM does not stop in time, `:exit` is
  `:value-type.test-support/timeout`."
  [^File dir ^String form]
  (let [cp (str dir (System/getProperty "path.separator")
                (System/getProperty "java.class.path"))
        {:keys [exit out err]} (run-java cp form)]
    {:exit   exit
     :result (when (= 0 exit)
               (some-> out str/split-lines last edn/read-string))
     :err    err}))
