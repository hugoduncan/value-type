(ns value-type.consumer-aot-checks
  "Baseline-safe inspection of saved classes in source-free consumer JVMs.

   A spec map names the saved class and its checked fields:
   :source     producer source resource that must be absent
   :ns         producer namespace name (string)
   :owner      saved class name (string)
   :checked    checked field names; Java consumers inspect the first
   :arguments  fn from checked values to constructor arguments
   :valid      checked values that construct
   :invalid    checked value vectors that must be rejected (see
               `invalid-arguments`)"
  (:require [clojure.java.io :as io]
            [value-type.cell-registry :as registry])
  (:import (java.lang.annotation Annotation)
           (java.lang.reflect Constructor Field InvocationTargetException Modifier)))

(defn value-class?
  "Return true when owner is a value class; false where Class/isValue is
   absent."
  [^Class owner]
  (if-let [method (try (.getMethod Class "isValue" (make-array Class 0))
                       (catch NoSuchMethodException _ nil))]
    (boolean (.invoke method owner (object-array 0)))
    false))

(defn source-absent?
  "Return true when the resource of name (a source path) is not on the
   classpath."
  [resource-name]
  (nil? (io/resource resource-name)))

(defn annotation-names
  "Return the declared annotation class names of field."
  [^Field field]
  (mapv #(.getName (.annotationType ^Annotation %)) (.getDeclaredAnnotations field)))

(defn field-facts
  "Return the facts of a checked field: :type (class name), :final? and
   :annotations (annotation-names)."
  [^Field field]
  {:type (.getName (.getType field))
   :final? (Modifier/isFinal (.getModifiers field))
   :annotations (annotation-names field)})

(defn- cause
  "Return the target of a reflective invocation failure, else thrown."
  [^Throwable thrown]
  (if (instance? InvocationTargetException thrown) (.getCause thrown) thrown))

(defn outcome
  "Return :accepted, or the class name of the failure of f (the target of a
   reflective invocation failure)."
  [f]
  (registry/outcome #(.getName (class (cause %))) f))

(def checked-rejections
  "The expected constructor rejections of a non-nil checked field: the
   class names for nil, then for a value of a wrong type."
  ["java.lang.NullPointerException" "java.lang.ClassCastException"])

(defn checked-outcomes
  "Return the outcomes of build (a construction, see `builders`) for nil,
   then for wrong, as `checked-rejections` orders them."
  [build wrong]
  [(outcome #(build [nil])) (outcome #(build [wrong]))])

(defn invalid-arguments
  "Return the invalid checked value vectors of valid and wrongs (one wrong
   value for each checked field): for each position, valid with nil, then
   valid with the wrong value, as `checked-rejections` orders them."
  [valid wrongs]
  (vec (mapcat (fn [i wrong] [(assoc valid i nil) (assoc valid i wrong)])
               (range) wrongs)))

(defn spec-rejections
  "Return the expected rejections of the :invalid vectors of spec:
   checked-rejections for each :checked field, as `invalid-arguments`
   orders them."
  [{:keys [checked]}]
  (into [] cat (registry/each checked checked-rejections)))

(def native-annotations
  "The declared annotation names of a native checked field."
  ["jdk.internal.vm.annotation.NullRestricted"])

(defn- instance-fields
  [^Class owner]
  (remove #(Modifier/isStatic (.getModifiers ^Field %)) (.getFields owner)))

(def constructions
  "The construction axis of builders, in the order of builders."
  [:direct :factory :reflective])

(defn each-construction
  "Return a vector with expected once for each construction."
  [expected]
  (registry/each constructions expected))

(defn builders
  "Return the constructions of owner (in the order of constructions), a
   type of ns-or-name (a namespace or its name). Each takes a vector of
   constructor arguments."
  [ns-or-name ^Class owner]
  (let [^Constructor constructor (first (.getConstructors owner))
        parameters (vec (repeatedly (alength (.getParameterTypes constructor)) gensym))
        direct (eval (list 'fn [parameters]
                           (list* 'new (symbol (.getName owner)) parameters)))
        factory @(resolve (registry/factory-symbol ns-or-name (.getSimpleName owner)))]
    [direct
     #(apply factory %)
     #(try (.newInstance constructor (object-array %))
           (catch InvocationTargetException thrown (throw (cause thrown))))]))

(defn named-builders
  "Return pairs of construction and build of owner (see builders), in the
   order of constructions."
  [ns-or-name ^Class owner]
  (mapv vector constructions (builders ns-or-name owner)))

(defn construction-results
  "Return, for each construction of owner (see builders), :stored (the
   value of field after construction with valid) and :rejections
   (checked-outcomes for wrong). Each construction takes [value]. Optional
   :facts maps the valid instance to a map merged into its entry."
  [ns-or-name ^Class owner ^Field field valid wrong & {:keys [facts] :or {facts (constantly {})}}]
  (mapv (fn [build]
          (let [instance (build [valid])]
            (merge {:stored (.get field instance)
                    :rejections (checked-outcomes build wrong)}
                   (facts instance))))
        (builders ns-or-name owner)))

(defn input-results
  "Return, for each construction of owner (see builders), :stored-input?
   (true when field holds input after construction with input) and
   :rejections (checked-outcomes for wrong). Each construction takes
   [value]."
  [ns-or-name ^Class owner ^Field field input wrong]
  (mapv (fn [build]
          {:stored-input? (identical? input (.get field (build [input])))
           :rejections (checked-outcomes build wrong)})
        (builders ns-or-name owner)))

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
  (let [absent? (source-absent? source)
        _ (require (symbol ns))
        owner-class (Class/forName owner)
        reads (into {} (map (juxt identity #(compiled-read owner-class %))) checked)]
    {:source-absent? absent?
     :value? (value-class? owner-class)
     :fields (into {}
                   (map (fn [field-name]
                          [field-name (field-facts (.getField owner-class field-name))]))
                   checked)
     :constructions
     (mapv (fn [build]
             (let [construct #(build (apply arguments %))
                   instance (construct valid)]
               {:equal? (= instance (construct valid))
                :stored (into {} (map (fn [^Field field]
                                        [(.getName field) (.get field instance)]))
                              (instance-fields owner-class))
                :read (into {} (map (fn [[field-name read]] [field-name (read instance)])) reads)
                :call ((.call ^java.util.concurrent.Callable instance))
                :rejections (mapv (fn [values] (outcome #(construct values))) invalid)}))
           (builders ns owner-class))}))

(defn holder-consumer-form
  "Return a child form whose value is the holder-consumer result for spec."
  [spec]
  (registry/check-form 'value-type.consumer-aot-checks/holder-consumer spec))

(def unchecked-stored
  "The stored values of the unchecked Holder fields that each :arguments of
   string-holder and of its variants give."
  {"count" 7 "weight" 2.5 "nullable" nil})

(defn holder-result
  "Return the expected holder-consumer result of spec: value?, fields (the
   field facts of each :checked field) and, for each construction (equal
   when value?), :stored and :read from :valid, call and spec-rejections."
  [spec fields value? call]
  (let [read (zipmap (:checked spec) (:valid spec))]
    {:source-absent? true :value? value? :fields fields
     :constructions (each-construction {:equal? value?
                                        :stored (merge unchecked-stored read)
                                        :read read
                                        :call call
                                        :rejections (spec-rejections spec)})}))

(defn java-consumer
  "Inspect the saved owner of spec, then call consumer/runChecks.

   Return source absence, value?, annotations of the first checked field and
   the runChecks outcome: :passed, or the failure class and message."
  [{:keys [source owner checked]} consumer]
  (let [absent? (source-absent? source)
        owner-class (Class/forName owner)]
    {:source-absent? absent?
     :value? (value-class? owner-class)
     :annotations (annotation-names (.getField owner-class (first checked)))
     :checks (try
               (.invoke (.getMethod (Class/forName consumer) "runChecks" (make-array Class 0))
                        nil (object-array 0))
               :passed
               (catch InvocationTargetException thrown
                 (let [target (cause thrown)]
                   {:class (.getName (class target)) :message (.getMessage target)})))}))

(defn java-consumer-form
  "Return a child form whose value is the java-consumer result for spec and
   consumer, the Java class with a static runChecks method."
  [spec consumer]
  (registry/check-form 'value-type.consumer-aot-checks/java-consumer spec consumer))

(defn java-consumer-result
  "Return the expected java-consumer result: source absent, value?, the
   annotations of the first checked field and :passed checks."
  [value? annotations]
  {:source-absent? true :value? value? :annotations annotations :checks :passed})

(defn incompatible-consumer
  "Load value-type.core, then load the saved owner of spec twice.

   Return source absence and each load outcome: :accepted or
   :unsupported-class-version. Other failures propagate."
  [{:keys [source owner]}]
  (require 'value-type.core)
  {:source-absent? (source-absent? source)
   :loads (registry/twice (fn []
                            (try (Class/forName owner) :accepted
                                 (catch UnsupportedClassVersionError _ :unsupported-class-version))))})

(defn incompatible-consumer-form
  "Return a child form whose value is the incompatible-consumer result
   for spec."
  [spec]
  (registry/check-form 'value-type.consumer-aot-checks/incompatible-consumer spec))

(defn strict-consumer
  "Require the saved namespace of spec with :reload twice.

   Return source absence and each load outcome: :accepted, or the root
   failure (cell-registry/failure) with :factory-bound?, true when the
   owner factory var is bound after the failure."
  [{:keys [source ns owner]}]
  (let [factory (registry/factory-symbol ns (last (.split ^String owner "\\.")))]
    {:source-absent? (source-absent? source)
     :loads (registry/twice
             #(registry/outcome
               (fn [thrown]
                 (assoc (registry/failure thrown)
                        :factory-bound? (boolean (when-let [v (resolve factory)]
                                                   (bound? v)))))
               (fn [] (require (symbol ns) :reload))))}))

(defn strict-consumer-form
  "Return a child form whose value is the strict-consumer result for spec."
  [spec]
  (registry/check-form 'value-type.consumer-aot-checks/strict-consumer spec))

(def incompatible-result
  "Expected incompatible-consumer result: each load rejects the class version."
  {:source-absent? true :loads (registry/each-attempt :unsupported-class-version)})

(def string-holder
  "Spec of saved checked.aot-fixture/Holder [required ^long count ^double weight
   nullable] with String values; other tests assoc their values."
  {:source "checked/aot_fixture.clj"
   :ns "checked.aot-fixture"
   :owner "checked.aot_fixture.Holder"
   :checked ["required"]
   :arguments '(fn [required] [required 7 2.5 nil])
   :valid ["valid"]
   :invalid (invalid-arguments ["valid"] [42])})

(def string-holder-fields
  "The expected checked field facts (field-facts) of string-holder."
  {"required" {:type "java.lang.String" :final? true :annotations []}})

(defn string-holder-result
  "Return the expected holder-consumer result of string-holder."
  [value?]
  (holder-result string-holder string-holder-fields value? "valid"))
