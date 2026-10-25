(ns value-type.transfer-checks
  (:require [value-type.core]))

(defn produce
  "Return serialized production expansions from both expansion entry points."
  []
  (mapv (fn [expand]
          (pr-str (expand '(value-type.core/defvaluetype Transferred [x]))))
        [macroexpand macroexpand-1]))

(defn- absent?
  []
  (and (nil? (ns-resolve *ns* 'Transferred))
       (try
         (Class/forName (str (munge (str (ns-name *ns*))) ".Transferred")
                        false (clojure.lang.RT/baseLoader))
         false
         (catch ClassNotFoundException _ true))))

(defn- attempt
  [operation expansion]
  (try
    (operation expansion)
    (eval '(let [a (->Transferred 42) b (->Transferred 42)]
             {:field (.-x a) :identity? (not= a b)
              :factory? (= Transferred (class a))}))
    (catch Throwable error
      {:absent? (absent?)
       :chain (loop [cause error names []]
                (let [names (conj names (.getName (class cause)))]
                  (if-let [next-cause (.getCause cause)]
                    (recur next-cause names)
                    names)))})))

(defn consume
  "Compile transferred expansions through eval and source loading, twice each."
  [expansions]
  (into {}
        (for [[mode operation] [[:eval eval]
                                [:source #(load-string (pr-str %))]]]
          [mode
           (mapv (fn [index serialized]
                   (let [target (create-ns (symbol (str "transfer." (name mode) index)))]
                     (binding [*ns* target]
                       (clojure.core/refer 'clojure.core)
                       (mapv (fn [_] (attempt operation (read-string serialized)))
                             (range 2)))))
                 (range) expansions)])))
