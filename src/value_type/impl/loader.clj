(ns value-type.impl.loader
  "The capture loader. It changes the class of one `deftype` form into
  a value class when the compiler defines it."
  (:require
   [value-type.impl.transform :as transform])
  (:import
   (clojure.lang Compiler DynamicClassLoader)))

(set! *warn-on-reflection* true)

(defn- define-value-class
  "Define `bs` as class `class-name` with `delegate`, then link the
  class so that the JVM verifies it now. Return the class. Wrap a
  `LinkageError` as `ex-info` with `:value-type/error :jvm-rejected`."
  ^Class [^DynamicClassLoader delegate ^String class-name ^bytes bs src]
  (try
    (let [cls (.defineClass delegate class-name bs src)]
      (.getDeclaredMethods cls)
      cls)
    (catch LinkageError e
      (throw (ex-info (str "Cannot define value class " class-name
                           ": the JVM rejected the class. " (.getMessage e))
                      {:value-type/error :jvm-rejected
                       :class-name class-name}
                      e)))))

(defn- capture-loader
  "A `DynamicClassLoader` that sends each define to one delegate, and
  changes the class `target` into a value class."
  ^DynamicClassLoader [^String target ^ClassLoader parent]
  (let [delegate (DynamicClassLoader. parent)]
    (proxy [DynamicClassLoader] [parent]
      (defineClass [^String class-name ^bytes bs src]
        (if (= class-name target)
          (let [bs' (transform/->value-class bs)]
            (when *compile-files*
              (Compiler/writeClassFile (.replace class-name \. \/) bs'))
            (define-value-class delegate class-name bs' src))
          (.defineClass delegate class-name bs src))))))

(defn install!
  "Make the next define of class `class-name` give a value class.

  Input: `class-name`, the binary name of the `deftype` class (a
  string, for example `\"my.ns.Point\"`).
  Output: `true` when `Compiler/LOADER` has a thread binding. Then the
  binding is set to a new capture loader whose parent is the old
  value. Else `false`, and nothing changes.

  Call point: at macroexpansion time of the top-level form that
  contains the `deftype`. `Compiler.eval` and AOT compilation push a
  new `LOADER` binding for each top-level form before macroexpansion.

  The capture loader defines each class through one delegate loader.
  For `class-name` only, it changes the bytes with
  `value-type.impl.transform/->value-class` (errors pass through), it
  writes them to the class file when `*compile-files*` is true, it
  defines them and links the class. A `LinkageError` of the define or
  link gives `ex-info` with `:value-type/error :jvm-rejected`,
  `:class-name`, and the error as cause. The compiler wraps these
  errors in a `CompilerException`. `install!` throws no error of its
  own."
  [^String class-name]
  (if (.isBound Compiler/LOADER)
    (do (.set Compiler/LOADER (capture-loader class-name @Compiler/LOADER))
        true)
    false))
