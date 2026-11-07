(ns value-type.impl.probe-builder
  "Capability probe class bytes on `java.lang.classfile` and the format row.
  Load only after the D12 API checks find `java.lang.classfile`."
  (:require
   [value-type.impl.jvm-format :as jvm-format])
  (:import
   (clojure.lang DynamicClassLoader)
   (java.lang.classfile Annotation ClassBuilder ClassFile CodeBuilder
                        FieldBuilder MethodBuilder)
   (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util List UUID)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private null-restricted
  (Annotation/of (ClassDesc/of "jdk.internal.vm.annotation.NullRestricted") (List/of)))

(defn unique-probe-name
  "Return a new internal probe class name `value_type/probe/{prefix}_{uuid}`.
  `DynamicClassLoader` caches classes across loaders. Never reuse a name."
  ^String [prefix]
  (str "value_type/probe/" prefix "_" (.replace (str (UUID/randomUUID)) "-" "_")))

(defn define-probe
  "Define bytes as the class with the internal name internal-name in loader.
  Return the Class. Errors, for example `UnsupportedClassVersionError`,
  propagate."
  ^Class [^DynamicClassLoader loader ^String internal-name ^bytes bytes]
  (.defineClass loader (.replace internal-name "/" ".") bytes nil))

(defn probe-bytes
  "Return preview class bytes for the internal name owner, with one public
  final strict field `value` of descriptor and one public constructor that
  takes the field value and writes it before the super constructor.
  restricted? adds the runtime-visible `NullRestricted` annotation to the
  field."
  ^bytes [^String owner ^String descriptor restricted?]
  (let [{:keys [major minor strict-field-flag] :as format-row} (jvm-format/row)
        _ (jvm-format/require-constructor-order! format-row)
        this (ClassDesc/ofInternalName owner)
        field-type (ClassDesc/ofDescriptor descriptor)]
    (.build
     (ClassFile/of) this
     (reify Consumer
       (accept [_ cb]
         (let [^ClassBuilder cb cb]
           (.withVersion cb (int major) (int minor))
           (.withFlags cb (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL)))
           (.withSuperclass cb ConstantDescs/CD_Object)
           (.withField
            cb "value" field-type
            (reify Consumer
              (accept [_ fb]
                (let [^FieldBuilder fb fb]
                  (.withFlags fb (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL
                                              strict-field-flag)))
                  (when restricted?
                    (.with fb (RuntimeVisibleAnnotationsAttribute/of
                               ^List (List/of null-restricted))))))))
           (.withMethod
            cb "<init>"
            (MethodTypeDesc/of ConstantDescs/CD_void
                               ^"[Ljava.lang.constant.ClassDesc;"
                               (into-array ClassDesc [field-type]))
            (int ClassFile/ACC_PUBLIC)
            (reify Consumer
              (accept [_ mb]
                (.withCode
                 ^MethodBuilder mb
                 (reify Consumer
                   (accept [_ code]
                     (let [^CodeBuilder code code]
                       (.aload code 0)
                       (if (= "J" descriptor)
                         (.lload code 1)
                         (.aload code 1))
                       (.putfield code this "value" field-type)
                       (.aload code 0)
                       (.invokespecial code ConstantDescs/CD_Object "<init>"
                                       ConstantDescs/MTD_void)
                       (.return_ code))))))))))))))

(defn define-probe-class
  "Define a new probe class in loader: a unique name from prefix, the
  `probe-bytes` of the descriptor of field-type and restricted?, then
  `define-probe`. field-type is a Class, for example `Long/TYPE` or a probe
  Class. Return the Class. Errors, for example
  `UnsupportedClassVersionError`, propagate."
  ^Class [^DynamicClassLoader loader prefix ^Class field-type restricted?]
  (let [owner (unique-probe-name prefix)]
    (define-probe loader owner
      (probe-bytes owner (.descriptorString field-type) restricted?))))
