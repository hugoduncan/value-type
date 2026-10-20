(ns value-type.core
  "Define JEP 401 value classes with `defvaluetype`, a `deftype`
  compatible macro."
  (:require
   [value-type.impl.loader :as loader])
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
  `->name` factory and the import), but the class is a final JEP 401
  value class with final fields.

  Requires a JEP 401 JDK started with `--enable-preview`.

  Restriction: no mutable fields (`:volatile-mutable` or
  `:unsynchronized-mutable`).

  Differences from `deftype`: instances have no identity.
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
    (let [installed (loader/install! class-name)
          cls       (symbol class-name)]
      `(let []
         (deftype ~name ~fields ~@opts+specs)
         (check-value-class ~cls ~installed)
         ~cls))))
