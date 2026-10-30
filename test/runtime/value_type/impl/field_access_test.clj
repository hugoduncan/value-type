(ns value-type.impl.field-access-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.field-access :as access])
  (:import
   (java.lang.classfile ClassBuilder ClassFile ClassTransform CodeBuilder
                        CodeModel MethodModel Opcode)
   (java.lang.classfile.instruction FieldInstruction)
   (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private owner (ClassDesc/of "access.Owner"))
(def ^:private concrete (ClassDesc/of "access.Value"))

(defn- fixture ^bytes [^ClassDesc caller]
  (.build
   (ClassFile/of) caller
   (reify Consumer
     (accept [_ builder]
       (.withMethodBody
        ^ClassBuilder builder "read" (MethodTypeDesc/ofDescriptor "(Laccess/Owner;)V")
        (bit-or ClassFile/ACC_PUBLIC ClassFile/ACC_STATIC)
        (reify Consumer
          (accept [_ code]
            (let [^CodeBuilder code code]
              (doseq [[target field descriptor]
                      [[owner "required" ConstantDescs/CD_Object]
                       [owner "nullable" ConstantDescs/CD_Object]
                       [(ClassDesc/of "access.Other") "required" ConstantDescs/CD_Object]
                       [owner "required" concrete]]]
                (.aload code 0)
                (.getfield code target field descriptor)
                (.pop code))
              (.aload code 0)
              (.aconst_null code)
              (.putfield code owner "required" ConstantDescs/CD_Object)
              (.return_ code)))))))))

(defn- references [^bytes bytes]
  (let [model (.parse (ClassFile/of) bytes)
        ^MethodModel method (first (.methods model))
        ^CodeModel code (.get (.code method))]
    (into []
          (comp (filter #(instance? FieldInstruction %))
                (map (fn [^FieldInstruction instruction]
                       [(.opcode instruction)
                        (.asInternalName (.owner instruction))
                        (.stringValue (.name instruction))
                        (.stringValue (.type instruction))])))
          (.elementList code))))

(deftest selected-instance-references
  ;; Owner and companion code must use the same concrete field descriptor.
  (testing "only selected Object instance references change"
    (doseq [caller [owner (ClassDesc/of "access.Companion")]]
      (let [cf (ClassFile/of)
            bytes (fixture caller)
            model (.parse cf bytes)
            output (access/rewrite-class bytes "access.Owner"
                                         {"required" {:storage-type concrete}} nil)]
        (is (= [[Opcode/GETFIELD "access/Owner" "required" "Laccess/Value;"]
                [Opcode/GETFIELD "access/Owner" "nullable" "Ljava/lang/Object;"]
                [Opcode/GETFIELD "access/Other" "required" "Ljava/lang/Object;"]
                [Opcode/GETFIELD "access/Owner" "required" "Laccess/Value;"]
                [Opcode/PUTFIELD "access/Owner" "required" "Laccess/Value;"]]
               (references output)))
        (is (= (references bytes)
               (references (.transformClass cf model
                                            (ClassTransform/transformingMethodBodies
                                             (access/rewrite owner {}))))))))))
