(ns value-type.probe-test-support
  "Shared probe Classes for the capability and array self-check tests."
  (:require
   [value-type.impl.jvm-format :as jvm-format]
   [value-type.impl.probe-builder :as probe-builder])
  (:import
   (clojure.lang DynamicClassLoader)
   (java.lang.classfile ClassBuilder ClassFile CodeBuilder MethodBuilder)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(deftype IdentityLong [^long value])

(defn- define
  ^Class [^String internal-name ^bytes bytes]
  (probe-builder/define-probe (DynamicClassLoader.) internal-name bytes))

(def builder-probe-class
  "The builder `J` non-restricted probe Class."
  (probe-builder/define-probe-class (DynamicClassLoader.) "SupportProbe" Long/TYPE false))

(defn- long-class-bytes
  "A public final class with superclass `Object`, a public final `long` field
  `value` and a public `(long)` constructor with the body that emit-body
  writes to the `CodeBuilder` (it gets the `CodeBuilder` and the owner
  ClassDesc). value-class? gives the row version and the
  `:strict-field-flag`; else the class is an identity class."
  ^bytes [^String owner value-class? emit-body]
  (let [{:keys [major minor strict-field-flag]} (jvm-format/row)
        this (ClassDesc/ofInternalName owner)
        field-flags (cond-> (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL)
                      value-class? (bit-or strict-field-flag))]
    (.build
     (ClassFile/of) this
     (reify Consumer
       (accept [_ cb]
         (let [^ClassBuilder cb cb]
           (when value-class?
             (.withVersion cb (int major) (int minor)))
           (.withFlags cb (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL)))
           (.withSuperclass cb ConstantDescs/CD_Object)
           (.withField cb "value" ConstantDescs/CD_long (int field-flags))
           (.withMethod
            cb "<init>"
            (MethodTypeDesc/of ConstantDescs/CD_void
                               ^"[Ljava.lang.constant.ClassDesc;"
                               (into-array ClassDesc [ConstantDescs/CD_long]))
            (int ClassFile/ACC_PUBLIC)
            (reify Consumer
              (accept [_ mb]
                (.withCode
                 ^MethodBuilder mb
                 (reify Consumer
                   (accept [_ code]
                     (emit-body code this)))))))))))))

(defn- constant-bytes
  "A value class with a public final `long` field `value` and a public
  `(long)` constructor that writes 0, not its argument."
  ^bytes [^String owner]
  (long-class-bytes
   owner true
   (fn [^CodeBuilder code ^ClassDesc this]
     (.aload code 0)
     (.lconst_0 code)
     (.putfield code this "value" ConstantDescs/CD_long)
     (.aload code 0)
     (.invokespecial code ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
     (.return_ code))))

(def constant-value-class
  "A value Class whose constructor writes 0 for every argument."
  (let [owner (probe-builder/unique-probe-name "ConstantProbe")]
    (define owner (constant-bytes owner))))

(defn- throwing-bytes
  "An identity class with a public final `long` field `value` and a public
  `(long)` constructor that throws `UnsupportedOperationException`."
  ^bytes [^String owner]
  (let [uoe (ClassDesc/of "java.lang.UnsupportedOperationException")]
    (long-class-bytes
     owner false
     (fn [^CodeBuilder code _]
       (.aload code 0)
       (.invokespecial code ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
       (.new_ code uoe)
       (.dup code)
       (.ldc code ^java.lang.constant.ConstantDesc (identity "probe constructor failed"))
       (.invokespecial code uoe "<init>"
                       (MethodTypeDesc/of
                        ConstantDescs/CD_void
                        ^"[Ljava.lang.constant.ClassDesc;"
                        (into-array ClassDesc [ConstantDescs/CD_String])))
       (.athrow code)))))

(def throwing-class
  "A Class whose `(long)` constructor throws `UnsupportedOperationException`."
  (let [owner (probe-builder/unique-probe-name "ThrowingProbe")]
    (define owner (throwing-bytes owner))))
