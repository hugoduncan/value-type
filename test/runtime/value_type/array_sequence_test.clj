(ns value-type.array-sequence-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.array :as array]
            [value-type.array-test-support :as support]))

(deftest next-length-boundary-test
  ;; Check sequence length arithmetic without allocating large storage.
  (testing "accepted counts advance without narrowing"
    (doseq [[length expected] [[0 1] [(dec Integer/MAX_VALUE) Integer/MAX_VALUE]]]
      (is (= expected (#'array/checked-next-length Object length)))))
  (testing "the attempted length is retained at the rejection boundary"
    (let [error (try
                  (#'array/checked-next-length Object Integer/MAX_VALUE)
                  nil
                  (catch clojure.lang.ExceptionInfo error error))]
      (is (= {:value-type/error :invalid-array-length
              :component Object
              :length 2147483648
              :reason :out-of-range-or-non-integer}
             (ex-data error))))))

(deftest finite-construction-test
  ;; Real value arrays preserve shape and contents for each finite input form.
  (doseq [[component seed replacement] (support/fixtures)
          expected [[replacement] [replacement seed replacement]]
          [input-kind input] [[:vector expected]
                              [:list (apply list expected)]
                              [:lazy (map identity expected)]]
          arity [:inferred :explicit]]
    (testing (str component " " input-kind " " arity " length " (count expected))
      (let [result (case arity
                     :inferred (array/into-array component input)
                     :explicit (array/into-array component seed input))]
        (is (= component (.getComponentType (class result))))
        (is (= (count expected) (alength result)))
        (is (= expected (vec result)))))))

(defn- construction-error
  [construct]
  (try
    (construct)
    nil
    (catch clojure.lang.ExceptionInfo error error)))

(deftest empty-input-test
  ;; Empty input needs an explicit valid seed, but adds no output slot.
  (doseq [[component seed _] (support/fixtures)
          input [nil [] '()]]
    (testing (str component " input " (pr-str input))
      (let [error (construction-error #(array/into-array component input))
            result (array/into-array component seed input)]
        (is (= {:value-type/error :empty-array-input :component component}
               (ex-data error)))
        (is (re-find #"explicit-seed.*into-array component initial values"
                     (or (ex-message error) "")))
        (is (= component (.getComponentType (class result))))
        (is (zero? (alength result)))))
    (doseq [[initial reason] [[nil :null-initial] ["wrong seed" :incompatible-initial]]]
      (testing (str "invalid explicit seed " reason)
        (is (= {:value-type/error :invalid-array-element
                :component component :reason reason}
               (ex-data (construction-error
                         #(array/into-array component initial input)))))))))

(deftest invalid-component-test
  ;; Both public arities reject non-Class, primitive and identity components.
  (doseq [component [nil :not-a-class Long/TYPE String Object]
          arity [:inferred :explicit]]
    (testing (str component " " arity)
      (is (= {:value-type/error :invalid-array-component
              :component component :reason :not-reference-value-class}
             (ex-data (construction-error
                       #(case arity
                          :inferred (array/into-array component [])
                          :explicit (array/into-array component nil [])))))))))

(deftest invalid-sequence-element-test
  ;; Exact error maps identify the slot without retaining input or values.
  (doseq [[component seed replacement] (support/fixtures)
          arity [:inferred :explicit]
          index (range 3)
          [invalid reason] [[nil :null-element] [(Object.) :incompatible-element]]]
    (testing (str component " " arity " index " index " " reason)
      (let [input (assoc [replacement seed replacement] index invalid)
            error (construction-error
                   #(case arity
                      :inferred (array/into-array component input)
                      :explicit (array/into-array component seed input)))]
        (is (= {:value-type/error :invalid-array-element
                :component component :index index :reason reason}
               (ex-data error)))))))

(deftest non-seqable-input-test
  ;; Ordinary Clojure input errors are not converted to capability errors.
  (let [[component seed] (second (support/fixtures))]
    (doseq [arity [:inferred :explicit]]
      (testing (str arity " rejects non-seqable input")
        (is (thrown? IllegalArgumentException
                     (case arity
                       :inferred (array/into-array component 42)
                       :explicit (array/into-array component seed 42))))))))

(defn- construct
  [arity component seed input]
  (case arity
    :inferred (array/into-array component input)
    :explicit (array/into-array component seed input)))

(defn- observed-input
  [values effects]
  (lazy-seq
   (when-let [remaining (seq values)]
     (swap! effects conj (first remaining))
     (cons (first remaining) (observed-input (rest remaining) effects)))))

(deftest single-input-traversal-test
  ;; A fresh cursor on each seq request exposes repeated input traversal.
  (let [[component seed replacement] (second (support/fixtures))
        expected [replacement seed replacement]]
    (doseq [arity [:inferred :explicit]]
      (testing (str arity " walks a non-caching Seqable once")
        (let [walks (atom 0)
              effects (atom [])
              input (reify clojure.lang.Seqable
                      (seq [_]
                        (swap! walks inc)
                        (seq (observed-input expected effects))))
              result (construct arity component seed input)]
          (is (= expected (vec result)))
          (is (= 1 @walks))
          (is (= expected @effects)))))))

(deftest validation-input-effects-test
  ;; Early validation leaves input untouched; later rejection keeps prior effects.
  (let [[component seed replacement] (second (support/fixtures))]
    (testing "component and explicit seed checks precede realization"
      (doseq [[arity candidate initial reason]
              [[:inferred Object seed :not-reference-value-class]
               [:explicit Object seed :not-reference-value-class]
               [:explicit component nil :null-initial]
               [:explicit component (Object.) :incompatible-initial]]]
        (let [effects (atom [])
              input (observed-input [replacement] effects)
              error (construction-error #(construct arity candidate initial input))]
          (is (= reason (:reason (ex-data error))))
          (is (= [] @effects)))))
    (testing "an invalid element returns no partial array and does not undo effects"
      (doseq [arity [:inferred :explicit]]
        (let [effects (atom [])
              returned (atom ::not-returned)
              input (observed-input [replacement nil seed] effects)
              error (construction-error
                     #(reset! returned (construct arity component seed input)))]
          (is (= :invalid-array-element (:value-type/error (ex-data error))))
          (is (= 1 (:index (ex-data error))))
          (is (= ::not-returned @returned))
          (is (= [replacement nil] (vec (take 2 @effects)))))))))

(deftest realization-failure-test
  ;; Input exceptions escape unchanged, without poisoning later construction.
  (let [[component seed replacement] (second (support/fixtures))]
    (doseq [arity [:inferred :explicit]
            prefix [[] [replacement seed]]]
      (testing (str arity " failure after " (count prefix) " elements")
        (let [failure (ex-info "input failed" {:source :input})
              effects (atom [])
              returned (atom ::not-returned)
              input (concat (observed-input prefix effects)
                            (lazy-seq (throw failure)))
              error (construction-error
                     #(reset! returned (construct arity component seed input)))]
          (is (identical? failure error))
          (is (= prefix @effects))
          (is (= ::not-returned @returned))
          (is (= [replacement seed]
                 (vec (construct arity component seed [replacement seed])))))))))

(deftest chunked-input-test
  ;; Chunked producers may realize ahead; only ordered complete output is promised.
  (let [[component seed replacement] (second (support/fixtures))
        expected (vec (take 65 (cycle [replacement seed])))]
    (doseq [arity [:inferred :explicit]]
      (testing (str arity " accepts chunked lazy input")
        (let [input (map identity expected)]
          (is (chunked-seq? (seq input)))
          (is (= expected (vec (construct arity component seed input)))))))
    (testing "chunked failure does not promise full consumption or rollback"
      (doseq [arity [:inferred :explicit]]
        (let [effects (atom [])
              input (map (fn [index]
                           (swap! effects conj index)
                           (when-not (= 1 index) replacement))
                         (vec (range 65)))
              error (construction-error #(construct arity component seed input))]
          (is (= 1 (:index (ex-data error))))
          (is (= :null-element (:reason (ex-data error))))
          (is (= [0 1] (subvec @effects 0 2))))))))

(deftest selected-slot-store-test
  ;; Native stores change only the selected slot, or reject without mutation.
  (doseq [[component seed replacement] (support/fixtures)
          arity [:inferred :explicit]
          index (range 3)]
    (testing (str component " " arity " slot " index)
      (let [expected [seed seed seed]
            ^objects result (construct arity component seed expected)]
        (is (= 3 (alength result)))
        (doseq [slot (range 3)]
          (is (= (nth expected slot) (aget result slot))))
        (aset result index replacement)
        (doseq [slot (range 3)]
          (is (= (nth (assoc expected index replacement) slot)
                 (aget result slot)))))
      (doseq [[invalid exception] [[nil NullPointerException]
                                   [(Object.) ArrayStoreException]]]
        (testing (str "rejects " exception)
          (let [expected [replacement seed replacement]
                ^objects result (construct arity component seed expected)
                error (try
                        (aset result index invalid)
                        nil
                        (catch Exception error error))]
            (is (instance? exception error))
            (is (= 3 (alength result)))
            (doseq [slot (range 3)]
              (is (= (nth expected slot) (aget result slot))))))))))
