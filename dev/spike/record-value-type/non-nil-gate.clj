(ns non-nil-gate
  "Non-nil feasibility gate for task 020 (D7 non-nil gate). Transform a
  record-shaped `deftype*` class into a value class with a spike-local
  transform. Field `p` has native storage (value-class tag,
  `NullRestricted`); field `s` has generated storage (`String`,
  `Objects.requireNonNull` and `checkcast`). Only the main constructor
  changes. Exercise every constructor, reflection, `create`, both record
  literals and a compiled record constant, for valid and rejected
  arguments. Prints one EDN result map; exits non-zero on any failed case.
  `value-type.core/defvaluetype` defines only the field tag class `Pt`."
  (:require [value-type.core :refer [defvaluetype]])
  (:import
   (clojure.lang Compiler DynamicClassLoader)
   (java.lang.classfile AccessFlags Annotation ClassBuilder ClassElement
                        ClassFile ClassFile$ClassHierarchyResolverOption
                        ClassFile$Option ClassFileVersion ClassHierarchyResolver
                        ClassModel ClassTransform CodeBuilder CodeElement
                        CodeModel CodeTransform FieldBuilder FieldElement
                        FieldModel MethodBuilder MethodElement MethodModel
                        MethodTransform Opcode TypeKind)
   (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute)
   (java.lang.classfile.instruction FieldInstruction)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util List)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(defvaluetype Pt [^long x])

(def ^:private ACC_IDENTITY 0x20)
(def ^:private ACC_STRICT_INIT 0x800)
(def ^:private hash-fields #{"__hash" "__hasheq"})

(def ^:private storage
  "Emitted field name -> concrete storage."
  {"p" {:type (ClassDesc/of "non_nil_gate.Pt") :native? true}
   "s" {:type ConstantDescs/CD_String :native? false}})

(def ^:private null-restricted
  (Annotation/of (ClassDesc/of "jdk.internal.vm.annotation.NullRestricted") (List/of)))

(def ^:private require-non-null
  (MethodTypeDesc/of ConstantDescs/CD_Object
                     ^"[Ljava.lang.constant.ClassDesc;"
                     (into-array ClassDesc [ConstantDescs/CD_Object])))

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ClassFile/ACC_STATIC)))

(defn- field-name [^FieldModel f] (.stringValue (.fieldName f)))

(defn- kind ^TypeKind [^FieldModel f] (TypeKind/from (.fieldTypeSymbol f)))

(defn- main-constructor? [^MethodModel m n]
  (and (= "<init>" (.stringValue (.methodName m)))
       (= n (count (.parameterList (.methodTypeSymbol m))))))

(defn- main-code
  "Write kept fields from their original slots, with casts and checks for
  fields with storage, then call Object.<init>."
  ^Consumer [^ClassDesc this fields]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb
            slots (butlast (reductions #(+ %1 (.slotSize (kind %2))) 1 fields))]
        (doseq [[^FieldModel f slot] (map vector fields slots)
                :when (not (hash-fields (field-name f)))]
          (let [{:keys [^ClassDesc type native?]} (storage (field-name f))]
            (.aload cb 0)
            (.loadLocal cb (kind f) (int slot))
            (when type
              (when-not native?
                (.invokestatic cb (ClassDesc/of "java.util.Objects")
                               "requireNonNull" ^MethodTypeDesc require-non-null))
              (.checkcast cb type))
            (.putfield cb this (field-name f) (or type (.fieldTypeSymbol f)))))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn- emit-field! [^ClassBuilder b ^FieldModel f]
  (let [{:keys [^ClassDesc type native?]} (storage (field-name f))]
    (.withField
     b ^String (field-name f) ^ClassDesc (or type (.fieldTypeSymbol f))
     (reify Consumer
       (accept [_ fb]
         (let [^FieldBuilder fb fb]
           (doseq [fe (.elementList f)]
             (if (instance? AccessFlags fe)
               (.withFlags fb (int (bit-or (.flagsMask ^AccessFlags fe) (long ACC_STRICT_INIT))))
               (.with fb ^FieldElement fe)))
           (when native?
             (.with fb (RuntimeVisibleAnnotationsAttribute/of
                        ^List (List/of null-restricted))))))))))

(defn- access-rewrite
  "Owner getfield/putfield of Object on a field with storage -> concrete
  descriptor."
  ^CodeTransform [^ClassDesc this]
  (reify CodeTransform
    (accept [_ b e]
      (let [^CodeBuilder b b
            ^ClassDesc type
            (when (and (instance? FieldInstruction e)
                       (#{Opcode/GETFIELD Opcode/PUTFIELD} (.opcode ^FieldInstruction e))
                       (= this (.asSymbol (.owner ^FieldInstruction e)))
                       (= ConstantDescs/CD_Object (.typeSymbol ^FieldInstruction e)))
              (:type (storage (.stringValue (.name ^FieldInstruction e)))))]
        (if type
          (.fieldAccess b (.opcode ^FieldInstruction e) this
                        (.stringValue (.name ^FieldInstruction e)) type)
          (.with b ^CodeElement e))))))

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
          (when-not (hash-fields (field-name e))
            (emit-field! b e))

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
        this (.asSymbol (.thisClass cm))
        fields (filterv instance-field? (.fields cm))
        bs (.transformClass cf cm (record-transform this fields))]
    (.transformClass cf (.parse cf bs)
                     (ClassTransform/transformingMethodBodies (access-rewrite this)))))

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

(value-deftype* non-nil-gate/Rec non_nil_gate.Rec [p s __meta __extmap ^int __hash ^int __hasheq]
  :implements [clojure.lang.IRecord java.util.Map clojure.lang.ILookup clojure.lang.IObj]
  (valAt [this k] (.valAt this k nil))
  (valAt [this k nf] (case k :p p :s s (get __extmap k nf)))
  (entrySet [this] (.entrySet ^java.util.Map (merge {:p p :s s} __extmap)))
  (meta [this] __meta)
  (withMeta [this m] (new Rec p s m __extmap)))

(def ^:private pt (Pt. 7))

(defn- view [^non_nil_gate.Rec r]
  {:p (some-> ^Pt (.valAt r :p) .x (->> (vector :Pt)))
   :s (.valAt r :s) :x (.valAt r :x)})

(defn- value-class? [^Class c]
  (let [^java.lang.reflect.Method m
        (try (.getMethod Class "isValue" (make-array Class 0))
             (catch NoSuchMethodException _ nil))]
    (boolean (and m (.invoke m c (object-array 0))))))

(defn- field-facts []
  (into (sorted-map)
        (map (fn [^java.lang.reflect.Field f]
               [(.getName f)
                {:type (.getName (.getType f))
                 :null-restricted (boolean (some #(= "jdk.internal.vm.annotation.NullRestricted"
                                                     (.getName (.annotationType ^java.lang.annotation.Annotation %)))
                                                 (.getDeclaredAnnotations f)))}]))
        (remove #(java.lang.reflect.Modifier/isStatic (.getModifiers ^java.lang.reflect.Field %))
                (.getDeclaredFields non_nil_gate.Rec))))

(defn- reflect [& args]
  (let [^java.lang.reflect.Constructor c
        (first (filter #(= (count args) (.getParameterCount ^java.lang.reflect.Constructor %))
                       (.getDeclaredConstructors non_nil_gate.Rec)))]
    (.newInstance c (object-array args))))

(defn- lit
  "Reader text for a literal element; `Pt` uses `#=` (read-eval)."
  [v]
  (if (instance? Pt v) "#=(non_nil_gate.Pt. 7)" (pr-str v)))

(defn- paths
  "Construction path name -> (fn [p s]) for valid and rejected arguments."
  []
  {:main (fn [p s] (non_nil_gate.Rec. p s nil nil 0 0))
   :declared (fn [p s] (non_nil_gate.Rec. p s))
   :meta-extmap (fn [p s] (non_nil_gate.Rec. p s nil nil))
   :reflect-main (fn [p s] (reflect p s nil nil (int 0) (int 0)))
   :reflect-declared (fn [p s] (reflect p s))
   :reflect-meta-extmap (fn [p s] (reflect p s nil nil))
   :create (fn [p s] (non_nil_gate.Rec/create {:p p :s s}))
   :map-literal (fn [p s] (load-string (str "#non_nil_gate.Rec{:p " (lit p) " :s " (lit s) "}")))
   :positional-literal (fn [p s] (load-string (str "#non_nil_gate.Rec[" (lit p) " " (lit s) "]")))
   :read-string-literal (fn [p s] (read-string (str "#non_nil_gate.Rec[" (lit p) " " (lit s) "]")))
   ;; Valid instance whose map view gives p and s (extmap merged last).
   :compiled-constant
   (fn [p s] (eval `(identity ~(non_nil_gate.Rec. pt "ok" nil {:p p :s s}))))})

(defn- chain [^Throwable t]
  (mapv #(.getName (class %)) (take-while some? (iterate #(.getCause ^Throwable %) t))))

(defn- run [f expected]
  (try
    (let [v (view (f))]
      (if (= :ok expected)
        {:ok v}
        {:ok v :error "expected rejection"}))
    (catch Throwable t
      (let [c (chain t)]
        (cond-> {:thrown c}
          (not (some #{expected} c)) (assoc :error "unexpected exception"))))))

(def ^:private arguments
  "case -> [p s expected]."
  {:valid [pt "ok" :ok]
   :nil-native [nil "ok" "java.lang.NullPointerException"]
   :nil-generated [pt nil "java.lang.NullPointerException"]
   :wrong-native ["no" "ok" "java.lang.ClassCastException"]
   :wrong-generated [pt 5 "java.lang.ClassCastException"]})

(defn- check-valid [r]
  (if (and (:ok r) (not= {:p [:Pt 7] :s "ok" :x nil} (:ok r)))
    (assoc r :error "wrong fields")
    r))

(defn -main [& _]
  (let [structure [[:value-class {:ok (value-class? non_nil_gate.Rec)}]
                   [:constructors {:ok (count (.getDeclaredConstructors non_nil_gate.Rec))}]
                   [:fields {:ok (field-facts)}]
                   [:create-absent-key
                    (run #(non_nil_gate.Rec/create {:s "ok"}) "java.lang.NullPointerException")]
                   [:with-meta {:ok (meta (with-meta (non_nil_gate.Rec. pt "ok") {:m 1}))}]]
        structure (mapv (fn [[k r]]
                          [k (cond-> r
                               (and (= k :value-class) (not (:ok r))) (assoc :error "not value")
                               (and (= k :constructors) (not= 3 (:ok r))) (assoc :error "count")
                               (and (= k :fields)
                                    (not= {"__extmap" {:type "java.lang.Object" :null-restricted false}
                                           "__meta" {:type "java.lang.Object" :null-restricted false}
                                           "p" {:type "non_nil_gate.Pt" :null-restricted true}
                                           "s" {:type "java.lang.String" :null-restricted false}}
                                          (:ok r)))
                               (assoc :error "fields"))])
                        structure)
        results (into structure
                      (for [[path f] (sort-by key (paths))
                            [case [p s expected]] (sort-by key arguments)]
                        [[path case] (cond-> (run #(f p s) expected)
                                       (= :valid case) check-valid)]))
        failed (filterv #(:error (second %)) results)]
    (prn {:java (System/getProperty "java.runtime.version")
          :clojure (clojure-version)
          :results results
          :failed (mapv first failed)})
    (shutdown-agents)
    (System/exit (if (seq failed) 1 0))))

(-main)
