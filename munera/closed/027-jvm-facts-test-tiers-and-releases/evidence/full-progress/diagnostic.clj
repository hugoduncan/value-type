(require '[scry.core :as scry])

;; Keep one grouped run and normal discovery; observe completed vars only.
(let [[output-prefix namespace-name] *command-line-args*
      progress-path (str output-prefix "-progress.edn")
      selection (if namespace-name
                  {:namespaces [(symbol namespace-name)]}
                  {:dirs ["test/cross-jvm"]})]
  (spit progress-path "")
  (let [result (scry/run
                (assoc selection :progress-callback
                       (fn [entry]
                         (spit progress-path
                               (str (pr-str {:time-ms (System/currentTimeMillis)
                                             :result entry}) "\n")
                               :append true))))]
    (spit (str output-prefix "-result.edn") (pr-str result))
    (shutdown-agents)
    (System/exit (if (and (:pass? result)
                          (pos? (get-in result [:summary :test])))
                   0
                   1))))
