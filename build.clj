(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def ^:private lib 'org.hugoduncan/value-type)
(def ^:private class-dir "target/classes")

(defn- version-from-result
  [{:keys [exit out err]}]
  (when-not (= 0 exit)
    (throw (ex-info "Unable to compute version: git rev-list --count HEAD failed"
                    {:exit exit :err err})))
  (let [count-text (str/trim (or out ""))]
    (when-not (re-matches #"[0-9]+" count-text)
      (throw (ex-info "Unable to compute version: Git returned an invalid commit count"
                      {:out out :err err})))
    (str "0.1." count-text)))

(defn- version
  []
  (version-from-result
   (try
     (b/process {:command-args ["git" "rev-list" "--count" "HEAD"]
                 :out :capture
                 :err :capture})
     (catch java.io.IOException cause
       (throw (ex-info "Unable to compute version: could not launch Git" {} cause))))))

(defn clean
  "Removes target/ without requiring Git metadata."
  [_]
  (b/delete {:path "target"}))

(defn jar
  "Builds a source-only library JAR with a Git commit-count version."
  [_]
  (let [v (version)
        jar-file (str "target/value-type-" v ".jar")]
    (clean nil)
    (b/write-pom
     {:class-dir class-dir
      :lib lib
      :version v
      :basis (b/create-basis {:project "deps.edn" :user nil :aliases []})
      :src-dirs ["src"]
      :pom-data [[:description "A Clojure library for JEP 401 value objects."]
                 [:url "https://github.com/hugoduncan/value-type"]
                 [:licenses
                  [:license
                   [:name "Eclipse Public License 2.0"]
                   [:url "https://www.eclipse.org/legal/epl-2.0/"]
                   [:distribution "repo"]]]
                 [:scm
                  [:url "https://github.com/hugoduncan/value-type"]
                  [:connection "scm:git:https://github.com/hugoduncan/value-type.git"]
                  [:developerConnection "scm:git:ssh://git@github.com/hugoduncan/value-type.git"]]]})
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
    (b/copy-file {:src "LICENSE" :target (str class-dir "/LICENSE")})
    (b/jar {:class-dir class-dir :jar-file jar-file})
    {:jar-file jar-file :version v}))
