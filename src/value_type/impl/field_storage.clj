(ns value-type.impl.field-storage
  "Concrete field storage emission for the value transform."
  (:import
   (java.lang.classfile Annotation ClassBuilder FieldBuilder FieldElement FieldModel)
   (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute)
   (java.lang.constant ClassDesc)
   (java.util List)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private null-restricted
  (Annotation/of (ClassDesc/of "jdk.internal.vm.annotation.NullRestricted") (List/of)))

(defn emit!
  "Emit concrete storage with the ClassDesc `storage-type`. A true `native?`
   adds the native null restriction. Preserve all original elements except the
   descriptor; merge runtime-visible annotations and omit an empty annotations
   attribute. The caller owns strict initialization, constructor casts and field
   accesses."
  [^ClassBuilder builder ^FieldModel field ^ClassDesc storage-type native?]
  (.withField
   builder (.stringValue (.fieldName field)) storage-type
   (reify Consumer
     (accept [_ output]
       (let [^FieldBuilder output output
             annotations (into (if native? [null-restricted] [])
                               (comp (filter #(instance?
                                               RuntimeVisibleAnnotationsAttribute %))
                                     (mapcat #(.annotations
                                               ^RuntimeVisibleAnnotationsAttribute %)))
                               (.elementList field))]
         (doseq [element (.elementList field)
                 :when (not (instance? RuntimeVisibleAnnotationsAttribute element))]
           (.with output ^FieldElement element))
         (when (seq annotations)
           (.with output (RuntimeVisibleAnnotationsAttribute/of ^List annotations))))))))
