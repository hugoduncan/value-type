(ns value-type.impl.method-attributes-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.annotation-support :as annotation-support]
   [value-type.impl.transform :as transform]
   [value-type.impl.transform-test :as transform-test])
  (:import
   (java.lang.classfile Annotation AnnotationElement ClassFile ClassTransform
                        MethodBuilder MethodModel MethodTransform)
   (java.lang.classfile.attribute DeprecatedAttribute ExceptionsAttribute MethodParameterInfo MethodParametersAttribute RuntimeInvisibleAnnotationsAttribute RuntimeVisibleAnnotationsAttribute)
   (java.lang.constant ClassDesc)
   (java.util List)))

(set! *warn-on-reflection* true)

(deftest integrated-method-attributes-test
  ;; Inspect constructor and ordinary method metadata through real composed transforms.
  (testing "native and generated storage preserve method metadata"
    (let [cf (ClassFile/of)
          original (.parse cf ^bytes (:bytes (#'transform-test/fixture :five-fields)))
          visible (RuntimeVisibleAnnotationsAttribute/of
                   (List/of
                    (Annotation/of
                     (ClassDesc/of "java.lang.Deprecated")
                     (List/of (AnnotationElement/ofString "since" "method-review")
                              (AnnotationElement/ofBoolean "forRemoval" true)))))
          invisible (RuntimeInvisibleAnnotationsAttribute/of
                     (List/of
                      (Annotation/of
                       (ClassDesc/of "storage.Marker")
                       (List/of (AnnotationElement/ofInt "revision" 29)))))
          exceptions (ExceptionsAttribute/ofSymbols
                      (List/of (ClassDesc/of "java.io.IOException")
                               (ClassDesc/of "java.lang.ReflectiveOperationException")))
          annotated (.transformClass
                     cf original
                     (ClassTransform/transformingMethods
                      (reify MethodTransform
                        (accept [_ builder element]
                          (.with ^MethodBuilder builder element))
                        (atEnd [_ builder]
                          (.with ^MethodBuilder builder visible)
                          (.with ^MethodBuilder builder invisible)
                          (.with ^MethodBuilder builder exceptions)
                          (.with ^MethodBuilder builder (DeprecatedAttribute/of))))))]
      (doseq [native? [true false]
              :let [result (.parse cf ^bytes (transform/->value-class
                                              annotated {"e" {:storage-type (ClassDesc/of "storage.Value")
                                                              :native? native?}}
                                              nil))]]
        (is (= (count (.methods original)) (count (.methods result))))
        (is (some #(= "<init>" (.stringValue (.methodName ^MethodModel %)))
                  (.methods result)))
        (doseq [^MethodModel method (.methods result)]
          (testing (.stringValue (.methodName method))
            ;; The marker attribute is distinct from the runtime annotation.
            (is (= 1 (count (filterv #(instance? DeprecatedAttribute %)
                                     (.attributes method)))))
            (is (= {["Ljava/lang/Deprecated;"
                     {"since" "method-review" "forRemoval" true}] 1}
                   (annotation-support/annotation-contents
                    method RuntimeVisibleAnnotationsAttribute)))
            (is (= {["Lstorage/Marker;" {"revision" 29}] 1}
                   (annotation-support/annotation-contents
                    method RuntimeInvisibleAnnotationsAttribute)))
            (is (= [["java/io/IOException" "java/lang/ReflectiveOperationException"]]
                   (into []
                         (comp (filter #(instance? ExceptionsAttribute %))
                               (map (fn [^ExceptionsAttribute attribute]
                                      (mapv #(.asInternalName ^java.lang.classfile.constantpool.ClassEntry %)
                                            (.exceptions attribute)))))
                         (.attributes method))))))))))

(deftest integrated-parameter-attributes-test
  ;; Parameter metadata follows argument order, not the two-slot local indexes.
  (testing "native and generated constructors preserve parameter names and flags"
    (let [cf (ClassFile/of)
          original (.parse cf ^bytes (:bytes (#'transform-test/fixture :five-fields)))
          expected [["first" ClassFile/ACC_FINAL]
                    ["wide-long" ClassFile/ACC_SYNTHETIC]
                    ["wide-double" ClassFile/ACC_MANDATED]
                    [nil 0]
                    ["required" ClassFile/ACC_FINAL]]
          parameters (MethodParametersAttribute/of
                      ^List (mapv (fn [[parameter-name flags]]
                                    (MethodParameterInfo/ofParameter
                                     (java.util.Optional/ofNullable parameter-name)
                                     (int flags)))
                                  expected))
          annotated (.transformClass
                     cf original
                     (ClassTransform/transformingMethods
                      (reify java.util.function.Predicate
                        (test [_ method]
                          (= "<init>" (.stringValue (.methodName ^MethodModel method)))))
                      (reify MethodTransform
                        (accept [_ builder element]
                          (.with ^MethodBuilder builder element))
                        (atEnd [_ builder]
                          (.with ^MethodBuilder builder parameters)))))]
      (doseq [native? [true false]
              :let [result (.parse cf ^bytes (transform/->value-class
                                              annotated {"e" {:storage-type (ClassDesc/of "storage.Value")
                                                              :native? native?}}
                                              nil))
                    constructors (filterv #(= "<init>" (.stringValue (.methodName ^MethodModel %)))
                                          (.methods result))]]
        (is (= 1 (count constructors)))
        (doseq [^MethodModel constructor constructors]
          (is (= [expected]
                 (into []
                       (comp
                        (filter #(instance? MethodParametersAttribute %))
                        (map (fn [^MethodParametersAttribute attribute]
                               (mapv (fn [^MethodParameterInfo parameter]
                                       [(some-> (.name parameter) (.orElse nil) str)
                                        (.flagsMask parameter)])
                                     (.parameters attribute)))))
                       (.attributes constructor)))))))))
