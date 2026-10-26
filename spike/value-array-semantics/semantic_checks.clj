(ns semantic-checks)

(def fixtures
  {:empty "allocation.EmptyValue" :long "allocation.LongValue"
   :pair "allocation.PairValue" :reference-null "allocation.ReferenceValue"
   :reference "allocation.ReferenceValue"})

(def queries ["isFlatArray" "isNullRestrictedArray" "isAtomicArray"])

(defn required-cases
  "Return the fixed case contract without loading JVM fixtures."
  [phase]
  (case phase
    :allocation
    (into {}
          (concat
           (for [[fixture component] (assoc fixtures :identity "allocation.IdentityValue" :primitive "long")]
             [(keyword "component" (name fixture)) [:value-status {:component component}]])
           (for [[fixture component] fixtures
                 [length seed] (conj (vec (for [length [0 1 3] seed [:valid :null :wrong]] [length seed])) [-1 :valid])]
             [(keyword "allocation" (str (name fixture) "-" length "-" (name seed)))
              [:atomic-allocation {:component component :length length :seed seed}]])
           (for [[fixture component] {:identity "allocation.IdentityValue" :primitive "long"}]
             [(keyword "allocation" (str (name fixture) "-1-valid"))
              [:atomic-allocation {:component component :length 1 :seed :valid}]])))
    :stores
    (into {} (for [fixture (keys fixtures) access [:clojure :java] length [1 3] kind [:valid :null :wrong] index (range length)]
               [(keyword "store" (str (name fixture) "-" (name access) "-" length "-" (name kind) "-" index))
                [:array-store {:fixture fixture :access access :length length :store kind :index index}]]))
    :diagnostics
    (into {} (for [[input restricted?]
                   (concat [[:object false] [:identity false] [:primitive false]]
                           (for [fixture (keys fixtures) suffix ["ordinary" "restricted-0" "restricted-1" "restricted-3"]]
                             [(keyword (str (name fixture) "-" suffix)) (not= "ordinary" suffix)]))
                   query queries]
               [(keyword "diagnostic" (str (name input) "-" query))
                [:array-diagnostic {:input input :query query :restricted? restricted?}]]))))

(defn coverage!
  "Require every case ID, operation and relevant input from the contract."
  [phase cases]
  (let [required (required-cases phase)]
    (assert (= (count required) (count cases)))
    (assert (= (set (keys required)) (set (mapv :id cases))))
    (doseq [{:keys [id operation inputs]} cases]
      (let [[expected-operation expected-inputs] (get required id)]
        (assert (= expected-operation operation) (str id))
        (assert (= expected-inputs (select-keys inputs (keys expected-inputs))) (str id))))))

(defn allocation!
  "Check coverage and outcomes, not just successful process exit."
  [cases]
  (coverage! :allocation cases)
  (assert (= 59 (count cases)) "Missing allocation cases")
  (assert (= (count cases) (count (set (mapv :id cases)))) "Duplicate case IDs")
  (doseq [{:keys [id operation inputs status value causes]} cases]
    (let [control? (contains? #{"allocation.IdentityValue" "long"} (:component inputs))
          accepted? (and (not control?) (= :valid (:seed inputs))
                         (<= 0 (:length inputs 0)))]
      (if (= :value-status operation)
        (assert (and (= :pass status) (= (not= "allocation.IdentityValue" (:component inputs)) value)) (str id))
        (if accepted?
          (assert (and (= :pass status) (= (:length inputs) (:length value))
                       (= (:component inputs) (:component value))
                       (:all-slots-match-seed? value)) (str id))
          (assert (and (= :rejection status)
                       (= ["java.lang.reflect.InvocationTargetException"
                           "java.lang.IllegalArgumentException"]
                          (mapv :class causes))) (str id)))))))

(defn stores!
  "Assert native store restrictions and contents through both access paths."
  [cases]
  (coverage! :stores cases)
  (assert (= 120 (count cases)) "Missing store cases")
  (assert (= 120 (count (set (mapv :id cases)))) "Duplicate store IDs")
  (doseq [{:keys [id inputs status causes checks]} cases]
    (assert (every? true? (mapv checks [:length-matches? :initial-slots-match? :final-slots-match?])) (str id))
    (if (= :valid (:store inputs))
      (do (assert (= :pass status) (str id))
          (assert (= (not= :empty (:fixture inputs)) (:payload-changed? checks)) (str id)))
      (assert (and (= :rejection status)
                   (= [(if (= :null (:store inputs))
                         "java.lang.NullPointerException"
                         "java.lang.ArrayStoreException")]
                      (mapv :class causes))) (str id)))))

(defn diagnostics!
  "Check coverage and semantic diagnostics without requiring flattening."
  [cases]
  (coverage! :diagnostics cases)
  (assert (= 69 (count cases)) "Missing diagnostic cases")
  (assert (= 69 (count (set (mapv :id cases)))) "Duplicate diagnostic IDs")
  (doseq [{:keys [id inputs status value]} cases]
    (when-not (contains? #{:null :non-array} (:input inputs))
      (assert (and (= :pass status) (boolean? value)) (str id))
      (case (:query inputs)
        "isNullRestrictedArray" (assert (= (:restricted? inputs) value) (str id))
        "isAtomicArray" (assert (= (not= :primitive (:input inputs)) value) (str id))
        "isFlatArray" nil))))

