(require '[criterium.bench :as bench]
         '[value-type.bench :as vb])
(import '[investigation InterfaceControl])

(defn measure
  [pass a]
  (bench/bench (pass a) :limit-time-s 10 :viewer :none)
  (let [{:keys [batch-size stats]} (-> (bench/last-bench) :data :stats)]
    {:ns-per-element (/ (double (-> stats :elapsed-time :median))
                        (long batch-size) (alength ^objects a))
     :batch-size batch-size}))

(let [[mode size order output] *command-line-args*
      n (parse-long size)
      java? (= mode "java")
      dt (if java? (InterfaceControl/instances n false)
             (#'vb/instances vb/->IDt n))
      vt (if java? (InterfaceControl/instances n true)
             (#'vb/instances vb/->IVt n))
      dt-pass (if java? (fn [a] (InterfaceControl/identityPass a))
                  vb/interface-dt)
      vt-pass (if java? (fn [a] (InterfaceControl/valuePass a))
                  vb/interface-vt)
      expected (quot (* 3 n (dec n)) 2)
      _ (assert (= expected (dt-pass dt) (vt-pass vt)))
      _ (assert (not (.isValue (class (aget ^objects dt 0)))))
      _ (assert (.isValue (class (aget ^objects vt 0))))
      cases (if (= order "dt-first")
              [[:dt dt-pass dt] [:vt vt-pass vt]]
              [[:vt vt-pass vt] [:dt dt-pass dt]])
      results (into {} (map (fn [[kind pass a]] [kind (measure pass a)])) cases)
      report {:mode mode :n n :order order
              :java-version (System/getProperty "java.runtime.version")
              :os-version (System/getProperty "os.version")
              :results results
              :ratio (/ (get-in results [:vt :ns-per-element])
                        (get-in results [:dt :ns-per-element]))}]
  (spit output (str (pr-str report) "\n"))
  (prn report))
(shutdown-agents)
