(ns check-validation
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]))

(defn validate [root]
  (:exit @(process/process
           ["bb" "spike/value-array-semantics/validate_matrix.clj" (str root)]
           {:out :string :err :string})))

(defn change! [root path f]
  (let [file (fs/path root path)]
    (spit (str file) (pr-str (f (edn/read-string (slurp (str file))))))))

(defn change-diagnostics! [root f]
  (doseq [layout ["default" "disabled"]]
    (change! root (str "semantics/" layout "/diagnostics/diagnostics.edn")
             #(update % :cases (fn [cases] (mapv f cases))))))

(defn evidence-hashes [root]
  (into {} (for [file (fs/glob root "**") :when (fs/regular-file? file)]
             [(str (fs/relativize root file))
              (vec (.digest (java.security.MessageDigest/getInstance "SHA-256")
                            (java.nio.file.Files/readAllBytes file)))])))

(defn check-copy! [source label mutate expected-exit?]
  (let [temporary (fs/create-temp-dir {:prefix "value-array-validation-"})
        root (fs/path temporary "matrix")]
    (try
      (fs/copy-tree source root)
      (mutate root)
      (let [before (evidence-hashes root)]
        (assert (expected-exit? (validate root)) label)
        (assert (= before (evidence-hashes root)) (str label " modified evidence")))
      (println "Passed:" label)
      (finally (fs/delete-tree temporary)))))

(let [source (or (first *command-line-args*)
                 "ramora/value-type-array-design/evidence/matrix-selected-slots")]
  (check-copy! source "unchanged evidence" identity zero?)
  (check-copy! source "false null-restriction in both layouts"
               #(change-diagnostics! % (fn [record]
                                         (if (= :diagnostic/long-restricted-0-isNullRestrictedArray (:id record))
                                           (assoc record :value false) record)))
               pos?)
  (check-copy! source "renamed required case in both layouts"
               #(change-diagnostics! % (fn [record]
                                         (if (= :diagnostic/long-restricted-0-isNullRestrictedArray (:id record))
                                           (assoc record :id :diagnostic/renamed) record)))
               pos?)
  (doseq [status [:unresolved :rejection]]
    (check-copy! source (str "baseline allocation status " status)
                 #(change! % "startup/baseline.edn"
                           (fn [result]
                             (update result :cases
                                     (fn [cases]
                                       (mapv (fn [record]
                                               (if (= :startup/allocation (:id record))
                                                 (assoc record :status status)
                                                 record))
                                             cases)))))
                 pos?))
  (check-copy! source "incorrect startup flags in both records"
               (fn [root]
                 (doseq [file ["startup/baseline.edn" "startup/baseline-discovery.edn"]]
                   (change! root file #(assoc-in % [:environment :jvm-flags] []))))
               pos?)
  (check-copy! source "incorrect startup runtime in both records"
               (fn [root]
                 (doseq [file ["startup/baseline.edn" "startup/baseline-discovery.edn"]]
                   (change! root file #(assoc-in % [:environment :properties "java.runtime.version"] "wrong"))))
               pos?))

(check-copy! (or (first *command-line-args*)
                 "ramora/value-type-array-design/evidence/matrix-selected-slots")
             "corrupted isolated record"
             #(change! % "semantics/default/diagnostics/isolated.edn"
                       (fn [record] (assoc-in record [:cases 0 :signal] :wrong)))
             pos?)

(defn change-isolated-environment!
  "Change raw and retained environments together to test matrix constraints."
  [root layout index f]
  (let [directory (str "semantics/" layout "/diagnostics/")
        record (edn/read-string (slurp (str (fs/path root directory "isolated.edn"))))]
    (change! root (str directory (name (:id (nth (:cases record) index))) "/allocation.edn")
             #(update % :environment f))
    (change! root (str directory "isolated.edn")
             #(update-in % [:cases index :environment] f))))

(let [source (or (first *command-line-args*)
                 "ramora/value-type-array-design/evidence/matrix-selected-slots")]
  (check-copy! source "default isolated evidence substituted for disabled layout"
               (fn [root]
                 (let [default (fs/path root "semantics/default/diagnostics")
                       disabled (fs/path root "semantics/disabled/diagnostics")]
                   (doseq [dir (fs/list-dir default) :when (fs/directory? dir)]
                     (let [target (fs/path disabled (fs/file-name dir))]
                       (fs/delete-tree target)
                       (fs/copy-tree dir target)))
                   (fs/copy (fs/path default "isolated.edn")
                            (fs/path disabled "isolated.edn") {:replace-existing true})))
               pos?)
  ;; Mutate each process separately so no unchecked process can hide in a group.
  (doseq [layout ["default" "disabled"]
          index (range 6)
          [label mutate] [["runtime" #(assoc % :java-version "wrong")]
                          ["preview" #(update % :jvm-flags
                                              (fn [flags] (filterv (partial not= "--enable-preview") flags)))]
                          ["exports" #(update % :jvm-flags
                                              (fn [flags] (filterv (partial not= "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED") flags)))]
                          ["layout" #(update % :jvm-flags
                                             (fn [flags]
                                               (if (= "default" layout)
                                                 (conj flags "-XX:-UseArrayFlattening")
                                                 (filterv (partial not= "-XX:-UseArrayFlattening") flags))))]]]
    (check-copy! source (str "isolated " layout " process " index " incorrect " label)
                 #(change-isolated-environment! % layout index mutate)
                 pos?)))

(defn change-signatures! [root configuration f]
  (change! root (str "startup/" configuration "-discovery.edn")
           #(update % :cases
                    (fn [cases]
                      (mapv (fn [record]
                              (if (= :discovery/signatures (:id record))
                                (update record :value f)
                                record))
                            cases)))))

(let [source (or (first *command-line-args*)
                 "ramora/value-type-array-design/evidence/matrix-selected-slots")
      allocator "newNullRestrictedAtomicArray"
      seed-free {:name allocator :parameters ["java.lang.Class" "int"]
                 :returns "[Ljava.lang.Object;" :modifiers "public static native"}]
  ;; Check each startup record independently; agreement cannot prove correctness.
  (doseq [configuration ["baseline" "no-exports" "no-preview" "neither"]]
    (doseq [[label mutate expected] [["empty signatures" (constantly []) pos?]
                                     ["seed-free atomic overload" #(conj % seed-free) pos?]
                                     ["unrelated API method" #(conj % (assoc seed-free :name "otherAllocator")) zero?]]]
      (check-copy! source (str configuration " " label)
                   #(change-signatures! % configuration mutate) expected))
    (doseq [method [allocator "isAtomicArray" "isFlatArray" "isNullRestrictedArray"]
            [label mutate] [["missing" (fn [signatures]
                                         (filterv #(not= method (:name %)) signatures))]
                            ["parameters" (fn [signatures]
                                            (mapv #(if (= method (:name %))
                                                     (assoc % :parameters []) %) signatures))]
                            ["return type" (fn [signatures]
                                             (mapv #(if (= method (:name %))
                                                      (assoc % :returns "void") %) signatures))]]]
      (check-copy! source (str configuration " " method " " label)
                   #(change-signatures! % configuration mutate) pos?))))
