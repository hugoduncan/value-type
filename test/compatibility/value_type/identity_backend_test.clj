(ns value-type.identity-backend-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [value-type.cell-registry :as registry]
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
                    "Shape" [{:field 'label :type String :enforcement :generated}] nil :plain)]
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

(defn- super-call
  "Emit `super()`."
  [^clojure.asm.MethodVisitor m]
  (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
  (.visitMethodInsn m clojure.asm.Opcodes/INVOKESPECIAL
                    "java/lang/Object" "<init>" "()V" false))

(defn- constructor-bytes
  "Class `Shape` (field `label`) with a constructor `(Object)V` that calls
  `super()`, then writes `label` `write-count` times (after a `GOTO` when
  `branch?`). When given, `second-body` (an ASM method visitor function)
  builds a second constructor `()V`."
  ([write-count branch?]
   (constructor-bytes write-count branch? nil))
  ([write-count branch? second-body]
   (let [writer (clojure.asm.ClassWriter. 0)
         op clojure.asm.Opcodes/ACC_PUBLIC]
     (.visit writer clojure.asm.Opcodes/V1_8 op "Shape" nil "java/lang/Object" nil)
     (.visitEnd (.visitField writer clojure.asm.Opcodes/ACC_FINAL
                             "label" "Ljava/lang/Object;" nil nil))
     (let [m (.visitMethod writer op "<init>" "(Ljava/lang/Object;)V" nil nil)]
       (.visitCode m)
       (super-call m)
       (when branch?
         (let [label (clojure.asm.Label.)]
           (.visitJumpInsn m clojure.asm.Opcodes/GOTO label)
           (.visitLabel m label)))
       (dotimes [_ write-count]
         (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
         (.visitVarInsn m clojure.asm.Opcodes/ALOAD 1)
         (.visitFieldInsn m clojure.asm.Opcodes/PUTFIELD
                          "Shape" "label" "Ljava/lang/Object;"))
       (.visitInsn m clojure.asm.Opcodes/RETURN)
       (.visitMaxs m 2 2)
       (.visitEnd m))
     (when second-body
       (let [m (.visitMethod writer op "<init>" "()V" nil nil)]
         (.visitCode m)
         (second-body m)
         (.visitInsn m clojure.asm.Opcodes/RETURN)
         (.visitMaxs m 4 1)
         (.visitEnd m)))
     (.visitEnd writer)
     (.toByteArray writer))))

(deftest constructor-coverage
  ;; Unsupported constructors must fail before unchecked classes can be installed.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Shape" [{:field 'label :type String :enforcement :generated}] nil :plain))]
    (testing "straight-line writes cover the selected field"
      (is (bytes? (transform (constructor-bytes 1 false)))))
    (testing "missing constructors, missing or repeated writes and branches fail"
      (doseq [[case-name bytes part]
              [["no constructor" (field-reference-bytes true nil nil)
                "missing non-nil constructor"]
               ["no write" (constructor-bytes 0 false)
                "incomplete non-nil constructor writes"]
               ["two writes" (constructor-bytes 2 false)
                "repeated non-nil constructor write"]
               ["branch" (constructor-bytes 1 true)
                "unsupported constructor control flow"]
               ["extra constructor" (constructor-bytes 1 false super-call)
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
    (.accept (clojure.asm.ClassReader. ^bytes (constructor-bytes 1 false)) visitor 0)
    (.toByteArray writer)))

(deftest unrelated-attributes-survive
  ;; Load real transformed bytes so reflection checks retained JVM attributes.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Shape" [{:field 'label :type String :enforcement :generated}] nil :plain))
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
    (.accept (clojure.asm.ClassReader. ^bytes (constructor-bytes 1 false))
             visitor 0)
    (.toByteArray writer)))

(deftest unsupported-identity-storage-and-writes
  ;; Reject malformed storage and mutation paths before defining JVM classes.
  (let [transforms (value-type.impl.identity-backend/prepare
                    "Shape" [{:field 'label :type String :enforcement :generated}] nil :plain)]
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

(deftest delegating-record-constructors
  ;; Real defrecord output: two compiler delegating constructors and create.
  (let [rec value_type.identity_backend.fixture.Rec
        build {:declared #(clojure.lang.Reflector/invokeConstructor
                           rec (object-array [% 7]))
               :meta-extmap #(clojure.lang.Reflector/invokeConstructor
                              rec (object-array [% 7 nil nil]))
               :main #(clojure.lang.Reflector/invokeConstructor
                       rec (object-array [% 7 nil nil (int 0) (int 0)]))
               :create #(clojure.lang.Reflector/invokeStaticMethod
                         rec "create" (object-array [{:label % :count 7}]))
               :map-factory #((resolve 'value-type.identity-backend.fixture/map->Rec)
                              {:label % :count 7})}]
    (testing "concrete final storage; hash fields kept"
      (is (= String (.getType (.getField rec "label"))))
      (is (Modifier/isFinal (.getModifiers (.getField rec "label"))))
      (is (= Integer/TYPE (.getType (.getDeclaredField rec "__hash")))))
    (doseq [[path f] build]
      (testing (pr-str path)
        (is (= "ok" (:label (f "ok"))))
        (is (= checks/checked-rejections
               (checks/checked-outcomes (fn [[x]] (f x)) 42)))))))

(defn- delegate-to-main
  "Emit `this(\"x\")`."
  [^clojure.asm.MethodVisitor m]
  (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
  (.visitLdcInsn m "x")
  (.visitMethodInsn m clojure.asm.Opcodes/INVOKESPECIAL
                    "Shape" "<init>" "(Ljava/lang/Object;)V" false))

(deftest constructor-delegation-rule
  ;; D8: delegation is a call to a target constructor; shape-independent.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Shape" [{:field 'label :type String :enforcement :generated}] nil :plain))]
    (testing "a delegating constructor that writes no field is accepted and loads"
      (let [bytes (transform (constructor-bytes 1 false delegate-to-main))
            cls (.defineClass (clojure.lang.DynamicClassLoader.) "Shape" bytes nil)]
        (is (= "x" (.get (doto (.getDeclaredField cls "label") (.setAccessible true))
                         (.newInstance (.getConstructor cls (into-array Class []))
                                       (object-array 0)))))))
    (testing "rejected delegation shapes"
      (doseq [[case-name body part]
              [["delegating constructor writes a field"
                (fn [^clojure.asm.MethodVisitor m]
                  (delegate-to-main m)
                  (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
                  (.visitLdcInsn m "y")
                  (.visitFieldInsn m clojure.asm.Opcodes/PUTFIELD
                                   "Shape" "label" "Ljava/lang/Object;"))
                "delegating non-nil constructor writes a field"]
               ["constructor creates and calls the target"
                (fn [^clojure.asm.MethodVisitor m]
                  (super-call m)
                  (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
                  (.visitTypeInsn m clojure.asm.Opcodes/NEW "Shape")
                  (.visitInsn m clojure.asm.Opcodes/DUP)
                  (.visitLdcInsn m "z")
                  (.visitMethodInsn m clojure.asm.Opcodes/INVOKESPECIAL
                                    "Shape" "<init>" "(Ljava/lang/Object;)V" false)
                  (.visitFieldInsn m clojure.asm.Opcodes/PUTFIELD
                                   "Shape" "label" "Ljava/lang/Object;"))
                "constructor both creates and calls the target"]]]
        (testing case-name
          (let [{error :value-type/error message :message}
                (shape-failure transform (constructor-bytes 1 false body))]
            (is (= :unsupported-shape error))
            (is (message-names? message (shape-parts part)) message)))))))

(def ^:private closed-main
  "Main constructor descriptor of the synthetic closed class: a `long`
  field (two slots), then `__meta`, `__extmap`, `__hash`, `__hasheq`."
  "(JLjava/lang/Object;Ljava/lang/Object;II)V")

(defn- closed-class-bytes
  "Class `Closed` without enabled fields. `constructors` is a sequence of
  `[descriptor body max-stack]`; `body` emits the code before `RETURN`."
  [constructors]
  (let [writer (clojure.asm.ClassWriter. 0)
        op clojure.asm.Opcodes/ACC_PUBLIC]
    (.visit writer clojure.asm.Opcodes/V1_8 op "Closed" nil "java/lang/Object" nil)
    (doseq [[desc body max-stack] constructors]
      (let [m (.visitMethod writer op "<init>" desc nil nil)
            locals (inc (reduce + (map #(.getSize ^clojure.asm.Type %)
                                       (clojure.asm.Type/getArgumentTypes ^String desc))))]
        (.visitCode m)
        (body m)
        (.visitInsn m clojure.asm.Opcodes/RETURN)
        (.visitMaxs m max-stack locals)
        (.visitEnd m)))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn- delegate-to-closed-main
  "Emit `this(x, nil, nil, 0, 0)` from a constructor `(J)V`."
  [^clojure.asm.MethodVisitor m]
  (.visitVarInsn m clojure.asm.Opcodes/ALOAD 0)
  (.visitVarInsn m clojure.asm.Opcodes/LLOAD 1)
  (.visitInsn m clojure.asm.Opcodes/ACONST_NULL)
  (.visitInsn m clojure.asm.Opcodes/ACONST_NULL)
  (.visitInsn m clojure.asm.Opcodes/ICONST_0)
  (.visitInsn m clojure.asm.Opcodes/ICONST_0)
  (.visitMethodInsn m clojure.asm.Opcodes/INVOKESPECIAL
                    "Closed" "<init>" closed-main false))

(defn- class-structure
  "Field names in order, and per constructor descriptor the ordered
  markers `:iae` (`new IllegalArgumentException`) and `:nn` (a
  `requireNonNull` call)."
  [bytes]
  (let [fields (volatile! [])
        ctors (volatile! {})]
    (.accept (clojure.asm.ClassReader. ^bytes bytes)
             (proxy [clojure.asm.ClassVisitor] [clojure.asm.Opcodes/ASM6]
               (visitField [_access field _desc _signature _value]
                 (vswap! fields conj field)
                 nil)
               (visitMethod [_access method desc _signature _exceptions]
                 (when (= method "<init>")
                   (vswap! ctors assoc desc [])
                   (proxy [clojure.asm.MethodVisitor] [clojure.asm.Opcodes/ASM6]
                     (visitTypeInsn [opcode type]
                       (when (and (= opcode clojure.asm.Opcodes/NEW)
                                  (= type "java/lang/IllegalArgumentException"))
                         (vswap! ctors update desc conj :iae)))
                     (visitMethodInsn [_opcode _owner method-name _desc _itf]
                       (when (= method-name "requireNonNull")
                         (vswap! ctors update desc conj :nn)))))))
             0)
    {:fields @fields :ctors @ctors}))

(defn- checked-constructors
  "Descriptor to markers of the constructors of `structure` (from
  `class-structure`) whose markers contain `:iae`."
  [structure]
  (into {} (filter (comp #(some #{:iae} %) val)) (:ctors structure)))

(deftest closed-identity-entry-check
  ;; D10: the identity backend checks __meta and __extmap by parameter position.
  (let [transform (:owner (value-type.impl.identity-backend/prepare
                           "Closed" [] nil :closed))
        delegating ["(J)V" delegate-to-closed-main 7]
        main [closed-main super-call 1]
        checked (comp keys checked-constructors class-structure)]
    (testing "check in the non-delegating constructor only; class verifies"
      (let [bytes (transform (closed-class-bytes [main delegating]))
            cls (.defineClass (clojure.lang.DynamicClassLoader.) "Closed" bytes nil)
            ctor (.getConstructor cls (into-array Class [Long/TYPE Object Object
                                                         Integer/TYPE Integer/TYPE]))
            build (fn [meta extmap]
                    (try
                      (.newInstance ctor (object-array [7 meta extmap (int 0) (int 0)]))
                      (catch Exception e (class (registry/root e)))))]
        (is (= [closed-main] (checked bytes)))
        (is (= cls (class (build nil nil))))
        (is (= IllegalArgumentException (build {} nil)))
        (is (= IllegalArgumentException (build nil {:k 1})))
        (is (= cls (class (.newInstance (.getConstructor cls (into-array Class [Long/TYPE]))
                                        (object-array [7])))))))
    (testing "companions get no check"
      ;; One specification, so that the companion transform rewrites the bytes.
      (let [input (closed-class-bytes [main delegating])
            output ((:companion (value-type.impl.identity-backend/prepare
                                 "Closed"
                                 [{:field 'label :type String :enforcement :generated}]
                                 nil :closed))
                    input)]
        (is (not (identical? input output)))
        (is (empty? (checked output)))))
    (testing "closed unsupported shapes"
      (doseq [[case-name constructors part]
              [["fewer than four parameters"
                [["(Ljava/lang/Object;)V" super-call 1]]
                "closed constructor without Object __meta and __extmap parameters"]
               ["non-Object __meta parameter"
                [["(Ljava/lang/Object;ILjava/lang/Object;II)V" super-call 1]]
                "closed constructor without Object __meta and __extmap parameters"]
               ["non-Object __extmap parameter"
                [["(Ljava/lang/Object;Ljava/lang/Object;III)V" super-call 1]]
                "closed constructor without Object __meta and __extmap parameters"]
               ["no non-delegating constructor"
                [["(J)V" delegate-to-closed-main 7]]
                "closed class without non-delegating constructor"]
               ["constructor creates and calls the target"
                [main
                 ["(Ljava/lang/Object;)V"
                  (fn [^clojure.asm.MethodVisitor m]
                    (super-call m)
                    (.visitTypeInsn m clojure.asm.Opcodes/NEW "Closed")
                    (.visitInsn m clojure.asm.Opcodes/DUP)
                    (.visitInsn m clojure.asm.Opcodes/LCONST_0)
                    (.visitInsn m clojure.asm.Opcodes/ACONST_NULL)
                    (.visitInsn m clojure.asm.Opcodes/ACONST_NULL)
                    (.visitInsn m clojure.asm.Opcodes/ICONST_0)
                    (.visitInsn m clojure.asm.Opcodes/ICONST_0)
                    (.visitMethodInsn m clojure.asm.Opcodes/INVOKESPECIAL
                                      "Closed" "<init>" closed-main false)
                    (.visitInsn m clojure.asm.Opcodes/POP))
                  8]]
                "constructor both creates and calls the target"]
               ["constructor with a jump"
                [[closed-main
                  (fn [^clojure.asm.MethodVisitor m]
                    (super-call m)
                    (let [label (clojure.asm.Label.)]
                      (.visitJumpInsn m clojure.asm.Opcodes/GOTO label)
                      (.visitLabel m label)))
                  1]]
                "unsupported constructor control flow"]]]
        (testing case-name
          (let [{error :value-type/error message :message}
                (shape-failure transform (closed-class-bytes constructors))]
            (is (= :unsupported-shape error))
            (is (message-names? message ["Closed" part "use Clojure 1.12"])
                message)))))))

;;; Closed identity output of real compiler output (Acceptance 4)

(defn- compiled-record-bytes
  "Compile a record-shaped `deftype*` with `declared` fields to a new temp
  directory. Return the class file bytes."
  [declared]
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory
                      "vt-closed" (make-array java.nio.file.attribute.FileAttribute 0)))
        n (gensym "ClosedOwner")
        cname (str "value_type.identity_backend_test." n)
        fields (into declared '[__meta __extmap ^int __hash ^int __hasheq])]
    (binding [*ns* (the-ns 'value-type.identity-backend-test)
              *compile-files* true
              *compile-path* (str dir)]
      (eval (list 'deftype* n (symbol cname) fields
                  :implements ['clojure.lang.IRecord])))
    (let [file (io/file dir (str (.replace cname \. \/) ".class"))
          bytes (java.nio.file.Files/readAllBytes (.toPath file))]
      (run! #(.deleteOnExit ^java.io.File %) (reverse (file-seq dir)))
      {:bytes bytes :owner cname})))

(deftest closed-identity-compiler-output-bytes
  (doseq [[case-name declared specs]
          [["no enabled field" '[^long x y] []]
           ["one enabled field" '[^long x ^String y]
            [{:field 'y :type String :enforcement :generated}]]]]
    (testing case-name
      (let [{:keys [bytes owner]} (compiled-record-bytes declared)
            main "(JLjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;II)V"
            out (class-structure
                 ((:owner (value-type.impl.identity-backend/prepare
                           owner specs nil :closed))
                  bytes))
            checked (checked-constructors out)]
        (testing "all four hidden fields are kept"
          (is (= ["x" "y" "__meta" "__extmap" "__hash" "__hasheq"] (:fields out))))
        (testing "constructor descriptors unchanged"
          (is (= (set (keys (:ctors (class-structure bytes))))
                 (set (keys (:ctors out))))))
        (testing "only the main constructor has the check, before null checks"
          (is (= [main] (keys checked)))
          (when (seq specs)
            (let [markers (get checked main)]
              (is (< (.indexOf ^java.util.List markers :iae)
                     (.indexOf ^java.util.List markers :nn))))))))))
