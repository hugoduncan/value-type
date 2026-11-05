(ns value-type.deftype-star-record-test
  "Integration tests for record-shaped and plain `defvaluetype*` forms in
  both representations: value output (`vt/defvaluetype*` on a capable JVM)
  and identity output (`runtime/guarded-deftype*`, the fallback expansion).
  Record forms implement the interfaces that record literals, record
  constants and metadata need; hash methods compute from entries.

  Non-nil fields are tested through the fixture macros `value-installed*`
  and `identity-installed*` (backends), and through `vt/defvaluetype*` and
  `identity-receiving*` (`vt/identity-field-deftype*`, receiving macros)."
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.core :as vt]
   [value-type.impl.capability :as capability]
   [value-type.impl.fields :as fields]
   [value-type.impl.loader :as loader]
   [value-type.impl.runtime :as runtime]
   [value-type.test-support :refer [record-tail]])
  (:import
   (clojure.lang Reflector)
   (java.lang.reflect Field Modifier)))

(set! *warn-on-reflection* true)

(defn- entries-form
  "Form that builds the entry map from the declared fields and `__extmap`."
  [declared]
  `(merge ~(into {} (map (fn [f] [(keyword (name f)) f])) declared) ~'__extmap))

(defn- record-form
  "A record-shaped definition form under `definer`, for `classname` with
  `declared` fields. `interfaces` and `methods` add to the record ones."
  [definer classname declared & {:keys [interfaces methods]}]
  (let [entries (entries-form declared)]
    (list* definer (symbol (name classname)) classname
           (into (vec declared) record-tail)
           :implements (into ['clojure.lang.IRecord 'java.util.Map
                              'clojure.lang.ILookup 'clojure.lang.IObj
                              'clojure.lang.IHashEq]
                             interfaces)
           (into
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
             (list 'hashCode '[this] `(.hashCode ~(with-meta entries {:tag 'Object})))]
            methods))))

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

;;; Non-nil fields through the backends (fixture macros)

(defn- installed*
  "Expansion of a fixture macro: install capture for `representation` with
  the specifications of the `:non-nil` fields, then define the class with
  `deftype*` and return it. The shape comes from the fields."
  [representation tagname classname fields opts+specs]
  (let [class-name (str classname)
        shape (@#'vt/record-shape class-name fields)
        specs (cond-> (fields/specifications *ns* class-name fields
                                             {:hidden-field? true})
                (= :value representation)
                (fields/select-enforcement
                 #(:available? (runtime/field-capability))))]
    (loader/install! class-name specs representation shape)
    `(let [] (deftype* ~tagname ~classname ~fields ~@opts+specs) ~classname)))

(defmacro value-installed*
  "Define a class as `defvaluetype*` does, with value output and the
  enforcement of its `:non-nil` fields."
  [tagname classname fields & opts+specs]
  (installed* :value tagname classname fields opts+specs))

(defmacro identity-installed*
  "Define a class as the identity fallback does, with generated checks for
  its `:non-nil` fields."
  [tagname classname fields & opts+specs]
  (installed* :identity tagname classname fields opts+specs))

(vt/defvaluetype NnPt [x])

(def ^:private pt-tag 'value_type.deftype_star_record_test.NnPt)

(definterface NnReader
  (reader []))

(def ^:private reader-method
  "Method that returns a companion closure. The closure reads `p` and `s`
  (or the nullable `__extmap`) through the captured receiver, so the
  companion holds owner field references."
  '(reader [this]
           (fn [choose]
             (if choose [(.-p this) (.-s this)] (.-__extmap this)))))

(defn- non-nil-declared
  "One native field (value-class tag) and one generated field."
  []
  [(with-meta 'p {:tag pt-tag :non-nil true})
   (with-meta 's {:tag 'java.lang.String :non-nil true})])

(defn- cause-chain-classes
  "Classes of the cause chain of the exception that `f` throws, or
  `:returned`."
  [f]
  (try (f) :returned
       (catch Throwable t
         (set (map class (take-while some? (iterate #(.getCause ^Throwable %) t)))))))

(defn- rejection
  "`expected` if the cause chain of the exception that `f` throws has it,
  else `:returned` or the classes of the cause chain."
  [expected f]
  (let [r (cause-chain-classes f)]
    (if (and (set? r) (contains? r expected)) expected r)))

(def ^:private expected-rejections
  {:nil-p NullPointerException :nil-s NullPointerException
   :wrong-p ClassCastException :wrong-s ClassCastException})

(defn- rejections
  "Outcomes of `f` (a function of `[p s]`) for `nil` and a wrong type in
  each field, as `expected-rejections` gives them."
  [f p]
  {:nil-p (rejection NullPointerException #(f nil "a"))
   :nil-s (rejection NullPointerException #(f p nil))
   :wrong-p (rejection ClassCastException #(f "x" "a"))
   :wrong-s (rejection ClassCastException #(f p 5))})

(defn- direct-constructor
  "Function of `n` arguments that calls the constructor of `c` with
  arity `n` directly, through compiled `(new c …)`."
  [^Class c n]
  (let [args (vec (repeatedly n gensym))]
    (eval `(fn ~args (new ~(symbol (.getName c)) ~@args)))))

(defn- non-nil-record-paths
  "Construction paths of a record class `c` (fields `p` `s`): name ->
  function of `[p s]` that builds through that path. `p` is a form for
  the literal paths."
  [^Class c]
  (let [cname (.getName c)
        lit (fn [v] (if (instance? NnPt v) (str "#=(" pt-tag ". " (.x ^NnPt v) ")") (pr-str v)))
        new2 (direct-constructor c 2)
        new4 (direct-constructor c 4)
        new6 (direct-constructor c 6)]
    {:declared #(construct c [%1 %2])
     :meta-extmap #(construct c [%1 %2 nil nil])
     :main #(construct c [%1 %2 nil nil (int 0) (int 0)])
     :declared-direct #(new2 %1 %2)
     :meta-extmap-direct #(new4 %1 %2 nil nil)
     :main-direct #(new6 %1 %2 nil nil (int 0) (int 0))
     :create #(Reflector/invokeStaticMethod
               c "create" (object-array [(cond-> {:p %1} (some? %2) (assoc :s %2))]))
     :map-literal #(binding [*read-eval* true]
                     (load-string (str "#" cname "{:p " (lit %1) " :s " (lit %2) "}")))
     :positional-literal #(binding [*read-eval* true]
                            (load-string (str "#" cname "[" (lit %1) " " (lit %2) "]")))
     ;; A compiled record constant calls `create` with its map view. The
     ;; valid fields `(NnPt. 1)` and "ok" only build the record; `__extmap`
     ;; puts the tested values in the map view, so `create` gets them (the
     ;; nil-in-map-view evidence).
     :constant #(eval `(identity ~(construct c [(NnPt. 1) "ok" nil {:p %1 :s %2}])))}))

;; Field `p` has a value-class tag: native under value output, generated
;; under identity output. Field `s` is always generated.

(defn- null-restricted? [^Class c field-name]
  (boolean
   (some #(= "jdk.internal.vm.annotation.NullRestricted"
             (.getName (.annotationType ^java.lang.annotation.Annotation %)))
         (.getAnnotations (.getDeclaredField c field-name)))))

(defn- check-enforcement
  "Field `p` is native (`NullRestricted`) only in value output; `s` never."
  [^Class c value?]
  (is (= {"p" value? "s" false}
         {"p" (null-restricted? c "p") "s" (null-restricted? c "s")})))

(defn- check-field-types
  "Field `p` has the storage type `pt-tag` and `s` has `String`."
  [^Class c]
  (is (= [pt-tag 'java.lang.String]
         (mapv #(symbol (.getName (.getType (.getField c %)))) ["p" "s"]))))

(defn- check-record-non-nil [^Class c value?]
  (let [p (NnPt. 7)]
    (is (= value? (capability/value-class? c)))
    (check-enforcement c value?)
    (check-field-types c)
    (is (= (if value? 0 2)
           (count (filter #{"__hash" "__hasheq"} (field-names c)))))
    (doseq [[path f] (non-nil-record-paths c)]
      (testing (str path)
        (testing "valid values construct and read back"
          (let [r (f p "a")]
            (is (= [p "a"] [(:p r) (:s r)]))
            (testing "through a companion closure"
              (let [closure (.reader ^NnReader r)]
                (is (= [p "a"] (closure true)))
                (is (= (.get (.getField c "__extmap") r) (closure false)))))))
        (testing "nil and wrong type are rejected"
          (is (= expected-rejections (rejections f p))))))))

(defn- check-plain-non-nil [^Class c value?]
  (let [p (NnPt. 7)
        new2 (direct-constructor c 2)]
    (is (= value? (capability/value-class? c)))
    (check-enforcement c value?)
    (check-field-types c)
    (doseq [[path f] {:reflection #(construct c [%1 %2]) :direct new2}]
      (testing (str path)
        (is (= [p "a"] (let [r (f p "a")]
                         [(.get (.getField c "p") r) (.get (.getField c "s") r)])))
        (is (= expected-rejections (rejections f p)))))))

(defn- define-non-nil [definer shape suffix]
  (let [tag (symbol (str "NonNil" suffix))
        cn  (symbol (str "value_type.deftype_star_record_test." tag))]
    (eval (if (= :record shape)
            (record-form definer cn (non-nil-declared)
                         :interfaces ['value_type.deftype_star_record_test.NnReader]
                         :methods [reader-method])
            `(~definer ~tag ~cn ~(non-nil-declared))))))

(deftest value-record-non-nil-test
  (check-record-non-nil (define-non-nil `value-installed* :record "ValueRec") true))

(deftest value-plain-non-nil-test
  (check-plain-non-nil (define-non-nil `value-installed* :plain "ValuePlain") true))

(deftest identity-record-non-nil-test
  (check-record-non-nil (define-non-nil `identity-installed* :record "IdentityRec")
                        false))

(deftest identity-plain-non-nil-test
  (check-plain-non-nil (define-non-nil `identity-installed* :plain "IdentityPlain")
                       false))

;;; Non-nil fields through the public form and the receiving macros

(defmacro identity-receiving*
  "Expand as the `defvaluetype*` fallback expansion with enabled fields:
  `vt/identity-field-deftype*` with one `:generated` mode per enabled field."
  [tagname classname fields & opts+specs]
  (let [modes (mapv (constantly :generated)
                    (fields/enabled-fields (str classname) fields))]
    `(vt/identity-field-deftype* ~tagname ~classname ~modes ~fields
                                 ~@opts+specs)))

(deftest public-value-record-non-nil-test
  (check-record-non-nil (define-non-nil `vt/defvaluetype* :record "PublicValueRec")
                        true))

(deftest public-value-plain-non-nil-test
  (check-plain-non-nil (define-non-nil `vt/defvaluetype* :plain "PublicValuePlain")
                       true))

(deftest receiving-identity-record-non-nil-test
  (check-record-non-nil
   (define-non-nil `identity-receiving* :record "ReceivingIdentityRec") false))

(deftest receiving-identity-plain-non-nil-test
  (check-plain-non-nil
   (define-non-nil `identity-receiving* :plain "ReceivingIdentityPlain") false))
