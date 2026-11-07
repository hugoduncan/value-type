(ns value-type.impl.loader
  "The capture loader. It transforms the class of one `deftype` form, and
  references to its fields in companion classes, when the compiler defines them.
  The selected backend gives value or identity output."
  (:import
   (clojure.lang Compiler DynamicClassLoader)))

(set! *warn-on-reflection* true)

(defn- define-target-class
  "Define `bs` as class `class-name` with `delegate`, then link the
  class so that the JVM verifies it now. Return the class. Wrap a
  `LinkageError` as `ex-info` with `:value-type/error :jvm-rejected`.
  Value and identity output both use this check."
  ^Class [^DynamicClassLoader delegate ^String class-name ^bytes bs src]
  (try
    (let [cls (.defineClass delegate class-name bs src)]
      (.getDeclaredMethods cls)
      cls)
    (catch LinkageError e
      (throw (ex-info (str "Cannot define class " class-name
                           ": the JVM rejected the class. " (.getMessage e))
                      {:value-type/error :jvm-rejected
                       :class-name class-name}
                      e)))))

(defn- check-field-classes!
  "Throw `:invalid-field` `:field-class-mismatch` when a field type of
  `specifications` resolves to a different Class in `delegate`."
  [target ^ClassLoader delegate specifications]
  (doseq [{:keys [field type]} specifications]
    (when-not (identical? type (Class/forName (.getName ^Class type) false delegate))
      (throw (ex-info (str "Cannot define class " target ": field " field
                           " type " (.getName ^Class type) " resolves to a "
                           "different Class in the compilation loader. "
                           "Fix: redefine the field type and the dependent "
                           "definition in the same loader, or reload both.")
                      {:value-type/error :invalid-field
                       :class-name target :field field
                       :reason :field-class-mismatch})))))

(defn- capture-loader
  "Carry ordered field specifications to the target transform.
   The exact `:type` Classes serve `check-field-classes!`."
  ^DynamicClassLoader [^String target ^ClassLoader parent specifications representation shape]
  (let [delegate (DynamicClassLoader. parent)
        ;; Resolve only at installation, not when baseline code loads capture support.
        {:keys [owner companion]}
        (case representation
          :value ((requiring-resolve 'value-type.impl.value-backend/prepare)
                  target specifications delegate shape)
          :identity ((requiring-resolve 'value-type.impl.identity-backend/prepare)
                     target specifications delegate shape))]
    (proxy [DynamicClassLoader] [parent]
      (defineClass [^String class-name ^bytes bs src]
        (if (= class-name target)
          (do
            (check-field-classes! target delegate specifications)
            (let [bs' (owner bs)]
              (when *compile-files*
                (Compiler/writeClassFile (.replace class-name \. \/) bs'))
              (define-target-class delegate class-name bs' src)))
          (let [output (companion bs)]
            (when (and *compile-files* (seq specifications))
              (Compiler/writeClassFile (.replace class-name \. \/) output))
            (.defineClass delegate class-name output src)))))))

(defn install!
  "Make the next define of class `class-name` give the selected output.

  Input: `class-name`, the binary name of the `deftype` class (a
  string, for example `\"my.ns.Point\"`). Ordered `specifications`
  contain `:field`, exact `:type` Class and `:enforcement`.
  `:generated` selects constructor checks; `:native` selects native enforcement.
  `representation` is :value or :identity. Identity output requires
  generated enforcement. `shape` is `:plain`, `:record` or `:closed`
  (a record shape with the `:value-type/closed true` option); both
  backends get it.
  A resolved Class identity mismatch gives `:invalid-field` with reason
  `:field-class-mismatch` before target definition.
  Output: `true` when `Compiler/LOADER` has a thread binding. Then the
  binding is set to a new capture loader whose parent is the old
  value. Else `false`, and nothing changes.

  Call point: at macroexpansion time of the top-level form that
  contains the `deftype`. `Compiler.eval` and AOT compilation push a
  new `LOADER` binding for each top-level form before macroexpansion.

  The capture loader defines each class through one delegate loader. For
  `class-name` only, it changes the bytes with the owner transform of the
  selected backend (errors pass through): `:value` uses
  `value-type.impl.transform/->value-class` for `:plain` and
  `value-type.impl.transform/->record-value-class` for `:record` and
  `:closed`; `:identity` uses `value-type.impl.identity-backend`
  constructor checks. It writes the bytes to the class file when
  `*compile-files*` is true, it defines them and links the class. With
  field specifications, it also rewrites references to non-nil target
  fields in every other captured class and writes those bytes during AOT
  compilation. Companion classes keep their class kind. A `LinkageError`
  of the target define or link gives `ex-info` with `:value-type/error
  :jvm-rejected`, `:class-name`, and the error as cause. The compiler
  wraps these errors in a `CompilerException`. `install!` throws no error
  of its own."
  [^String class-name specifications representation shape]
  (if (.isBound Compiler/LOADER)
    (do (.set Compiler/LOADER (capture-loader class-name @Compiler/LOADER
                                              specifications representation shape))
        true)
    false))
