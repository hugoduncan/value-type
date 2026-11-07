(ns value-type.cell-registry
  "Child-safe support of grouped cells: new-Class and absence checks of
   type definitions, the attempt axis, expected values of an axis, the
   identity fact, factory symbols, cell namespace names, failure maps (of
   an error: thrown, failure, root-data and root-failure; of a failed type
   definition), rejection maps, call outcomes, failed, guarded and
   rejected calls, runtime facts, the invalid-policy message, child call
   forms, the hash-field count, the closed meta rejection and the record
   tail of `deftype*`.

   Requires only clojure.core and uses Java 8 APIs, so a child JVM loads it
   without test support."
  (:import (clojure.lang Reflector RT)
           (java.util Collections IdentityHashMap Set)))

(defonce ^:private ^Set classes
  (Collections/synchronizedSet (Collections/newSetFromMap (IdentityHashMap.))))

(defn new-class!
  "Return true only when value is a Class that no earlier call in this JVM
   got; add it. Compare by identity, not by name."
  [value]
  (and (class? value) (.add classes value)))

(def record-tail
  "The hidden fields of the record shape of `deftype*`, in field order."
  ['__meta '__extmap
   (with-meta '__hash {:tag 'int})
   (with-meta '__hasheq {:tag 'int})])

(def ^:private cell-ns-prefix "cell.")

(defn cell-ns-name
  "Return the namespace name (a string) of the cell at index of a child."
  [index]
  (str cell-ns-prefix index))

(defn each
  "Return a vector with expected once for each entry of axis (a
   collection)."
  [axis expected]
  (vec (repeat (count axis) expected)))

(defn check-form
  "Return a child form that requires the namespace of check (a
   namespace-qualified symbol), then evaluates (check & args)."
  [check & args]
  (list 'do
        (list 'require (list 'quote (symbol (namespace check))))
        (list* check args)))

(defn identity?
  "Return true when two calls of factory with args give values that are not
   equal (identity equality)."
  [factory & args]
  (not= (apply factory args) (apply factory args)))

(def attempt-count
  "The number of attempts that attempts and twice run."
  2)

(defn attempts
  "Call f with each attempt index, attempt-count times in order; return the
   results as a vector. A later attempt shows the state that the earlier
   attempts left (warn-once state, or no partial definition after a
   failure)."
  [f]
  (mapv f (range attempt-count)))

(defn twice
  "Call f (no arguments) for each attempt (see attempts); return the results
   as a vector."
  [f]
  (attempts (fn [_] (f))))

(defn each-attempt
  "Return the expected results of twice: expected for each attempt."
  [expected]
  (each (range attempt-count) expected))

(defn- chain
  "Return the cause chain of error, outermost first."
  [^Throwable error]
  (vec (take-while some? (iterate #(.getCause ^Throwable %) error))))

(defn root
  "Return the root cause of error: the last Throwable of its cause chain."
  ^Throwable [^Throwable error]
  (peek (chain error)))

(defn thrown
  "Return the failure map {:class :message :chain} of error: the root class
   name, the root message and the class names of the cause chain, outermost
   first."
  [^Throwable error]
  (let [causes (chain error)
        ^Throwable root-cause (peek causes)]
    {:class (.getName (class root-cause))
     :message (.getMessage root-cause)
     :chain (mapv #(.getName (class %)) causes)}))

(defn failure
  "Return the root failure map {:class :message} of error: thrown without
   :chain."
  [^Throwable error]
  (select-keys (thrown error) [:class :message]))

(defn root-data
  "Return the ex-data of the root cause of error."
  [^Throwable error]
  (ex-data (root error)))

(defn root-failure
  "Return the failure of error with :data (root-data)."
  [^Throwable error]
  (assoc (failure error) :data (root-data error)))

(defn outcome
  "Call f (no arguments). Return :accepted, or (success value) of the value
   of f; or (failure-of error) of the Throwable that f throws."
  ([failure-of f]
   (outcome failure-of f (constantly :accepted)))
  ([failure-of f success]
   (try
     (success (f))
     (catch Throwable thrown
       (failure-of thrown)))))

(def unexpected-success
  "The outcome of a call that must throw, when it returns."
  {:status :unexpected-success})

(defn failed-call
  "Call f (no arguments) that must throw (see outcome). Return
   (failure-of error), or unexpected-success when f returns."
  [failure-of f]
  (outcome failure-of f (constantly unexpected-success)))

(defn rejection
  "Return the outcome map {:exception :data :message :cause} of a call that
   threw error: the class name, the ex-data, the ex-message and the class
   name of the direct cause (nil when there is no cause)."
  [^Throwable error]
  {:exception (.getName (class error))
   :data (ex-data error)
   :message (ex-message error)
   :cause (some-> error ex-cause class .getName)})

(defn guarded-call
  "Call f (no arguments). Return (success value) of the value of f, or
   {:status :rejected} merged with the rejection map of the
   clojure.lang.ExceptionInfo that f throws."
  [f success]
  (try
    (success (f))
    (catch clojure.lang.ExceptionInfo thrown
      (assoc (rejection thrown) :status :rejected))))

(defn rejected-call
  "Call f (no arguments) that must throw (see guarded-call). Return
   unexpected-success when f returns."
  [f]
  (guarded-call f (constantly unexpected-success)))

(defn jvm-facts
  "Return the facts of this JVM: {:runtime :flags}. :runtime is the
   java.version property and :flags the JVM input arguments."
  []
  {:runtime (System/getProperty "java.version")
   :flags (vec (.getInputArguments
                (java.lang.management.ManagementFactory/getRuntimeMXBean)))})

(def policy-property
  "Name of the system property that sets the fallback policy."
  "value-type.fallback")

(defn invalid-policy-message
  "Return the message of the IllegalArgumentException of the invalid
   policy-property value policy."
  [policy]
  (str "Invalid value-type.fallback: " (pr-str policy)
       "; expected silent, warn or error"))

(defn runtime-facts
  "Return the runtime facts of this JVM after the requires of require-order:
   {:runtime :policy :flags :require-order}. :runtime and :flags are from
   jvm-facts, :policy is the policy-property value."
  [require-order]
  (assoc (jvm-facts)
         :policy (System/getProperty policy-property)
         :require-order require-order))

(defn class-name
  "Return the binary class name of simple-name in ns-or-name (a namespace or
   its name): the munged namespace name, \".\" and simple-name."
  [ns-or-name simple-name]
  (str (namespace-munge ns-or-name) "." simple-name))

(defn hash-field-count
  "Return the count of fields named __hash or __hasheq that Class c
   declares."
  [^Class c]
  (count (filter #(#{"__hash" "__hasheq"} (.getName ^java.lang.reflect.Field %))
                 (.getDeclaredFields c))))

(defn meta-rejected?
  "For a record-shaped Class c with one declared field that accepts 42 (the
   transfer and AOT fixtures), return true when the meta-and-extmap
   constructor rejects a non-nil __meta with IllegalArgumentException (in
   the cause chain). Find that constructor by parameter count 3 and call it
   with [42 {:m 1} nil]. For a plain-shaped c, or a c with another field
   count, return nil."
  [^Class c]
  (when (some #(= 3 (alength (.getParameterTypes ^java.lang.reflect.Constructor %)))
              (.getConstructors c))
    (try
      (Reflector/invokeConstructor c (object-array [42 {:m 1} nil]))
      false
      (catch Throwable thrown
        (boolean (some #(instance? IllegalArgumentException %)
                       (chain thrown)))))))

(defn factory-symbol
  "Return the factory symbol ->simple-name; with ns-or-name (a namespace or
   its name), qualified by its name."
  ([simple-name]
   (symbol (str "->" simple-name)))
  ([ns-or-name simple-name]
   (symbol (str ns-or-name) (str "->" simple-name))))

(defn absent?
  "Return true only when simple-name does not resolve in ns-or-name
   (import), ->simple-name does not resolve in ns-or-name (factory), and
   binary-name does not load through RT/baseLoader. binary-name defaults to
   (class-name ns-or-name simple-name)."
  ([ns-or-name simple-name]
   (absent? ns-or-name simple-name (class-name (the-ns ns-or-name) simple-name)))
  ([ns-or-name simple-name binary-name]
   (let [n (the-ns ns-or-name)]
     (and (nil? (ns-resolve n (symbol (str simple-name))))
          (nil? (ns-resolve n (factory-symbol simple-name)))
          (try
            (Class/forName (str binary-name) false (RT/baseLoader))
            false
            (catch ClassNotFoundException _ true))))))

(defn failed-definition
  "Return the failure map of a definition of simple-name in ns-or-name (a
   namespace or its name) that threw error: the failure map of error (from
   failure-of: thrown, failure or root-failure; default thrown) with
   :absent? (see absent?)."
  ([^Throwable error ns-or-name simple-name]
   (failed-definition thrown error ns-or-name simple-name))
  ([failure-of ^Throwable error ns-or-name simple-name]
   (assoc (failure-of error) :absent? (absent? ns-or-name simple-name))))

(def new-definition
  "The `defined` map of a new Class with factory identity."
  {:new-class? true :factory? true})

(defn defined
  "Return the definition map {:new-class? :factory?} of a successful
   definition of simple-name in ns-or-name that gave Class c. :factory? is
   true only when simple-name resolves to c and (->simple-name & args)
   gives an instance of exactly c. Call it once for each definition."
  [ns-or-name simple-name c & args]
  (let [n (the-ns ns-or-name)
        factory (ns-resolve n (factory-symbol simple-name))]
    {:new-class? (new-class! c)
     :factory? (boolean
                (and (identical? c (ns-resolve n (symbol (str simple-name))))
                     factory
                     (try (identical? c (class (apply factory args)))
                          (catch Throwable _ false))))}))
