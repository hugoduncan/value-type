(ns value-type.deftype-star-record-test
  "Integration tests for record-shaped and plain `defvaluetype*` forms in
  both representations: value output (`vt/defvaluetype*` on a capable JVM)
  and identity output (`runtime/guarded-deftype*`, the fallback expansion).
  Record forms implement the interfaces that record literals, record
  constants and metadata need; hash methods compute from entries."
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.core :as vt]
   [value-type.impl.capability :as capability]
   [value-type.impl.runtime :as runtime]
   [value-type.test-support :refer [record-tail]])
  (:import
   (java.lang.reflect Field Modifier)))

(set! *warn-on-reflection* true)

(defn- entries-form
  "Form that builds the entry map from the declared fields and `__extmap`."
  [declared]
  `(merge ~(into {} (map (fn [f] [(keyword (name f)) f])) declared) ~'__extmap))

(defn- record-form
  "A record-shaped definition form under `definer`, for `classname` with
  `declared` fields."
  [definer classname declared]
  (let [entries (entries-form declared)]
    (list* definer (symbol (name classname)) classname
           (into (vec declared) record-tail)
           :implements ['clojure.lang.IRecord 'java.util.Map
                        'clojure.lang.ILookup 'clojure.lang.IObj
                        'clojure.lang.IHashEq]
           [(list 'valAt '[this k] '(.valAt this k nil))
            (list 'valAt '[this k nf]
                  `(case ~'k
                     ~@(mapcat (fn [f] [(keyword (name f)) f]) declared)
                     (get ~'__extmap ~'k ~'nf)))
            (list 'entrySet '[this] `(.entrySet ~(with-meta entries {:tag 'java.util.Map})))
            (list 'meta '[this] '__meta)
            (list 'withMeta '[this m]
                  `(new ~classname ~@declared ~'m ~'__extmap))
            (list 'hasheq '[this] `(hash ~entries))
            (list 'hashCode '[this] `(.hashCode ~(with-meta entries {:tag 'Object})))])))

(def ^:private variants
  "Field variants of Acceptance 2: name suffix, declared fields, values."
  [["Zero" [] []]
   ["Ref" '[a b] [:x "y"]]
   ["Mixed" [(with-meta 'l {:tag 'long}) (with-meta 'd {:tag 'double})
             'a]
    [Long/MAX_VALUE 2.5 :z]]])

(defn- representations
  "The representations to check: identity always, value when capable."
  []
  (cond-> [[:identity `runtime/guarded-deftype*]]
    (:available? (runtime/capability)) (conj [:value `vt/defvaluetype*])))

(defn- construct [^Class c args]
  (clojure.lang.Reflector/invokeConstructor c (object-array args)))

(defn- view
  "Entry map and metadata of record `r`, read through the record interface."
  [r]
  {:entries (into {} r) :meta (meta r)})

(defn- field-names [^Class c]
  (set (map #(.getName ^Field %) (.getDeclaredFields c))))

(defn- record-classname [rep suffix]
  (str "value_type.deftype_star_record_test." (name rep) suffix))

(defn- define-record [rep definer suffix declared]
  (eval (record-form definer (symbol (record-classname rep suffix)) declared)))

(deftest record-entry-points-test
  (doseq [[rep definer] (representations)
          [suffix declared values] variants]
    (testing (str rep " " suffix)
      (let [^Class c (define-record rep definer suffix declared)
            cname (.getName c)
            ks (mapv (comp keyword name) declared)
            entries (zipmap ks values)
            ext (assoc entries :x 9)]
        (testing "definition returns the Class in the selected representation"
          (is (class? c))
          (is (= (record-classname rep suffix) cname))
          (is (= (= rep :value) (capability/value-class? c))))
        (testing "three constructors"
          (is (= 3 (count (.getDeclaredConstructors c))))
          (is (= {:entries entries :meta nil} (view (construct c values))))
          (is (= {:entries ext :meta {:m 1}}
                 (view (construct c (concat values [{:m 1} {:x 9}])))))
          (is (= {:entries ext :meta {:m 1}}
                 (view (construct c (concat values [{:m 1} {:x 9}
                                                    (int 0) (int 0)]))))))
        (testing "create"
          (is (= {:entries ext :meta nil}
                 (view (clojure.lang.Reflector/invokeStaticMethod
                        c "create" (object-array [ext]))))))
        (testing "getBasis lists the declared fields"
          (is (= declared
                 (clojure.lang.Reflector/invokeStaticMethod
                  c "getBasis" (object-array 0)))))
        (testing "map record literal"
          (is (= {:entries ext :meta nil}
                 (view (binding [*read-eval* true]
                         (load-string (str "#" cname (pr-str ext))))))))
        (testing "positional record literal"
          (is (= {:entries entries :meta nil}
                 (view (binding [*read-eval* true]
                         (load-string
                          (str "#" cname "[" (apply pr-str values) "]")))))))
        (testing "compiled record constant"
          (is (= {:entries ext :meta nil}
                 (view (eval `(identity ~(construct c (concat values
                                                              [nil {:x 9}]))))))))
        (testing "metadata"
          (let [r (with-meta (construct c values) {:m 2})]
            (is (= {:entries entries :meta {:m 2}} (view r)))
            (is (= (hash entries) (hash r)))
            (is (= (.hashCode ^Object entries) (.hashCode ^Object r)))))
        (testing "hash fields: removed in value output, immutable in identity"
          (if (= rep :value)
            (is (empty? (filter #{"__hash" "__hasheq"} (field-names c))))
            (doseq [n ["__hash" "__hasheq"]]
              (let [^Field f (.getDeclaredField c n)]
                (is (= Integer/TYPE (.getType f)))
                (is (Modifier/isFinal (.getModifiers f)))
                (is (= 0 (.get f (construct c values))))))))
        (testing "construction preserves kept fields"
          (let [r (construct c (concat values [{:m 1} {:x 9} (int 0) (int 0)]))]
            (is (= (concat values [{:m 1} {:x 9}])
                   (map #(.get (.getField c (name %)) r)
                        (concat declared ['__meta '__extmap]))))))))))

(deftest plain-entry-points-test
  (doseq [[rep definer] (representations)]
    (testing (str rep)
      (let [classname (symbol (str "value_type.deftype_star_record_test.Plain"
                                   (name rep)))
            ^Class c (eval (list definer (symbol (name classname)) classname
                                 [(with-meta 'l {:tag 'long})
                                  (with-meta 'd {:tag 'double}) 'a]
                                 :implements ['clojure.lang.IType
                                              'clojure.lang.ILookup]
                                 '(valAt [this k] (.valAt this k nil))
                                 '(valAt [this k nf]
                                         (case k :l l :d d :a a nf))))
            r (construct c [Long/MIN_VALUE -1.5 :q])]
        (is (= (str classname) (.getName c)))
        (is (= (= rep :value) (capability/value-class? c)))
        (is (= [Long/MIN_VALUE -1.5 :q] (mapv #(get r %) [:l :d :a])))
        (is (= '[l d a] (clojure.lang.Reflector/invokeStaticMethod
                         c "getBasis" (object-array 0))))
        (testing "compiled IType constant"
          (is (= [Long/MIN_VALUE -1.5 :q]
                 (mapv #(get (eval `(identity ~r)) %) [:l :d :a]))))))))
