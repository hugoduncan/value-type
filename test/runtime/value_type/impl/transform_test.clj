(ns value-type.impl.transform-test
  "Tests for `value-type.impl.transform/->value-class`: structure,
  round trip on the running JVM, and the error contract."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.transform :as transform]
   [value-type.test-support :refer [value?]])
  (:import
   (clojure.lang DynamicClassLoader ExceptionInfo Reflector)
   (java.io File)
   (java.lang.classfile AccessFlags Attribute AttributedElement ClassBuilder
                        ClassFile ClassModel CodeBuilder CodeModel FieldModel
                        Label MethodModel Opcode Signature TypeKind)
   (java.lang.classfile.attribute CodeAttribute SourceDebugExtensionAttribute
                                  SourceFileAttribute)
   (java.lang.classfile.constantpool ClassEntry MemberRefEntry)
   (java.lang.classfile.instruction FieldInstruction InvokeInstruction
                                    LineNumber LoadInstruction LocalVariable
                                    LocalVariableType ReturnInstruction)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(defprotocol Probe
  (probe [this x] "A fixture method."))

;;; Fixture helpers

(defn- compile-fixture
  "Evaluate the form that `make-form` returns for a new unique name,
  with class file output to a new temp directory. Return a map with
  `:bytes`, `:class` (the class that `eval` returned) and `:dir`.
  The JVM deletes the directory and its files at exit (`deleteOnExit`
  deletes in reverse order of registration, so children go first)."
  [make-form]
  (let [dir (.toFile (Files/createTempDirectory
                      "vt-fixture"
                      (make-array FileAttribute 0)))
        cls (binding [*ns* (the-ns 'value-type.impl.transform-test)
                      *compile-files* true
                      *compile-path* (str dir)]
              (eval (make-form (gensym "Fixture"))))
        file (io/file dir (str (.replace (.getName ^Class cls) \. \/) ".class"))]
    (run! #(.deleteOnExit ^File %) (file-seq dir))
    {:bytes (Files/readAllBytes (.toPath file))
     :class cls
     :dir dir}))

(def ^:private shared-object (Object.))

(def ^:private fixtures
  "Verification 1 fixtures. `:args` are the constructor arguments,
  `:call` is the argument of `probe` (nil: no method), `:receiver?` is
  true when `probe` returns the receiver."
  {:no-fields
   {:form (fn [n] `(deftype ~n []))
    :args []}
   :five-fields
   {:form (fn [n] `(deftype ~n [~(with-meta 'a {:tag 'int})
                                ~(with-meta 'b {:tag 'long})
                                ~(with-meta 'c {:tag 'double})
                                ~(with-meta 'd {:tag 'boolean})
                                ~'e]
                     Probe
                     (probe [~'_ ~'x] [~'a ~'b ~'c ~'d ~'e ~'x])))
    :args [(int 7) 8 2.5 true shared-object]
    :call 3}
   :branch
   {:form (fn [n] `(deftype ~n [~(with-meta 'n {:tag 'long})]
                     Probe
                     (probe [~'_ ~'x] (if (pos? ~'x) (+ ~'n ~'x) (- ~'n ~'x)))))
    :args [10]
    :call -4}
   :inner-fn
   {:form (fn [n] `(deftype ~n [~'v]
                     Probe
                     (probe [~'this ~'x] ((fn [] ~'this)))))
    :args [shared-object]
    :call 1
    :receiver? true}
   :static-constants
   {:form (fn [n] `(deftype ~n [~'v]
                     Probe
                     (probe [~'_ ~'x] [:fixture/k ~'v ~'x])))
    :args [shared-object]
    :call 5}})

(def ^:private compiled
  (delay (update-vals fixtures #(merge % (compile-fixture (:form %))))))

(defn- fixture [k] (get @compiled k))

(defn- build-class
  "Build a class `name` with superclass `super` and the instance fields
  `fields` (a list of `[name ClassDesc]`). When `ctor-desc` is not
  nil, add one constructor with descriptor `ctor-desc` and code from
  `code!`. The short form has superclass `Object` and one `long` field
  `v`."
  (^bytes [name ctor-desc code!]
   (build-class name ConstantDescs/CD_Object [["v" ConstantDescs/CD_long]]
                ctor-desc code!))
  (^bytes [^String name ^ClassDesc super fields ^String ctor-desc code!]
   (let [this (ClassDesc/of name)]
     (.build (ClassFile/of) this
             (reify Consumer
               (accept [_ cb]
                 (let [^ClassBuilder cb cb]
                   (.withFlags cb (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL
                                               @#'transform/ACC_IDENTITY)))
                   (.withSuperclass cb super)
                   (doseq [[^String fname ^ClassDesc ftype] fields]
                     (.withField cb fname ftype
                                 (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL))))
                   (when ctor-desc
                     (.withMethodBody cb "<init>" (MethodTypeDesc/ofDescriptor ctor-desc)
                                      (int ClassFile/ACC_PUBLIC)
                                      (reify Consumer
                                        (accept [_ code] (code! this code))))))))))))

(defn- no-ctor-class
  "Build a class `name` with superclass `Object`, one `long` field `v`
  and no constructor."
  ^bytes [name]
  (build-class name nil nil))

(defn- super-call! [^CodeBuilder c]
  (.aload c 0)
  (.invokespecial c ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void))

(defn- write-v!
  "The `deftype` field write of argument 1 to the `long` field `v`."
  [^ClassDesc this ^CodeBuilder c]
  (.aload c 0) (.lload c 1)
  (.putfield c this "v" ConstantDescs/CD_long))

(defn- int-param-class ^bytes [name]
  (build-class name "(I)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (.aload c 0) (.iload c 1) (.i2l c)
                 (.putfield c this "v" ConstantDescs/CD_long)
                 (.return_ c))))

(defn- write-before-super-class ^bytes [name]
  (build-class name "(J)V"
               (fn [this ^CodeBuilder c]
                 (write-v! this c)
                 (super-call! c)
                 (.return_ c))))

(defn- first-element-class
  "A first element that is not `aload 0`, then the `deftype` shape."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [this ^CodeBuilder c]
                 (.lload c 1) (.pop2 c)
                 (super-call! c)
                 (write-v! this c)
                 (.return_ c))))

(defn- lconst-class ^bytes [name]
  (build-class name "(J)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (.aload c 0) (.lconst_0 c)
                 (.putfield c this "v" ConstantDescs/CD_long)
                 (.return_ c))))

(defn- extra-write-class
  "The `deftype` shape plus a second field write before `return`."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (write-v! this c)
                 (write-v! this c)
                 (.return_ c))))

(defn- other-super-class
  "The `deftype` shape, but the super call is `Number.<init>()V`."
  ^bytes [name]
  (let [number (ClassDesc/of "java.lang.Number")]
    (build-class name number [["v" ConstantDescs/CD_long]] "(J)V"
                 (fn [this ^CodeBuilder c]
                   (.aload c 0)
                   (.invokespecial c number "<init>" ConstantDescs/MTD_void)
                   (write-v! this c)
                   (.return_ c)))))

(defn- swapped-writes-class
  "Two `long` fields `a` `b`; the `deftype` shape with the two
  `putfield` names swapped."
  ^bytes [name]
  (build-class name ConstantDescs/CD_Object
               [["a" ConstantDescs/CD_long] ["b" ConstantDescs/CD_long]] "(JJ)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (.aload c 0) (.lload c 1)
                 (.putfield c this "b" ConstantDescs/CD_long)
                 (.aload c 0) (.lload c 3)
                 (.putfield c this "a" ConstantDescs/CD_long)
                 (.return_ c))))

(defn- swapped-loads-class
  "Two `long` fields `a` `b`; the `deftype` shape with the two loads
  swapped."
  ^bytes [name]
  (build-class name ConstantDescs/CD_Object
               [["a" ConstantDescs/CD_long] ["b" ConstantDescs/CD_long]] "(JJ)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (.aload c 0) (.lload c 3)
                 (.putfield c this "a" ConstantDescs/CD_long)
                 (.aload c 0) (.lload c 1)
                 (.putfield c this "b" ConstantDescs/CD_long)
                 (.return_ c))))

(defn- other-owner-class
  "The `deftype` shape, but the `putfield` owner is another class."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [_this ^CodeBuilder c]
                 (super-call! c)
                 (.aload c 0) (.lload c 1)
                 (.putfield c (ClassDesc/of "vt.built.Other") "v"
                            ConstantDescs/CD_long)
                 (.return_ c))))

(defn- no-write-class
  "The super call, then `return`: no field write."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [_this ^CodeBuilder c]
                 (super-call! c)
                 (.return_ c))))

(defn- after-return-class
  "The `deftype` shape, then a second `return` (dead code)."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [this ^CodeBuilder c]
                 (super-call! c)
                 (write-v! this c)
                 (.return_ c)
                 (.return_ c))))

(defn- debug-elements-class
  "The `deftype` shape inside a start and an end label, with
  `localVariable` entries for slots 0 and 1 and one
  `localVariableType` entry for slot 0."
  ^bytes [name]
  (build-class name "(J)V"
               (fn [^ClassDesc this ^CodeBuilder c]
                 (let [start (.newBoundLabel c)
                       end (.newLabel c)]
                   (super-call! c)
                   (write-v! this c)
                   (.return_ c)
                   (.labelBinding c end)
                   (.localVariable c 0 "this" this start end)
                   (.localVariable c 1 "v" ConstantDescs/CD_long start end)
                   (.localVariableType c 0 "this"
                                       (Signature/parseFrom
                                        (.descriptorString this))
                                       start end)))))

;;; Round-trip loader

(defn- round-trip-loader
  "Return a child-first loader that defines the fixture class from
  `out-bytes` and its inner classes from `dir`."
  ^ClassLoader [^String class-name ^bytes out-bytes dir]
  (let [parent (DynamicClassLoader.)
        defined (atom {})
        prefix (str class-name "$")]
    (letfn [(load-class [^DynamicClassLoader loader ^String name]
              (or (get @defined name)
                  (let [bs (cond
                             (= name class-name) out-bytes
                             (.startsWith name prefix)
                             (Files/readAllBytes
                              (.toPath (io/file dir (str (.replace name \. \/)
                                                         ".class")))))]
                    (if bs
                      (let [c (.defineClass loader name bs nil)]
                        (swap! defined assoc name c)
                        c)
                      (.loadClass parent name)))))]
      (proxy [DynamicClassLoader] [parent]
        (loadClass
          ([name] (load-class this name))
          ([name _resolve] (load-class this name)))))))

(defn- value-class
  "Define the output class of fixture `fx` in a round-trip loader."
  ^Class [{:keys [^Class class bytes dir]}]
  (.loadClass (round-trip-loader (.getName class) (transform/->value-class bytes)
                                 dir)
              (.getName class)))

(defn- new-instance [^Class c args]
  (Reflector/invokeConstructor c (object-array args)))

;;; Class file views

(defn- parse ^ClassModel [^bytes bs] (.parse (ClassFile/of) bs))

(defn- flags [x]
  (.flagsMask ^AccessFlags
   (cond (instance? FieldModel x) (.flags ^FieldModel x)
         (instance? MethodModel x) (.flags ^MethodModel x)
         :else (.flags ^ClassModel x))))

(defn- static? [x] (not (zero? (bit-and (flags x) ClassFile/ACC_STATIC))))

(defn- attribute-view
  "Return a comparable value for attribute `a`. Fail on an unknown kind."
  [^Attribute a]
  (let [name (.stringValue (.attributeName a))]
    (cond
      (instance? SourceFileAttribute a)
      [name (.stringValue (.sourceFile ^SourceFileAttribute a))]
      (instance? SourceDebugExtensionAttribute a)
      [name (vec (.contents ^SourceDebugExtensionAttribute a))]
      :else (throw (ex-info (str "No comparison rule for attribute " name)
                            {:attribute name})))))

(defn- attributes [^AttributedElement x]
  (mapv attribute-view (.attributes x)))

(defn- field-view [^FieldModel f]
  {:name (.stringValue (.fieldName f))
   :type (.stringValue (.fieldType f))
   :flags (flags f)
   :attributes (attributes f)})

(defn- code-bytes [^MethodModel m]
  (some-> ^CodeAttribute (.orElse (.code m) nil) .codeArray vec))

(defn- method-view [^MethodModel m]
  {:name (.stringValue (.methodName m))
   :type (.stringValue (.methodType m))
   :flags (flags m)})

(defn- ctor? [^MethodModel m]
  (= "<init>" (:name (method-view m))))

(defn- ctor ^MethodModel [^ClassModel cm]
  (first (filter ctor? (.methods cm))))

(defn- ctor-elements [^ClassModel cm]
  (.elementList ^CodeModel (.orElse (.code (ctor cm)) nil)))

(defn- member-ref-view [opcode ^MemberRefEntry m]
  [opcode (.asInternalName (.owner m))
   (.stringValue (.name m)) (.stringValue (.type m))])

(defn- instruction-view
  "Return data for a constructor code element; nil for debug elements."
  [e]
  (cond
    (or (instance? Label e) (instance? LineNumber e)
        (instance? LocalVariable e) (instance? LocalVariableType e)) nil
    (instance? LoadInstruction e)
    [:load (.typeKind ^LoadInstruction e) (.slot ^LoadInstruction e)]
    (instance? InvokeInstruction e)
    (let [^InvokeInstruction i e] (member-ref-view (.opcode i) (.method i)))
    (instance? FieldInstruction e)
    (let [^FieldInstruction i e] (member-ref-view (.opcode i) (.field i)))
    (instance? ReturnInstruction e)
    [:return (.typeKind ^ReturnInstruction e)]
    :else [:other e]))

(defn- ctor-code-view [^ClassModel cm]
  (->> (ctor-elements cm)
       (keep instruction-view)
       vec))

(defn- value-ctor-code
  "Return the expected value class constructor code view."
  [^ClassModel cm]
  (let [owner (.asInternalName (.thisClass cm))
        fields (remove static? (.fields cm))
        kinds (map #(TypeKind/from (.fieldTypeSymbol ^FieldModel %)) fields)
        slots (reductions + 1 (map #(.slotSize ^TypeKind %) kinds))]
    (-> []
        (into (mapcat (fn [^FieldModel f ^TypeKind k s]
                        [[:load TypeKind/REFERENCE 0]
                         [:load (.asLoadable k) s]
                         [Opcode/PUTFIELD owner (.stringValue (.fieldName f))
                          (.stringValue (.fieldType f))]])
                      fields kinds slots))
        (into [[:load TypeKind/REFERENCE 0]
               [Opcode/INVOKESPECIAL "java/lang/Object" "<init>" "()V"]
               [:return TypeKind/VOID]]))))

;;; Verification 1: structure

(defn- superclass-name [^ClassModel cm]
  (some-> ^ClassEntry (.orElse (.superclass cm) nil) .asInternalName))

(deftest structure-test
  (doseq [k (keys fixtures)]
    (testing (str k)
      (let [in (parse (:bytes (fixture k)))
            out (parse (transform/->value-class (:bytes (fixture k))))
            in-fields (.fields in)
            out-fields (.fields out)]
        (testing "version"
          (is (= (ClassFile/latestMajorVersion) (.majorVersion out)))
          (is (= ClassFile/PREVIEW_MINOR_VERSION (.minorVersion out))))
        (testing "class flags lose ACC_IDENTITY"
          (is (not (zero? (bit-and (flags in) @#'transform/ACC_IDENTITY))))
          (is (= (bit-and-not (flags in) @#'transform/ACC_IDENTITY) (flags out))))
        (testing "fields"
          (when (= :static-constants k)
            (testing "guard: the input has a static field"
              (is (some static? in-fields))))
          (is (= (count in-fields) (count out-fields)))
          (doseq [[fi fo] (map vector in-fields out-fields)]
            (if (static? fi)
              (is (= (field-view fi) (field-view fo)))
              (is (= (update (field-view fi) :flags
                             bit-or @#'transform/ACC_STRICT_INIT)
                     (field-view fo))))))
        (testing "pass-through"
          (is (= (.asInternalName (.thisClass in)) (.asInternalName (.thisClass out))))
          (is (= (superclass-name in) (superclass-name out)))
          (is (= (mapv #(.asInternalName ^ClassEntry %)
                       (.interfaces in))
                 (mapv #(.asInternalName ^ClassEntry %)
                       (.interfaces out))))
          (is (= (attributes in) (attributes out)))
          (is (= (method-view (ctor in)) (method-view (ctor out))))
          (let [others (fn [^ClassModel cm]
                         (->> (.methods cm)
                              (remove ctor?)
                              (mapv #(assoc (method-view %) :code (code-bytes %)))))]
            (is (= (others in) (others out)))))
        (testing "constructor code is the value class shape"
          (is (= (value-ctor-code in) (ctor-code-view out))))))))

(deftest five-fields-load-test
  (testing "literal load slots and kinds (design Verification 1)"
    (let [out (parse (transform/->value-class (:bytes (fixture :five-fields))))
          loads (->> (ctor-code-view out)
                     (filter #(= :load (first %)))
                     (remove #(= 0 (nth % 2))))]
      (is (= [1 2 4 6 7] (mapv #(nth % 2) loads)))
      (is (= [TypeKind/INT TypeKind/LONG TypeKind/DOUBLE TypeKind/INT
              TypeKind/REFERENCE]
             (mapv second loads))))))

;;; Verification 2: round trip

(defn- field-names
  "Instance field names of the class file `bs`, in declaration order."
  [^bytes bs]
  (into []
        (comp (remove static?)
              (map #(.stringValue (.fieldName ^FieldModel %))))
        (.fields (parse bs))))

(defn- field-values
  "Values of the fields `names` of `o`, read by name."
  [o names]
  (mapv #(.get (.getDeclaredField (class o) ^String %) o)
        names))

(defn- contains-fixture? [x ^Class vc ^Class dc]
  (some #(or (instance? vc %) (instance? dc %))
        (tree-seq coll? seq x)))

(deftest round-trip-test
  (doseq [k (keys fixtures)]
    (testing (str k)
      (let [{:keys [args call receiver?] :as fx} (fixture k)
            vc (value-class fx)
            dc (:class fx)
            v1 (new-instance vc args)
            v2 (new-instance vc args)
            d (new-instance dc args)]
        (is (value? vc))
        (is (= (vec args) (field-values v1 (field-names (:bytes fx)))))
        (is (identical? v1 v2))
        (when call
          (let [rv (probe v1 call)
                rd (probe d call)]
            (if receiver?
              (do (is (identical? v1 rv))
                  (is (identical? d rd)))
              (do (is (not (contains-fixture? rv vc dc)))
                  (is (not (contains-fixture? rd vc dc)))
                  (is (= rd rv))))))))))

;;; Verification 3: errors

(defn- thrown-data
  "Return `[message data]` of the ex-info that `->value-class` throws
  for `bs`."
  [bs]
  (try (transform/->value-class bs)
       nil
       (catch ExceptionInfo e [(ex-message e) (ex-data e)])))

(defn- check-error [bs class-name part]
  (let [[msg data] (thrown-data bs)]
    (is (= {:value-type/error :unexpected-constructor :class-name class-name}
           (select-keys data [:value-type/error :class-name])))
    (is (string? msg))
    (when msg
      (is (.contains ^String msg class-name))
      (is (.contains ^String msg part))
      (is (.contains ^String msg "Clojure 1.12")))))

(defn- built-name
  "A new built class name `vt.built.Built<n>`."
  []
  (str "vt.built." (gensym "Built")))

(defn- check-built-error
  "Build a class with `build` and a new name, then `check-error` with
  `part`."
  [build part]
  (let [n (built-name)]
    (check-error (build n) n part)))

(deftest defrecord-error-test
  (testing "defrecord: more than one constructor"
    (let [{:keys [^Class class bytes]}
          (compile-fixture (fn [n] `(defrecord ~n [~'a])))]
      (check-error bytes (.getName class) "count of constructors"))))

(deftest no-ctor-error-test
  (testing "no constructor"
    (check-built-error no-ctor-class "count of constructors")))

(deftest int-param-error-test
  (testing "int parameter for a long field"
    (check-built-error int-param-class "parameters")))

(deftest write-before-super-error-test
  (testing "field write before super call: first difference at position 2"
    (check-built-error write-before-super-class "super call")))

(deftest first-element-error-test
  (testing "first element not aload 0: first difference at position 1"
    (check-built-error first-element-class "super call")))

(deftest other-super-error-test
  (testing "super call to another owner: first difference at position 2"
    (check-built-error other-super-class "super call")))

(deftest no-write-error-test
  (testing "no field write: first difference at position 3"
    (check-built-error no-write-class "field writes")))

(deftest lconst-error-test
  (testing "lconst_0 for the slot 1 load: first difference at position 4"
    (check-built-error lconst-class "field writes")))

(deftest swapped-loads-error-test
  (testing "field loads swapped: first difference at position 4"
    (check-built-error swapped-loads-class "field writes")))

(deftest swapped-writes-error-test
  (testing "field writes swapped: first difference at position 5"
    (check-built-error swapped-writes-class "field writes")))

(deftest field-owner-error-test
  (testing "field write to another owner: first difference at position 5"
    (check-built-error other-owner-class "field writes")))

(deftest extra-write-error-test
  (testing "extra field write before return: first difference at position 6"
    (check-built-error extra-write-class "field writes")))

(deftest after-return-error-test
  (testing "element after return: first difference at position 7"
    (check-built-error after-return-class "field writes")))

(deftest not-class-file-error-test
  (testing "bytes that are not a class file: not wrapped"
    (let [e (try (transform/->value-class (byte-array [1 2 3]))
                 nil
                 (catch Exception e e))]
      (is (instance? Exception e))
      (is (not (instance? ExceptionInfo e))))))

;;; Debug elements in the input

(deftest debug-elements-test
  (testing "labels and local variable entries: stripped before the check"
    (let [n (built-name)
          bs (debug-elements-class n)
          in (parse bs)
          elements (ctor-elements in)]
      (testing "guard: the input has each debug element kind"
        (is (some #(instance? Label %) elements))
        (is (some #(instance? LocalVariable %) elements))
        (is (some #(instance? LocalVariableType %) elements)))
      (let [out (transform/->value-class bs)]
        (is (bytes? out))
        (when out
          (is (= (value-ctor-code in) (ctor-code-view (parse out)))))))))
