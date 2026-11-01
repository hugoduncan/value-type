(ns value-type.unavailable-predicates-test
  "In-process tests of the verdicts of the unavailable cells:
   the predicates of array-capability/rejection and
   value-capability/unavailable.
   No child JVM starts."
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.array-capability :as capability]
            [value-type.value-capability :as value-capability]))

(defn- without
  "Return outcome with text replaced by replacement in its message."
  [outcome text replacement]
  (update outcome :message #(.replace ^String % ^String text ^String replacement)))

(defn- array-rejection
  "Return a rejection of reason and cause; the data has a :detail unless
   cause gives it."
  [reason cause]
  {:exception capability/rejection-exception
   :data (cond-> {:value-type/error :array-unavailable :reason reason}
           (nil? cause) (assoc :detail "java/lang/classfile/ClassFile"))
   :cause cause
   :message "Value arrays need --enable-preview and --add-exports."})

(def ^:private missing-api (array-rejection :missing-classfile-api nil))

(def ^:private access-denied
  (array-rejection :array-access-denied "java.lang.IllegalAccessException"))

(defn- missing-api?
  [outcome]
  ((second (capability/rejection :missing-classfile-api)) outcome))

(defn- access-denied?
  [outcome]
  ((second (capability/rejection :array-access-denied)) outcome))

(deftest array-rejection-test
  (testing "matching outcome"
    (is (true? (missing-api? missing-api)))
    (is (true? (access-denied? access-denied))))
  (testing "other error"
    (is (false? (missing-api? (assoc-in missing-api [:data :value-type/error] :invalid-array)))))
  (testing "other reason"
    (is (false? (missing-api?
                 (assoc-in missing-api [:data :reason] :unsupported-class-version)))))
  (testing "data without detail"
    (is (false? (missing-api? (update missing-api :data dissoc :detail)))))
  (testing "data with other key"
    (is (false? (access-denied? (assoc-in access-denied [:data :detail] "x")))))
  (testing "cause against the reason rule"
    (is (false? (missing-api? (assoc missing-api :cause "java.lang.IllegalAccessException"))))
    (is (false? (access-denied? (assoc access-denied :cause nil)))))
  (testing "message without --enable-preview"
    (is (false? (missing-api? (without missing-api "--enable-preview and " "")))))
  (testing "message without --add-exports"
    (is (false? (missing-api? (without missing-api " and --add-exports" "")))))
  (testing "nil message"
    (is (false? (missing-api? (assoc missing-api :message nil))))))

(def ^:private class-name "fixture.Point")

(def ^:private capability {:reason :no-value-classes})

(def ^:private extra-names ["no value classes" "Use a JEP 401 JDK"])

(def ^:private unavailable
  (assoc (value-capability/unavailable-failure class-name capability)
         :absent? true
         :message (str class-name ": fixed value representation is unavailable"
                       " (no value classes). Use a JEP 401 JDK with --enable-preview.")))

(defn- unavailable?
  [result]
  ((second (value-capability/unavailable class-name capability extra-names)) result))

(deftest unavailable-outcome-test
  (testing "matching outcome with extra names"
    (is (true? (unavailable? unavailable))))
  (testing "not absent"
    (is (false? (unavailable? (assoc unavailable :absent? false)))))
  (testing "other reason"
    (is (false? (unavailable? (assoc-in unavailable [:data :reason] :preview-disabled)))))
  (testing "other class name"
    (is (false? (unavailable? (assoc-in unavailable [:data :class-name] "fixture.Other")))))
  (testing "message without one extra name"
    (is (false? (unavailable? (without unavailable "Use a JEP 401 JDK" "Use a JDK")))))
  (testing "message without the class name"
    (is (false? (unavailable? (without unavailable (str class-name ": ") "")))))
  (testing "message without --enable-preview"
    (is (false? (unavailable? (without unavailable " with --enable-preview" ""))))))
