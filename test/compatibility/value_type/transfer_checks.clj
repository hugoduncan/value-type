(ns value-type.transfer-checks
  "Checks of serialized fixed fallback expansions in a receiving cell. Checks
   evaluate in the current namespace (the cell namespace)."
  (:require [value-type.cell-registry :as registry]
            [value-type.core]))

(def modes
  "Receiving modes of evaluate-form, in cell order."
  [:eval :analysis :source])

(def expands
  "Expand keywords, in cell order. Each names its clojure.core expander."
  [:macroexpand :macroexpand-1])

(def expanders
  "Map from an expand keyword to its expander function."
  (into {}
        (map (juxt identity #(deref (resolve (symbol "clojure.core" (name %))))))
        expands))

(defn each-expand
  "Return expected for each expand of expands, as a vector."
  [expected]
  (registry/each expands expected))

(defn serialize
  "Expand form with expand (:macroexpand or :macroexpand-1) in the namespace
   namespace-name (created when absent; refers clojure.core), after
   (prepare) in that namespace. Return the expansion printed, with metadata
   unless print-meta? is false."
  [namespace-name expand form & {:keys [print-meta? prepare]
                                 :or {print-meta? true prepare (fn [])}}]
  (binding [*ns* (create-ns (symbol namespace-name))]
    (refer 'clojure.core)
    (prepare)
    (binding [*print-meta* print-meta?]
      (pr-str ((expanders expand) form)))))

(defn evaluate-form
  "Evaluate form, an expansion, in the current namespace in mode, and return
   its value. :eval evaluates it, :source loads its text printed with
   metadata, :analysis evaluates a call of a function form that contains the
   expansion. The :analysis call runs in the LOADER frame of its eval: a call
   outside that frame imports through a loader that already resolved the
   class name, and the import of a redefinition stays stale."
  [form mode]
  (case mode
    :eval (eval form)
    :source (load-string (binding [*print-meta* true] (pr-str form)))
    :analysis (eval (list (list 'fn [] form)))))

(defn evaluate
  "Evaluate serialized, a printed expansion, in the current namespace in
   mode (see evaluate-form), and return its value. :source loads the text."
  [serialized mode]
  (if (= :source mode)
    (load-string serialized)
    (evaluate-form (read-string serialized) mode)))

(defn produce
  "Expand form once for each spec [namespace-name expand] (see serialize).
   Return the serialized expansions in spec order, printed with metadata
   when print-meta?. Each expansion has the class name of its namespace."
  [form print-meta? specs]
  (mapv (fn [[namespace-name expand]]
          (serialize namespace-name expand form :print-meta? print-meta?))
        specs))

(defn- attempt
  [n mode serialized]
  (registry/outcome #(registry/failed-definition % n 'Transferred)
                    #(evaluate serialized mode)
                    (fn [c]
                      (merge (eval '{:stored (.-x (->Transferred 42))
                                     :identity? (value-type.cell-registry/identity? ->Transferred 42)})
                             (registry/defined n 'Transferred c 42)))))

(defn consume
  "Compile the serialized expansion twice in the current namespace through
   mode :eval or :source (see evaluate). Return one result for each
   attempt: the definition map with :stored and :identity?, or
   registry/failed-definition."
  [serialized mode]
  (let [n *ns*]
    (registry/twice (fn [] (attempt n mode serialized)))))
