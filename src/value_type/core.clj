(ns value-type.core
  "Define JEP 401 value classes with `defvaluetype`, a `deftype`
  compatible macro."
  (:require
   [value-type.impl.capability :as capability]
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
  (let [{:keys [available? reason detail]} (runtime/capability)]
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

  The expansion of `defvaluetype` calls it, with `installed` the result
  of the capture loader install at macroexpansion."
  [^Class cls installed]
  (when-not (capability/value-class? cls)
    (let [key        (if installed :not-value-class :not-installed)
          class-name (.getName cls)]
      (throw (ex-info (error-message key class-name)
                      {:value-type/error key
                       :class-name       class-name})))))

(defn- mutable-fields [fields]
  (filterv #(and (symbol? %)
                 (let [m (meta %)]
                   (or (:volatile-mutable m) (:unsynchronized-mutable m))))
           fields))

(defn- check-fields
  "Return nil when `fields` is not a vector (`deftype` rejects it) or
  has no mutable field. Else throw `ex-info`
  with `:value-type/error` `:mutable-field`, `:class-name` and
  `:fields` (the mutable fields, in field order)."
  [class-name fields]
  (let [ms (when (vector? fields) (mutable-fields fields))]
    (when (seq ms)
      (throw (ex-info (str "Cannot define value class " class-name
                           ": mutable fields " (pr-str ms)
                           ". Value class fields are final. Fix: remove "
                           "the :volatile-mutable or :unsynchronized-mutable "
                           "metadata.")
                      {:value-type/error :mutable-field
                       :class-name       class-name
                       :fields           ms})))))

(defn- not-installed-message [kind class-name]
  (str "Cannot define class " class-name ": " kind " field capture requires "
       "a compiler loader. " macroexpanded-outside-compilation ". "
       fields/direct-fix))

(defn- install-capture!
  "Install class capture; return the loader install result. Resolve the loader
  lazily: `core` does not require it eagerly."
  [class-name specifications representation]
  ((requiring-resolve 'value-type.impl.loader/install!)
   class-name specifications representation))

(defn- install-receiving!
  "Install capture for a receiving expansion. Throw `:not-installed` when
  `Compiler/LOADER` has no thread binding."
  [kind class-name specifications representation]
  (when-not (install-capture! class-name specifications representation)
    (throw (ex-info (not-installed-message kind class-name)
                    {:value-type/error :not-installed
                     :class-name class-name}))))

(defmacro value-field-deftype
  "Internal receiving boundary for fixed value field enforcement modes."
  [class-name modes name fields & opts+specs]
  (check-fields class-name fields)
  (require-value-capability! class-name)
  (let [specifications (fields/receiving-specifications
                        *ns* class-name fields modes #{:native :generated})
        native? (some #(= :native (:enforcement %)) specifications)]
    (when native? (runtime/require-field-capability! class-name))
    (install-receiving! "value" class-name specifications :value)
    `(let []
       ~@(when native? `((runtime/require-field-capability! ~class-name)))
       (deftype ~name ~fields ~@opts+specs)
       (check-value-class ~(symbol class-name) true)
       ~(symbol class-name))))

(defmacro identity-field-deftype
  "Internal receiving boundary for fixed generated identity field declarations.

  Resolve local Classes and install capture during analysis. Apply fixed identity
  policy during both analysis and evaluation; do not upgrade representation."
  [class-name modes name fields & opts+specs]
  (check-fields class-name fields)
  (let [specifications (fields/receiving-specifications
                        *ns* class-name fields modes #{:generated})]
    (runtime/enforce-fixed-fallback!)
    (install-receiving! "identity" class-name specifications :identity)
    `(let []
       (runtime/enforce-fixed-fallback!)
       (deftype ~name ~fields ~@opts+specs)
       ~(symbol class-name))))

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
    (let [validated (fields/specifications *ns* class-name fields)
          {:keys [available? detail]} (runtime/capability)
          specifications (fields/select-enforcement
                          validated
                          #(and available?
                                (:available? (runtime/field-capability))))
          modes (mapv :enforcement specifications)]
      (if available?
        (if (seq specifications)
          ;; Preserve receiving analysis checks through both macroexpand forms.
          `(let [] (value-field-deftype ~class-name ~modes ~name ~fields ~@opts+specs))
          (let [installed (install-capture! class-name [] :value)
                cls (symbol class-name)]
            `(let []
               (deftype ~name ~fields ~@opts+specs)
               (check-value-class ~cls ~installed)
               ~cls)))
        (do
          (runtime/enforce-fallback! detail)
          (if (seq specifications)
            `(let []
               (identity-field-deftype ~class-name ~modes ~name ~fields
                                       ~@opts+specs))
            `(do (runtime/guarded-deftype ~name ~fields ~@opts+specs))))))))
