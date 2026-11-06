(ns spike.use (:require [spike.vt :refer [defvaluetype]]))
(defprotocol Area (area [x]))
(defvaluetype Rect [^long w ^double h tag]
  Area
  (area [_] (if (pos? w) (* w h) 0.0))
  Object
  (toString [this] (str "Rect" [w h tag]))
  clojure.lang.IFn
  (invoke [this] (mapv (fn [x] [x this w]) (range 2))))
