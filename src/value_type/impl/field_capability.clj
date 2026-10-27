(ns value-type.impl.field-capability
  "Baseline-safe probe of native nil-restricted field enforcement."
  (:require [value-type.impl.capability :as capability])
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader)
           (java.lang.reflect InvocationTargetException)
           (java.util UUID)))

(defn- probe-bytes
  [major owner descriptor restricted?]
  (let [writer (ClassWriter. 0)
        primitive? (= "J" descriptor)]
    (.visit writer (unchecked-int (bit-or 0xffff0000 major))
            (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL)
            owner nil "java/lang/Object" nil)
    (let [field (.visitField writer
                             (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL 0x800)
                             "value" descriptor nil nil)]
      (when restricted?
        (.visitEnd (.visitAnnotation field
                                     "Ljdk/internal/vm/annotation/NullRestricted;" true)))
      (.visitEnd field))
    (let [ctor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>"
                             (str "(" descriptor ")V") nil nil)]
      (.visitCode ctor)
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitVarInsn ctor (if primitive? Opcodes/LLOAD Opcodes/ALOAD) 1)
      (.visitFieldInsn ctor Opcodes/PUTFIELD owner "value" descriptor)
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitMethodInsn ctor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn ctor Opcodes/RETURN)
      (.visitMaxs ctor 3 3)
      (.visitEnd ctor))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn- verify-enforcement
  [^Class leaf ^Class holder]
  (let [value (.newInstance (.getConstructor leaf (into-array Class [Long/TYPE]))
                            (object-array [(long 42)]))
        ctor (.getConstructor holder (into-array Class [leaf]))
        instance (.newInstance ctor (object-array [value]))
        readback (.get (.getField holder "value") instance)]
    (when-not (and (= leaf (.getType (.getField holder "value")))
                   (= value readback)
                   (= 42 (.getLong (.getField leaf "value") readback)))
      (throw (IllegalStateException. "Restricted-field probe readback invariant failed")))
    (let [rejected? (try
                      (.newInstance ctor (object-array [nil]))
                      false
                      (catch InvocationTargetException error
                        (if (instance? NullPointerException (.getCause error))
                          true
                          (throw error))))]
      (when-not rejected?
        (throw (IllegalStateException. "Restricted-field probe accepted null"))))
    {:available? true}))

(defn- probe
  []
  (let [classfile (Class/forName "java.lang.classfile.ClassFile")
        major (.invoke (.getMethod classfile "latestMajorVersion" (make-array Class 0))
                       nil (object-array 0))
        prefix (str "value_type/probe/Fields_"
                    (.replace (str (UUID/randomUUID)) "-" "_"))
        leaf-name (str prefix "Leaf")
        holder-name (str prefix "Holder")
        loader (DynamicClassLoader.)
        leaf (.defineClass loader (.replace leaf-name "/" ".")
                           (probe-bytes major leaf-name "J" false) nil)
        holder (.defineClass loader (.replace holder-name "/" ".")
                             (probe-bytes major holder-name (str "L" leaf-name ";") true) nil)]
    (verify-enforcement leaf holder)))

(defn detect
  "Prove valid nested construction and native null rejection without exports.
   Inherit only the value probe's narrow API/version absence classifications.
   Field linkage, verification and enforcement failures remain errors."
  []
  (let [prerequisites (capability/detect)]
    (if (:available? prerequisites)
      (probe)
      prerequisites)))
