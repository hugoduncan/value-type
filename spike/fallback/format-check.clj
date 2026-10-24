;; Fixed JEP 401 format probe, independent of the production transformer.
(require '[clojure.edn :as edn])
(import '(clojure.asm ClassWriter Opcodes)
        '(clojure.lang DynamicClassLoader))

(defn probe-bytes
  "Build a value class with a strict field and pre-super assignment."
  [major]
  (let [writer (ClassWriter. 0)]
    (.visit writer (unchecked-int (bit-or 0xffff0000 major))
            (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL)
            "fallback/CapabilityProbe" nil "java/lang/Object" nil)
    ;; 0x800 is ACC_STRICT_INIT in the selected JEP 401 format.
    (.visitEnd (.visitField writer
                           (bit-or Opcodes/ACC_PUBLIC Opcodes/ACC_FINAL 0x800)
                           "value" "J" nil nil))
    (let [ctor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>" "(J)V" nil nil)]
      (.visitCode ctor)
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitVarInsn ctor Opcodes/LLOAD 1)
      (.visitFieldInsn ctor Opcodes/PUTFIELD "fallback/CapabilityProbe" "value" "J")
      (.visitVarInsn ctor Opcodes/ALOAD 0)
      (.visitMethodInsn ctor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn ctor Opcodes/RETURN)
      (.visitMaxs ctor 3 3)
      (.visitEnd ctor))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn probe
  "Define and instantiate the fixed format, preserving the exact JVM rejection."
  []
  (try
    (let [loader (DynamicClassLoader.)
          klass (.defineClass loader "fallback.CapabilityProbe" (probe-bytes (if (= "latest" (second *command-line-args*))
                                        (let [api (Class/forName "java.lang.classfile.ClassFile")]
                                          (.invoke (.getMethod api "latestMajorVersion" (make-array Class 0))
                                                   nil (object-array 0)))
                                        71)) nil)
          instance (.newInstance (.getConstructor klass (into-array Class [Long/TYPE]))
                                 (object-array [(long 42)]))]
      {:outcome :defined
       :value? (.invoke (.getMethod Class "isValue" (make-array Class 0))
                        klass (object-array 0))
       :field-value (.getLong (.getField klass "value") instance)
       :instance-class (.getName (class instance))})
    (catch UnsupportedClassVersionError error
      {:outcome :unsupported-version
       :error-class (.getName (class error))
       :message (.getMessage error)})))

(let [result (probe)
      expected (edn/read-string (first *command-line-args*))]
  (prn (assoc result :java-version (System/getProperty "java.runtime.version")))
  (assert (= expected (select-keys result (keys expected))))
  (when (= :defined (:outcome result))
    (assert (true? (:value? result)))
    (assert (= 42 (:field-value result)))))
