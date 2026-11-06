(require '[value-type.core :refer [defvaluetype]])

(defn cause-chain [error]
  (loop [current error result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result {:class (symbol (.getName (class current)))
                           :message (.getMessage ^Throwable current)}))
      result)))

(defn attempt [form]
  (try (eval form) {:outcome :accepted}
       (catch Throwable error {:outcome :rejected :causes (cause-chain error)})))

(let [mode (first *command-line-args*)
      result
      (case mode
        "hint"
        (do
          (eval '(defvaluetype HintPoint [^long x]))
          (eval '(defvaluetype HintHolder [^user.HintPoint point]))
          {:field-type (.getName (.getType (.getField (Class/forName "user.HintHolder" true
                                                                                   (clojure.lang.RT/baseLoader))
                                                     "point")))})

        "annotation"
        (attempt '(defvaluetype Annotated
                    [^{jdk.internal.vm.annotation.NullRestricted true} value]))

        "string"
        (do
          (require '[field-probe.support :refer [probe-type]])
          (attempt '(probe-type StringHolder [^{:probe/type java.lang.String} value] true)))

        "primitive"
        (attempt '(defvaluetype Primitive
                    [^{:tag long jdk.internal.vm.annotation.NullRestricted true} value]))

        "recursive"
        (do
          (require '[field-probe.support :refer [probe-type]])
          (attempt '(probe-type Recursive [^{:probe/type user.Recursive} value] true))))]
  (prn {:mode mode :java-version (System/getProperty "java.runtime.version") :result result})
  (if (= mode "hint")
    (assert (= "java.lang.Object" (:field-type result)))
    (let [expected (symbol (second *command-line-args*))]
      (if (= expected 'accepted)
        (assert (= :accepted (:outcome result)))
        (assert (some #(= expected (:class %)) (:causes result)))))))
