(ns value-type.field-aot-checks
  "EA3-only inspection of persisted restricted-field fixtures."
  (:require [clojure.java.io :as io])
  (:import (java.lang.classfile Annotation ClassFile FieldModel MethodModel)
           (java.lang.classfile.attribute RuntimeVisibleAnnotationsAttribute)
           (java.lang.classfile.instruction FieldInstruction)
           (java.nio.file Files)))

(defn inspect-output
  "Read the saved owner and companion bytes, without using loaded Classes."
  [output]
  (let [cf (ClassFile/of)
        owner (.parse cf (Files/readAllBytes (.toPath (io/file output "restricted/aot_fixture/Holder.class"))))
        fields (into {} (map (fn [^FieldModel field]
                               [(.stringValue (.fieldName field)) (.stringValue (.fieldType field))]))
                     (.fields owner))
        annotations (into []
                          (comp (filter #(= "required" (.stringValue (.fieldName ^FieldModel %))))
                                (mapcat #(.elementList ^FieldModel %))
                                (filter #(instance? RuntimeVisibleAnnotationsAttribute %))
                                (mapcat #(.annotations ^RuntimeVisibleAnnotationsAttribute %))
                                (map #(.stringValue (.className ^Annotation %))))
                          (.fields owner))
        constructors (into []
                           (comp (filter #(= "<init>" (.stringValue (.methodName ^MethodModel %))))
                                 (map #(.stringValue (.methodType ^MethodModel %))))
                           (.methods owner))
        references (into []
                         (comp (filter #(and (.isFile ^java.io.File %)
                                             (.endsWith (.getName ^java.io.File %) ".class")
                                             (not= "Holder.class" (.getName ^java.io.File %))))
                               (map #(.parse cf (Files/readAllBytes (.toPath ^java.io.File %))))
                               (mapcat #(.methods ^java.lang.classfile.ClassModel %))
                               (mapcat #(if (.isPresent (.code ^MethodModel %))
                                          (.elementList (.get (.code ^MethodModel %))) []))
                               (filter #(instance? FieldInstruction %))
                               (filter #(= "restricted/aot_fixture/Holder"
                                           (.asInternalName (.owner ^FieldInstruction %))))
                               (filter #(= "required" (.stringValue (.name ^FieldInstruction %))))
                               (map #(.stringValue (.type ^FieldInstruction %))))
                         (file-seq (io/file output)))]
    {:fields (select-keys fields ["required" "nullable" "count" "weight"])
     :annotations annotations :constructors constructors
     :companion-references references}))
