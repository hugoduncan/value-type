(ns value-type.core
  "Define JEP 401 value classes with `defvaluetype`, a `deftype`
  compatible macro."
  (:require
   [value-type.impl.runtime :as runtime])
  (:import
   (java.lang.reflect Method)))

(set! *warn-on-reflection* true)

(def ^:private is-value-method
  "The `Class::isValue` method, or nil on a JVM with no value classes."
  (delay
    (try
      (.getMethod Class "isValue" (make-array Class 0))
      (catch NoSuchMethodException _ nil))))

(def ^:private no-value-classes-message
  (str "The JVM has no value classes (Class::isValue is missing). "
       "Use a JEP 401 JDK with --enable-preview."))

(defn- error-message [key class-name]
  (str "Class " class-name " is not a value class. "
       (if (nil? @is-value-method)
         no-value-classes-message
         (case key
           :not-installed
           (str "The defvaluetype form was macroexpanded outside "
                "compilation (no Compiler/LOADER binding), then evaluated. "
                "Fix: evaluate the defvaluetype form directly.")
           :not-value-class
           (str "The capture loader did not catch the class. Known causes: "
                "the expansion was evaluated apart from its macroexpansion "
                "(for example (eval (macroexpand form))); fix: evaluate the "
                "defvaluetype form directly. A defvaluetype nested in a "
                "method body; fix: move the inner defvaluetype form to the "
                "top level.")))))

(defn- value-class?
  "True when `cls` is a value class. False when the JVM has no
  `Class::isValue`."
  [^Class cls]
  (let [^Method m @is-value-method]
    (boolean (and m (.invoke m cls (object-array 0))))))

(defn check-value-class
  "Return nil when `cls` is a value class. Else throw `ex-info` with
  `:value-type/error` `:not-installed` when `installed` is false, or
  `:not-value-class` when `installed` is true, and `:class-name`.

  The expansion of `defvaluetype` calls it, with `installed` the result
  of the capture loader install at macroexpansion."
  [^Class cls installed]
  (when-not (value-class? cls)
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

(defmacro defvaluetype
  "As `deftype` (see its docstring for syntax, options, specs, the
  `->name` factory and the import). Define a final JEP 401 value class
  when supported, or an ordinary identity class under fallback policy.

  Uses value classes on a supported JEP 401 JDK with `--enable-preview`.
  Otherwise uses ordinary identity `deftype`. Set JVM property
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

  Errors (`ex-info` with `:value-type/error` and `:class-name`):
  - `:mutable-field` (macroexpansion; data `:fields`): a mutable field.
  - `:not-installed` (run time): macroexpanded with no compilation
    loader binding, then evaluated.
  - `:not-value-class` (run time): the class is not a value class.
  - `:jvm-rejected`, `:unexpected-constructor`: from the loader."
  {:arglists '([name [& fields] & opts+specs])}
  [name fields & opts+specs]
  (let [class-name (str (namespace-munge *ns*) "." name)]
    (check-fields class-name fields)
    (let [{:keys [available? detail]} (runtime/capability)]
      (if available?
        (let [installed ((requiring-resolve 'value-type.impl.loader/install!) class-name)
              cls (symbol class-name)]
          `(let []
             (deftype ~name ~fields ~@opts+specs)
             (check-value-class ~cls ~installed)
             ~cls))
        (do
          (runtime/enforce-fallback! detail)
          ;; Keep the analysis guard in both macroexpand entry-point results.
          `(do (runtime/guarded-deftype ~name ~fields ~@opts+specs)))))))
