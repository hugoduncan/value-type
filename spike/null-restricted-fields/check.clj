(require '[field-probe.fixtures :as fixtures])
(import '(field_probe.fixtures Point Segment NullableSegment PointReader)
        '(java.lang.reflect InvocationTargetException))

(defn outcome [f]
  (try (f) :accepted
       (catch Throwable error
         (let [cause (if (instance? InvocationTargetException error)
                       (.getCause error)
                       error)]
           (symbol (.getName (class cause)))))))

(let [point (Point. 11 23)
      segment (Segment. point point)
      field (.getField Segment "start")
      checks {:constructor-null (outcome #(Segment. nil point))
              :second-field-null (outcome #(Segment. point nil))
              :factory-null (outcome #(fixtures/->Segment nil point))
              :reflection-null (outcome #(.newInstance
                                         (.getConstructor Segment (into-array Class [Object Object]))
                                         (object-array [nil point])))
              :wrong-type (outcome #(Segment. "wrong" point))
              :small-null (outcome #(field_probe.fixtures.SmallHolder. nil))
              :nullable (outcome #(NullableSegment. nil))
              :direct-read (= point (.-start segment))
              :reflective-read (= point (.get field segment))
              :method-read (= point (.point ^PointReader segment))
              :closure-read (= point ((fixtures/captured-reader segment)))
              :inner-closure-read (= point ((.reader ^PointReader segment)))
              :factory-read (= point (.-end ^Segment (fixtures/->Segment point point)))
              :field-type (= Point (.getType field))
              :value-class (.isValue Segment)}]
  (prn {:java-version (System/getProperty "java.runtime.version") :checks checks})
  (doseq [key [:constructor-null :second-field-null :factory-null :reflection-null :small-null]]
    (assert (= 'java.lang.NullPointerException (get checks key)) (str key)))
  (assert (= 'java.lang.ClassCastException (:wrong-type checks)))
  (assert (= :accepted (:nullable checks)))
  (doseq [key [:direct-read :reflective-read :method-read :closure-read :factory-read
              :field-type :value-class :inner-closure-read]]
    (assert (true? (get checks key)) (str key)))
  ;; Exercise compiled call sites, then reject null after warmup.
  (dotimes [index 30000]
    (let [value (Point. index index)
          holder (Segment. value value)]
      (assert (= index (.-x ^Point (.point ^PointReader holder))))))
  (assert (= 'java.lang.NullPointerException (outcome #(Segment. nil point))))
  (when (= "diagnostics" (first *command-line-args*))
    (let [api (Class/forName "jdk.internal.value.ValueClass")
          query (.getMethod api "isNullRestrictedField" (into-array Class [java.lang.reflect.Field]))
          unsafe-api (Class/forName "jdk.internal.misc.Unsafe")
          unsafe (.invoke (.getMethod unsafe-api "getUnsafe" (make-array Class 0)) nil (object-array 0))
          flat (.getMethod unsafe-api "isFlatField" (into-array Class [java.lang.reflect.Field]))
          nullable-field (.getField NullableSegment "start")
          info {:restricted? (.invoke query nil (object-array [field]))
                :flat? (.invoke flat unsafe (object-array [field]))
                :nullable-restricted? (.invoke query nil (object-array [nullable-field]))
                :nullable-flat? (.invoke flat unsafe (object-array [nullable-field]))
                :small-flat? (.invoke flat unsafe (object-array [(.getField field_probe.fixtures.SmallHolder "value")]))
                :nullable-small-flat? (.invoke flat unsafe (object-array [(.getField field_probe.fixtures.NullableSmallHolder "value")]))}]
      (prn {:diagnostics info})
      (assert (true? (:restricted? info)))
      (assert (false? (:nullable-restricted? info))))))
