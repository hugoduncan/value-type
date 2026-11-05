(ns value-type.impl.transform
  "Change the bytes of a Clojure `deftype` class into the bytes of a
  JEP 401 value class (`->value-class`), and the bytes of a record-shaped
  `deftype*` class into the bytes of a value class without the hash
  fields (`->record-value-class`). Uses only JDK classes:
  `java.lang.classfile`, `java.lang.constant` and `java.util.function`.
  No Clojure compiler classes."
  (:require
   [value-type.impl.class-file :as class-file]
   [value-type.impl.field-access :as field-access]
   [value-type.impl.field-storage :as field-storage]
   [value-type.impl.fields :as fields])
  (:import
   (java.lang.classfile AccessFlags ClassBuilder ClassElement ClassFile
                        ClassFileVersion ClassModel ClassTransform CodeBuilder
                        CodeModel FieldBuilder FieldElement
                        FieldModel FieldTransform Instruction Label
                        MethodBuilder MethodElement MethodModel
                        MethodTransform Opcode TypeKind)
   (java.lang.classfile.instruction FieldInstruction InvokeInstruction
                                    LineNumber LoadInstruction LocalVariable
                                    LocalVariableType ReturnInstruction)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private ACC_IDENTITY 0x20)
(def ^:private ACC_STRICT_INIT 0x800)

(def ^:private ^ClassDesc objects-class (ClassDesc/of "java.util.Objects"))

(def ^:private ^MethodTypeDesc require-non-null
  (MethodTypeDesc/of ConstantDescs/CD_Object
                     ^"[Ljava.lang.constant.ClassDesc;"
                     (into-array ClassDesc [ConstantDescs/CD_Object])))

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ClassFile/ACC_STATIC)))

(defn- constructor? [^MethodModel m]
  (= "<init>" (.stringValue (.methodName m))))

(defn- field-name ^String [^FieldModel f] (.stringValue (.fieldName f)))

(defn- parameter-descriptors [^MethodModel m]
  (mapv #(.descriptorString ^ClassDesc %)
        (.parameterList (.methodTypeSymbol m))))

(defn- field-type-kind ^TypeKind [^FieldModel f]
  (TypeKind/from (.fieldTypeSymbol f)))

(defn- argument-slots
  "Return the argument slot of each field, then the next free slot: 1,
  then +2 after a `long` or `double`, else +1."
  [fields]
  (reductions (fn [slot f] (+ slot (.slotSize (field-type-kind f))))
              1 fields))

(defn- field-slots
  "Return the argument slot of each field."
  [fields]
  (butlast (argument-slots fields)))

;;; Input check

(defn- unexpected-constructor!
  "Throw `:unexpected-constructor` for `cm`; `:class-name` is its binary
  name."
  [^ClassModel cm ^String part]
  (let [class-name (.replace (.asInternalName (.thisClass cm)) \/ \.)]
    (throw
     (ex-info
      (str "Cannot make a value class from " class-name
           ": unexpected constructor (" part "). "
           "The constructor is not the deftype constructor shape that "
           "value-type supports. Fix: use Clojure 1.12.")
      {:value-type/error :unexpected-constructor
       :class-name class-name}))))

(defn- load? [e ^TypeKind kind slot]
  (and (instance? LoadInstruction e)
       (= kind (.typeKind ^LoadInstruction e))
       (= slot (.slot ^LoadInstruction e))))

(defn- invoke-init?
  "True when `e` is an `invokespecial` of `<init>` on the class `owner`
  (internal name) with `descriptor`."
  [e ^String owner ^String descriptor]
  (and (instance? InvokeInstruction e)
       (let [^InvokeInstruction i e]
         (and (= Opcode/INVOKESPECIAL (.opcode i))
              (not (.isInterface i))
              (= owner (.asInternalName (.owner i)))
              (= "<init>" (.stringValue (.name i)))
              (= descriptor (.stringValue (.type i)))))))

(defn- object-init? [e]
  (invoke-init? e "java/lang/Object" "()V"))

(defn- putfield? [e ^String owner ^FieldModel f]
  (and (instance? FieldInstruction e)
       (let [^FieldInstruction i e]
         (and (= Opcode/PUTFIELD (.opcode i))
              (= owner (.asInternalName (.owner i)))
              (= (field-name f) (.stringValue (.name i)))
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

(defn- code-difference
  "Return the first position where the code of `m`, without debug
  elements, does not match `preds`, or nil."
  [^MethodModel m preds]
  (first-difference
   (->> (some-> ^CodeModel (.orElse (.code m) nil) .elementList)
        (remove debug-element?))
   preds))

(defn- field-descriptors
  "Return the type descriptors of `fields`."
  [fields]
  (mapv #(.stringValue (.fieldType ^FieldModel %)) fields))

(defn- main-constructor-part
  "Return the part where the code of the main constructor `m` does not
  write `fields` of `owner` (`super call` or `field writes`), or nil."
  [^MethodModel m ^String owner fields]
  (when-let [pos (code-difference m (expected-code owner fields))]
    (if (<= pos 2) "super call" "field writes")))

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

      (not= (parameter-descriptors ctor) (field-descriptors fields))
      "parameters"

      :else
      (main-constructor-part ctor owner fields))))

;;; Transform

(defn- constructor-code
  "Emit writes of the fields that `keep-field?` selects, from their argument
   slots over all `fields`, with optional concrete storage, then call
   `Object.<init>`. `storage` maps emitted field names to
   `{:storage-type :native?}`. Casts enforce type compatibility; non-native
   fields get explicit null checks."
  ^Consumer [^ClassDesc this fields keep-field? storage]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb]
        (doseq [[^FieldModel f slot] (map vector fields (field-slots fields))
                :when (keep-field? f)]
          (let [n (field-name f)
                {:keys [^ClassDesc storage-type native?]} (get storage n)]
            (.aload cb 0)
            (.loadLocal cb (field-type-kind f) (int slot))
            (when storage-type
              (when-not native?
                (.invokestatic cb objects-class "requireNonNull"
                               require-non-null))
              (.checkcast cb storage-type))
            (.putfield cb this n (or storage-type (.fieldTypeSymbol f)))))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn- constructor-transform
  "Replace the constructor body with `code`."
  ^MethodTransform [^Consumer code]
  (reify MethodTransform
    (accept [_ mb e]
      (let [^MethodBuilder mb mb]
        (if (instance? CodeModel e)
          (.withCode mb code)
          (.with mb ^MethodElement e))))))

(def ^:private strict-init-transform
  (reify FieldTransform
    (accept [_ fb e]
      (let [^FieldBuilder fb fb]
        (if (instance? AccessFlags e)
          (.withFlags fb (int (bit-or (.flagsMask ^AccessFlags e)
                                      (long ACC_STRICT_INIT))))
          (.with fb ^FieldElement e))))))

(defn- value-class-transform
  "Set the preview version, clear `ACC_IDENTITY`, keep the instance fields
  that `keep-field?` selects with `ACC_STRICT_INIT`, and replace the body
  of each constructor for which `ctor-code` returns a code `Consumer`."
  ^ClassTransform [keep-field? ctor-code]
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
          (when (keep-field? e)
            (.transformField b ^FieldModel e strict-init-transform))

          (and (instance? MethodModel e) (constructor? e))
          (if-let [code (ctor-code e)]
            (.transformMethod b ^MethodModel e (constructor-transform code))
            (.with b ^ClassElement e))

          :else (.with b ^ClassElement e))))))

(defn- storage-transform
  ^ClassTransform [storage]
  (reify ClassTransform
    (accept [_ builder element]
      (let [^ClassBuilder builder builder
            {:keys [^ClassDesc storage-type native?]}
            (when (instance? FieldModel element)
              (get storage (field-name element)))]
        (if storage-type
          (field-storage/emit! builder element storage-type native?)
          (.with builder ^ClassElement element))))))

(defn- with-storage
  "Return constructor-transform, then concrete field storage, then owner
  field accesses rewritten to the storage descriptors of this."
  ^ClassTransform [this storage ^ClassTransform constructor-transform]
  (.andThen
   (.andThen constructor-transform (storage-transform storage))
   (ClassTransform/transformingMethodBodies
    (field-access/rewrite this storage))))

(defn ->value-class
  "Change the bytes of a Clojure `deftype` class into the bytes of a
  JEP 401 value class.

  Input: the class file bytes of a `deftype` class. storage maps
  emitted field names to `{:storage-type ClassDesc :native? boolean}` for
  concrete storage. `:native? false` selects constructor null checks
  instead of native annotations. class-loader (or nil) resolves dynamic
  types during stack-map generation.
  Output: new class file bytes. The version is the latest version of
  the running JDK with the preview minor version. The class has no
  `ACC_IDENTITY` flag. Each instance field has `ACC_STRICT_INIT`. The
  constructor writes the fields, then calls `Object.<init>`. Selected fields
  use concrete storage and constructor casts. Non-native fields get
  `Objects.requireNonNull` checks and no native annotation; native fields
  get native restriction. Owner field accesses use the concrete
  descriptors. Other parts retain their behavior.

  Errors: when the constructor is not the `deftype` shape, throws
  `ex-info` with `:value-type/error :unexpected-constructor` and
  `:class-name` (binary name). The message gives the class name, the
  part that is different (`count of constructors`, `parameters`,
  `super call` or `field writes`), the cause (the constructor is not
  the `deftype` constructor shape that value-type supports) and the
  fix (use `Clojure 1.12`). Bytes that are not a class file give the
  exceptions of `java.lang.classfile`."
  ^bytes [^bytes bs storage class-loader]
  (let [cf (class-file/configured class-loader)
        cm (.parse cf bs)
        this (.asSymbol (.thisClass cm))
        fields (filterv instance-field? (.fields cm))]
    (when-let [part (constructor-difference cm fields)]
      (unexpected-constructor! cm part))
    (.transformClass
     cf cm
     (with-storage this storage
       (let [code (constructor-code this fields (constantly true) storage)]
         (value-class-transform (constantly true) (constantly code)))))))

;;; Record path

(def hash-field-names
  "Hash fields that the record path removes, in report order: the
  `fields/record-hidden-fields` entries with tag `int`."
  (into [] (keep (fn [[n tag]] (when (= 'int tag) n)))
        fields/record-hidden-fields))

(def hash-field?
  "True for a name in `hash-field-names`."
  (set hash-field-names))

(defn- opcode? [e op]
  (and (instance? Instruction e) (= op (.opcode ^Instruction e))))

(defn- delegating-code
  "Return a predicate for each position of a compiler-generated record
  delegating constructor: load `this` and the declared arguments, the
  optional meta and extmap arguments (else `null`), two `0` hashes, then
  call the main constructor."
  [owner main-descriptor declared meta-extmap?]
  (let [slots (argument-slots declared)
        next-slot (last slots)]
    (concat
     [#(load? % TypeKind/REFERENCE 0)]
     (map (fn [^FieldModel f slot]
            #(load? % (.asLoadable (field-type-kind f)) slot))
          declared slots)
     (if meta-extmap?
       [#(load? % TypeKind/REFERENCE next-slot)
        #(load? % TypeKind/REFERENCE (inc next-slot))]
       [#(opcode? % Opcode/ACONST_NULL) #(opcode? % Opcode/ACONST_NULL)])
     [#(opcode? % Opcode/ICONST_0) #(opcode? % Opcode/ICONST_0)
      #(invoke-init? % owner main-descriptor)
      void-return?])))

(defn- record-constructor-difference
  "Return `[part main-descriptor]`: `part` names where the constructors of
  `cm` are not the compiler record shape (`count of constructors`,
  `parameters`, `delegating constructor`, `super call` or `field writes`),
  or is nil; `main-descriptor` is the descriptor of the main constructor
  (all `fields`), or nil when there is none. Fewer than four `fields`
  give `parameters`."
  [^ClassModel cm fields]
  (if (< (count fields) 4)
    ["parameters" nil]
    (let [owner (.asInternalName (.thisClass cm))
          ctors (filterv constructor? (.methods cm))
          types (field-descriptors fields)
          declared (subvec fields 0 (- (count fields) 4))
          by-params (group-by parameter-descriptors ctors)
          mains (by-params types)
          shorts (by-params (subvec types 0 (count declared)))
          metas (by-params (subvec types 0 (+ 2 (count declared))))
          ^MethodModel main (first mains)
          main-descriptor (some-> main .methodType .stringValue)]
      [(cond
         (not= 3 (count ctors))
         "count of constructors"

         (not (= 1 (count mains) (count shorts) (count metas)))
         "parameters"

         (or (code-difference (first shorts)
                              (delegating-code owner main-descriptor declared false))
             (code-difference (first metas)
                              (delegating-code owner main-descriptor declared true)))
         "delegating constructor"

         :else
         (main-constructor-part main owner fields))
       main-descriptor])))

(defn- record-value-class-transform
  "Drop the hash fields and rewrite only the main constructor, with the
  concrete `storage` of `constructor-code`."
  ^ClassTransform [this fields ^String main-descriptor storage]
  (let [keep-field? #(not (hash-field? (field-name %)))
        code (constructor-code this fields keep-field? storage)]
    (value-class-transform
     keep-field?
     #(when (= main-descriptor (.stringValue (.methodType ^MethodModel %)))
        code))))

(defn ->record-value-class
  "Change the bytes of a record-shaped Clojure `deftype*` class into the
  bytes of a JEP 401 value class.

  Input: the class file bytes of a `deftype*` class whose last four
  instance fields are `__meta`, `__extmap`, `int __hash` and
  `int __hasheq`, with the three compiler record constructors. storage is
  as for `->value-class` (declared fields only). class-loader (or nil)
  resolves types during stack-map generation.
  Output: new class file bytes, as for `->value-class` with `storage`,
  except: the class has no `__hash` and `__hasheq` fields; only the main
  constructor (all fields) is rewritten (casts, null checks and concrete
  writes), and it ignores the two hash arguments; the two delegating
  constructors, `create`, `getBasis` and all constructor descriptors are
  unchanged.

  Errors: when the constructors are not the compiler record shape,
  throws `ex-info` with `:value-type/error :unexpected-constructor` and
  `:class-name`, before any bytes change. The message part is
  `count of constructors`, `parameters`, `delegating constructor`,
  `super call` or `field writes`."
  ^bytes [^bytes bs storage class-loader]
  (let [cf (class-file/configured class-loader)
        cm (.parse cf bs)
        this (.asSymbol (.thisClass cm))
        fields (filterv instance-field? (.fields cm))
        [part main-descriptor] (record-constructor-difference cm fields)]
    (when part
      (unexpected-constructor! cm part))
    (.transformClass
     cf cm
     (with-storage this storage
       (record-value-class-transform this fields main-descriptor storage)))))
