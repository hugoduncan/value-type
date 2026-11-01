(ns value-type.capture-loading-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]))

(def ^:private capture-form
  '(do
     (require 'value-type.core 'value-type.impl.loader)
     {:install-callable? (ifn? (deref (resolve 'value-type.impl.loader/install!)))
      :optional-loaded? (boolean
                         (some find-ns
                               '[value-type.impl.value-backend
                                 value-type.impl.transform
                                 value-type.impl.field-access]))}))

(deftest baseline-capture-loading-test
  ;; Loading capture support must not load optional APIs. Each runtime is its
  ;; own child (rule 1).
  (harness/run-cells
   (for [jvm (config/incompatible-receivers (config/configuration))]
     (harness/cell-child
      jvm
      [{:context [(:java jvm)]
        :form capture-form
        :expected {:install-callable? true :optional-loaded? false}}]))))
