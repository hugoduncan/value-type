(ns value-type.impl.field-storage
  "Concrete field storage emission for the native value transform."
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
  "Emit a field with concrete storage and native restriction. Preserve all
   original elements except the descriptor; merge runtime-visible annotations.
   The caller owns strict initialization, constructor casts and field accesses."
  [^ClassBuilder builder ^FieldModel field ^String descriptor]
  (.withField
   builder (.stringValue (.fieldName field)) (ClassDesc/ofDescriptor descriptor)
   (reify Consumer
     (accept [_ output]
       (let [^FieldBuilder output output
             annotations (into [null-restricted]
                               (comp (filter #(instance? RuntimeVisibleAnnotationsAttribute %))
                                     (mapcat #(.annotations ^RuntimeVisibleAnnotationsAttribute %)))
                               (.elementList field))]
         (doseq [element (.elementList field)
                 :when (not (instance? RuntimeVisibleAnnotationsAttribute element))]
           (.with output ^FieldElement element))
         (.with output (RuntimeVisibleAnnotationsAttribute/of ^List annotations)))))))
