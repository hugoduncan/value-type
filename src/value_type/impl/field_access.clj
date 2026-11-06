(ns value-type.impl.field-access
  "Field-reference rewriting shared by owner and companion class transforms."
  (:require [value-type.impl.class-file :as class-file])
  (:import
   (java.lang.classfile ClassFile ClassTransform CodeBuilder CodeElement CodeTransform
                        Opcode)
   (java.lang.classfile.instruction FieldInstruction)
   (java.lang.constant ClassDesc ConstantDescs)))

(set! *warn-on-reflection* true)

(defn owner-field-access?
  "True when `element` is a `getfield` or `putfield` on the class `owner`
  (a `ClassDesc`)."
  [^ClassDesc owner element]
  (and (instance? FieldInstruction element)
       (#{Opcode/GETFIELD Opcode/PUTFIELD} (.opcode ^FieldInstruction element))
       (= owner (.asSymbol (.owner ^FieldInstruction element)))))

(defn rewrite
  "Return a code transform for Object instance-field references to `owner`.
   `storage` maps emitted field names to `{:storage-type ClassDesc ...}`.
   Preserve references to other owners, unselected fields and other descriptors.
   Constructor emission owns casts before writes; this transform adds no casts."
  ^CodeTransform [^ClassDesc owner storage]
  (reify CodeTransform
    (accept [_ builder element]
      (let [^CodeBuilder builder builder
            ^FieldInstruction instruction (when (owner-field-access? owner element)
                                            element)
            ^ClassDesc storage-type
            (when (and instruction
                       (= ConstantDescs/CD_Object (.typeSymbol instruction)))
              (:storage-type (get storage (.stringValue (.name instruction)))))]
        (if storage-type
          (.fieldAccess builder (.opcode instruction) owner
                        (.stringValue (.name instruction)) storage-type)
          (.with builder ^CodeElement element))))))

(defn rewrite-class
  "Rewrite references to non-nil fields of `owner-name` in captured bytes.
   `storage` is as for `rewrite`. Keep the caller's class kind, fields and
   method signatures unchanged.
   Stack-map generation resolves the captured class and the owner from their
   known hierarchy, because neither is loadable yet. `class-loader`, when not
   nil, resolves the other types."
  ^bytes [^bytes bytes ^String owner-name storage class-loader]
  (let [model (.parse (ClassFile/of) bytes)
        cf (class-file/configured class-loader
                                  (into {} [(class-file/owner-hierarchy-info owner-name)
                                            (class-file/hierarchy-info model)]))]
    (.transformClass cf model
                     (ClassTransform/transformingMethodBodies
                      (rewrite (ClassDesc/of owner-name) storage)))))
