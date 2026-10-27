(require '[value-type.core :refer [defvaluetype]]
         '[value-type.array :as va])

(defvaluetype ArrayPoint [^long x])
(def inferred (va/into-array ArrayPoint [(ArrayPoint. 2) (ArrayPoint. 5)]))
(def explicit (va/into-array ArrayPoint (ArrayPoint. 99)
                             (list (ArrayPoint. 2) (ArrayPoint. 5))))
(let [actual {:inferred (mapv #(.-x %) inferred)
              :explicit (mapv #(.-x %) explicit)
              :empty (alength (va/into-array ArrayPoint (ArrayPoint. 0) []))
              :nil (alength (va/into-array ArrayPoint (ArrayPoint. 0) nil))}
      expected {:inferred [2 5] :explicit [2 5] :empty 0 :nil 0}]
  (assert (= expected actual))
  (prn {:pass? true :java-version (System/getProperty "java.runtime.version")
        :expected expected :actual actual}))
