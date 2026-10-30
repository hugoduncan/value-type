(ns value-type.consumer-aot-checks
  "Baseline-safe inspection of saved classes in source-free consumer JVMs.

   A spec map names the saved class and its checked fields:
   :source     producer source resource that must be absent
   :ns         producer namespace name (string)
   :owner      saved class name (string)
   :checked    checked field names; Java consumers inspect the first
   :arguments  fn from checked values to constructor arguments
   :valid      checked values that construct
   :invalid    checked value vectors that must be rejected"
  (:require [clojure.java.io :as io]
            [value-type.message-checks :refer [message-names?]])
  (:import (java.lang.annotation Annotation)
           (java.lang.reflect Constructor Field InvocationTargetException Modifier)))

(defn value-class?
  "Return true when owner is a value class; false where Class/isValue is absent."
  [^Class owner]
  (if-let [method (try (.getMethod Class "isValue" (make-array Class 0))
                       (catch NoSuchMethodException _ nil))]
    (boolean (.invoke method owner (object-array 0)))
    false))

(defn annotation-names
  "Return the declared annotation class names of field."
  [^Field field]
  (mapv #(.getName (.annotationType ^Annotation %)) (.getDeclaredAnnotations field)))

(defn- cause
  "Return the target of a reflective invocation failure, else failure."
  [^Throwable failure]
  (if (instance? InvocationTargetException failure) (.getCause failure) failure))

(defn outcome
  "Return :accepted, or the class name of the failure of f (the target of a
   reflective invocation failure)."
  [f]
  (try (f) :accepted
       (catch Throwable failure (.getName (class (cause failure))))))

(defn failure
  "Return the class name and message of the root cause of failure."
  [^Throwable failure]
  (let [^Throwable root (loop [^Throwable cause failure]
                          (if-let [nested (.getCause cause)] (recur nested) cause))]
    {:class (.getName (class root)) :message (.getMessage root)}))

(defn policy-rejection
  "Return the expected failure of strict policy for context: the capability
   detail or the fixed fallback artifact."
  [context]
  {:class "java.lang.Error"
   :message (str "value-type: identity deftype fallback: " context)})

(def fallback-rejection
  "Expected failure of strict policy for a fixed fallback representation."
  (policy-rejection "fixed fallback representation"))

(defn unavailable-failure
  "Return the expected `:value-capability-unavailable` failure for class-name
   on a receiver with capability, without `:absent?` and `:message`.
   Error text is not contractual; see `unavailable-message?`."
  [class-name {:keys [reason]}]
  {:class "clojure.lang.ExceptionInfo"
   :data {:value-type/error :value-capability-unavailable
          :class-name class-name
          :reason reason}})

(defn unavailable-message?
  "True when the `:message` of failure names class-name, the cause and the
   fix. Both receivers without capability (no value classes, preview off)
   need `--enable-preview`."
  [class-name failure]
  (message-names? (:message failure)
                  [class-name "fixed value representation is unavailable"
                   "--enable-preview"]))

(defn- instance-fields
  [^Class owner]
  (remove #(Modifier/isStatic (.getModifiers ^Field %)) (.getFields owner)))

(defn builders
  "Return the direct, factory and reflective constructions of owner. Each
   takes a vector of constructor arguments."
  [ns-name ^Class owner]
  (let [^Constructor constructor (first (.getConstructors owner))
        parameters (vec (repeatedly (alength (.getParameterTypes constructor)) gensym))
        direct (eval (list 'fn [parameters]
                           (list* 'new (symbol (.getName owner)) parameters)))
        factory @(resolve (symbol ns-name (str "->" (.getSimpleName owner))))]
    [direct
     #(apply factory %)
     #(try (.newInstance constructor (object-array %))
           (catch InvocationTargetException failure (throw (cause failure))))]))

(defn- compiled-read
  "Return a compiled type-hinted read of field-name on owner."
  [^Class owner field-name]
  (let [instance (gensym "instance")]
    (eval (list 'fn [instance]
                (list '. (with-meta instance {:tag (symbol (.getName owner))})
                      (symbol (str "-" field-name)))))))

(defn holder-consumer
  "Require the saved namespace of spec and inspect its owner class.

   Return source absence, value?, checked field facts and, per construction
   entry (direct, factory, reflective), the stored values, compiled reads,
   Callable result, equality of two valid instances and invalid outcomes."
  [{:keys [source ns owner checked arguments valid invalid]}]
  (let [source-absent? (nil? (io/resource source))
        _ (require (symbol ns))
        owner (Class/forName owner)
        reads (into {} (map (juxt identity #(compiled-read owner %))) checked)]
    {:source-absent? source-absent?
     :value? (value-class? owner)
     :fields (into {}
                   (map (fn [field-name]
                          (let [field (.getField owner field-name)]
                            [field-name {:type (.getName (.getType field))
                                         :final? (Modifier/isFinal (.getModifiers field))
                                         :annotations (annotation-names field)}])))
                   checked)
     :constructions
     (mapv (fn [build]
             (let [construct #(build (apply arguments %))
                   instance (construct valid)]
               {:equal? (= instance (construct valid))
                :stored (into {} (map (fn [^Field field]
                                        [(.getName field) (.get field instance)]))
                              (instance-fields owner))
                :read (into {} (map (fn [[field-name read]] [field-name (read instance)])) reads)
                :call ((.call ^java.util.concurrent.Callable instance))
                :rejections (mapv (fn [values] (outcome #(construct values))) invalid)}))
           (builders ns owner))}))

(defn holder-consumer-form
  "Return a child form that prints the holder-consumer result for spec."
  [spec]
  (list 'do
        '(require 'value-type.consumer-aot-checks)
        (list 'prn (list 'value-type.consumer-aot-checks/holder-consumer spec))))

(defn holder-result
  "Return the expected holder-consumer result; each construction is equal."
  [value? fields construction]
  {:source-absent? true :value? value? :fields fields
   :constructions (vec (repeat 3 (assoc construction :equal? value?)))})

(defn java-consumer
  "Inspect the saved owner of spec, then call consumer/runChecks.

   Return source absence, value?, annotations of the first checked field and
   the runChecks outcome: :passed, or the failure class and message."
  [{:keys [source owner checked]} consumer]
  (let [source-absent? (nil? (io/resource source))
        owner (Class/forName owner)]
    {:source-absent? source-absent?
     :value? (value-class? owner)
     :annotations (annotation-names (.getField owner (first checked)))
     :checks (try
               (.invoke (.getMethod (Class/forName consumer) "runChecks" (make-array Class 0))
                        nil (object-array 0))
               :passed
               (catch InvocationTargetException failure
                 (let [failure (cause failure)]
                   {:class (.getName (class failure)) :message (.getMessage failure)})))}))

(defn java-consumer-form
  "Return a child form that prints the java-consumer result for spec and
   consumer, the Java class with a static runChecks method.

   Optional :requires names namespaces loaded first; optional :wrap maps the
   java-consumer call form to the evaluated form."
  [spec consumer & {:keys [requires wrap] :or {wrap identity}}]
  (list 'do
        (list* 'require ''value-type.consumer-aot-checks
               (map #(list 'quote %) requires))
        (list 'prn (wrap (list 'value-type.consumer-aot-checks/java-consumer
                               spec consumer)))))

(defn incompatible-consumer
  "Load value-type.core, then load the saved owner of spec twice.

   Return source absence and each load outcome: :accepted or
   :unsupported-class-version. Other failures propagate."
  [{:keys [source owner]}]
  (require 'value-type.core)
  {:source-absent? (nil? (io/resource source))
   :loads (mapv (fn [_]
                  (try (Class/forName owner) :accepted
                       (catch UnsupportedClassVersionError _ :unsupported-class-version)))
                (range 2))})

(defn incompatible-consumer-form
  "Return a child form that prints the incompatible-consumer result for spec."
  [spec]
  (list 'do
        '(require 'value-type.consumer-aot-checks)
        (list 'prn (list 'value-type.consumer-aot-checks/incompatible-consumer spec))))

(defn strict-consumer
  "Require the saved namespace of spec with :reload twice.

   Return source absence and each load outcome: :accepted, or the root
   failure (failure) with :factory-bound?, true when the owner factory var is
   bound after the failure."
  [{:keys [source ns owner]}]
  (let [factory (symbol ns (str "->" (last (.split ^String owner "\\."))))]
    {:source-absent? (nil? (io/resource source))
     :loads (mapv (fn [_]
                    (try (require (symbol ns) :reload) :accepted
                         (catch Throwable thrown
                           (assoc (failure thrown)
                                  :factory-bound? (boolean
                                                   (when-let [v (resolve factory)]
                                                     (bound? v)))))))
                  (range 2))}))

(defn strict-consumer-form
  "Return a child form that prints the strict-consumer result for spec."
  [spec]
  (list 'do
        '(require 'value-type.consumer-aot-checks)
        (list 'prn (list 'value-type.consumer-aot-checks/strict-consumer spec))))

(def incompatible-result
  "Expected incompatible-consumer result: both loads reject the class version."
  {:source-absent? true :loads [:unsupported-class-version :unsupported-class-version]})

(def string-holder
  "Spec of saved checked.aot-fixture/Holder [required ^long count ^double weight
   nullable] with String values; other tests assoc their values."
  {:source "checked/aot_fixture.clj"
   :ns "checked.aot-fixture"
   :owner "checked.aot_fixture.Holder"
   :checked ["required"]
   :arguments '(fn [required] [required 7 2.5 nil])
   :valid ["valid"]
   :invalid [[nil] [42]]})

(defn string-holder-result
  "Return the expected holder-consumer result of string-holder."
  [value?]
  (holder-result value?
                 {"required" {:type "java.lang.String" :final? true :annotations []}}
                 {:stored {"required" "valid" "count" 7 "weight" 2.5 "nullable" nil}
                  :read {"required" "valid"}
                  :call "valid"
                  :rejections ["java.lang.NullPointerException"
                               "java.lang.ClassCastException"]}))
