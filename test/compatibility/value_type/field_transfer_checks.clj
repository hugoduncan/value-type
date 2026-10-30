(ns value-type.field-transfer-checks
  (:require [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.runtime :as runtime]))

(defn produce
  "Serialize public definitions with native-eligible or generated-only tags."
  ([] (produce 'Long))
  ([tag]
   (mapv (fn [expand]
           (binding [*print-meta* true]
             (pr-str (expand (list 'value-type.core/defvaluetype 'TransferredField
                                   [(with-meta 'value {:tag tag :non-nil true})])))))
         [macroexpand macroexpand-1])))

(defn- attempt
  "Return :unexpected-success, else the root failure (checks/failure) with
   its ex-data and whether both transferred owners are absent."
  [operation expansion]
  (try
    (operation expansion)
    :unexpected-success
    (catch Throwable error
      (let [root (loop [cause error]
                   (if-let [nested (ex-cause cause)] (recur nested) cause))]
        (assoc (checks/failure error)
               :data (ex-data root)
               :absent? (and (nil? (ns-resolve *ns* 'TransferredField))
                             (nil? (ns-resolve *ns* 'TransferredMixed))))))))

(defn consume
  "Receive public expansions twice through analysis and evaluation paths.
   Give the attempts per mode and the receiver :capability."
  [expansions]
  (let [operations {:eval eval
                    :source #(binding [*print-meta* true]
                               (load-string (pr-str %)))
                    :analysis #(eval (list 'fn [] %))}]
    (reduce-kv
     (fn [results mode operation]
       (assoc results mode
              (mapv (fn [serialized]
                      (let [expansion (read-string serialized)]
                        (mapv (fn [_] (attempt operation expansion)) (range 2))))
                    expansions)))
     {:capability (runtime/capability)} operations)))

(defn consume-supported
  "Repeat one transfer case in a fresh receiver and check each installed Class."
  [serialized mode]
  (let [operation (case mode
                    :eval eval
                    :analysis #(let [definition (eval (list 'fn [] %))]
                                 (definition))
                    :source #(binding [*print-meta* true]
                               (load-string (pr-str %))))]
    {:initially-absent? (and (nil? (ns-resolve *ns* 'TransferredField))
                             (nil? (ns-resolve *ns* '->TransferredField)))
     :attempts
     (loop [previous nil results []]
       (if (= 2 (count results))
         results
         (let [installed (operation (read-string serialized))
               factory (ns-resolve *ns* '->TransferredField)
               direct (eval '(fn [input] (new TransferredField input)))
               constructor (.getConstructor ^Class installed (into-array Class [Object]))
               reflective (fn [input]
                            (try
                              (.newInstance constructor (object-array [input]))
                              (catch java.lang.reflect.InvocationTargetException error
                                (throw (.getCause error)))))
               field (.getField ^Class installed "value")
               string? (= String (.getType field))
               input (if string? "valid" 42)
               result {:new-class? (and (class? installed)
                                        (not (identical? previous installed)))
                       :identity? (not= (factory input) (factory input))
                       :native? (boolean (some #(= "jdk.internal.vm.annotation.NullRestricted"
                                                   (.getName (.annotationType %)))
                                               (.getDeclaredAnnotations field)))
                       :type (.getName (.getType field))
                       :construction
                       (mapv (fn [construct]
                               (let [value (construct input)]
                                 {:class? (identical? installed (class value))
                                  :field (.get field value)
                                  :nil (try (construct nil) :accepted
                                            (catch NullPointerException _ :rejected))
                                  :wrong (try (construct (Object.)) :accepted
                                              (catch ClassCastException _ :rejected))}))
                             [factory direct reflective])}]
           (recur installed (conj results result)))))}))

(defn produce-mixed
  "Serialize a public definition with native and generated constraints."
  []
  (mapv (fn [expand]
          (binding [*print-meta* true]
            (pr-str (expand '(value-type.core/defvaluetype TransferredMixed
                               [^Long ^:non-nil number ^String ^:non-nil text])))))
        [macroexpand macroexpand-1]))

(defn consume-mixed
  "Repeat mixed installation and check each Class, constructor and constrained slot."
  [serialized mode]
  (loop [previous nil results []]
    (if (= 2 (count results))
      results
      (let [expansion (read-string serialized)
            installed (case mode
                        :eval (eval expansion)
                        :analysis ((eval (list 'fn [] expansion)))
                        :source (load-string serialized))
            factory (ns-resolve *ns* '->TransferredMixed)
            direct (eval '(fn [number text] (new TransferredMixed number text)))
            constructor (.getConstructor ^Class installed (into-array Class [Object Object]))
            reflective (fn [number text]
                         (try
                           (.newInstance constructor (object-array [number text]))
                           (catch java.lang.reflect.InvocationTargetException error
                             (throw (.getCause error)))))
            fields (mapv #(.getField ^Class installed %) ["number" "text"])]
        (recur installed
               (conj results
                     {:new-class? (and (class? installed)
                                       (not (identical? previous installed)))
                      :types (mapv #(.getName (.getType ^java.lang.reflect.Field %)) fields)
                      :native (mapv (fn [^java.lang.reflect.Field field]
                                      (boolean (some #(= "jdk.internal.vm.annotation.NullRestricted"
                                                         (.getName (.annotationType %)))
                                                     (.getDeclaredAnnotations field)))) fields)
                      :construction
                      (mapv (fn [construct]
                              (let [value (construct 42 "valid")]
                                {:class? (identical? installed (class value))
                                 :fields (mapv #(.get ^java.lang.reflect.Field % value) fields)
                                 :nil (mapv (fn [arguments]
                                              (try (apply construct arguments) :accepted
                                                   (catch NullPointerException _ :rejected)))
                                            [[nil "valid"] [42 nil]])
                                 :wrong (mapv (fn [arguments]
                                                (try (apply construct arguments) :accepted
                                                     (catch ClassCastException _ :rejected)))
                                              [[(Object.) "valid"] [42 (Object.)]])}))
                            [factory direct reflective])}))))))
