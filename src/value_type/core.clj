(ns value-type.core
  "Define JEP 401 value classes with `defvaluetype`, a `deftype`
  compatible macro."
  (:require
   [value-type.impl.capability :as capability]
   [value-type.impl.field-capability :as field-capability]
   [value-type.impl.fields :as fields]
   [value-type.impl.runtime :as runtime]))

(set! *warn-on-reflection* true)

(def ^:private no-value-classes-message
  (str "The JVM has no value classes (Class::isValue is missing). "
       "Use a JEP 401 JDK with --enable-preview."))

(def ^:private macroexpanded-outside-compilation
  (str "The defvaluetype form was macroexpanded outside compilation "
       "(no Compiler/LOADER binding)"))

(defn- no-value-classes?
  "True when the JVM has no `Class::isValue`."
  []
  (nil? @capability/value-predicate))

(defn- require-value-capability!
  "Return nil when the JVM can define value classes. Else throw `ex-info`
  with `:value-type/error` `:value-capability-unavailable`, `:class-name`
  and `:reason`."
  [class-name]
  (let [{:keys [available? reason detail]} (capability/capability)]
    (when-not available?
      (throw (ex-info (str "Cannot define value class " class-name
                           ": fixed value representation is unavailable. "
                           (if (no-value-classes?)
                             no-value-classes-message
                             detail))
                      {:value-type/error :value-capability-unavailable
                       :class-name class-name
                       :reason reason})))))

(defn- error-message [key class-name]
  (str "Class " class-name " is not a value class. "
       (if (no-value-classes?)
         no-value-classes-message
         (case key
           :not-installed
           (str macroexpanded-outside-compilation ", then evaluated. "
                fields/direct-fix)
           :not-value-class
           (str "The capture loader did not catch the class. Known causes: "
                "the expansion was evaluated apart from its macroexpansion "
                "(for example (eval (macroexpand form))); fix: evaluate the "
                "defvaluetype form directly. A defvaluetype nested in a "
                "method body; fix: move the inner defvaluetype form to the "
                "top level.")))))

(defn check-value-class
  "Return nil when `cls` is a value class. Else throw `ex-info` with
  `:value-type/error` `:not-installed` when `installed` is false, or
  `:not-value-class` when `installed` is true, and `:class-name`.

  The value expansions of `defvaluetype` and `defvaluetype*` call it,
  with `installed` the result of the capture loader install at
  macroexpansion."
  [^Class cls installed]
  (when-not (capability/value-class? cls)
    (let [key        (if installed :not-value-class :not-installed)
          class-name (.getName cls)]
      (throw (ex-info (error-message key class-name)
                      {:value-type/error key
                       :class-name       class-name})))))

(defn- field-symbols
  "Return `[index symbol]` for each symbol in `fields` when it is a
  vector, else nil. All declaration checks select fields with it;
  `deftype*` reports other elements and shapes."
  [fields]
  (when (vector? fields)
    (into [] (keep-indexed #(when (symbol? %2) [%1 %2])) fields)))

(defn- mutable-fields [fields]
  (into []
        (comp (map second)
              (filter #(let [m (meta %)]
                         (or (:volatile-mutable m)
                             (:unsynchronized-mutable m)))))
        (field-symbols fields)))

(defn- check-fields
  "Return nil when `fields` is not a vector (`deftype` or `deftype*`
  rejects it) or has no mutable field. Else throw `ex-info` with
  `:value-type/error` `:mutable-field`, `:class-name` and
  `:fields` (the mutable fields, in field order)."
  [class-name fields]
  (let [ms (mutable-fields fields)]
    (when (seq ms)
      (throw (ex-info (str "Cannot define value class " class-name
                           ": mutable fields " (pr-str ms)
                           ". Value class fields are final. Fix: remove "
                           "the :volatile-mutable or :unsynchronized-mutable "
                           "metadata.")
                      {:value-type/error :mutable-field
                       :class-name       class-name
                       :fields           ms})))))

(defn- hidden-index
  "Index of `field` in `fields/record-hidden-fields` when it is a symbol
  with a hidden name (namespace ignored), else nil."
  [field]
  (when (symbol? field)
    (first (keep-indexed (fn [i [n _]] (when (= n (name field)) i))
                         fields/record-hidden-fields))))

(defn- in-place?
  "True when the hidden-name `field` at index `i` of `n` fields is the
  unqualified symbol at index n - 4 + k with its required tag."
  [n i field]
  (let [k (hidden-index field)]
    (and (= i (+ (- n 4) k))
         (nil? (namespace field))
         (= (second (fields/record-hidden-fields k)) (:tag (meta field))))))

(defn- record-shape
  "Return `:plain` or `:record` for `fields`. Throw `ex-info` with
  `:value-type/error` `:invalid-field`, `:reason :record-shape`,
  `:class-name` and `:field` for other shapes. A non-vector `fields` is
  `:plain` (`deftype*` reports it)."
  [class-name fields]
  (let [hidden (filterv (comp hidden-index second) (field-symbols fields))]
    (if (empty? hidden)
      :plain
      (let [n         (count fields)
            misplaced (remove (fn [[i f]] (in-place? n i f)) hidden)]
        (if (and (= 4 (count hidden)) (empty? misplaced))
          :record
          (let [field (second (or (first misplaced) (first hidden)))]
            (throw (ex-info (str "Cannot define class " class-name
                                 ": unsupported record shape at field " field
                                 ". Fix: use no hidden field names, or end the "
                                 "fields with __meta __extmap ^int __hash "
                                 "^int __hasheq.")
                            {:value-type/error :invalid-field
                             :class-name       class-name
                             :field            field
                             :reason           :record-shape}))))))))

(defn- not-installed-message [kind class-name]
  (str "Cannot define class " class-name ": " kind " field capture requires "
       "a compiler loader. " macroexpanded-outside-compilation ". "
       fields/direct-fix))

(defn- install-capture!
  "Install class capture; return the loader install result. Resolve the loader
  lazily: `core` does not require it eagerly."
  [class-name specifications representation shape]
  ((requiring-resolve 'value-type.impl.loader/install!)
   class-name specifications representation shape))

(defn- value-expansion
  "Install capture for `class-name` with `shape` at macroexpansion; return
  the value expansion of `definition`, which evaluates to the Class."
  [class-name shape definition]
  (let [installed (install-capture! class-name [] :value shape)
        cls       (symbol class-name)]
    `(let []
       ~definition
       (check-value-class ~cls ~installed)
       ~cls)))

(defn- install-receiving!
  "Install capture for a receiving expansion. Throw `:not-installed` when
  `Compiler/LOADER` has no thread binding."
  [kind class-name specifications representation shape]
  (when-not (install-capture! class-name specifications representation shape)
    (throw (ex-info (not-installed-message kind class-name)
                    {:value-type/error :not-installed
                     :class-name class-name}))))

(defn- value-receiving
  "Value receiving expansion of `definition` for fixed `modes`. The caller
  checks `fields` and derives `shape` first. `opts` is as for
  `fields/receiving-specifications`."
  [class-name fields modes shape opts definition]
  (require-value-capability! class-name)
  (let [specifications (fields/receiving-specifications
                        *ns* class-name fields modes #{:native :generated}
                        opts)
        native? (some #(= :native (:enforcement %)) specifications)
        cls     (symbol class-name)]
    (when native? (field-capability/require-field-capability! class-name))
    (install-receiving! "value" class-name specifications :value shape)
    `(let []
       ~@(when native? `((field-capability/require-field-capability! ~class-name)))
       ~definition
       (check-value-class ~cls true)
       ~cls)))

(defn- identity-receiving
  "Identity receiving expansion of `definition` for fixed generated `modes`.
  The caller checks `fields` and derives `shape` first. Apply fixed identity
  policy during analysis and evaluation. `opts` is as for
  `fields/receiving-specifications`."
  [class-name fields modes shape opts definition]
  (let [specifications (fields/receiving-specifications
                        *ns* class-name fields modes #{:generated} opts)]
    (runtime/enforce-fixed-fallback!)
    (install-receiving! "identity" class-name specifications :identity shape)
    `(let []
       (runtime/enforce-fixed-fallback!)
       ~definition
       ~(symbol class-name))))

(defn- policy-expansion
  "Select representation and field enforcement for `validated`
  specifications; return the expansion. The caller validates the declaration
  first. `value-form` and `identity-form` take the enforcement modes and
  return a receiving form; `definition` is the plain definition and
  `guarded` its fallback form. A receiving form is wrapped in `let` to keep
  receiving analysis checks through both macroexpand forms."
  [class-name shape validated value-form identity-form definition guarded]
  (let [{:keys [available? detail]} (capability/capability)
        specifications (fields/select-enforcement
                        validated
                        #(and available?
                              (:available? (field-capability/capability))))
        modes (mapv :enforcement specifications)]
    (if available?
      (if (seq specifications)
        `(let [] ~(value-form modes))
        (value-expansion class-name shape definition))
      (do
        (runtime/enforce-fallback! detail)
        (if (seq specifications)
          `(let [] ~(identity-form modes))
          `(do ~guarded))))))

(defmacro value-field-deftype
  "Internal receiving boundary for fixed value field enforcement modes."
  [class-name modes name fields & opts+specs]
  (check-fields class-name fields)
  (value-receiving class-name fields modes :plain nil
                   `(deftype ~name ~fields ~@opts+specs)))

(defmacro identity-field-deftype
  "Internal receiving boundary for fixed generated identity field declarations.

  Resolve local Classes and install capture during analysis. Apply fixed identity
  policy during both analysis and evaluation; do not upgrade representation."
  [class-name modes name fields & opts+specs]
  (check-fields class-name fields)
  (identity-receiving class-name fields modes :plain nil
                      `(deftype ~name ~fields ~@opts+specs)))

(defmacro value-field-deftype*
  "Internal `defvaluetype*` receiving boundary for fixed value field
  enforcement modes. Derive the shape from `fields`."
  [tagname classname modes fields & opts+specs]
  (let [class-name (str classname)]
    (check-fields class-name fields)
    (value-receiving class-name fields modes (record-shape class-name fields)
                     {:hidden-field? true}
                     `(deftype* ~tagname ~classname ~fields ~@opts+specs))))

(defmacro identity-field-deftype*
  "Internal `defvaluetype*` receiving boundary for fixed generated identity
  field declarations. Derive the shape from `fields`; apply fixed identity
  policy during analysis and evaluation."
  [tagname classname modes fields & opts+specs]
  (let [class-name (str classname)]
    (check-fields class-name fields)
    (identity-receiving class-name fields modes
                        (record-shape class-name fields) {:hidden-field? true}
                        `(deftype* ~tagname ~classname ~fields ~@opts+specs))))

(defmacro defvaluetype
  "As `deftype` (see its docstring for syntax, options, specs, the
  `->name` factory and the import). Define a final JEP 401 value class
  when supported, or an ordinary identity class under fallback policy when
  identity fallback is permitted.

  Uses value classes on a supported JEP 401 JDK with `--enable-preview`.
  Otherwise uses identity storage with generated checks for enabled fields.
  Representation policy is independent of field enforcement. Set JVM property
  `value-type.fallback` before loading: silent (default), warn (once per
  library runtime), or error (reject with Java Error). Compiler operations
  wrap that Error in CompilerException. Configuration is read and validated
  once; invalid values fail and later changes are unsupported. Requiring
  alone does not warn. Concurrent types and namespaces share warning state.

  Selecting fallback during macroexpansion applies policy even if the
  expansion is discarded. Returned expansions enforce receiving policy at
  compilation and evaluation. Strict retries reject after a caught failure.
  Fallback retains ordinary identity, equality and hashing semantics.
  Invalid forms and implementation defects are not retried as fallback.

  AOT fixes representation. Fallback enforces receiving policy where the
  form is evaluated, even on a capable JVM. Top-level forms enforce it during
  namespace initialization. Forms inside functions permit namespace loading
  and enforce policy on invocation; repeated strict calls throw plain Error.
  Failed initialization retries can throw NoClassDefFoundError (also an Error).
  Guards do not cover direct class access that bypasses evaluation of the
  guarded form, or guarantee that no class was loaded before rejection.
  Value artifacts do not convert.
  Fallback adds no minimum JDK beyond the supported Clojure runtime; see
  README for tested JVM versions and verification commands.

  Non-nil fields: ^Point ^:non-nil field requires an explicit tag naming an
  already defined concrete class, including identity and external classes. The
  explicit form is ^{:tag Point :non-nil true} field. Only true enables the
  constraint; absent, false and nil keep ordinary behavior. Other metadata
  values are definition errors. The removed :nil-restricted key is rejected by
  presence, including false and nil; use :non-nil instead. Missing/unresolved
  tags, primitives, interfaces, abstract classes, arrays, forward types and
  the enclosing type are not eligible. Construction rejects nil with
  NullPointerException and other types with ClassCastException, including
  direct and reflective constructors. Reflection can wrap these exceptions.
  Enabled fields use native enforcement when eligible and available, otherwise
  generated constructor checks. Missing native support alone does not invoke
  fallback policy. No internal-package exports are needed; flattened storage
  is not guaranteed. Non-nil field storage has the declared concrete type;
  reference constructor parameters remain Object, with unchanged order and
  arity. Nested values may contain fields that permit nil. Receiving checks
  also apply to transferred expansions. Both `macroexpand` forms retain
  receiver-local loader installation for enabled fields, supporting separate
  `eval`, metadata-preserving source loading and function analysis then
  invocation. Ordinary value definitions do not support separate
  expansion/evaluation this way. Value AOT artifacts require the same JDK
  feature release with preview enabled and have no identity fallback. Identity
  artifacts retain generated checks on baseline and capable JVMs.

  Restriction: no mutable fields (`:volatile-mutable` or
  `:unsynchronized-mutable`).

  In value mode, differences from `deftype`: instances have no identity.
  - `identical?` (the Java `==` operator, `acmp`) compares field values.
  - The default `equals` and `hashCode` use field values.
  - A `double` field compares by bit pattern in `identical?` (`NaN`
    equals `NaN`; `0.0` does not equal `-0.0`).
  - A field that holds an identity object compares by reference (two
    equal vectors that are not the same object give instances that
    are not equal).
  - A field that holds a value object (for example a boxed `Long`
    under `--enable-preview`, or a `defvaluetype` instance) compares
    by its field values.
  - `locking`, `WeakReference` and other identity operations throw
    `IdentityException`.
  - AOT output is tied to one JDK feature release.

  Errors (`ex-info` with `:value-type/error` and `:class-name`; compiler
  wrapping applies):
  - `:invalid-field` (macroexpansion; data `:field`, `:reason`): an invalid
    field declaration.
  - `:mutable-field` (macroexpansion; data `:fields`): a mutable field.
  - `:not-installed` (run time): an ordinary definition macroexpanded with no
    compilation loader binding, then evaluated.
  - `:not-installed` (macroexpansion): a definition with enabled fields fully
    expanded with no compilation loader binding (for example
    `clojure.walk/macroexpand-all`).
  - `:not-value-class` (run time): the class is not a value class.
  - `:value-capability-unavailable` (receiving boundary; data `:reason`): a
    transferred value expansion on a JVM that cannot define value classes.
  - `:field-capability-unavailable` (receiving boundary; data `:reason`): a
    transferred expansion with fixed `:native` modes on a JVM without native
    field support.
  - `:invalid-field-enforcement` (receiving boundary; data `:modes`): invalid
    transferred enforcement modes, or identity fields with non-generated modes.
  - `:invalid-field` (loader; data `:field`, `:reason :field-class-mismatch`):
    the field tag resolves to a different Class in the compilation loader.
  - `:jvm-rejected` (loader): the JVM rejects the generated class.
  - `:unexpected-constructor` (loader; value output): the constructor is not the
    supported `deftype` shape.
  - `:unsupported-shape` (loader; identity output): the class or constructor is
    not the supported `deftype` shape for generated checks."
  {:arglists '([name [& fields] & opts+specs])}
  [name fields & opts+specs]
  (let [class-name (str (namespace-munge *ns*) "." name)]
    (check-fields class-name fields)
    (policy-expansion
     class-name :plain (fields/specifications *ns* class-name fields)
     (fn [modes]
       `(value-field-deftype ~class-name ~modes ~name ~fields ~@opts+specs))
     (fn [modes]
       `(identity-field-deftype ~class-name ~modes ~name ~fields ~@opts+specs))
     `(deftype ~name ~fields ~@opts+specs)
     `(runtime/guarded-deftype ~name ~fields ~@opts+specs))))

(defmacro defvaluetype*
  "As `deftype*`, with the same arguments. Evaluate to the defined Class.
  Define a JEP 401 value class when supported, or an ordinary identity
  class under the `value-type.fallback` policy (see `defvaluetype`). Use
  it in the same top-level position as `deftype*`.

  Field shapes:
  - Plain: no field has a hidden name (`__meta`, `__extmap`, `__hash`,
    `__hasheq`; namespace ignored).
  - Record: the fields end with `__meta __extmap ^int __hash ^int __hasheq`,
    unqualified, with no tag on `__meta` and `__extmap`.
  Other shapes give `:invalid-field` with `:reason :record-shape`.

  Caller obligations for record shapes: do not read or write `__hash` or
  `__hasheq`, and pass 0 for both hash arguments in explicit constructor
  calls. Value output removes both fields and ignores those arguments.
  Identity output keeps both fields; they hold 0 when callers meet these
  obligations. value-type does not validate the hash arguments.

  Non-nil fields: `:non-nil` metadata as for `defvaluetype`, in both
  shapes. Hidden-name fields cannot be non-nil. The main constructor checks
  each enabled field, so each constructor, `create`, record literals,
  compiled record constants and reflection reject nil with
  `NullPointerException` and wrong types with `ClassCastException`. The
  compiler, class initialization and reflection can wrap these exceptions;
  find the exception in the cause chain. Error text is not specified.
  With enabled fields, the expansion is a receiving form, also on
  fallback, and receivers repeat the declaration checks.

  Restrictions: no mutable fields and no `:nil-restricted` field metadata.

  Errors (`ex-info` with `:value-type/error` and `:class-name`), checked at
  macroexpansion before representation policy, in this order:
  - `:mutable-field` (data `:fields`).
  - `:invalid-field` `:reason :record-shape` (data `:field`).
  - `:invalid-field` `:reason :removed-nil-restricted` or `:invalid-non-nil`
    (data `:field`, `:value`).
  - `:invalid-field` `:reason :hidden-field` (data `:field`): a hidden-name
    field with `:non-nil true`.
  - The `defvaluetype` tag and type reasons of enabled fields.
  Errors of value output, when the capture loader defines the class:
  - `:unexpected-constructor`: the constructors are
    not the plain or the record shape.
  - `:invalid-field` `:reason :removed-field-access` (data `:field`): a
    captured class reads or writes `__hash` or `__hasheq`.
  Errors of identity output with enabled fields, when the capture loader
  defines the class:
  - `:unsupported-shape`: a delegating constructor writes an enabled field,
    or a constructor both creates and calls the target.
  Other policy, capability, receiving, loader and run time errors are as for
  `defvaluetype`. A value expansion without enabled fields
  evaluated apart from its macroexpansion fails with `:not-installed` or
  `:not-value-class`; it never selects fallback. With enabled fields,
  separate `eval` of the expansion works, and only a full expansion
  outside compilation (for example `clojure.walk/macroexpand-all`) gives
  `:not-installed`; see doc/usage.md.

  Excluded: mutable caches, value-equality emulation and
  serialization guarantees. Evidence covers Clojure 1.12.6, Java 8 and
  Valhalla 27-jep401ea3+1-1 only. See doc/usage.md."
  {:arglists '([tagname classname [& fields] & opts+specs])}
  [tagname classname fields & opts+specs]
  (let [class-name (str classname)]
    (check-fields class-name fields)
    (let [shape (record-shape class-name fields)]
      (policy-expansion
       class-name shape
       (fields/specifications *ns* class-name fields {:hidden-field? true})
       (fn [modes]
         `(value-field-deftype* ~tagname ~classname ~modes ~fields
                                ~@opts+specs))
       (fn [modes]
         `(identity-field-deftype* ~tagname ~classname ~modes ~fields
                                   ~@opts+specs))
       `(deftype* ~tagname ~classname ~fields ~@opts+specs)
       `(runtime/guarded-deftype* ~tagname ~classname ~fields ~@opts+specs)))))
