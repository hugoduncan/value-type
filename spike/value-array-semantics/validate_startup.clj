(ns validate-startup
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn read-result [root filename]
  (edn/read-string (slurp (str root "/" filename))))

(defn classes [observation]
  (mapv :class (:causes observation)))

(defn validate-signatures
  "Require the selected API and reject a contradictory seed-free overload."
  [signatures]
  (let [allocator "newNullRestrictedAtomicArray"
        contracts (into #{} (map #(select-keys % [:name :parameters :returns])) signatures)]
    (doseq [required (into [{:name allocator
                             :parameters ["java.lang.Class" "int" "java.lang.Object"]
                             :returns "[Ljava.lang.Object;"}]
                           (map (fn [method]
                                  {:name method :parameters ["java.lang.Object"]
                                   :returns "boolean"}))
                           ["isAtomicArray" "isFlatArray" "isNullRestrictedArray"])]
      (assert (contains? contracts required) (str "Missing API signature: " required)))
    (assert (not-any? #(and (= allocator (:name %))
                            (= ["java.lang.Class" "int"] (:parameters %)))
                      signatures)
            "Unexpected seed-free atomic allocator")))

(defn validate-run
  "Assert startup coverage and actual outcomes, without mocks."
  [root configuration]
  (let [result (read-result root (str configuration ".edn"))
        discovery (read-result root (str configuration "-discovery.edn"))
        [definition allocation dependent] (:cases result)
        [presence signatures access] (:cases discovery)
        preview? (contains? #{"baseline" "no-exports"} configuration)
        exports? (contains? #{"baseline" "no-preview"} configuration)
        java8? (= "java8" configuration)]
    (assert (= "0" (str/trim (slurp (str root "/" configuration ".exit")))))
    (assert (= [:startup/value-definition :startup/allocation :startup/dependent-semantics]
               (mapv :id (:cases result))))
    (assert (= [:discovery/presence :discovery/signatures :discovery/access]
               (mapv :id (:cases discovery))))
    (assert (= (:environment result) (:environment discovery)))
    (let [{:keys [properties jvm-flags]} (:environment result)
          flags (set jvm-flags)]
      (assert (= (if java8? "1.8.0_492-b09" "27-jep401ea3+1-1") (get properties "java.runtime.version")))
      (assert (= preview? (contains? flags "--enable-preview")))
      (assert (= exports? (contains? flags "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")))
      (assert (not (contains? flags "-XX:-UseArrayFlattening"))))
    (assert (= (if preview? :pass :rejection) (:status definition)))
    (when-not preview?
      (assert (= ["clojure.lang.Compiler$CompilerException" "java.lang.Error"]
                 (classes definition)))
      (when-not java8?
        (assert (str/includes? (get-in definition [:causes 1 :message])
                               "Preview features are not enabled"))))
    (if java8?
      (doseq [observation [presence signatures access]]
        (assert (= :rejection (:status observation)))
        (assert (= ["java.lang.ClassNotFoundException"] (classes observation))))
      (do
        (assert (= :pass (:status presence) (:status signatures)))
        (validate-signatures (:value signatures))
        (assert (= (if exports? :pass :rejection) (:status access)))
        (if exports?
          (assert (false? (:value access)))
          (assert (= ["java.lang.IllegalAccessException"] (classes access))))))
    (case configuration
      "baseline" (do (assert (= :pass (:status allocation)))
                     (assert (= {:length 0 :value-class? true} (:value allocation))))
      "no-exports" (do (assert (= :rejection (:status allocation)))
                       (assert (= ["java.lang.IllegalAccessException"] (classes allocation))))
      (assert (= [:unresolved :value-definition-rejected]
                 ((juxt :status :reason) allocation))))
    (assert (= :unresolved (:status dependent)))
    {:configuration configuration :validated? true
     :unresolved (filterv #(= :unresolved (:status %)) (:cases result))}))

(prn {:startup-controls
      (mapv #(validate-run (first *command-line-args*) %)
            ["baseline" "no-exports" "no-preview" "neither" "java8"])})
