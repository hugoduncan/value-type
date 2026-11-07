(ns value-type.impl.jvm-format-test
  (:require
   [clojure.test :refer [deftest is]]
   [value-type.impl.jvm-format :as jvm-format])
  (:import
   (java.lang.classfile ClassFile)))

(deftest row-test
  (is (= {:major (ClassFile/latestMajorVersion)
          :minor 0xFFFF
          :identity-class-flag 0x20
          :strict-field-flag 0x800
          :constructor-order :fields-before-super}
         (jvm-format/row))))
