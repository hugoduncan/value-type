(ns field-probe.support
  "Experiment only: retype selected fields after the production value transform."
  (:require [value-type.impl.transform :as transform])
  (:import
   (clojure.lang Compiler DynamicClassLoader)
   (java.lang.classfile Annotation ClassBuilder ClassElement ClassFile
                        ClassTransform CodeBuilder CodeElement CodeTransform
                        FieldBuilder FieldModel MethodModel Opcode)
   (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute)
   (java.lang.classfile.instruction FieldInstruction)
   (java.lang.constant ClassDesc)
   (java.util.function Consumer)))

(defn- field-code
  [owner field-types]
  (reify CodeTransform
    (accept [_ builder element]
      (let [^CodeBuilder builder builder]
        (if (and (instance? FieldInstruction element)
                 (= owner (.asInternalName (.owner ^FieldInstruction element)))
                 (contains? field-types (.stringValue (.name ^FieldInstruction element))))
          (let [^FieldInstruction instruction element
                field-name (.stringValue (.name instruction))
                descriptor (ClassDesc/of ^String (get field-types field-name))]
            (when (= Opcode/PUTFIELD (.opcode instruction))
              (.checkcast builder descriptor))
            (.fieldAccess builder (.opcode instruction) (.asSymbol (.owner instruction))
                          field-name descriptor))
          (.with builder ^CodeElement element))))))

(defn retype-fields
  "Change field descriptors and their accesses; retain Object constructor arguments."
  [^bytes value-bytes field-types restricted?]
  (let [classfile (ClassFile/of)
        model (.parse classfile value-bytes)
        owner (.asInternalName (.thisClass model))]
    (.transformClass
     classfile model
     (reify ClassTransform
       (accept [_ builder element]
         (let [^ClassBuilder builder builder]
           (cond
             (and (instance? FieldModel element)
                  (contains? field-types (.stringValue (.fieldName ^FieldModel element))))
             (let [^FieldModel field element
                   field-name (.stringValue (.fieldName field))]
               (.withField
                builder field-name (ClassDesc/of ^String (get field-types field-name))
                (reify Consumer
                  (accept [_ field-builder]
                    (let [^FieldBuilder field-builder field-builder]
                      (.withFlags field-builder (.flagsMask (.flags field)))
                      (when restricted?
                        (.with field-builder
                               (RuntimeVisibleAnnotationsAttribute/of
                                [(Annotation/of
                                  (ClassDesc/of "jdk.internal.vm.annotation.NullRestricted")
                                  [])]))))))))

             (instance? MethodModel element)
             (.transformMethod
              builder ^MethodModel element
              (java.lang.classfile.MethodTransform/transformingCode
               (field-code owner field-types)))

             :else (.with builder ^ClassElement element))))))))

(defn install!
  "Install an experiment loader. No production Vars are replaced."
  [target field-types restricted?]
  (let [parent @Compiler/LOADER
        delegate (DynamicClassLoader. parent)
        loader (proxy [DynamicClassLoader] [parent]
                 (defineClass [class-name bytes source]
                   (let [output (if (= target class-name)
                                  (retype-fields (transform/->value-class bytes)
                                                 field-types restricted?)
                                  bytes)]
                     (when (and *compile-files* (= target class-name))
                       (Compiler/writeClassFile (.replace ^String class-name \. \/) output))
                     (let [defined (.defineClass delegate class-name output source)]
                       (.getDeclaredMethods defined)
                       defined))))]
    (.set Compiler/LOADER loader)))

(defmacro probe-type
  "Use :probe/type metadata to select concrete reference field descriptors."
  [type-name fields restricted? & specs]
  (let [target (str (namespace-munge *ns*) "." type-name)
        field-types (into {}
                          (keep (fn [field]
                                  (when-let [field-type (:probe/type (meta field))]
                                    [(Compiler/munge (name field)) (str field-type)])))
                          fields)]
    (install! target field-types restricted?)
    `(deftype ~type-name ~fields ~@specs)))
