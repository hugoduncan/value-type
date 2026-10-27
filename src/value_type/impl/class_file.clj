(ns value-type.impl.class-file
  "Class-file configuration for owner and companion stack-map generation."
  (:import
   (java.lang.classfile ClassFile ClassFile$Option
                        ClassFile$ClassHierarchyResolverOption ClassHierarchyResolver)))

(set! *warn-on-reflection* true)

(defn configured
  "Return a ClassFile using the supplied loader for hierarchy resolution.
   With no loader, retain the JDK defaults. Load only behind the optional JDK boundary."
  ^ClassFile [class-loader]
  (if class-loader
    (ClassFile/of
     (into-array ClassFile$Option
                 [(ClassFile$ClassHierarchyResolverOption/of
                   (ClassHierarchyResolver/ofClassLoading ^ClassLoader class-loader))]))
    (ClassFile/of)))
