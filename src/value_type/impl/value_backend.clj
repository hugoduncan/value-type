(ns value-type.impl.value-backend
  "Optional value-class transforms for a capture loader."
  (:require [value-type.impl.field-access :as field-access]
            [value-type.impl.fields :as fields]
            [value-type.impl.transform :as transform])
  (:import (java.lang.constant ClassDesc)))

(set! *warn-on-reflection* true)

(defn prepare
  "Return owner and companion transforms for ordered field specifications.
   Load this namespace only after selecting value output."
  [target specifications delegate]
  (let [storage (into {} (map (juxt fields/emitted-name
                                    (fn [{:keys [^Class type enforcement]}]
                                      {:storage-type (ClassDesc/ofDescriptor
                                                      (.descriptorString type))
                                       :native? (= :native enforcement)})))
                      specifications)]
    {:owner #(transform/->value-class % storage delegate)
     :companion #(if (empty? storage)
                   %
                   (field-access/rewrite-class % target storage delegate))}))
