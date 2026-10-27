(ns field-probe.fixtures
  (:require [field-probe.support :refer [probe-type]]
            [value-type.core :refer [defvaluetype]]))

(defvaluetype Point [^long x ^long y])

(definterface PointReader
  (point [])
  (reader []))

(defvaluetype Small [^long x])

(probe-type SmallHolder [^{:probe/type field_probe.fixtures.Small} value] true)
(probe-type NullableSmallHolder [^{:probe/type field_probe.fixtures.Small} value] false)

(probe-type Segment [^{:probe/type field_probe.fixtures.Point} start
                     ^{:probe/type field_probe.fixtures.Point} end]
  true
  PointReader
  (point [_] start)
  (reader [_] (fn [] start)))

(probe-type NullableSegment [^{:probe/type field_probe.fixtures.Point} start]
  false
  PointReader
  (point [_] start))

(defn captured-reader [^Segment segment]
  (fn [] (.-start segment)))
