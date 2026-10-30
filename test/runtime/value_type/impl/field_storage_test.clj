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
  ;; Inspect real emitted bytes for both enforcement modes, without mocks.
  (doseq [native? [true false]]
    (testing (str "retyping preserves unrelated field elements; native=" native?)
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
                                    (storage/emit! output element (ClassDesc/ofDescriptor "Lstorage/Value;") native?)
                                    (.with output ^ClassElement element)))))))
            ^FieldModel required (first (.fields result))
            ^FieldModel nullable (second (.fields result))
            visible (cond-> {["Ljava/lang/Deprecated;" {"since" "field-review" "forRemoval" true}] 1}
                      native? (assoc ["Ljdk/internal/vm/annotation/NullRestricted;" {}] 1))]
        (is (= "Lstorage/Value;" (.stringValue (.fieldType required))))
        (is (= (.flagsMask (.flags ^FieldModel (first (.fields original))))
               (.flagsMask (.flags required))))
        (is (= visible
               (annotation-support/annotation-contents required RuntimeVisibleAnnotationsAttribute)))
        (is (= {["Lstorage/Marker;" {"value" "retained" "revision" 17}] 1}
               (annotation-support/annotation-contents required RuntimeInvisibleAnnotationsAttribute)))
        (is (= "Ljava/lang/Object;" (.stringValue (.fieldType nullable))))
        (is (empty? (annotation-support/annotation-contents nullable RuntimeVisibleAnnotationsAttribute)))))))

(deftest generated-storage-without-annotations-has-no-attribute
  (testing "a generated field without source annotations gets no empty attribute"
    (let [cf (ClassFile/of)
          result (.parse cf
                         (.transformClass
                          cf (.parse cf (fixture))
                          (reify ClassTransform
                            (accept [_ output element]
                              (let [^ClassBuilder output output]
                                (if (and (instance? FieldModel element)
                                         (= "nullable" (.stringValue (.fieldName ^FieldModel element))))
                                  (storage/emit! output element (ClassDesc/ofDescriptor "Lstorage/Value;") false)
                                  (.with output ^ClassElement element)))))))
          ^FieldModel generated (second (.fields result))]
      (is (= "Lstorage/Value;" (.stringValue (.fieldType generated))))
      (is (not-any? #(instance? RuntimeVisibleAnnotationsAttribute %)
                    (.elementList generated))))))
