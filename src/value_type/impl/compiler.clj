(ns value-type.impl.compiler
  "The compiler boundary. All use of Clojure compiler code is here:
  `Compiler/LOADER`, `DynamicClassLoader`, class definition, class file
  output, type tag resolution and `munge`. Requires no value-type
  namespace and loads on Java 8."
  (:import
   (clojure.lang Compiler Compiler$HostExpr DynamicClassLoader)))

(set! *warn-on-reflection* true)

(defn define-class
  "Define `bs` as class `class-name` in `loader`, a loader from this
  namespace. Return the Class. Errors, for example `LinkageError`,
  propagate."
  ^Class [loader ^String class-name ^bytes bs src]
  (.defineClass ^DynamicClassLoader loader class-name bs src))

(defn probe-loader
  "Return a new loader for probe classes, with the context loader of
  Clojure as parent."
  ^ClassLoader []
  (DynamicClassLoader.))

(defn- write-class-file!
  [^String class-name ^bytes bs]
  (Compiler/writeClassFile (.replace class-name \. \/) bs))

(defn capture-loader
  "Return a capture loader with parent `parent`.

  It makes one delegate loader with parent `parent` and calls
  `(prepare delegate)` once; the result is `{:owner :companion}`, two
  byte transforms. At the define of class `target`, it calls
  `(check delegate)`, transforms the bytes with `owner`, writes them when
  `*compile-files*` is true and calls `(define-target delegate class-name
  bytes src)`. Other classes are transformed with `companion`, written
  when `*compile-files*` and `rewrite-companions?` are true, and defined
  in the delegate. Reads and sets no `LOADER` binding."
  ^ClassLoader [^String target ^ClassLoader parent prepare check define-target rewrite-companions?]
  (let [delegate (DynamicClassLoader. parent)
        {:keys [owner companion]} (prepare delegate)]
    (proxy [DynamicClassLoader] [parent]
      (defineClass [^String class-name ^bytes bs src]
        (if (= class-name target)
          (do
            (check delegate)
            (let [bs' (owner bs)]
              (when *compile-files*
                (write-class-file! class-name bs'))
              (define-target delegate class-name bs' src)))
          (let [output (companion bs)]
            (when (and *compile-files* rewrite-companions?)
              (write-class-file! class-name output))
            (define-class delegate class-name output src)))))))

(defn install-capture!
  "When `Compiler/LOADER` has a thread binding, set it to `(make-loader
  old-loader)` and return `true`. Else return `false`."
  [make-loader]
  (if (.isBound Compiler/LOADER)
    (do (.set Compiler/LOADER (make-loader @Compiler/LOADER))
        true)
    false))

(defn resolve-tag
  "Resolve type tag `tag` to a Class with Clojure's tag rules in the
  current `*ns*`, or return nil. `ClassNotFoundException` propagates."
  [tag]
  (or (when (symbol? tag)
        (or (when-not (namespace tag)
              (Compiler$HostExpr/maybeSpecialTag tag))
            (Compiler$HostExpr/maybeArrayClass tag)))
      (Compiler$HostExpr/maybeClass tag true)))

(defn munge-name
  "Return the class file name of the Clojure name string `s`."
  ^String [^String s]
  (Compiler/munge s))

;;; Compiler class shape data

(def record-hidden-fields
  "Hidden fields of the `deftype*` record shape, in place order. Each
  entry is `[name tag delegating-argument]`: the field name, its required
  tag, and the constant that a delegating record constructor passes when
  it has no parameter for the field. Value output of the record shape
  removes the fields with tag `int`; value output of the closed shape
  removes all of them."
  [["__meta" nil nil] ["__extmap" nil nil]
   ["__hash" 'int 0] ["__hasheq" 'int 0]])

(def hidden-field-count
  "The number of `record-hidden-fields`."
  (count record-hidden-fields))

(defn hidden-field-index
  "Return the field (or constructor parameter) index of hidden field `k`
  (index in `record-hidden-fields`) when there are `n` fields."
  [n k]
  (+ (- n hidden-field-count) k))

(defn hidden-field-position
  "Return the index in `record-hidden-fields` of the hidden field named
  `field-name`, or nil."
  [field-name]
  (first (keep-indexed (fn [i [n]] (when (= n field-name) i))
                       record-hidden-fields)))

(def record-constructors
  "The constructors of the `deftype*` record shape, as the count of their
  hidden-field parameters (the first hidden fields, in place order). The
  entry with all hidden fields is the main constructor; the others
  delegate to it."
  [0 2 hidden-field-count])

(def deftype-constructor
  "Constructor data of the plain `deftype*` shape: the constructor count
  and the internal owner name and descriptor of its super constructor."
  {:count 1
   :super-owner "java/lang/Object"
   :super-descriptor "()V"})
