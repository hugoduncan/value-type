(ns value-type.impl.compiler-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.compiler :as compiler]
   [value-type.test-support :as support])
  (:import
   (clojure.asm ClassReader ClassVisitor Opcodes Type)
   (java.io File)
   (java.nio.file Files)))

(defn- record-class-bytes
  "Compile `(defrecord ShapeRecord [a b])` with class file output and
  return the bytes of the record class that Clojure emits."
  ^bytes []
  (support/with-temp-dir [dir]
    (let [^Class c (binding [*compile-files* true
                             *compile-path*  (str dir)
                             *ns*            (the-ns 'value-type.impl.compiler-test)]
                     (eval '(defrecord ShapeRecord [a b])))
          f (File. ^File dir (str (.replace (.getName c) \. \/) ".class"))]
      (Files/readAllBytes (.toPath f)))))

(defn- class-shape
  "Return `{:fields [[name descriptor] ...] :constructors [arg-count ...]}`
  of class `bytes`: the instance fields in class file order, and the
  parameter counts of the constructors."
  [^bytes bytes]
  (let [shape (atom {:fields [] :constructors []})]
    (.accept (ClassReader. bytes)
             (proxy [ClassVisitor] [Opcodes/ASM4]
               (visitField [access name desc _ _]
                 (when (zero? (bit-and (int access) Opcodes/ACC_STATIC))
                   (swap! shape update :fields conj [name desc]))
                 nil)
               (visitMethod [_ name desc _ _]
                 (when (= "<init>" name)
                   (swap! shape update :constructors conj
                          (count (Type/getArgumentTypes ^String desc))))
                 nil))
             0)
    @shape))

(deftest shape-data-test
  ;; The shape data must agree with the record shape that Clojure emits.
  ;; The field order is read from the class file, as the backends do.
  (let [{:keys [fields constructors]} (class-shape (record-class-bytes))
        n (count fields)]
    (testing "hidden fields are at their indexes, with their tags"
      (doseq [[k [nm tag]] (map-indexed vector compiler/record-hidden-fields)]
        (is (= [nm (if tag "I" "Ljava/lang/Object;")]
               (nth fields (compiler/hidden-field-index n k))))))
    (testing "record constructors have the listed hidden parameter counts"
      (is (= (set (map #(+ (- n compiler/hidden-field-count) %)
                       compiler/record-constructors))
             (set constructors))))))
