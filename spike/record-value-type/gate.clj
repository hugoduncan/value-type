(ns gate
  "D3/D7 feasibility gate for task 019. Transform a record-shaped
  `deftype*` class into a value class with a spike-local transform, then
  exercise every constructor, `create`, both record literals and a
  compiled record constant. Prints one EDN result map; exits non-zero on
  any failed case."
  (:import
   (clojure.lang Compiler DynamicClassLoader)
   (java.lang.classfile AccessFlags ClassBuilder ClassElement ClassFile
                        ClassFile$ClassHierarchyResolverOption ClassFile$Option
                        ClassFileVersion ClassHierarchyResolver ClassModel
                        ClassTransform CodeBuilder CodeModel FieldBuilder
                        FieldElement FieldModel FieldTransform MethodBuilder
                        MethodElement MethodModel MethodTransform TypeKind)
   (java.lang.constant ClassDesc ConstantDescs)
   (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:private ACC_IDENTITY 0x20)
(def ^:private ACC_STRICT_INIT 0x800)
(def ^:private hash-fields #{"__hash" "__hasheq"})

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ClassFile/ACC_STATIC)))

(defn- field-name [^FieldModel f] (.stringValue (.fieldName f)))

(defn- kind ^TypeKind [^FieldModel f] (TypeKind/from (.fieldTypeSymbol f)))

(defn- main-constructor? [^MethodModel m n]
  (and (= "<init>" (.stringValue (.methodName m)))
       (= n (count (.parameterList (.methodTypeSymbol m))))))

(defn- main-code
  "Write kept fields from their original slots (computed over all
  fields), then call Object.<init>."
  ^Consumer [^ClassDesc this fields]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb
            slots (butlast (reductions #(+ %1 (.slotSize (kind %2))) 1 fields))]
        (doseq [[^FieldModel f slot] (map vector fields slots)
                :when (not (hash-fields (field-name f)))]
          (.aload cb 0)
          (.loadLocal cb (kind f) (int slot))
          (.putfield cb this (field-name f) (.fieldTypeSymbol f)))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn- record-transform ^ClassTransform [this fields]
  (reify ClassTransform
    (accept [_ b e]
      (let [^ClassBuilder b b]
        (cond
          (instance? ClassFileVersion e)
          (.withVersion b (ClassFile/latestMajorVersion) ClassFile/PREVIEW_MINOR_VERSION)

          (instance? AccessFlags e)
          (.withFlags b (int (bit-and-not (.flagsMask ^AccessFlags e) (long ACC_IDENTITY))))

          (and (instance? FieldModel e) (instance-field? e))
          (when-not (hash-fields (field-name e))
            (.transformField
             b ^FieldModel e
             (reify FieldTransform
               (accept [_ fb fe]
                 (let [^FieldBuilder fb fb]
                   (if (instance? AccessFlags fe)
                     (.withFlags fb (int (bit-or (.flagsMask ^AccessFlags fe) (long ACC_STRICT_INIT))))
                     (.with fb ^FieldElement fe)))))))

          (and (instance? MethodModel e) (main-constructor? e (count fields)))
          (.transformMethod
           b ^MethodModel e
           (reify MethodTransform
             (accept [_ mb me]
               (let [^MethodBuilder mb mb]
                 (if (instance? CodeModel me)
                   (.withCode mb (main-code this fields))
                   (.with mb ^MethodElement me))))))

          :else (.with b ^ClassElement e))))))

(defn ->record-value-class ^bytes [^bytes bs ^ClassLoader loader]
  (let [cf (ClassFile/of (into-array ClassFile$Option
                                     [(ClassFile$ClassHierarchyResolverOption/of
                                       (ClassHierarchyResolver/ofClassLoading loader))]))
        ^ClassModel cm (.parse cf bs)
        fields (filterv instance-field? (.fields cm))]
    (.transformClass cf cm (record-transform (.asSymbol (.thisClass cm)) fields))))

(defmacro value-deftype*
  "Spike-only: install a capture loader for `classname`, then expand to
  `deftype*`. Not the public API."
  [tagname classname fields & body]
  (let [target (str classname)
        parent ^ClassLoader @Compiler/LOADER
        delegate (DynamicClassLoader. parent)]
    (.set Compiler/LOADER
          (proxy [DynamicClassLoader] [parent]
            (defineClass [^String n ^bytes bs src]
              (let [cls (.defineClass delegate n
                                      (if (= n target) (->record-value-class bs delegate) bs)
                                      src)]
                (.getDeclaredMethods cls) ; link and verify now
                cls))))
    `(let [] (deftype* ~tagname ~classname ~fields ~@body) ~(symbol target))))

(value-deftype* gate/Rec gate.Rec [a ^long b ^double c __meta __extmap ^int __hash ^int __hasheq]
  :implements [clojure.lang.IRecord java.util.Map clojure.lang.ILookup
               clojure.lang.IObj clojure.lang.IHashEq]
  (valAt [this k] (.valAt this k nil))
  (valAt [this k nf] (case k :a a :b b :c c (get __extmap k nf)))
  (entrySet [this] (.entrySet ^java.util.Map (merge {:a a :b b :c c} __extmap)))
  (meta [this] __meta)
  (withMeta [this m] (new Rec a b c m __extmap))
  (hasheq [this] (hash (merge {:a a :b b :c c} __extmap)))
  (hashCode [this] (.hashCode ^Object (merge {:a a :b b :c c} __extmap))))

(defn- view [^gate.Rec r]
  {:a (.valAt r :a) :b (.valAt r :b) :c (.valAt r :c) :x (.valAt r :x)
   :meta (meta r)})

(defn- value-class? [^Class c]
  (let [^java.lang.reflect.Method m
        (try (.getMethod Class "isValue" (make-array Class 0))
             (catch NoSuchMethodException _ nil))]
    (boolean (and m (.invoke m c (object-array 0))))))

(def cases
  (partition 2
   [
   :value-class (fn [] (value-class? gate.Rec))
   :hash-fields-removed
   (fn [] (empty? (filter #(hash-fields (.getName ^java.lang.reflect.Field %))
                          (.getDeclaredFields gate.Rec))))
   :constructors (fn [] (count (.getDeclaredConstructors gate.Rec)))
   :main-constructor (fn [] (view (gate.Rec. 1 2 3.5 {:m 1} {:x 9} 0 0)))
   :declared-constructor (fn [] (view (gate.Rec. 1 2 3.5)))
   :meta-extmap-constructor (fn [] (view (gate.Rec. 1 2 3.5 {:m 1} {:x 9})))
   :create (fn [] (view (gate.Rec/create {:a 1 :b 2 :c 3.5 :x 9})))
   :map-literal (fn [] (view (load-string "#gate.Rec{:a 1 :b 2 :c 3.5 :x 9}")))
   :positional-literal (fn [] (view (load-string "#gate.Rec[1 2 3.5]")))
   :compiled-constant
   (fn [] (view (eval `(identity ~(gate.Rec. 1 2 3.5 nil {:x 9})))))
   :with-meta (fn [] (view (with-meta (gate.Rec. 1 2 3.5) {:m 2})))
   :value-equality (fn [] (= (gate.Rec. 1 2 3.5) (gate.Rec. 1 2 3.5)))]))

(defn -main [& _]
  (let [results (into []
                      (map (fn [[k f]]
                             [k (try {:ok (f)}
                                     (catch Throwable t
                                       {:error (str (class t) ": " (.getMessage t))
                                        :cause (some-> (.getCause t) str)}))]))
                      cases)
        failed (filterv #(:error (second %)) results)]
    (prn {:java (System/getProperty "java.runtime.version")
          :clojure (clojure-version)
          :results results
          :failed (mapv first failed)})
    (shutdown-agents)
    (System/exit (if (seq failed) 1 0))))

(-main)
