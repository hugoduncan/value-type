(ns value-type.impl.fields
  "Baseline-safe declaration handling for native nil-restricted fields.")

(defn restricted-fields
  "Return enabled field symbols in declaration order, with metadata intact.

  Accept only true, false or nil for :nil-restricted. Leave malformed field
  vectors and non-symbol entries to deftype validation. This function does not
  resolve tags, test capability or change declarations. Call after the existing
  mutable-field check."
  [class-name fields]
  (if (vector? fields)
    (into []
          (filter
           (fn [field]
             (when (symbol? field)
               (let [value (:nil-restricted (meta field))]
                 (when-not (or (nil? value) (true? value) (false? value))
                   (throw
                    (ex-info
                     (str "Invalid :nil-restricted metadata on " class-name
                          "/" field "; expected true, false or nil.")
                     {:value-type/error :invalid-field
                      :class-name class-name
                      :field field
                      :reason :invalid-nil-restricted
                      :value value})))
                 (true? value)))))
          fields)
    []))

(defn resolve-tag
  "Resolve an enabled field's explicit tag in defining-ns to a Class.

  Use Clojure's tag rules and preserve Class identity. Resolution does not test
  value-class eligibility or native capability. Only missing classes become
  declaration errors; linkage and initialization failures remain errors."
  [defining-ns class-name field]
  (let [tag (:tag (meta field))
        resolved (when tag
                   (binding [*ns* defining-ns]
                     (try
                       (or (when (symbol? tag)
                             (or (when-not (namespace tag)
                                   (clojure.lang.Compiler$HostExpr/maybeSpecialTag tag))
                                 (clojure.lang.Compiler$HostExpr/maybeArrayClass tag)))
                           (clojure.lang.Compiler$HostExpr/maybeClass tag true))
                       (catch ClassNotFoundException _ nil))))]
    (or resolved
        (throw
         (ex-info
          (str "Cannot resolve nil-restricted field type for " class-name
               "/" field ": " (pr-str tag))
          {:value-type/error :invalid-field
           :class-name class-name
           :field field
           :reason (if (nil? tag) :missing-tag :unresolved-tag)
           :tag tag})))))

(def ^:private value-predicate
  (delay
    (try
      (.getMethod Class "isValue" (make-array Class 0))
      (catch NoSuchMethodException _ nil))))

(defn validate-type
  "Return the resolved Class if it is eligible for native restricted storage.

  Reject the enclosing binary name even if it resolves to an older Class.
  This checks declaration eligibility, not native field capability. On baseline
  JVMs no class is a value class; optional API lookup remains lazy."
  [class-name field ^Class field-type]
  (let [reason (cond
                 (= class-name (.getName field-type)) :enclosing-type
                 (.isPrimitive field-type) :primitive-type
                 (.isArray field-type) :array-type
                 (.isInterface field-type) :interface-type
                 (java.lang.reflect.Modifier/isAbstract (.getModifiers field-type))
                 :abstract-type
                 (not (when-let [^java.lang.reflect.Method predicate @value-predicate]
                        (.invoke predicate field-type (object-array 0))))
                 :identity-type)]
    (when reason
      (throw
       (ex-info
        (str "Invalid nil-restricted field type for " class-name "/" field
             ": " (.getName field-type))
        {:value-type/error :invalid-field
         :class-name class-name
         :field field
         :reason reason
         :tag (:tag (meta field))})))
    field-type))

(defn specifications
  "Return ordered native field specifications for enabled declarations.

  Validate all restriction metadata before resolving and checking enabled types
  in declaration order. Each specification retains the original field symbol,
  exact Class and JVM storage descriptor. Call after mutable-field validation;
  native capability is a separate check. Disabled fields are not resolved."
  [defining-ns class-name declarations]
  (mapv (fn [field]
          (let [field-type (validate-type
                            class-name field
                            (resolve-tag defining-ns class-name field))]
            {:field field
             :type field-type
             :descriptor (str "L" (.replace (.getName ^Class field-type) \. \/) ";")}))
        (restricted-fields class-name declarations)))
