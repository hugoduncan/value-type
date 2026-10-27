(ns value-type.field-transfer-checks
  (:require [value-type.core]))

(defn produce
  "Serialize enabled public definitions through both expansion entry points."
  []
  (mapv (fn [expand]
          (binding [*print-meta* true]
            (pr-str (expand '(value-type.core/defvaluetype TransferredField
                               [^Long ^:nil-restricted value])))))
        [macroexpand macroexpand-1]))

(defn- attempt
  [operation expansion]
  (try
    (operation expansion)
    :unexpected-success
    (catch Throwable error
      (loop [cause error]
        (if-let [nested (ex-cause cause)]
          (recur nested)
          {:data (ex-data cause)
           :absent? (nil? (ns-resolve *ns* 'TransferredField))})))))

(defn consume
  "Receive public expansions twice through analysis and evaluation paths."
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
     {} operations)))

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
               value (factory 42)
               result {:new-class? (and (class? installed)
                                        (not (identical? previous installed)))
                       :factory-class? (identical? installed (class value))
                       :field (clojure.lang.Reflector/getInstanceField value "value")
                       :type (.getName (.getType (.getField (class value) "value")))
                       :nil (try (factory nil) :accepted
                                 (catch NullPointerException _ :rejected))
                       :wrong (try (factory "wrong") :accepted
                                   (catch ClassCastException _ :rejected))}]
           (recur installed (conj results result)))))}))
