(require '[clojure.java.io :as io])

(defn check-resource-origins!
  []
  (doseq [path ["value_type/core.clj" "value_type/array.clj"
                "value_type/impl/array.clj"]
          :let [resource (io/resource path)]]
    (assert (= "jar" (.getProtocol resource)))
    (assert (= (.getCanonicalFile (io/file (System/getProperty "consumer.jar")))
               (.getCanonicalFile
                (io/file (.toURI (.getJarFileURL (.openConnection resource)))))))))

(check-resource-origins!)

(require '[value-type.core :refer [defvaluetype]]
         '[value-type.array :as va])

(defn check-unsupported-diagnostics!
  []
  (doseq [input [nil "not-array" (int-array 0)]]
    (assert (= :invalid-array
               (try
                 (va/array-info input)
                 :unexpected-success
                 (catch clojure.lang.ExceptionInfo e
                   (:value-type/error (ex-data e)))))))
  (dotimes [_ 2]
    (if (Boolean/getBoolean "consumer.inspection")
      (assert (= {:component-class Object
                  :component-value? false
                  :null-restricted? false
                  :atomic? true :flat? false}
                 (va/array-info (object-array 0))))
      (assert (= :array-inspection-unavailable
                 (try
                   (va/array-info (object-array 0))
                   :unexpected-success
                   (catch clojure.lang.ExceptionInfo e
                     (:value-type/error (ex-data e))))))))
  :checked)

(defn unsupported-allocation-results
  []
  (mapv (fn [_]
          (try
            (va/make-array nil -1 nil)
            :unexpected-success
            (catch clojure.lang.ExceptionInfo e
              (:value-type/error (ex-data e)))))
        (range 2)))

(defn unsupported-sequence-results
  []
  (mapv (fn [explicit?]
          (mapv (fn [_]
                  (let [effects (atom [])
                        input (lazy-seq
                               (swap! effects conj :consumed)
                               (list nil))
                        error (try
                                (if explicit?
                                  (va/into-array nil nil input)
                                  (va/into-array nil input))
                                :unexpected-success
                                (catch clojure.lang.ExceptionInfo e
                                  (:value-type/error (ex-data e))))]
                    {:error error :effects @effects}))
                (range 2)))
        [false true]))

;; Controls must run before any value-type creation or supported operation.
(def diagnostic-result
  (when-not (Boolean/getBoolean "consumer.arrays")
    (check-unsupported-diagnostics!)))
(def array-result
  (when-not (Boolean/getBoolean "consumer.arrays")
    (unsupported-allocation-results)))
(def sequence-result
  (when-not (Boolean/getBoolean "consumer.arrays")
    (unsupported-sequence-results)))

(defvaluetype Point [^long x ^long y])

(defn sequence-results
  [a b]
  (mapv (fn [[values expected]]
          {:component? (= Point (.getComponentType (class values)))
           :length (alength values)
           :contents? (= expected (vec values))})
        [[(va/into-array Point (list b a b)) [b a b]]
         [(va/into-array Point a [b a b]) [b a b]]
         [(va/into-array Point a []) []]]))

(defn check-supported-diagnostics!
  [values]
  (let [ordinary-info (va/array-info (object-array 0))
        restricted-info (va/array-info values)]
    (assert (= {:component-class Object
                :component-value? false
                :null-restricted? false
                :atomic? true :flat? false}
               ordinary-info))
    (assert (= #{:component-class :component-value?
                 :null-restricted? :atomic? :flat?}
               (set (keys restricted-info))))
    (assert (= Point (:component-class restricted-info)))
    (assert (every? true? (mapv restricted-info
                                [:component-value? :null-restricted? :atomic?])))
    (assert (boolean? (:flat? restricted-info)))))

(defn allocation-results
  [a b]
  (let [values (va/make-array Point 3 a)
        initial? (= [a a a] (vec values))
        _ (aset values 1 b)
        rejected (mapv
                  (fn [value]
                    (try
                      (aset values 0 value)
                      :unexpected-success
                      (catch NullPointerException _ :null)
                      (catch ArrayStoreException _ :type)))
                  [nil "wrong"])]
    (check-supported-diagnostics! values)
    {:component? (= Point (.getComponentType (class values)))
     :diagnostics? true
     :length (alength values)
     :initial? initial?
     :stored? (= b (aget values 1) (java.lang.reflect.Array/get values 1))
     :rejected rejected
     :contents? (= [a b a] (vec values))}))

(let [a (Point. 3 7)
      b (Point. 11 13)]
  (prn {:value-class? (when-let [predicate (first (filterv #(= "isValue" (.getName %))
                                                           (.getMethods Class)))]
                        (.invoke predicate (class a) (object-array 0)))
        :equal-copy? (= a (->Point 3 7))
        :factory-class? (= Point (class (->Point 3 7)))
        :fields [(.-x a) (.-y a) (.-x b) (.-y b)]
        :diagnostic-control diagnostic-result
        :sequence-control sequence-result
        :sequences (when (Boolean/getBoolean "consumer.arrays")
                     (sequence-results a b))
        :arrays (if (Boolean/getBoolean "consumer.arrays")
                  (allocation-results a b)
                  array-result)}))
