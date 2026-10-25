(ns hooks.value-type
  (:require [clj-kondo.hooks-api :as api]
            [clojure.walk :as walk]))

(defn with-temp-dir
  "Analyze the directory binding and body without filesystem operations."
  [{:keys [node]}]
  (let [[_ bindings & body] (:children node)
        binding-node (first (:children bindings))]
    {:node (api/list-node
            (list* (api/token-node 'let)
                   (api/vector-node [binding-node (api/token-node nil)])
                   body))}))

(defn defpair
  "Analyze both benchmark types with their implicit x and y fields."
  [{:keys [node]}]
  (let [[_ dt vt & specs] (:children node)
        type-form (fn [type-name]
                    (api/list-node
                     (list* (api/token-node 'deftype)
                            type-name
                            (api/vector-node [(api/token-node 'x)
                                              (api/token-node 'y)])
                            specs)))]
    {:node (api/list-node [(api/token-node 'do)
                           (type-form dt)
                           (type-form vt)])}))

(defn- substitute-type
  [type-name expression]
  (walk/postwalk
   (fn [form]
     (let [replaced (cond
                      (= 'T form) type-name
                      (= 'T. form) (symbol (str type-name "."))
                      :else form)]
       (if (= 'T (:tag (meta form)))
         (vary-meta replaced assoc :tag type-name)
         replaced)))
   expression))

(defn defpass
  "Analyze generated pass functions and their implicit element bindings."
  [{:keys [node]}]
  (let [[_ value & fns] (:children node)
        expression (api/sexpr value)
        pair? (some #{'e2} (tree-seq coll? seq expression))
        function-form
        (fn [[fname type-node]]
          (let [type-name (api/sexpr type-node)
                body (substitute-type type-name expression)]
            (api/coerce
             (list 'defn (api/sexpr fname)
                   (if pair? '[a b] '[a])
                   (list 'let (if pair?
                                '[e (aget a 0) e2 (aget b 0)]
                                '[e (aget a 0)])
                         body)))))]
    {:node (api/list-node
            (into [(api/token-node 'do)]
                  (map function-form)
                  (partition 2 fns)))}))
