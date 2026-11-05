(ns value-type.identity-backend.fixture
  (:require [value-type.impl.loader :as loader]))

(definterface Reads
  (snapshot [])
  (captured [])
  (choose [selected]))

(defprotocol Describe
  (describe [value]))

(defmacro define-checked []
  (loader/install! "value_type.identity_backend.fixture.Mixed"
                   [{:field 'label :type String :enforcement :generated}]
                   :identity :plain)
  `(let []
     (deftype ~'Mixed [~'^long count ~'label ~'^double weight ~'nullable]
       Reads
       (~'snapshot [~'_] [~'count ~'label ~'weight ~'nullable])
       (~'captured [~'this] (fn [] (. ~'this ~'-label)))
       (~'choose [~'this ~'selected]
         (fn []
           (let [~'result (if ~'selected (. ~'this ~'-label) (. ~'this ~'-nullable))]
             (if (nil? ~'result) "empty" (str ~'result)))))
       Describe
       (~'describe [~'_] (str ~'label ":" ~'count))
       Object
       (~'toString [~'_] ~'label))))

(define-checked)

(defmacro define-pair []
  (loader/install! "value_type.identity_backend.fixture.Pair"
                   [{:field 'left :type String :enforcement :generated}
                    {:field 'right :type java.util.Date :enforcement :generated}]
                   :identity :plain)
  `(let []
     (deftype ~'Pair [~'left ~'^long count ~'right ~'^double weight ~'nullable])))

(define-pair)

(defmacro define-nested []
  (loader/install! "value_type.identity_backend.fixture.Nested"
                   [{:field 'child :type value_type.identity_backend.fixture.Mixed
                     :enforcement :generated}]
                   :identity :plain)
  `(let []
     (deftype ~'Nested [~'child]
       Reads
       (~'snapshot [~'_] (.snapshot ^Reads ~'child))
       (~'captured [~'this] (fn [] (. ~'this ~'-child)))
       (~'choose [~'_ ~'selected] (if ~'selected ~'child nil)))))

(define-nested)

(defmacro define-record
  "A `defrecord` (record shape: three constructors, two delegate) with one
  generated field."
  []
  (loader/install! "value_type.identity_backend.fixture.Rec"
                   [{:field 'label :type String :enforcement :generated}]
                   :identity :record)
  `(defrecord ~'Rec [~'label ~'^long count]))

(define-record)
