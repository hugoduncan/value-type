(ns value-type.public-field-annotations-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.core]))

(deftest public-field-annotations-test
  ;; Real public compilation must retain metadata unrelated to enforcement.
  (doseq [tags [['String] ['Long] ['Long 'String]]
          expansion [macroexpand macroexpand-1]]
    (testing (str "annotations with " tags)
      (let [receiver (create-ns (gensym "field.annotations"))]
        (try
          (binding [*ns* receiver]
            (clojure.core/refer 'clojure.core)
            (let [type-name (with-meta 'Holder
                              {'Deprecated {}
                               'javax.annotation.processing.SupportedOptions
                               ["portable" "non-nil"]
                               'javax.annotation.processing.SupportedSourceVersion
                               {:value 'javax.lang.model.SourceVersion/RELEASE_8}})
                  field-names (mapv (fn [index tag]
                                      (with-meta (symbol (str "required" index))
                                        {:tag tag :non-nil true 'Deprecated {}}))
                                    (range (count tags)) tags)
                  method-name (with-meta 'toString
                                {'Deprecated {}
                                 'javax.management.DescriptorKey
                                 {:value "portable-field"}
                                 'java.beans.Transient {:value false}})
                  declaration (list 'value-type.core/defvaluetype
                                    type-name
                                    (into field-names [(with-meta 'optional {'Deprecated {}})
                                                       (with-meta 'count-value {:tag 'long 'Deprecated {}})
                                                       (with-meta 'ratio {:tag 'double 'Deprecated {}})])
                                    'Object (list method-name ['this]
                                                  (cons 'str field-names)))
                  ^Class owner (eval (expansion declaration))
                  method (.getMethod owner "toString" (make-array Class 0))
                  inputs (mapv #(if (= % 'String) "required" (Long/valueOf 42))
                               tags)
                  instance (apply (ns-resolve receiver '->Holder) (into inputs [nil 73 2.5]))]
              (testing "owner and ordinary two-slot field annotations survive"
                (is (.isAnnotationPresent owner Deprecated))
                (doseq [[field-name field-type expected]
                        [["count_value" Long/TYPE 73] ["ratio" Double/TYPE 2.5]]]
                  (let [field (.getField owner field-name)]
                    (is (.isAnnotationPresent field Deprecated))
                    (is (identical? field-type (.getType field)))
                    (is (= expected (.get field instance))))))
              (let [field (.getField owner "optional")]
                (is (.isAnnotationPresent field Deprecated))
                (is (identical? Object (.getType field)))
                (is (nil? (.get field instance))))
              (is (= javax.lang.model.SourceVersion/RELEASE_8
                     (.value ^javax.annotation.processing.SupportedSourceVersion
                      (.getAnnotation owner
                                      javax.annotation.processing.SupportedSourceVersion))))
              (is (= ["portable" "non-nil"]
                     (vec (.value ^javax.annotation.processing.SupportedOptions
                           (.getAnnotation owner
                                           javax.annotation.processing.SupportedOptions)))))
              (is (.isAnnotationPresent method Deprecated))
              (is (false? (.value ^java.beans.Transient
                           (.getAnnotation method java.beans.Transient))))
              (is (= "portable-field"
                     (.value ^javax.management.DescriptorKey
                      (.getAnnotation method javax.management.DescriptorKey))))
              (doseq [[field-name input] (mapv vector field-names inputs)]
                (let [field (.getField owner (name field-name))]
                  (is (.isAnnotationPresent field Deprecated))
                  (is (identical? (class input) (.getType field)))
                  (is (identical? input (.get field instance)))))
              (is (= (apply str inputs) (.invoke method instance (object-array 0))))))
          (finally
            (remove-ns (ns-name receiver))))))))
