(ns value-type.test-support
  "Helpers and fixtures shared by the test namespaces."
  (:require
   [value-type.impl.loader :as loader])
  (:import
   (clojure.lang Compiler Compiler$CompilerException Reflector)
   (java.io File StringReader)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)))

(set! *warn-on-reflection* true)

(defn binary-name
  "Binary name of the class `name` in the namespace `ns` (a namespace or
  a symbol), as `deftype` computes it."
  [ns name]
  (str (namespace-munge ns) "." name))

(defmacro vt-type
  "Install the capture loader for `name` (no `defvaluetype`), then
  expand to the `deftype` form and the class."
  [name fields & specs]
  (let [cname (binary-name *ns* name)]
    (loader/install! cname)
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
