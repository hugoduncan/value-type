(ns closed-gate
  "Closed gate for task 022 (D7 closed gate). Transform a record-shaped
  `deftype*` class with the fields `[^long x __meta __extmap ^int __hash
  ^int __hasheq]` into a value class with a spike-local transform. The
  transform removes all four hidden fields and adds the D10 check at the
  start of the main constructor: a non-nil `__meta` or `__extmap` argument
  throws `IllegalArgumentException`. Exercise every constructor, `create`,
  both record literals, a compiled record constant and a flat array.
  Prints one EDN result map; exits non-zero on any failed case."
  (:import
   (clojure.lang Compiler DynamicClassLoader)
   (java.lang.classfile AccessFlags ClassBuilder ClassElement ClassFile
                        ClassFile$ClassHierarchyResolverOption ClassFile$Option
                        ClassFileVersion ClassHierarchyResolver ClassModel
                        ClassTransform CodeBuilder CodeModel FieldBuilder
                        FieldElement FieldModel FieldTransform Label
                        MethodBuilder MethodElement MethodModel MethodTransform
                        TypeKind)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private ACC_IDENTITY 0x20)
(def ^:private ACC_STRICT_INIT 0x800)
(def ^:private hidden-fields #{"__meta" "__extmap" "__hash" "__hasheq"})

(def ^:private iae (ClassDesc/of "java.lang.IllegalArgumentException"))

(def ^:private string-ctor
  (MethodTypeDesc/of ConstantDescs/CD_void
                     ^"[Ljava.lang.constant.ClassDesc;"
                     (into-array ClassDesc [ConstantDescs/CD_String])))

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ClassFile/ACC_STATIC)))

(defn- field-name [^FieldModel f] (.stringValue (.fieldName f)))

(defn- kind ^TypeKind [^FieldModel f] (TypeKind/from (.fieldTypeSymbol f)))

(defn- main-constructor? [^MethodModel m n]
  (and (= "<init>" (.stringValue (.methodName m)))
       (= n (count (.parameterList (.methodTypeSymbol m))))))

(defn- closed-check!
  "aload slot; ifnull L; new/dup/ldc/invokespecial/athrow IAE; L:"
  [^CodeBuilder cb slot]
  (let [^Label l (.newLabel cb)]
    (.aload cb (int slot))
    (.ifnull cb l)
    (.new_ cb ^ClassDesc iae)
    (.dup cb)
    (.ldc cb ^java.lang.constant.ConstantDesc (identity "closed record: __meta and __extmap must be nil"))
    (.invokespecial cb ^ClassDesc iae "<init>" ^MethodTypeDesc string-ctor)
    (.athrow cb)
    (.labelBinding cb l)))

(defn- main-code
  "D10 check of `__meta` then `__extmap`, then write kept fields from their
  original slots, then call Object.<init>."
  ^Consumer [^ClassDesc this fields]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb
            slots (butlast (reductions #(+ %1 (.slotSize (kind %2))) 1 fields))
            slot-of (zipmap (map field-name fields) slots)]
        (closed-check! cb (slot-of "__meta"))
        (closed-check! cb (slot-of "__extmap"))
        (doseq [[^FieldModel f slot] (map vector fields slots)
                :when (not (hidden-fields (field-name f)))]
          (.aload cb 0)
          (.loadLocal cb (kind f) (int slot))
          (.putfield cb this (field-name f) (.fieldTypeSymbol f)))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn- record-transform ^ClassTransform [this fields]
  (reify ClassTransform
    (accept [_ b e]
      (let [^ClassBuilder b b]
        (cond
          (instance? ClassFileVersion e)
          (.withVersion b (ClassFile/latestMajorVersion) ClassFile/PREVIEW_MINOR_VERSION)

          (instance? AccessFlags e)
          (.withFlags b (int (bit-and-not (.flagsMask ^AccessFlags e) (long ACC_IDENTITY))))

          (and (instance? FieldModel e) (instance-field? e))
          (when-not (hidden-fields (field-name e))
            (.transformField
             b ^FieldModel e
             (reify FieldTransform
               (accept [_ fb fe]
                 (let [^FieldBuilder fb fb]
                   (if (instance? AccessFlags fe)
                     (.withFlags fb (int (bit-or (.flagsMask ^AccessFlags fe) (long ACC_STRICT_INIT))))
                     (.with fb ^FieldElement fe)))))))

          (and (instance? MethodModel e) (main-constructor? e (count fields)))
          (.transformMethod
           b ^MethodModel e
           (reify MethodTransform
             (accept [_ mb me]
               (let [^MethodBuilder mb mb]
                 (if (instance? CodeModel me)
                   (.withCode mb (main-code this fields))
                   (.with mb ^MethodElement me))))))

          :else (.with b ^ClassElement e))))))

(defn ->record-value-class ^bytes [^bytes bs ^ClassLoader loader]
  (let [cf (ClassFile/of (into-array ClassFile$Option
                                     [(ClassFile$ClassHierarchyResolverOption/of
                                       (ClassHierarchyResolver/ofClassLoading loader))]))
        ^ClassModel cm (.parse cf bs)
        fields (filterv instance-field? (.fields cm))]
    (.transformClass cf cm (record-transform (.asSymbol (.thisClass cm)) fields))))

(defmacro value-deftype*
  "Spike-only: install a capture loader for `classname`, then expand to
  `deftype*`. Not the public API."
  [tagname classname fields & body]
  (let [target (str classname)
        parent ^ClassLoader @Compiler/LOADER
        delegate (DynamicClassLoader. parent)]
    (.set Compiler/LOADER
          (proxy [DynamicClassLoader] [parent]
            (defineClass [^String n ^bytes bs src]
              (let [cls (.defineClass delegate n
                                      (if (= n target) (->record-value-class bs delegate) bs)
                                      src)]
                (.getDeclaredMethods cls) ; link and verify now
                cls))))
    `(let [] (deftype* ~tagname ~classname ~fields ~@body) ~(symbol target))))

;; Closed body: never reads or writes __meta or __extmap.
(value-deftype* closed-gate/Rec closed_gate.Rec [^long x __meta __extmap ^int __hash ^int __hasheq]
  :implements [clojure.lang.IRecord java.util.Map clojure.lang.ILookup clojure.lang.IObj]
  (valAt [this k] (.valAt this k nil))
  (valAt [this k nf] (case k :x x nf))
  (entrySet [this] (let [^java.util.Map m {:x x}] (.entrySet m)))
  (meta [this] nil)
  (withMeta [this m] this))

(defn- view [^closed_gate.Rec r] {:x (.valAt r :x) :y (.valAt r :y)})

(defn- value-class? [^Class c]
  (let [^java.lang.reflect.Method m
        (try (.getMethod Class "isValue" (make-array Class 0))
             (catch NoSuchMethodException _ nil))]
    (boolean (and m (.invoke m c (object-array 0))))))

(defn- instance-fields []
  (mapv #(.getName ^java.lang.reflect.Field %)
        (remove #(java.lang.reflect.Modifier/isStatic (.getModifiers ^java.lang.reflect.Field %))
                (.getDeclaredFields closed_gate.Rec))))

(defn- ctor ^java.lang.reflect.Constructor [n]
  (first (filter #(= n (.getParameterCount ^java.lang.reflect.Constructor %))
                 (.getDeclaredConstructors closed_gate.Rec))))

(defn- reflect [n & args] (view (.newInstance (ctor n) (object-array args))))

(defn- rejects-iae
  "Pass when IllegalArgumentException is in the cause chain."
  [f]
  (let [t (try (f) nil (catch Throwable t t))
        chain (take-while some? (iterate #(.getCause ^Throwable %) t))]
    (if (some #(instance? IllegalArgumentException %) chain)
      (mapv #(.getName (class %)) chain)
      (throw (ex-info "no IllegalArgumentException" {:chain (mapv str chain)})))))

(defn- flat-array? []
  (let [vc (Class/forName "jdk.internal.value.ValueClass")
        alloc (.getMethod vc "newNullRestrictedAtomicArray"
                          (into-array Class [Class Integer/TYPE Object]))
        flat (.getMethod vc "isFlatArray" (into-array Class [Object]))
        arr (.invoke alloc nil (object-array [closed_gate.Rec (int 4) (closed_gate.Rec. 0)]))]
    (boolean (.invoke flat nil (object-array [arr])))))

(def cases
  (partition 2
   [:value-class (fn [] (or (value-class? closed_gate.Rec) (throw (ex-info "not value" {}))))
    :fields (fn [] (let [fs (instance-fields)]
                     (if (= ["x"] fs) fs (throw (ex-info "fields" {:fields fs})))))
    :constructors (fn [] (count (.getDeclaredConstructors closed_gate.Rec)))
    :main-constructor (fn [] (view (closed_gate.Rec. 1 nil nil 0 0)))
    :main-constructor-reflective (fn [] (reflect 5 1 nil nil (int 0) (int 0)))
    :declared-constructor (fn [] (view (closed_gate.Rec. 1)))
    :declared-constructor-reflective (fn [] (reflect 1 1))
    :meta-extmap-constructor (fn [] (view (closed_gate.Rec. 1 nil nil)))
    :meta-extmap-constructor-reflective (fn [] (reflect 3 1 nil nil))
    :create (fn [] (view (closed_gate.Rec/create {:x 1})))
    :map-literal (fn [] (view (load-string "#closed_gate.Rec{:x 1}")))
    :positional-literal (fn [] (view (load-string "#closed_gate.Rec[1]")))
    :compiled-constant (fn [] (view (eval `(identity ~(closed_gate.Rec. 1)))))
    :reject-main-meta (fn [] (rejects-iae #(closed_gate.Rec. 1 {:m 1} nil 0 0)))
    :reject-main-extmap (fn [] (rejects-iae #(closed_gate.Rec. 1 nil {:y 2} 0 0)))
    :reject-main-meta-reflective (fn [] (rejects-iae #(reflect 5 1 {:m 1} nil (int 0) (int 0))))
    :reject-main-extmap-reflective (fn [] (rejects-iae #(reflect 5 1 nil {:y 2} (int 0) (int 0))))
    :reject-delegating-meta (fn [] (rejects-iae #(closed_gate.Rec. 1 {:m 1} nil)))
    :reject-delegating-extmap (fn [] (rejects-iae #(closed_gate.Rec. 1 nil {:y 2})))
    :reject-delegating-meta-reflective (fn [] (rejects-iae #(reflect 3 1 {:m 1} nil)))
    :reject-delegating-extmap-reflective (fn [] (rejects-iae #(reflect 3 1 nil {:y 2})))
    :reject-create-undeclared (fn [] (rejects-iae #(closed_gate.Rec/create {:x 1 :y 2})))
    :reject-map-literal-undeclared (fn [] (rejects-iae #(load-string "#closed_gate.Rec{:x 1 :y 2}")))
    :reject-map-literal-undeclared-read (fn [] (rejects-iae #(read-string "#closed_gate.Rec{:x 1 :y 2}")))
    :flat-array (fn [] (or (flat-array?) (throw (ex-info "not flat" {}))))]))

(defn -main [& _]
  (let [results (into []
                      (map (fn [[k f]]
                             [k (try {:ok (f)}
                                     (catch Throwable t
                                       {:error (str (class t) ": " (.getMessage t))
                                        :cause (some-> (.getCause t) str)}))]))
                      cases)
        failed (filterv #(:error (second %)) results)]
    (prn {:java (System/getProperty "java.runtime.version")
          :clojure (clojure-version)
          :results results
          :failed (mapv first failed)})
    (shutdown-agents)
    (System/exit (if (seq failed) 1 0))))

(-main)
