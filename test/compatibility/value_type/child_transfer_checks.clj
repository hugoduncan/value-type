(ns value-type.child-transfer-checks
  "Baseline-safe checks of serialized parent expansions against a receiver
   child. Checks evaluate in the current namespace and return inspected facts;
   a failure gives the root failure class and message
   (consumer-aot-checks/failure)."
  (:require [value-type.consumer-aot-checks :as checks]
            [value-type.core])
  (:import (java.lang.reflect Field Modifier)))

(defn- define-child
  "Define TransferChild with fields; a value declaration when native?."
  [native? fields]
  (eval (list (if native? 'value-type.core/defvaluetype 'deftype)
              'TransferChild fields)))

(defn- evaluate
  "Evaluate serialized in mode :eval, :source or :analysis. Return the result."
  [serialized mode]
  (let [expansion (read-string serialized)]
    (case mode
      :eval (eval expansion)
      :source (load-string serialized)
      :analysis ((eval (list 'fn [] expansion))))))

(defn produce
  "Expand a parent against the producer's child definition. Return the
   serialized expansions of macroexpand and macroexpand-1."
  ([] (produce false))
  ([native?]
   (define-child native? '[producer])
   (mapv (fn [expand]
           (binding [*print-meta* true]
             (pr-str
              (expand '(value-type.core/defvaluetype TransferParent
                         [^TransferChild ^:non-nil child])))))
         [macroexpand macroexpand-1])))

(defn consume
  "Resolve the transferred constraint against a different receiver child.

   Return the child field names, exact-class?, value?, final? and annotation
   names of the child field and, per construction (direct, factory,
   reflective), retained? of a valid child and the outcomes of nil and Object."
  ([serialized mode] (consume serialized mode false))
  ([serialized mode native?]
   (let [^Class child (define-child native? '[receiver replacement])
         input ((ns-resolve *ns* '->TransferChild) :receiver :replacement)
         ^Class owner (evaluate serialized mode)
         ^Field field (.getField owner "child")]
     {:child-fields (sort (map #(.getName ^Field %) (.getFields child)))
      :exact-class? (identical? child (.getType field))
      :value? (checks/value-class? owner)
      :final? (Modifier/isFinal (.getModifiers field))
      :annotations (checks/annotation-names field)
      :constructions
      (mapv (fn [build]
              {:retained? (identical? input (.get field (build [input])))
               :rejections [(checks/outcome #(build [nil]))
                            (checks/outcome #(build [(Object.)]))]})
            (checks/builders (str (ns-name *ns*)) owner))})))

(defn reject-replaced-child
  "Install a receiver-local replacement child, then evaluate serialized twice.

   Per evaluation, return the root failure (or :accepted) and whether the
   child is retained and the owner and factory are absent."
  [serialized mode]
  (let [child (define-child false '[receiver replacement])]
    (mapv
     (fn [_]
       {:outcome (try (evaluate serialized mode) :accepted
                      (catch Throwable thrown (checks/failure thrown)))
        :child-retained? (identical? child (resolve 'TransferChild))
        :owner-absent? (nil? (resolve 'TransferParent))
        :factory-absent? (nil? (resolve '->TransferParent))})
     [0 1])))
