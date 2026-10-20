(ns value-type.impl.transform
  "Change the bytes of a Clojure `deftype` class into the bytes of a
  JEP 401 value class. Uses only JDK classes: `java.lang.classfile`,
  `java.lang.constant` and `java.util.function`. No Clojure compiler
  classes."
  (:import
   (java.lang.classfile AccessFlags ClassBuilder ClassElement ClassFile
                        ClassFileVersion ClassModel ClassTransform CodeBuilder
                        CodeModel FieldBuilder FieldElement
                        FieldModel FieldTransform Label
                        MethodBuilder MethodElement MethodModel
                        MethodTransform Opcode TypeKind)
   (java.lang.classfile.instruction FieldInstruction InvokeInstruction
                                    LineNumber LoadInstruction LocalVariable
                                    LocalVariableType ReturnInstruction)
   (java.lang.constant ClassDesc ConstantDescs)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private ACC_IDENTITY 0x20)
(def ^:private ACC_STRICT_INIT 0x800)

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ClassFile/ACC_STATIC)))

(defn- constructor? [^MethodModel m]
  (= "<init>" (.stringValue (.methodName m))))

(defn- field-type-kind ^TypeKind [^FieldModel f]
  (TypeKind/from (.fieldTypeSymbol f)))

(defn- field-slots
  "Return the argument slot of each field: 1, then +2 after a
  `long` or `double`, else +1."
  [fields]
  (butlast (reductions (fn [slot f] (+ slot (.slotSize (field-type-kind f))))
                       1 fields)))

;;; Input check

(defn- unexpected-constructor! [^String class-name ^String part]
  (throw
   (ex-info
    (str "Cannot make a value class from " class-name
         ": unexpected constructor (" part "). "
         "The constructor is not the deftype constructor shape that "
         "value-type supports. Fix: use Clojure 1.12.")
    {:value-type/error :unexpected-constructor
     :class-name class-name})))

(defn- load? [e ^TypeKind kind slot]
  (and (instance? LoadInstruction e)
       (= kind (.typeKind ^LoadInstruction e))
       (= slot (.slot ^LoadInstruction e))))

(defn- object-init? [e]
  (and (instance? InvokeInstruction e)
       (let [^InvokeInstruction i e]
         (and (= Opcode/INVOKESPECIAL (.opcode i))
              (not (.isInterface i))
              (= "java/lang/Object" (.asInternalName (.owner i)))
              (= "<init>" (.stringValue (.name i)))
              (= "()V" (.stringValue (.type i)))))))

(defn- putfield? [e ^String owner ^FieldModel f]
  (and (instance? FieldInstruction e)
       (let [^FieldInstruction i e]
         (and (= Opcode/PUTFIELD (.opcode i))
              (= owner (.asInternalName (.owner i)))
              (= (.stringValue (.fieldName f)) (.stringValue (.name i)))
              (= (.stringValue (.fieldType f)) (.stringValue (.type i)))))))

(defn- void-return? [e]
  (and (instance? ReturnInstruction e)
       (= TypeKind/VOID (.typeKind ^ReturnInstruction e))))

(defn- expected-code
  "Return a predicate for each position of the `deftype` constructor
  code."
  [owner fields]
  (concat
   [#(load? % TypeKind/REFERENCE 0)
    object-init?]
   (mapcat (fn [^FieldModel f slot]
             [#(load? % TypeKind/REFERENCE 0)
              #(load? % (.asLoadable (field-type-kind f)) slot)
              #(putfield? % owner f)])
           fields (field-slots fields))
   [void-return?]))

(defn- debug-element? [e]
  (or (instance? Label e) (instance? LineNumber e)
      (instance? LocalVariable e) (instance? LocalVariableType e)))

(defn- first-difference
  "Return the 1-based first position where `elements` does not match
  `preds`, or nil."
  [elements preds]
  (loop [pos 1 [e & es :as elements] elements [p & ps :as preds] preds]
    (cond
      (and (empty? elements) (empty? preds)) nil
      (or (empty? elements) (empty? preds) (not (p e))) pos
      :else (recur (inc pos) es ps))))

(defn- constructor-difference
  "Return the part where the constructor of `cm` is not the `deftype`
  shape (`count of constructors`, `parameters`, `super call` or
  `field writes`), or nil."
  [^ClassModel cm fields]
  (let [owner (.asInternalName (.thisClass cm))
        ctors (filterv constructor? (.methods cm))
        ^MethodModel ctor (first ctors)]
    (cond
      (not= 1 (count ctors))
      "count of constructors"

      (not= (mapv #(.descriptorString ^ClassDesc %)
                  (.parameterList (.methodTypeSymbol ctor)))
            (mapv #(.stringValue (.fieldType ^FieldModel %)) fields))
      "parameters"

      :else
      (when-let [pos (first-difference
                      (->> (some-> ^CodeModel (.orElse (.code ctor) nil)
                                   .elementList)
                           (remove debug-element?))
                      (expected-code owner fields))]
        (if (<= pos 2) "super call" "field writes")))))

;;; Transform

(defn- constructor-code ^Consumer [^ClassDesc this fields]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb]
        (doseq [[^FieldModel f slot] (map vector fields (field-slots fields))]
          (.aload cb 0)
          (.loadLocal cb (field-type-kind f) (int slot))
          (.putfield cb this (.stringValue (.fieldName f)) (.fieldTypeSymbol f)))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn- constructor-transform ^MethodTransform [this fields]
  (reify MethodTransform
    (accept [_ mb e]
      (let [^MethodBuilder mb mb]
        (if (instance? CodeModel e)
          (.withCode mb (constructor-code this fields))
          (.with mb ^MethodElement e))))))

(def ^:private strict-init-transform
  (reify FieldTransform
    (accept [_ fb e]
      (let [^FieldBuilder fb fb]
        (if (instance? AccessFlags e)
          (.withFlags fb (int (bit-or (.flagsMask ^AccessFlags e)
                                      (long ACC_STRICT_INIT))))
          (.with fb ^FieldElement e))))))

(defn- value-class-transform ^ClassTransform [this fields]
  (reify ClassTransform
    (accept [_ b e]
      (let [^ClassBuilder b b]
        (cond
          (instance? ClassFileVersion e)
          (.withVersion b (ClassFile/latestMajorVersion)
                        ClassFile/PREVIEW_MINOR_VERSION)

          (instance? AccessFlags e)
          (.withFlags b (int (bit-and-not (.flagsMask ^AccessFlags e)
                                          (long ACC_IDENTITY))))

          (and (instance? FieldModel e) (instance-field? e))
          (.transformField b ^FieldModel e strict-init-transform)

          (and (instance? MethodModel e) (constructor? e))
          (.transformMethod b ^MethodModel e (constructor-transform this fields))

          :else (.with b ^ClassElement e))))))

(defn ->value-class
  "Change the bytes of a Clojure `deftype` class into the bytes of a
  JEP 401 value class.

  Input: the class file bytes of a `deftype` class.
  Output: new class file bytes. The version is the latest version of
  the running JDK with the preview minor version. The class has no
  `ACC_IDENTITY` flag. Each instance field has `ACC_STRICT_INIT`. The
  constructor writes the fields, then calls `Object.<init>`. All other
  parts are the same as the input.

  Errors: when the constructor is not the `deftype` shape, throws
  `ex-info` with `:value-type/error :unexpected-constructor` and
  `:class-name` (binary name). The message gives the class name, the
  part that is different (`count of constructors`, `parameters`,
  `super call` or `field writes`), the cause (the constructor is not
  the `deftype` constructor shape that value-type supports) and the
  fix (use `Clojure 1.12`). Bytes that are not a class file give the
  exceptions of `java.lang.classfile`."
  ^bytes [^bytes bs]
  (let [cf (ClassFile/of)
        cm (.parse cf bs)
        entry (.thisClass cm)
        this (.asSymbol entry)
        fields (filterv instance-field? (.fields cm))]
    (when-let [part (constructor-difference cm fields)]
      (unexpected-constructor! (.replace (.asInternalName entry) \/ \.) part))
    (.transformClass cf cm (value-class-transform this fields))))
