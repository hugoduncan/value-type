(ns value-type.annotation-support
  "Runtime-only ClassFile annotation comparisons."
  (:import
   (java.lang.classfile Annotation AnnotationElement AnnotationValue$OfConstant FieldModel)
   (java.lang.classfile.attribute RuntimeInvisibleAnnotationsAttribute
                                  RuntimeVisibleAnnotationsAttribute)))

(set! *warn-on-reflection* true)

(defn annotation-contents
  "Count annotations by type and named constant values, independent of order."
  [^FieldModel field attribute-class]
  (frequencies
   (into []
         (comp (filter #(instance? attribute-class %))
               (mapcat #(cond
                          (instance? RuntimeVisibleAnnotationsAttribute %)
                          (.annotations ^RuntimeVisibleAnnotationsAttribute %)
                          :else (.annotations ^RuntimeInvisibleAnnotationsAttribute %)))
               (map (fn [^Annotation annotation]
                      [(.stringValue (.className annotation))
                       (into {}
                             (map (fn [^AnnotationElement element]
                                    [(.stringValue (.name element))
                                     (.resolvedValue ^AnnotationValue$OfConstant (.value element))]))
                             (.elements annotation))])))
         (.elementList field))))
