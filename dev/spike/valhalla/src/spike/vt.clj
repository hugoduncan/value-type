(ns spike.vt
  (:import (clojure.lang Compiler DynamicClassLoader)
           (java.lang.classfile ClassFile ClassTransform ClassBuilder ClassElement
                                ClassFileVersion AccessFlags FieldModel MethodModel
                                FieldTransform FieldBuilder FieldElement CodeBuilder
                                TypeKind)
           (java.lang.constant ClassDesc ConstantDescs MethodTypeDesc)
           (java.util.function Consumer)))

(set! *warn-on-reflection* true)

(def ^:const ACC_IDENTITY 0x20)
(def ^:const ACC_STATIC 0x8)
(def ^:const ACC_STRICT_INIT 0x800)

(defn- instance-field? [^FieldModel f]
  (zero? (bit-and (.flagsMask (.flags f)) ACC_STATIC)))

(defn- ctor-code [^ClassDesc this fields]
  (reify Consumer
    (accept [_ cb]
      (let [^CodeBuilder cb cb]
        (loop [slot 1 [^FieldModel f & more] fields]
          (when f
            (let [desc (.fieldTypeSymbol f)
                  tk (TypeKind/from desc)]
              (.aload cb 0)
              (.loadLocal cb tk slot)
              (.putfield cb this (.stringValue (.fieldName f)) desc)
              (recur (+ slot (.slotSize tk)) more))))
        (.aload cb 0)
        (.invokespecial cb ConstantDescs/CD_Object "<init>" ConstantDescs/MTD_void)
        (.return_ cb)))))

(defn ->value-class ^bytes [^bytes bs]
  (let [cf (ClassFile/of)
        cm (.parse cf bs)
        this (.asSymbol (.thisClass cm))
        fields (filterv instance-field? (.fields cm))
        major (ClassFile/latestMajorVersion)]
    (.transformClass
     cf cm
     (reify ClassTransform
       (accept [_ b e]
         (let [^ClassBuilder b b]
           (cond
             (instance? ClassFileVersion e)
             (.withVersion b (int major) (int ClassFile/PREVIEW_MINOR_VERSION))

             (instance? AccessFlags e)
             (.withFlags b (int (bit-and-not (.flagsMask ^AccessFlags e) ACC_IDENTITY)))

             (and (instance? FieldModel e) (instance-field? e))
             (.transformField
              b ^FieldModel e
              (reify FieldTransform
                (accept [_ fb fe]
                  (if (instance? AccessFlags fe)
                    (.withFlags ^FieldBuilder fb
                                (int (bit-or (.flagsMask ^AccessFlags fe) ACC_STRICT_INIT)))
                    (.with ^FieldBuilder fb ^FieldElement fe)))))

             (and (instance? MethodModel e)
                  (= "<init>" (.stringValue (.methodName ^MethodModel e))))
             (let [^MethodModel m e]
               (.withMethod b (.methodName m) (.methodType m)
                            (int (.flagsMask (.flags m)))
                            (reify Consumer
                              (accept [_ mb]
                                (.withCode ^java.lang.classfile.MethodBuilder mb
                                           ^Consumer (ctor-code this fields))))))

             :else (.with b ^ClassElement e))))))))

(defn capture-loader [^String target ^DynamicClassLoader parent]
  (let [delegate (DynamicClassLoader. parent)]
    (proxy [DynamicClassLoader] [parent]
      (defineClass
        ([name bs src]
         (if (= name target)
           (let [bs' (->value-class bs)]
             (when *compile-files*
               (Compiler/writeClassFile (.replace ^String name \. \/) bs'))
             (.defineClass delegate name bs' src))
           (.defineClass delegate name bs src)))))))

(defn install! [target]
  (if (.isBound Compiler/LOADER)
    (do (.set Compiler/LOADER (capture-loader target @Compiler/LOADER)) true)
    false))

(defmacro defvaluetype [name fields & opts+specs]
  (let [cname (str (namespace-munge *ns*) "." name)
        installed (install! cname)]
    `(let []
       (deftype ~name ~fields ~@opts+specs)
       (when-not (.isValue ~(symbol cname))
         (throw (ex-info "not a value class" {:installed ~installed})))
       ~(symbol cname))))
