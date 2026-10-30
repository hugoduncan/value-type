(ns value-type.impl.capability
  "Baseline-safe checks for the value-class bytecode contract."
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader)
           (java.lang.reflect Method)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(def value-predicate
  "The cached `Class::isValue` Method, or nil on a JVM with no value classes."
  (delay
    (try
      (.getMethod Class "isValue" (make-array Class 0))
      (catch NoSuchMethodException _ nil))))

(defn value-class?
  "True when cls is a value class. False when `Class::isValue` is missing."
  [^Class cls]
  (let [^Method predicate @value-predicate]
    (boolean (and predicate (.invoke predicate cls (object-array 0))))))

(defn- unavailable
  [reason ^Throwable error]
  {:available? false :reason reason :detail (.getMessage error)})

(defn- required-apis
  []
  (let [classfile (try
                    (Class/forName "java.lang.classfile.ClassFile" false
                                   (.getClassLoader Class))
                    (catch ClassNotFoundException error
                      (unavailable :missing-classfile-api error)))]
    (if (map? classfile)
      classfile
      (if-let [predicate @value-predicate]
        {:predicate predicate}
        (unavailable :missing-value-predicate
                     (NoSuchMethodException. "java.lang.Class.isValue()"))))))

(defn latest-major-version
  "The latest class-file major version of the running JDK.
   Call only when `java.lang.classfile.ClassFile` exists."
  []
  (let [classfile (Class/forName "java.lang.classfile.ClassFile" false
                                 (.getClassLoader Class))]
    (.invoke (.getMethod classfile "latestMajorVersion" (make-array Class 0))
             nil (object-array 0))))

(defn probe-bytes
  "Return preview class bytes for owner with one strict final field `value`.
   descriptor is the field descriptor. The constructor takes the field value and
   assigns it before the super constructor, as JEP 401 strict fields require.
   restricted? adds the `NullRestricted` annotation to the field."
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

(defn- probe
  [{:keys [^Method predicate]}]
  (let [major (latest-major-version)
        ;; DynamicClassLoader caches classes across loaders. Never reuse a name.
        simple-name (str "CapabilityProbe_" (.replace (str (UUID/randomUUID)) "-" "_"))
        bytes (probe-bytes major (str "value_type/probe/" simple-name) "J" false)
        klass (try
                (.defineClass (DynamicClassLoader.)
                              (str "value_type.probe." simple-name) bytes nil)
                (catch UnsupportedClassVersionError error
                  (unavailable :unsupported-class-version error)))]
    (if (map? klass)
      klass
      (let [^Class probe-class klass
            instance (.newInstance (.getConstructor probe-class (into-array Class [Long/TYPE]))
                                   (object-array [(long 42)]))]
        (when-not (and (true? (.invoke predicate probe-class (object-array 0)))
                       (= 42 (.getLong (.getField probe-class "value") instance)))
          (throw (IllegalStateException. "Value-class capability probe invariant failed")))
        {:available? true}))))

(defn detect
  "Return capability and an unavailable reason, without applying policy.
   Only missing required APIs and known-probe version rejection permit absence.
   All other failures propagate. No user class is defined by this check."
  []
  (let [apis (required-apis)]
    (if (contains? apis :available?) apis (probe apis))))
