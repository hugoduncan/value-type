(ns value-type.fallback-policy-checks
  "Baseline-safe child checks of the first policy read. Loads no value-type
   namespace at its own load: each check requires the public namespaces,
   then makes the first policy read."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]))

(defn- report []
  ((resolve 'value-type.capability/report)))

(defn- define
  "Define an ordinary value type named simple-name with eval. Return the
   Class."
  [simple-name]
  (eval (list 'value-type.core/defvaluetype simple-name '[^long x])))

(defn- representation
  "Return :value when class c is a value class, else :identity."
  [c]
  (if (checks/value-class? c) :value :identity))

(defn- definition
  "Define simple-name. Return :value, :identity or the
   registry/failed-definition map of simple-name."
  [simple-name]
  (let [n *ns*]
    (registry/outcome
     #(registry/failed-definition % n simple-name)
     #(define simple-name)
     representation)))

(defn- set-policy! [policy]
  (System/setProperty registry/policy-property policy))

(defn set-after-load-error
  "Require value-type.core, set error with System/setProperty, then define
   an ordinary type. Return the definition outcome."
  []
  (require 'value-type.core)
  (set-policy! "error")
  (definition 'ErrorPoint))

(defn set-after-load-warn
  "Require value-type.core, set warn with System/setProperty, then define
   two ordinary types. Return the captured *err* text."
  []
  (require 'value-type.core)
  (set-policy! "warn")
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (define 'WarnPointA)
      (define 'WarnPointB))
    (str err)))

(defn report-then-set
  "Require value-type.core and value-type.capability; call report; set
   error; define an ordinary type. Return the reported policy and the
   definition outcome."
  []
  (require 'value-type.core 'value-type.capability)
  (let [policy (:fallback-policy (report))]
    (set-policy! "error")
    {:fallback-policy policy
     :definition (definition 'SilentPoint)}))

(defn invalid-report
  "Require value-type.core and value-type.capability (loading succeeds);
   call report twice, with silent set between the calls; define an
   ordinary type. Return the first failure, whether the second failure is
   the identical instance, and the definition outcome: :value, or
   whether the definition throws CompilerException with the first failure
   as its cause."
  []
  (require 'value-type.core 'value-type.capability)
  (let [first-failure (registry/caught report)
        _ (set-policy! "silent")
        second-failure (registry/caught report)
        failure-of (fn [^Throwable t]
                     {:class (.getName (class t))
                      :cause-identical? (identical? first-failure
                                                    (.getCause t))})]
    {:thrown (some-> first-failure registry/thrown)
     :second-identical? (identical? first-failure second-failure)
     :definition (registry/outcome failure-of
                                   #(define 'InvalidPoint)
                                   representation)}))

(defn invalid-definition
  "Require value-type.core; a definition makes the first read. Return the
   thrown map of the failure."
  []
  (require 'value-type.core)
  (registry/failed-call registry/thrown #(define 'FirstReadPoint)))
