(ns compare-semantics
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]))

(defn read-result
  "Read one retained semantic result."
  [root layout file]
  (edn/read-string (slurp (str root "/" layout "/" file))))

(defn compare!
  "Require identical semantic outcomes; report layout observations separately."
  [root]
  (doseq [file ["stores/allocation.edn" "stores/stores.edn"
                "diagnostics/allocation.edn" "diagnostics/diagnostics.edn"]]
    (let [baseline (:cases (read-result root "default" file))
          disabled (:cases (read-result root "disabled" file))
          semantic? #(not= "isFlatArray" (get-in % [:inputs :query]))]
      (assert (seq baseline) (str "Missing cases: " file))
      (assert (= (mapv :id baseline) (mapv :id disabled)) (str "Coverage differs: " file))
      (assert (= (mapv #(update % :causes (fn [causes] (mapv :class causes))) (filterv semantic? baseline))
                 (mapv #(update % :causes (fn [causes] (mapv :class causes))) (filterv semantic? disabled)))
              (str "Semantics differ: " file))
      (doseq [observation (remove semantic? disabled)]
        (assert (and (= :pass (:status observation)) (false? (:value observation)))
                (str "Flattening remains enabled: " (:id observation)))))))

(let [root (first *command-line-args*)]
  (compare! root)
  (pprint/pprint
   {:status :pass
    :scope :default-versus-disabled-array-flattening
    :layouts (into {}
                   (mapv (fn [layout]
                           [layout (filterv #(= "isFlatArray" (get-in % [:inputs :query]))
                                            (:cases (read-result root layout "diagnostics/diagnostics.edn")))])
                         ["default" "disabled"]))}))
