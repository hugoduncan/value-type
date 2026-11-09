(ns value-type.jvm-facts
  "Development-only, Java-8-safe JVM facts and profile selection."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- require-fact!
  [valid? message context]
  (when-not valid?
    (throw (ex-info message context))))

(defn- text?
  [value]
  (and (string? value) (not (str/blank? value))))

(defn path-component?
  "Return whether a string or unqualified keyword is one safe path component."
  [value]
  (let [component (cond
                    (string? value) value
                    (and (keyword? value) (nil? (namespace value))) (name value))]
    (and (text? component)
         (not (#{"." ".."} component))
         (not (re-find #"[/\\\u0000:]" component)))))

(defn- validate-platform!
  [role platform {:keys [architecture url sha256 java]}]
  (let [context {:role role :platform platform}]
    (require-fact! (and (keyword? platform) (path-component? platform)) "Invalid JVM platform" context)
    (require-fact! (#{:x64 :aarch64} architecture) "Invalid archive architecture" context)
    (require-fact! (and (text? url) (str/starts-with? url "https://"))
                   "JVM archive requires an HTTPS URL" context)
    (require-fact! (and (string? sha256) (re-matches #"[0-9a-f]{64}" sha256))
                   "Invalid JVM SHA-256" context)
    (require-fact! (and (text? java)
                        (not (.isAbsolute (io/file java)))
                        (not (some #{".."} (str/split java #"[/\\]"))))
                   "Invalid relative Java executable layout" context)))

(defn validate
  "Validate facts and return them. Duplicate roles and incomplete rows fail."
  [facts]
  (require-fact! (and (vector? facts) (seq facts)) "Expected JVM facts vector" {})
  (require-fact! (apply distinct? (mapv :role facts)) "Duplicate JVM roles" {})
  (doseq [{:keys [role build tier environment profiles platforms]} facts]
    (let [context {:role role}]
      (require-fact! (and (keyword? role) (path-component? role)) "Invalid JVM role" context)
      (require-fact! (and (string? build) (path-component? build)) "Invalid JVM build identity" context)
      (require-fact! (#{:full :smoke} tier) "Invalid JVM test tier" context)
      (require-fact! (and (string? environment)
                          (re-matches #"VALUE_TYPE_[A-Z_]+_JAVA" environment))
                     "Invalid JVM environment override" context)
      (require-fact! (and (map? profiles) (= [] (:plain profiles)))
                     "JVM profiles require an empty :plain profile" context)
      (doseq [[profile flags] profiles]
        (require-fact! (and (keyword? profile) (vector? flags) (every? text? flags))
                       "Invalid JVM flag profile" (assoc context :profile profile)))
      (require-fact! (and (map? platforms) (seq platforms)) "Missing JVM platforms" context)
      (doseq [[platform archive] platforms]
        (validate-platform! role platform archive))))
  facts)

(defn read-facts
  "Read and validate an explicit facts file, or the classpath resource."
  ([] (read-facts (io/resource "jvms.edn")))
  ([source] (validate (edn/read-string (slurp source)))))

(defn select
  "Select one role, platform and named flag profile without combining flags."
  [facts role platform profile]
  (let [entry (first (filterv #(= role (:role %)) (validate facts)))
        archive (get-in entry [:platforms platform])
        flags (get-in entry [:profiles profile])]
    (require-fact! entry "Unknown JVM role" {:role role})
    (require-fact! archive "Unknown JVM platform" {:role role :platform platform})
    (require-fact! flags "Unknown JVM flag profile" {:role role :profile profile})
    (assoc (dissoc entry :platforms :profiles)
           :platform platform :archive archive :jvm-opts flags)))
