(ns value-type.impl.capability
  "Baseline-safe checks for the value-class bytecode contract."
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader)
           (java.util UUID)))

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
      (try
        {:classfile classfile
         :predicate (.getMethod Class "isValue" (make-array Class 0))}
        (catch NoSuchMethodException error
          (unavailable :missing-value-predicate error))))))

(defn- probe-bytes
  [major internal-name]
  (let [writer (ClassWriter. 0)]
    (.visit writer (unchecked-int (bit-or 0xffff0000 major))
            (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL)
            internal-name nil "java/lang/Object" nil)
    ;; JEP 401 strict fields must be assigned before the super constructor.
    (.visitEnd (.visitField writer
                            (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL 0x800)
                            "value" "J" nil nil))
    (let [ctor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>" "(J)V" nil nil)]
      (.visitCode ctor)
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitVarInsn ctor Opcodes/LLOAD 1)
      (.visitFieldInsn ctor Opcodes/PUTFIELD internal-name "value" "J")
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitMethodInsn ctor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn ctor Opcodes/RETURN)
      (.visitMaxs ctor 3 3)
      (.visitEnd ctor))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn- probe
  [{:keys [^Class classfile ^java.lang.reflect.Method predicate]}]
  (let [major (.invoke (.getMethod classfile "latestMajorVersion" (make-array Class 0))
                       nil (object-array 0))
        ;; DynamicClassLoader caches classes across loaders. Never reuse a name.
        simple-name (str "CapabilityProbe_" (.replace (str (UUID/randomUUID)) "-" "_"))
        bytes (probe-bytes major (str "value_type/probe/" simple-name))
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
