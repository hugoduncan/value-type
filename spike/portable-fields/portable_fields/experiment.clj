(ns portable-fields.experiment
  "Isolated identity-backend feasibility experiment, not a supported API."
  (:require [value-type.core :as value-type])
  (:import (clojure.asm ClassReader ClassVisitor ClassWriter MethodVisitor Opcodes)
           (clojure.lang Compiler DynamicClassLoader)))

(defn rewrite
  "Retype selected storage and references; check values at actual field writes."
  [bytes owner descriptors owner?]
  (let [writer (ClassWriter. 0)
        visitor
        (proxy [ClassVisitor] [Opcodes/ASM6 writer]
          (visitField [access field desc signature value]
            (proxy-super visitField access field
                         (if owner? (get descriptors field desc) desc)
                         signature value))
          (visitMethod [access method desc signature exceptions]
            (let [delegate (proxy-super visitMethod access method desc
                                        signature exceptions)]
              (proxy [MethodVisitor] [Opcodes/ASM6 delegate]
                (visitFieldInsn [opcode target field field-desc]
                  (if-let [concrete (when (= target owner) (get descriptors field))]
                    (do
                      (when (= opcode Opcodes/PUTFIELD)
                        (.visitMethodInsn delegate Opcodes/INVOKESTATIC
                                          "java/util/Objects" "requireNonNull"
                                          "(Ljava/lang/Object;)Ljava/lang/Object;" false)
                        (.visitTypeInsn delegate Opcodes/CHECKCAST
                                        (subs concrete 1 (dec (count concrete)))))
                      (.visitFieldInsn delegate opcode target field concrete))
                    (.visitFieldInsn delegate opcode target field field-desc)))))))]
    (.accept (ClassReader. ^bytes bytes) visitor 0)
    (.toByteArray writer)))

(defn install!
  "Install a real capture loader for the experiment and save rewritten AOT bytes."
  [target descriptors]
  (let [parent @Compiler/LOADER
        delegate (DynamicClassLoader. parent)
        owner (.replace ^String target \. \/)]
    (.set Compiler/LOADER
          (proxy [DynamicClassLoader] [parent]
            (defineClass [class-name bytes source]
              (let [companion-output (rewrite bytes owner descriptors
                                              (= class-name target))]
                (when *compile-files*
                  (Compiler/writeClassFile (.replace ^String class-name \. \/)
                                           companion-output))
                (.defineClass delegate class-name companion-output source)))))))

(defmacro checked-identity
  "Exercise the public declaration path with an isolated capture backend.
   Only String constraints are supported in this feasibility fixture."
  [type fields & methods]
  (let [target (str (namespace-munge *ns*) "." type)
        descriptors (into {} (comp (filter #(:non-nil (meta %)))
                                   (map #(vector (name %) "Ljava/lang/String;")))
                          fields)]
    (install! target descriptors)
    `(let [] (value-type/defvaluetype ~type ~fields ~@methods))))
