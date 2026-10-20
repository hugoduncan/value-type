(ns value-type.impl.loader-test
  "Tests for `value-type.impl.loader/install!`: the catch paths, the
  AOT write, and the error contract."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.loader :as loader]
   [value-type.impl.transform :as transform])
  (:import
   (clojure.lang Compiler Compiler$CompilerException DynamicClassLoader
                 ExceptionInfo Reflector)
   (java.io File StringReader)
   (java.lang.classfile AccessFlags ClassBuilder ClassFile ClassFile$Option ClassModel
                        ClassFile$StackMapsOption CodeBuilder)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.net URL URLClassLoader)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(defprotocol Probe
  (probe [this] "A fixture method."))

;;; Test macros

(defn- binary-name
  "Binary name of the class `name` in the namespace `ns` (a namespace or
  a symbol), as `deftype` computes it."
  [ns name]
  (str (namespace-munge ns) "." name))

(defmacro vt-type
  "Install the capture loader for `name`, as the macro task will, then
  expand to the `deftype` form and the class."
  [name fields & specs]
  (let [cname (binary-name *ns* name)]
    (loader/install! cname)
    `(let [] (deftype ~name ~fields ~@specs) ~(symbol cname))))

(defmacro vt-record
  "As `vt-type`, but with a `defrecord` form."
  [name fields]
  (let [cname (binary-name *ns* name)]
    (loader/install! cname)
    `(let [] (defrecord ~name ~fields) ~(symbol cname))))

;;; Helpers

(def ^:private test-ns (the-ns 'value-type.impl.loader-test))

(def ^:private collected
  "Classes from the forms of a top-level `do`. Each form is a separate
  top-level form, so a return value cannot collect them."
  (atom []))

(defn- eval-here [form]
  (binding [*ns* test-ns] (eval form)))

(defn- point-form [name]
  `(vt-type ~name [~(with-meta 'x {:tag 'long}) ~'y]))

(defn- mutable-form
  "A fixture with a mutable `long` field. The JVM rejects its value class."
  [name]
  `(vt-type ~name [~(with-meta 'x {:tag 'long :unsynchronized-mutable true})]))

(defn- form-str
  "Source string of `form`, with its metadata (field type tags)."
  [form]
  (binding [*print-meta* true] (pr-str form)))

(defn- value? [^Class cls]
  (boolean (Reflector/invokeInstanceMethod cls "isValue" (object-array 0))))

(defn- point-state
  "Map of the value-class flag of `cls`, the type of its field `x`, and
  the fields of a new instance."
  [^Class cls]
  (let [o (Reflector/invokeConstructor cls (object-array [3 :b]))]
    {:value? (value? cls)
     :x-type (.getType (.getField cls "x"))
     :x      (Reflector/getInstanceField o "x")
     :y      (Reflector/getInstanceField o "y")}))

(def ^:private point-expected {:value? true :x-type Long/TYPE :x 3 :y :b})

(defn- temp-dir ^File []
  (.toFile (Files/createTempDirectory "vt-loader" (make-array FileAttribute 0))))

(defn- delete-tree [^File dir]
  (run! #(.delete ^File %) (reverse (file-seq dir))))

(defmacro with-temp-dir [[sym] & body]
  `(let [~sym (temp-dir)]
     (try ~@body (finally (delete-tree ~sym)))))

(defn- compile-cause
  "Call `f`. Return the cause of the `CompilerException`, or nil."
  [f]
  (try (f) nil
       (catch Compiler$CompilerException e (ex-cause e))))

(defn- eval-cause
  "Eval `form` in the test namespace. Return the cause of the
  `CompilerException`, or nil."
  [form]
  (compile-cause #(eval-here form)))

(defn- munged-path
  "Path of the munged name `s`: `.` → `/`."
  ^String [^String s]
  (.replace s \. \/))

(defn- aot-class-file
  "Class file of the class `name` in the namespace `ns-sym`, in `dir`."
  ^File [^File dir ns-sym name]
  (File. dir (str (munged-path (binary-name ns-sym name)) ".class")))

(defn- aot-compile
  "AOT compile an `ns` form for `ns-sym` and `form` into `dir`. Return
  the cause of the `CompilerException`, or nil."
  [^File dir ns-sym form]
  (let [source-path (str (munged-path (namespace-munge ns-sym)) ".clj")]
    (binding [*compile-files* true
              *compile-path* (str dir)]
      (compile-cause
       #(Compiler/compile (StringReader. (str "(ns " ns-sym ")\n" (form-str form)))
                          source-path
                          (.getName (File. source-path)))))))

;;; Behaviour

(deftest no-binding-test
  (testing "with no LOADER binding, install! changes nothing"
    (let [result (promise)
          t (Thread. ^Runnable
                     (fn [] (deliver result
                                     (try [(loader/install! "a.B")
                                           (.isBound Compiler/LOADER)]
                                          (catch Throwable e e)))))]
      (.start t)
      (is (= [false false] (deref result 1000 ::timeout))))))

(deftest install-binding-test
  (testing "with a LOADER binding, install! sets a new child loader"
    (let [old (DynamicClassLoader.)]
      (with-bindings {Compiler/LOADER old}
        (is (true? (loader/install! "a.B")))
        (let [new-loader @Compiler/LOADER]
          (is (instance? DynamicClassLoader new-loader))
          (is (not (identical? old new-loader)))
          (is (identical? old (.getParent ^ClassLoader new-loader))))))))

(deftest catch-paths-test
  (testing "eval of one form"
    (is (= point-expected
           (point-state (eval-here (point-form (gensym "Point")))))))
  (testing "load-string"
    (let [n (gensym "Point")]
      (is (= point-expected
             (point-state (binding [*ns* test-ns]
                            (load-string (form-str (point-form n)))))))))
  (testing "top-level do: two LOADER bindings"
    (reset! collected [])
    (eval-here `(do (swap! collected conj ~(point-form (gensym "Point")))
                    (swap! collected conj ~(point-form (gensym "Point")))))
    (let [[a b :as classes] @collected]
      (is (= 2 (count classes)))
      (is (= point-expected (point-state a)))
      (is (= point-expected (point-state b)))))
  (testing "top-level let: two installs in one binding"
    (let [[a b] (eval-here `(let [a# ~(point-form (gensym "Point"))
                                  b# ~(point-form (gensym "Point"))]
                              [a# b#]))]
      (is (= point-expected (point-state a)))
      (is (= point-expected (point-state b)))))
  (testing "redefinition gives a new value class"
    (let [n (gensym "Point")
          a (eval-here (point-form n))
          b (eval-here (point-form n))]
      (is (not= a b))
      (is (= point-expected (point-state a)))
      (is (= point-expected (point-state b))))))

(deftest only-target-test
  (testing "the inner fn class stays an identity class"
    (let [cls (eval-here `(vt-type ~(gensym "Holder") [~'v]
                                   Probe
                                   (probe [~'this] (fn [] ~'this))))
          o (Reflector/invokeConstructor cls (object-array [1]))
          f (probe o)]
      (is (value? cls))
      (is (not (value? (class f))))
      (is (identical? (.getClassLoader ^Class cls)
                      (.getClassLoader (class f))))
      (is (identical? o (f))))))

(deftest aot-write-test
  (testing "the class file is the value class"
    (with-temp-dir [dir]
      (let [ns-sym 'probe.aot
            n (gensym "Point")]
        (is (nil? (aot-compile dir ns-sym (point-form n))))
        (with-open [cl (URLClassLoader.
                        (into-array URL [(.toURL (.toURI dir))])
                        (.getClassLoader clojure.lang.IType))]
          (is (value? (Class/forName (binary-name ns-sym n) false cl))))))))

(deftest aot-write-before-define-test
  (testing "the class file is the value class when the JVM rejects it"
    (with-temp-dir [dir]
      (let [ns-sym 'probe.aot-rejected
            n (gensym "Mutable")
            e (aot-compile dir ns-sym (mutable-form n))
            f (aot-class-file dir ns-sym n)]
        (is (= :jvm-rejected (:value-type/error (ex-data e))))
        (is (.isFile f))
        (when (.isFile f)
          (let [^ClassModel cm (.parse (ClassFile/of) (Files/readAllBytes (.toPath f)))]
            (is (zero? (bit-and (.flagsMask ^AccessFlags (.flags cm))
                                @#'transform/ACC_IDENTITY)))))))))

(deftest no-aot-write-test
  (testing "with *compile-files* false, no file is written"
    (with-temp-dir [dir]
      (let [cls (binding [*compile-files* false
                          *compile-path* (str dir)]
                  (eval-here (point-form (gensym "Point"))))]
        (is (value? cls) "target path ran")
        (is (empty? (rest (file-seq dir))))))))

;;; Errors

(deftest jvm-rejected-test
  (testing "a mutable field gives ClassFormatError, wrapped"
    (let [n (gensym "Mutable")
          cname (binary-name test-ns n)
          e (eval-cause (mutable-form n))
          cause (ex-cause e)]
      (is (instance? ExceptionInfo e))
      (is (= {:value-type/error :jvm-rejected :class-name cname}
             (select-keys (ex-data e) [:value-type/error :class-name])))
      (is (instance? ClassFormatError cause))
      (is (str/includes? (ex-message e) cname))
      (is (str/includes? (ex-message e) (ex-message cause))))))

(deftest transform-error-test
  (testing "a defrecord gives the transform error, not wrapped"
    (let [e (eval-cause `(vt-record ~(gensym "Rec") [~'a]))]
      (is (instance? ExceptionInfo e))
      (is (= :unexpected-constructor (:value-type/error (ex-data e)))))))

(defn- super-before-write-class
  "Value class bytes whose constructor calls `Object.<init>` before the
  write of its strict final field. Stack maps are dropped, so the build
  succeeds and the JVM verifier rejects the class."
  ^bytes [^String name]
  (let [this (ClassDesc/of name)
        cf (ClassFile/of (into-array ClassFile$Option
                                     [ClassFile$StackMapsOption/DROP_STACK_MAPS]))]
    (.build cf this
            (reify Consumer
              (accept [_ cb]
                (let [^ClassBuilder cb cb]
                  (.withVersion cb (int (ClassFile/latestMajorVersion))
                                (int ClassFile/PREVIEW_MINOR_VERSION))
                  (.withFlags cb (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL)))
                  (.withSuperclass cb ConstantDescs/CD_Object)
                  (.withField cb "v" ConstantDescs/CD_long
                              (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL
                                           @#'transform/ACC_STRICT_INIT)))
                  (.withMethodBody
                   cb "<init>" (MethodTypeDesc/ofDescriptor "(J)V")
                   (int ClassFile/ACC_PUBLIC)
                   (reify Consumer
                     (accept [_ c]
                       (let [^CodeBuilder c c]
                         (.aload c 0)
                         (.invokespecial c ConstantDescs/CD_Object "<init>"
                                         ConstantDescs/MTD_void)
                         (.aload c 0) (.lload c 1)
                         (.putfield c this "v" ConstantDescs/CD_long)
                         (.return_ c)))))))))))

(deftest link-test
  (testing "the define step links the class and wraps VerifyError"
    (let [cname (binary-name test-ns (gensym "Bad"))
          e (try (#'loader/define-value-class
                  (DynamicClassLoader.) cname (super-before-write-class cname) nil)
                 nil
                 (catch ExceptionInfo e e))]
      (is (= {:value-type/error :jvm-rejected :class-name cname}
             (select-keys (ex-data e) [:value-type/error :class-name])))
      (is (instance? VerifyError (ex-cause e))))))
