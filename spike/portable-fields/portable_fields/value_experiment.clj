(ns portable-fields.value-experiment
  "Isolated generated-check value-owner gate. No native annotation is emitted."
  (:require [value-type.core :as value-type]
            [value-type.impl.class-file :as class-file]
            [value-type.impl.field-access :as field-access]
            [value-type.impl.transform :as transform])
  (:import (clojure.lang Compiler DynamicClassLoader)
           (java.lang.classfile ClassBuilder ClassElement ClassTransform
                                CodeBuilder CodeElement CodeTransform FieldModel Opcode)
           (java.lang.classfile.instruction FieldInstruction)
           (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
           (java.util.function Consumer)))

(defn rewrite
  "Retype value storage and insert branch-free checks before strict field writes."
  [bytes target storage loader]
  (let [cf (class-file/configured loader)
        owner (ClassDesc/of target)
        fields (reify ClassTransform
                 (accept [_ builder element]
                   (let [^ClassBuilder builder builder
                         concrete (when (instance? FieldModel element)
                                    (get storage (.stringValue (.fieldName ^FieldModel element))))]
                     (if concrete
                       (.withField builder (.stringValue (.fieldName ^FieldModel element))
                                   ^ClassDesc concrete
                                   (reify Consumer
                                     (accept [_ output]
                                       (doseq [part (.elementList ^FieldModel element)]
                                         (.with ^java.lang.classfile.FieldBuilder output
                                                ^java.lang.classfile.FieldElement part)))))
                       (.with builder ^ClassElement element)))))
        checks (reify CodeTransform
                 (accept [_ builder element]
                   (let [^CodeBuilder builder builder
                         instruction (when (instance? FieldInstruction element) element)
                         concrete (when (and instruction
                                             (= owner (.asSymbol (.owner ^FieldInstruction instruction))))
                                    (get storage (.stringValue (.name ^FieldInstruction instruction))))]
                     (if concrete
                       (do
                         (when (= Opcode/PUTFIELD (.opcode ^FieldInstruction instruction))
                           (.invokestatic builder (ClassDesc/of "java.util.Objects") "requireNonNull"
                                          (MethodTypeDesc/of ConstantDescs/CD_Object
                                                             (into-array ClassDesc [ConstantDescs/CD_Object])))
                           (.checkcast builder ^ClassDesc concrete))
                         (.fieldAccess builder (.opcode ^FieldInstruction instruction) owner
                                       (.stringValue (.name ^FieldInstruction instruction)) ^ClassDesc concrete))
                       (.with builder ^CodeElement element)))))]
    (.transformClass cf (.parse cf ^bytes bytes)
                     (.andThen fields (ClassTransform/transformingMethodBodies checks)))))

(defmacro checked-value
  "Exercise public representation selection, then isolated generated enforcement.
   All marked fields deliberately use generated checks, even for value tags."
  [type fields & methods]
  (let [expansion (macroexpand-1 `(value-type/defvaluetype ~type ~fields ~@methods))
        target (str (namespace-munge *ns*) "." type)
        storage (into {} (comp (filter #(:non-nil (meta %)))
                               (map (fn [field]
                                      [(name field) (ClassDesc/of (.getName ^Class (resolve (:tag (meta field)))))])))
                      fields)
        parent @Compiler/LOADER
        delegate (DynamicClassLoader. parent)]
    (.set Compiler/LOADER
          (proxy [DynamicClassLoader] [parent]
            (defineClass [class-name bytes source]
              (let [output (if (= target class-name)
                             (rewrite (transform/->value-class bytes {} delegate) target storage delegate)
                             (field-access/rewrite-class bytes target storage delegate))]
                (.defineClass delegate class-name output source)))))
    `(let [] ~expansion)))
