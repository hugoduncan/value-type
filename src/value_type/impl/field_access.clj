(ns value-type.impl.field-access
  "Field-reference rewriting shared by owner and companion class transforms."
  (:require [value-type.impl.class-file :as class-file])
  (:import
   (java.lang.classfile ClassTransform CodeBuilder CodeElement CodeTransform Opcode)
   (java.lang.classfile.instruction FieldInstruction)
   (java.lang.constant ClassDesc ConstantDescs)))

(set! *warn-on-reflection* true)

(defn rewrite
  "Return a code transform for Object instance-field references to `owner`.
   `storage-types` maps emitted field names to concrete ClassDesc values.
   Preserve references to other owners, unselected fields and other descriptors.
   Constructor emission owns casts before writes; this transform adds no casts."
  ^CodeTransform [^ClassDesc owner storage-types]
  (reify CodeTransform
    (accept [_ builder element]
      (let [^CodeBuilder builder builder
            ^FieldInstruction instruction (when (instance? FieldInstruction element)
                                            element)
            ^ClassDesc storage-type
            (when (and instruction
                       (#{Opcode/GETFIELD Opcode/PUTFIELD} (.opcode instruction))
                       (= owner (.asSymbol (.owner instruction)))
                       (= ConstantDescs/CD_Object (.typeSymbol instruction)))
              (get storage-types (.stringValue (.name instruction))))]
        (if storage-type
          (.fieldAccess builder (.opcode instruction) owner
                        (.stringValue (.name instruction)) storage-type)
          (.with builder ^CodeElement element))))))

(defn rewrite-class
  "Rewrite references to restricted fields of `owner-name` in captured bytes.
   Keep the caller's class kind, fields and method signatures unchanged.
   Optional class-loader resolves dynamic types during stack-map generation."
  (^bytes [^bytes bytes ^String owner-name storage-types]
   (rewrite-class bytes owner-name storage-types nil))
  (^bytes [^bytes bytes ^String owner-name storage-types class-loader]
   (let [cf (class-file/configured class-loader)]
     (.transformClass cf (.parse cf bytes)
                      (ClassTransform/transformingMethodBodies
                       (rewrite (ClassDesc/of owner-name) storage-types))))))
