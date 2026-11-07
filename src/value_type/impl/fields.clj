(ns value-type.impl.fields
  "Baseline-safe declaration handling for non-nil fields and the record
  hidden fields."
  (:require [clojure.string :as str]
            [value-type.impl.capability :as capability])
  (:import (clojure.lang Compiler Compiler$HostExpr)
           (java.lang.reflect Modifier)))

(set! *warn-on-reflection* true)

(def record-hidden-fields
  "Hidden field names of the `deftype*` record shape, in place order, each
  with its required tag. Value output of the record shape removes the fields
  with tag `int`; value output of the closed shape removes all four."
  [["__meta" nil] ["__extmap" nil] ["__hash" 'int] ["__hasheq" 'int]])

(def direct-fix
  "Fix text for receiving boundary errors."
  "Fix: evaluate the defvaluetype form directly.")

(defn closed-check-message
  "Return the message of the closed-shape constructor check of the class
  `class-name`. The text is not contract."
  [class-name]
  (str "Invalid constructor argument of closed record " class-name
       ": __meta and __extmap must be nil."
       " Fix: pass nil for __meta and __extmap, and use only declared keys."))

(defn removed-nil-restricted!
  "Throw `ex-info` with `:value-type/error` `:invalid-field`, `:class-name`,
  `:field`, `:reason :removed-nil-restricted` and `:value` (the
  `:nil-restricted` metadata of `field`). `fix` ends the message."
  [class-name field fix]
  (throw
   (ex-info
    (str "Removed :nil-restricted metadata on " class-name
         "/" field "; " fix)
    {:value-type/error :invalid-field
     :class-name class-name
     :field field
     :reason :removed-nil-restricted
     :value (:nil-restricted (meta field))})))

(defn- non-nil?
  "Validate the :non-nil metadata of `field`; return true when it is enabled.

  Accept only true, false or nil. Reject the removed :nil-restricted key."
  [class-name field]
  (let [metadata (meta field)
        value (:non-nil metadata)]
    (when (contains? metadata :nil-restricted)
      (removed-nil-restricted! class-name field "use :non-nil instead."))
    (when-not (or (nil? value) (true? value) (false? value))
      (throw
       (ex-info
        (str "Invalid :non-nil metadata on " class-name
             "/" field "; expected true, false or nil.")
        {:value-type/error :invalid-field
         :class-name class-name
         :field field
         :reason :invalid-non-nil
         :value value})))
    (true? value)))

(defn enabled-fields
  "Return enabled field symbols in declaration order, with metadata intact.

  Accept only true, false or nil for :non-nil. Leave malformed field
  vectors and non-symbol entries to deftype validation. This function does not
  resolve tags, test capability or change declarations. Call after the existing
  mutable-field check."
  [class-name fields]
  (if (vector? fields)
    (filterv #(and (symbol? %) (non-nil? class-name %)) fields)
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
                                   (Compiler$HostExpr/maybeSpecialTag tag))
                                 (Compiler$HostExpr/maybeArrayClass tag)))
                           (Compiler$HostExpr/maybeClass tag true))
                       (catch ClassNotFoundException _ nil))))]
    (or resolved
        (throw
         (ex-info
          (str "Cannot resolve non-nil field type for " class-name
               "/" field ": " (pr-str tag))
          {:value-type/error :invalid-field
           :class-name class-name
           :field field
           :reason (if (nil? tag) :missing-tag :unresolved-tag)
           :tag tag})))))

(defn validate-concrete-type
  "Return the exact Class if it is eligible for a non-nil field.

  Reject the enclosing binary name even if it resolves to an older Class.
  Concrete identity and value classes are eligible. This declaration check
  does not inspect optional JVM APIs or choose enforcement."
  [class-name field ^Class field-type]
  (let [reason (cond
                 (= class-name (.getName field-type)) :enclosing-type
                 (.isPrimitive field-type) :primitive-type
                 (.isArray field-type) :array-type
                 (.isInterface field-type) :interface-type
                 (Modifier/isAbstract (.getModifiers field-type))
                 :abstract-type)]
    (when reason
      (throw
       (ex-info
        (str "Invalid non-nil field type for " class-name "/" field
             ": " (.getName field-type))
        {:value-type/error :invalid-field
         :class-name class-name
         :field field
         :reason reason
         :tag (:tag (meta field))})))
    field-type))

(defn validate-native-type
  "Return the resolved Class if it is eligible for native enforcement.

  Native enforcement requires a concrete value class. Receiving analysis calls
  this for each fixed :native mode; generated enforcement does not."
  [class-name field ^Class field-type]
  (validate-concrete-type class-name field field-type)
  (when-not (capability/value-class? field-type)
    (throw
     (ex-info
      (str "Invalid native non-nil field type for " class-name "/" field
           ": " (.getName field-type))
      {:value-type/error :invalid-field
       :class-name class-name
       :field field
       :reason :identity-type
       :tag (:tag (meta field))})))
  field-type)

(defn select-enforcement
  "Return specs with :native enforcement for value-class types when the
  native-capable? thunk returns true. Other specs keep :generated enforcement.
  Call native-capable? only when a value-class spec exists."
  [specs native-capable?]
  (if (and (some #(capability/value-class? (:type %)) specs) (native-capable?))
    (mapv #(if (capability/value-class? (:type %)) (assoc % :enforcement :native) %)
          specs)
    specs))

(defn- field-specifications
  "Return ordered :generated specifications for the `enabled` field symbols."
  [defining-ns class-name enabled]
  (mapv (fn [field]
          (let [^Class field-type (validate-concrete-type
                                   class-name field
                                   (resolve-tag defining-ns class-name field))]
            {:field field
             :type field-type
             :enforcement :generated}))
        enabled))

(defn- check-hidden-fields!
  "Return `enabled`. Throw `ex-info` with `:value-type/error`
  `:invalid-field`, `:reason :hidden-field` and `:field` for the first
  enabled field with a hidden record field name (namespace ignored)."
  [class-name enabled]
  (let [hidden (set (map first record-hidden-fields))]
    (when-let [field (first (filter #(hidden (name %)) enabled))]
      (throw
       (ex-info
        (str "Invalid :non-nil metadata on hidden field " class-name
             "/" field "; hidden record fields cannot be non-nil.")
        {:value-type/error :invalid-field
         :class-name class-name
         :field field
         :reason :hidden-field}))))
  enabled)

(defn- declared-enabled
  "Return the enabled fields of `declarations`. With `hidden-field?`,
  check `:hidden-field` after the metadata keys."
  [class-name declarations hidden-field?]
  (cond->> (enabled-fields class-name declarations)
    hidden-field? (check-hidden-fields! class-name)))

(defn specifications
  "Return ordered field specifications for enabled declarations.

  Each specification has :generated enforcement; `select-enforcement` selects
  :native modes. Call after mutable-field validation. Disabled fields are not
  resolved; each specification preserves the exact Class and symbol.
  Option `:hidden-field?` (for `defvaluetype*`) rejects enabled hidden
  record fields after the metadata checks and before tag resolution."
  ([defining-ns class-name declarations]
   (specifications defining-ns class-name declarations nil))
  ([defining-ns class-name declarations {:keys [hidden-field?]}]
   (field-specifications defining-ns class-name
                         (declared-enabled class-name declarations
                                           hidden-field?))))

(defn emitted-name
  "Return the class file field name of specification `spec`."
  ^String [spec]
  (Compiler/munge (name (:field spec))))

(defn- invalid-enforcement!
  "Throw `ex-info` with `:value-type/error` `:invalid-field-enforcement`,
  `:class-name` and `:modes`. Name the `permitted` modes and the enabled
  field count."
  [class-name modes permitted enabled]
  (throw (ex-info (str "Cannot define class " class-name
                       ": fixed field enforcement requires a vector with one "
                       (str/join " or " (map pr-str (sort permitted)))
                       " mode for each enabled field (" enabled "), got "
                       (pr-str modes) ". " direct-fix)
                  {:value-type/error :invalid-field-enforcement
                   :class-name class-name
                   :modes modes})))

(defn receiving-specifications
  "Resolve receiver-local Classes while retaining selected enforcement modes.

  Modes are an ordered vector parallel to enabled fields; each mode must be in
  the `permitted` set. Reject invalid transport data before tag resolution, and
  native-ineligible Classes instead of selecting another mode. The caller must
  check representation and native capability before installing capture.
  Option `:hidden-field?` is as for `specifications`; the check runs before
  the modes check."
  ([defining-ns class-name declarations modes permitted]
   (receiving-specifications defining-ns class-name declarations modes
                             permitted nil))
  ([defining-ns class-name declarations modes permitted {:keys [hidden-field?]}]
   (let [enabled (declared-enabled class-name declarations hidden-field?)]
     (when-not (and (vector? modes)
                    (= (count enabled) (count modes))
                    (every? permitted modes))
       (invalid-enforcement! class-name modes permitted (count enabled)))
     (mapv (fn [spec mode]
             (when (= :native mode)
               (validate-native-type class-name (:field spec) (:type spec)))
             (assoc spec :enforcement mode))
           (field-specifications defining-ns class-name enabled) modes))))
