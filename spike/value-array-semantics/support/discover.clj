(ns discover
  (:require [clojure.pprint :as pprint])
  (:import [java.lang.management ManagementFactory]
           [java.lang.reflect Method Modifier]))

(defn causes
  "Return the complete native exception chain, including reflection wrappers."
  [error]
  (loop [current error result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result {:class (.getName (class current))
                           :message (.getMessage ^Throwable current)}))
      result)))

(defn observe
  "Record an operation without treating an observed rejection as a test pass."
  [id operation inputs f]
  (merge {:id id :operation operation :inputs inputs}
         (try {:status :pass :value (f)}
              (catch Throwable error
                {:status :rejection :causes (causes error)}))))

(defn signatures
  "Describe public methods without importing the optional API."
  [^Class api]
  (into []
        (comp (filter #(Modifier/isPublic (.getModifiers ^Method %)))
              (map (fn [^Method method]
                     {:name (.getName method)
                      :parameters (mapv #(.getName ^Class %) (.getParameterTypes method))
                      :returns (.getName (.getReturnType method))
                      :modifiers (Modifier/toString (.getModifiers method))})))
        (sort-by str (.getDeclaredMethods api))))

(defn discover
  "Probe API presence, signatures and access as separate observations."
  []
  [(observe :discovery/presence :class-for-name {:class "jdk.internal.value.ValueClass"}
            #(.getName (Class/forName "jdk.internal.value.ValueClass")))
   (observe :discovery/signatures :public-methods {}
            #(signatures (Class/forName "jdk.internal.value.ValueClass")))
   (observe :discovery/access :invoke {:method "isFlatArray" :input :object-array}
            #(let [api (Class/forName "jdk.internal.value.ValueClass")
                   method (.getMethod api "isFlatArray" (into-array Class [Object]))]
               (.invoke method nil (object-array [(object-array 0)]))))])

(defn environment
  "Record actual runtime properties and startup arguments."
  []
  {:properties (into (sorted-map)
                     (map (fn [key] [key (System/getProperty key)]))
                     ["java.home" "java.version" "java.runtime.version" "java.vm.name"
                      "os.name" "os.version" "os.arch"])
   :clojure-version (clojure-version)
   :jvm-flags (vec (.getInputArguments (ManagementFactory/getRuntimeMXBean)))})

(defn run-probe!
  "Write independent discovery evidence at the explicit output path."
  [output-path]
  (spit output-path
        (with-out-str
          (pprint/pprint {:schema-version 1 :phase :discovery
                          :environment (environment) :cases (discover)}))))
