(ns value-type.identity-backend-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.identity-backend.fixture]
            [value-type.impl.identity-backend]
            [value-type.message-checks :refer [message-names?]])
  (:import (java.lang.reflect Modifier)
           (value_type.identity_backend.fixture Mixed Reads)))

(def ^:private fixture-ns
  'value-type.identity-backend.fixture)

(deftest checked-identity-contract
  ;; Exercise real transformed constructors and field reads, not a factory check.
  (testing "concrete final storage and unchanged constructor parameters"
    (let [field (.getField Mixed "label")]
      (is (= String (.getType field)))
      (is (Modifier/isFinal (.getModifiers field))))
    (is (.getConstructor Mixed (into-array Class [Long/TYPE Object Double/TYPE Object]))))
  (testing "mixed slots, nullable control, owner and captured reads"
    (let [value (Mixed. 7 "valid" 2.5 nil)]
      (is (= [7 "valid" 2.5 nil] (.snapshot ^Reads value)))
      (is (= "valid" ((.captured ^Reads value))))
      (is (= "valid" (.-label value)))
      (is (= "valid" (.get (.getField Mixed "label") value)))))
  (testing "factory, direct and reflective constructors enforce checks"
    (doseq [[construction build] (checks/named-builders fixture-ns Mixed)]
      (testing (pr-str construction)
        (is (= checks/checked-rejections
               (checks/checked-outcomes (fn [[x]] (build [7 x 2.5 nil])) 42)))))))

(defn- field-reference-bytes
  [storage? opcode descriptor]
  (let [writer (clojure.asm.ClassWriter. 0)]
    (.visit writer clojure.asm.Opcodes/V1_8 clojure.asm.Opcodes/ACC_PUBLIC
            "Shape" nil "java/lang/Object" nil)
    (when storage?
      (.visitEnd (.visitField writer clojure.asm.Opcodes/ACC_FINAL
                              "label" "Ljava/lang/Object;" nil nil)))
    (when opcode
      (let [method (.visitMethod writer clojure.asm.Opcodes/ACC_PUBLIC
                                 "read" "()V" nil nil)]
        (.visitCode method)
        (.visitFieldInsn method opcode "Shape" "label" descriptor)
        (.visitInsn method clojure.asm.Opcodes/RETURN)
        (.visitMaxs method 1 1)
        (.visitEnd method)))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn- shape-parts
  "The parts that the `:unsupported-shape` message for class `Shape` and
  `part` must name."
  [part]
  ["Shape" part "use Clojure 1.12"])

(defn- shape-failure
  "Return the ex-data of the failure of `(transform bytes)` with its
  `:message`, or nil when it succeeds."
  [transform bytes]
  (try
    (transform bytes)
    nil
    (catch clojure.lang.ExceptionInfo e
      (assoc (ex-data e) :message (ex-message e)))))

(deftest unsupported-storage-references
  ;; Feed real class bytes to the backend and inspect its structured failures.
  (let [transforms (value-type.impl.identity-backend/prepare
                    "Shape" [{:field 'label :type String :enforcement :generated}] nil)]
    (testing "owner storage must exist"
      (let [{error :value-type/error message :message}
            (shape-failure (:owner transforms) (field-reference-bytes false nil nil))]
        (is (= :unsupported-shape error))
        (is (message-names? message (shape-parts "missing non-nil field storage"))
            message)))
    (testing "owner and companion references must be instance Object references"
      (doseq [[transform-key transform] transforms
              [instruction opcode descriptor]
              [['GETFIELD clojure.asm.Opcodes/GETFIELD "Ljava/lang/String;"]
               ['GETSTATIC clojure.asm.Opcodes/GETSTATIC "Ljava/lang/Object;"]
               ['PUTSTATIC clojure.asm.Opcodes/PUTSTATIC "Ljava/lang/Object;"]]]
        (testing (pr-str transform-key instruction)
          (let [{error :value-type/error message :message}
                (shape-failure transform (field-reference-bytes true opcode descriptor))]
            (is (= :unsupported-shape error))
            (is (message-names? message (shape-parts "unsupported non-nil field reference"))
                message)))))))

(defn- constructor-bytes
  [write-count branch? extra-constructor?]
  (let [writer (clojure.asm.ClassWriter. 0)
        emit (fn [descriptor count-writes]
               (let [method (.visitMethod writer clojure.asm.Opcodes/ACC_PUBLIC
                                          "<init>" descriptor nil nil)]
                 (.visitCode method)
                 (.visitVarInsn method clojure.asm.Opcodes/ALOAD 0)
                 (.visitMethodInsn method clojure.asm.Opcodes/INVOKESPECIAL
                                   "java/lang/Object" "<init>" "()V" false)
                 (when branch?
                   (let [label (clojure.asm.Label.)]
                     (.visitJumpInsn method clojure.asm.Opcodes/GOTO label)
                     (.visitLabel method label)))
                 (dotimes [_ count-writes]
                   (.visitVarInsn method clojure.asm.Opcodes/ALOAD 0)
                   (.visitVarInsn method clojure.asm.Opcodes/ALOAD 1)
                   (.visitFieldInsn method clojure.asm.Opcodes/PUTFIELD
                                    "Shape" "label" "Ljava/lang/Object;"))
                 (.visitInsn method clojure.asm.Opcodes/RETURN)
                 (.visitMaxs method 2 2)
                 (.visitEnd method)))]
    (.visit writer clojure.asm.Opcodes/V1_8 clojure.asm.Opcodes/ACC_PUBLIC
            "Shape" nil "java/lang/Object" nil)
    (.visitEnd (.visitField writer clojure.asm.Opcodes/ACC_FINAL
                            "label" "Ljava/lang/Object;" nil nil))
    (emit "(Ljava/lang/Object;)V" write-count)
    (when extra-constructor? (emit "()V" 0))
    (.visitEnd writer)
    (.toByteArray writer)))

(deftest constructor-coverage
  ;; Unsupported constructors must fail before unchecked classes can be installed.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Shape" [{:field 'label :type String :enforcement :generated}] nil))]
    (testing "straight-line writes cover the selected field"
      (is (bytes? (transform (constructor-bytes 1 false false)))))
    (testing "missing constructors, missing or repeated writes and branches fail"
      (doseq [[case-name bytes part]
              [["no constructor" (field-reference-bytes true nil nil)
                "missing non-nil constructor"]
               ["no write" (constructor-bytes 0 false false)
                "incomplete non-nil constructor writes"]
               ["two writes" (constructor-bytes 2 false false)
                "repeated non-nil constructor write"]
               ["branch" (constructor-bytes 1 true false)
                "unsupported non-nil constructor control flow"]
               ["extra constructor" (constructor-bytes 1 false true)
                "incomplete non-nil constructor writes"]]]
        (testing case-name
          (let [{error :value-type/error message :message} (shape-failure transform bytes)]
            (is (= :unsupported-shape error))
            (is (message-names? message (shape-parts part)) message)))))))

(deftest checked-identity-methods
  ;; Real compiler stack maps must remain valid across retyped companion reads.
  (testing "protocol and Object methods read concrete storage"
    (let [value (Mixed. 7 "label" 2.5 nil)]
      (is (= "label:7" (value-type.identity-backend.fixture/describe value)))
      (is (= "label" (str value)))))
  (testing "captured branch merges retain constrained and nullable reads"
    (doseq [[nullable expected] [[nil "empty"] [42 "42"] ["other" "other"]]]
      (let [value (Mixed. 7 "label" 2.5 nullable)]
        (is (= "label" ((.choose ^Reads value true))))
        (is (= expected ((.choose ^Reads value false))))))))

(deftest multiple-identity-constraints
  ;; Each constrained position must be checked by every construction entry point.
  (let [pair-class value_type.identity_backend.fixture.Pair
        date (java.util.Date. 0)
        field-names ["left" "count" "right" "weight" "nullable"]
        args ["left" 7 date 2.5 nil]
        checked [["left" date] ["right" "wrong"]]]
    (testing "both fields retain concrete final storage"
      (doseq [[field-name expected] [["left" String] ["right" java.util.Date]]]
        (let [field (.getField pair-class field-name)]
          (is (= expected (.getType field)))
          (is (Modifier/isFinal (.getModifiers field))))))
    (testing "valid arguments preserve slots and nullable controls"
      (doseq [[construction build] (checks/named-builders fixture-ns pair-class)]
        (testing (pr-str construction)
          (let [value (build args)]
            (is (= ["left" 7 date 2.5 nil]
                   (mapv #(.get (.getField pair-class %) value) field-names)))))))
    (testing "each constrained position rejects nil and wrong types"
      (doseq [[construction build] (checks/named-builders fixture-ns pair-class)]
        (testing (pr-str construction)
          (doseq [[field-name wrong] checked]
            (testing field-name
              (let [position (.indexOf ^java.util.List field-names field-name)]
                (is (= checks/checked-rejections
                       (checks/checked-outcomes
                        (fn [[x]] (build (assoc args position x))) wrong)))))))))))

(defn- annotated-constructor-bytes
  []
  (let [writer (clojure.asm.ClassWriter. 0)
        annotate (fn [visitor]
                   (.visitEnd ^clojure.asm.AnnotationVisitor visitor))
        visitor
        (proxy [clojure.asm.ClassVisitor] [clojure.asm.Opcodes/ASM6 writer]
          (visitEnd []
            (annotate (.visitAnnotation writer "Ljava/lang/Deprecated;" true))
            (proxy-super visitEnd))
          (visitField [access field desc signature value]
            (let [field-visitor (proxy-super visitField access field desc signature value)]
              (annotate (.visitAnnotation ^clojure.asm.FieldVisitor field-visitor
                                          "Ljava/lang/Deprecated;" true))
              field-visitor))
          (visitMethod [access method desc signature _exceptions]
            (let [method-visitor (proxy-super visitMethod access method desc signature
                                              (into-array String ["java/io/IOException"]))]
              (annotate (.visitAnnotation ^clojure.asm.MethodVisitor method-visitor
                                          "Ljava/lang/Deprecated;" true))
              method-visitor)))]
    (.accept (clojure.asm.ClassReader. ^bytes (constructor-bytes 1 false false)) visitor 0)
    (.toByteArray writer)))

(deftest unrelated-attributes-survive
  ;; Load real transformed bytes so reflection checks retained JVM attributes.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Shape" [{:field 'label :type String :enforcement :generated}] nil))
        bytes (transform (annotated-constructor-bytes))
        loader (clojure.lang.DynamicClassLoader.)
        cls (.defineClass loader "Shape" bytes nil)
        field (.getDeclaredField cls "label")
        ctor (.getConstructor cls (into-array Class [Object]))]
    (testing "runtime annotations survive on class, field and constructor"
      (is (.isAnnotationPresent cls Deprecated))
      (is (.isAnnotationPresent field Deprecated))
      (is (.isAnnotationPresent ctor Deprecated)))
    (testing "declared exceptions survive constructor rewriting"
      (is (= [java.io.IOException] (vec (.getExceptionTypes ctor)))))
    (testing "retained attributes do not replace storage or enforcement"
      (is (= String (.getType field)))
      (is (= cls (class (.newInstance ctor (object-array ["valid"])))))
      (is (= (first checks/checked-rejections)
             (checks/outcome #(.newInstance ctor (object-array [nil]))))))))

(defn- altered-storage-bytes
  [access descriptor]
  (let [writer (clojure.asm.ClassWriter. 0)
        visitor (proxy [clojure.asm.ClassVisitor] [clojure.asm.Opcodes/ASM6 writer]
                  (visitField [_access field _descriptor signature value]
                    (proxy-super visitField access field descriptor signature value)))]
    (.accept (clojure.asm.ClassReader. ^bytes (constructor-bytes 1 false false))
             visitor 0)
    (.toByteArray writer)))

(deftest unsupported-identity-storage-and-writes
  ;; Reject malformed storage and mutation paths before defining JVM classes.
  (let [transforms (value-type.impl.identity-backend/prepare
                    "Shape" [{:field 'label :type String :enforcement :generated}] nil)]
    (testing "selected storage must be final, non-static and Object-typed"
      (doseq [[case-name access descriptor]
              [["not final" clojure.asm.Opcodes/ACC_PUBLIC "Ljava/lang/Object;"]
               ["static" (bit-or clojure.asm.Opcodes/ACC_FINAL clojure.asm.Opcodes/ACC_STATIC)
                "Ljava/lang/Object;"]
               ["String typed" clojure.asm.Opcodes/ACC_FINAL "Ljava/lang/String;"]]]
        (testing case-name
          (let [data (shape-failure (:owner transforms)
                                    (altered-storage-bytes access descriptor))]
            (is (= :unsupported-shape (:value-type/error data)))
            (is (= "Shape" (:class-name data)))
            (is (= "label" (:field data)))
            (is (message-names? (:message data)
                                (shape-parts "unsupported non-nil field storage"))
                (:message data))))))
    (testing "owner methods and companions cannot write selected fields"
      (doseq [[transform-key transform] transforms]
        (testing (pr-str transform-key)
          (let [data (shape-failure transform
                                    (field-reference-bytes true clojure.asm.Opcodes/PUTFIELD
                                                           "Ljava/lang/Object;"))]
            (is (= :unsupported-shape (:value-type/error data)))
            (is (= "label" (:field data)))
            (is (= "read" (:method data)))
            (is (message-names? (:message data)
                                (shape-parts "non-nil field write outside constructor"))
                (:message data))))))))

(deftest nested-identity-constraint
  ;; The outer constraint must not reject nullable data within the child.
  (let [child (Mixed. 7 "nested" 2.5 nil)
        nested-class value_type.identity_backend.fixture.Nested
        field (.getField nested-class "child")]
    (testing "storage retains the exact nested identity Class"
      (is (identical? Mixed (.getType field)))
      (is (Modifier/isFinal (.getModifiers field))))
    (testing "all construction entries retain nested nullable data and reads"
      (doseq [[construction build] (checks/named-builders fixture-ns nested-class)]
        (testing (pr-str construction)
          (let [value (build [child])]
            (is (= [7 "nested" 2.5 nil] (.snapshot ^Reads value)))
            (is (identical? child ((.captured ^Reads value))))
            (is (identical? child (.get field value)))
            (is (identical? child (.choose ^Reads value true)))
            (is (nil? (.choose ^Reads value false)))))))
    (testing "the outer field still rejects nil and an unrelated identity object"
      (doseq [[construction build] (checks/named-builders fixture-ns nested-class)]
        (testing (pr-str construction)
          (is (= checks/checked-rejections
                 (checks/checked-outcomes build "not a child"))))))))
