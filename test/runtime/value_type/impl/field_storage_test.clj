(ns value-type.impl.field-storage-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.annotation-support :as annotation-support]
   [value-type.impl.field-storage :as storage])
  (:import
   (java.lang.classfile Annotation ClassBuilder ClassElement ClassFile ClassTransform
                        FieldBuilder FieldModel AnnotationElement)
   (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute
                                  RuntimeInvisibleAnnotationsAttribute)
   (java.lang.constant ClassDesc ConstantDescs)
   (java.util List)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(defn- fixture ^bytes []
  (.build
   (ClassFile/of) (ClassDesc/of "storage.Holder")
   (reify Consumer
     (accept [_ output]
       (let [^ClassBuilder output output]
         (.withField
          output "required" ConstantDescs/CD_Object
          (reify Consumer
            (accept [_ field]
              (let [^FieldBuilder field field]
                (.withFlags field (int (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_FINAL)))
                (.with field (RuntimeVisibleAnnotationsAttribute/of
                              (List/of (Annotation/of (ClassDesc/of "java.lang.Deprecated")
                                                      (List/of (AnnotationElement/ofString "since" "field-review")
                                                               (AnnotationElement/ofBoolean "forRemoval" true))))))
                (.with field (RuntimeInvisibleAnnotationsAttribute/of
                              (List/of (Annotation/of (ClassDesc/of "storage.Marker")
                                                      (List/of (AnnotationElement/ofString "value" "retained")
                                                               (AnnotationElement/ofInt "revision" 17))))))))))
         (.withField output "nullable" ConstantDescs/CD_Object ClassFile/ACC_PUBLIC))))))

(deftest concrete-storage-preserves-field-elements
  ;; Check emitted bytes without loading an incomplete constructor transform.
  (testing "retyping merges annotations and preserves unrelated field elements"
    (let [cf (ClassFile/of)
          original (.parse cf (fixture))
          result (.parse cf
                         (.transformClass
                          cf original
                          (reify ClassTransform
                            (accept [_ output element]
                              (let [^ClassBuilder output output]
                                (if (and (instance? FieldModel element)
                                         (= "required" (.stringValue (.fieldName ^FieldModel element))))
                                  (storage/emit! output element "Lstorage/Value;")
                                  (.with output ^ClassElement element)))))))
          ^FieldModel required (first (.fields result))
          ^FieldModel nullable (second (.fields result))]
      (is (= "Lstorage/Value;" (.stringValue (.fieldType required))))
      (is (= (.flagsMask (.flags ^FieldModel (first (.fields original))))
             (.flagsMask (.flags required))))
      (is (= {["Ljdk/internal/vm/annotation/NullRestricted;" {}] 1
              ["Ljava/lang/Deprecated;" {"since" "field-review" "forRemoval" true}] 1}
             (annotation-support/annotation-contents required RuntimeVisibleAnnotationsAttribute)))
      (is (= {["Lstorage/Marker;" {"value" "retained" "revision" 17}] 1}
             (annotation-support/annotation-contents required RuntimeInvisibleAnnotationsAttribute)))
      (is (= "Ljava/lang/Object;" (.stringValue (.fieldType nullable))))
      (is (empty? (annotation-support/annotation-contents nullable RuntimeVisibleAnnotationsAttribute))))))