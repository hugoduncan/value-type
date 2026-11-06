(ns value-type.impl.class-file
  "Class-file configuration for owner and companion stack-map generation."
  (:import
   (java.lang.classfile ClassFile ClassFile$Option
                        ClassFile$ClassHierarchyResolverOption
                        ClassHierarchyResolver ClassHierarchyResolver$ClassHierarchyInfo
                        ClassModel)
   (java.lang.classfile.constantpool ClassEntry)
   (java.lang.constant ClassDesc ConstantDescs)
   (java.lang.reflect AccessFlag)))

(set! *warn-on-reflection* true)

(defn hierarchy-info
  "Return the hierarchy entry `[ClassDesc ClassHierarchyInfo]` of the class
  in `model`."
  [^ClassModel model]
  [(.asSymbol (.thisClass model))
   (if (.has (.flags model) AccessFlag/INTERFACE)
     (ClassHierarchyResolver$ClassHierarchyInfo/ofInterface)
     (ClassHierarchyResolver$ClassHierarchyInfo/ofClass
      (when-let [^ClassEntry entry (.orElse (.superclass model) nil)]
        (.asSymbol entry))))])

(defn owner-hierarchy-info
  "Return the hierarchy entry of the owner `owner-name`. Owners are
  `deftype*` classes, so their superclass is Object."
  [^String owner-name]
  [(ClassDesc/of owner-name)
   (ClassHierarchyResolver$ClassHierarchyInfo/ofClass ConstantDescs/CD_Object)])

(defn- map-resolver
  ^ClassHierarchyResolver [classes]
  (reify ClassHierarchyResolver
    (getClassInfo [_ desc] (get classes desc))))

(defn configured
  "Return a ClassFile for stack-map generation. `classes` maps a ClassDesc
  to its ClassHierarchyInfo. Resolve these classes first: the class in
  transform and its owner are not loadable yet. Then resolve with
  `class-loader`, or with the JDK defaults when it is nil.
  Load only behind the optional JDK boundary."
  (^ClassFile [class-loader] (configured class-loader {}))
  (^ClassFile [class-loader classes]
   (let [fallback (if class-loader
                    (ClassHierarchyResolver/ofClassLoading ^ClassLoader class-loader)
                    (ClassHierarchyResolver/defaultResolver))]
     (ClassFile/of
      (into-array ClassFile$Option
                  [(ClassFile$ClassHierarchyResolverOption/of
                    (.orElse (map-resolver classes) fallback))])))))
