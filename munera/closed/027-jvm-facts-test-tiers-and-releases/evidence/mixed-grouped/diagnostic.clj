(require '[scry.core :as scry]
         '[value-type.public-mixed-field-aot-test])
(let [tests (sort-by (comp :line meta)
                     (filter (comp :test meta)
                             (vals (ns-publics 'value-type.public-mixed-field-aot-test))))]
  (doseq [test-var tests]
    (spit "/evidence/mixed-grouped-progress.edn"
          (str (pr-str {:event :start :var (str test-var) :time (System/currentTimeMillis)}) "\n")
          :append true)
    (let [result (scry/run {:vars [test-var]})]
      (spit (str "/evidence/mixed-grouped-" (:name (meta test-var)) ".edn") (pr-str result))
      (spit "/evidence/mixed-grouped-progress.edn"
            (str (pr-str {:event :end :var (str test-var) :summary (:summary result) :pass? (:pass? result)}) "\n")
            :append true)
      (when-not (:pass? result) (System/exit 1)))))
(shutdown-agents)
(System/exit 0)
