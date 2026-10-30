(ns value-type.identity-loader-checks
  "Baseline-safe stale sibling Class checks shared by in-process and child JVMs."
  (:require [value-type.core])
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader)))

(defn named-class
  "Define a public no-argument Class in a fresh sibling loader."
  [binary-name]
  (let [writer (ClassWriter. 0)
        parent (DynamicClassLoader. (.getContextClassLoader (Thread/currentThread)))]
    (.visit writer Opcodes/V1_8 Opcodes/ACC_PUBLIC
            (.replace ^String binary-name \. \/) nil "java/lang/Object" nil)
    (let [constructor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>" "()V" nil nil)]
      (.visitCode constructor)
      (.visitVarInsn constructor Opcodes/ALOAD 0)
      (.visitMethodInsn constructor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn constructor Opcodes/RETURN)
      (.visitMaxs constructor 1 1)
      (.visitEnd constructor))
    (.visitEnd writer)
    (.defineClass parent binary-name (.toByteArray writer) nil)))

(def public-stale-sibling-expected
  "Result of `public-stale-sibling` for each expansion and analysis case."
  {:visible-replacement? true
   :rejections (vec (repeat 2 {:warnings ""
                               :failure {:value-type/error :invalid-field
                                         :field 'required
                                         :reason :field-class-mismatch}
                               :receiver-owner? true
                               :absent? true}))
   :replacement-field? true
   :construction (zipmap [:factory :direct :reflective]
                         (repeat [:stored :null-pointer :class-cast]))})

(defn- outcome
  [construct field value]
  (try
    (if (identical? value (.get ^java.lang.reflect.Field field (construct value)))
      :stored
      :not-stored)
    (catch NullPointerException _ :null-pointer)
    (catch ClassCastException _ :class-cast)))

(defn- evaluate
  [analysis form]
  (case analysis
    :eval (eval form)
    :function ((eval (list 'fn [] form)))
    :source (load-string (binding [*print-meta* true] (pr-str form)))))

(defn- reject
  [receiver analysis form]
  (let [warnings (java.io.StringWriter.)
        failure (binding [*err* warnings]
                  (try
                    (case analysis
                      :function (eval (list 'fn [] form))
                      (evaluate analysis form))
                    nil
                    (catch clojure.lang.Compiler$CompilerException error
                      (ex-data (.getCause error)))))]
    {:warnings (str warnings)
     :failure (dissoc failure :class-name)
     :receiver-owner? (= (str (ns-name receiver) ".Holder") (:class-name failure))
     :absent? (and (nil? (ns-resolve receiver 'Holder))
                   (nil? (ns-resolve receiver '->Holder)))}))

(defn- stale-sibling-case
  [expand analysis]
  (let [binary-name (str "identity.loader.Child" (gensym))
        original (named-class binary-name)
        replacement (named-class binary-name)
        receiver (create-ns (gensym "identity.sibling"))
        simple-name (symbol (.getSimpleName ^Class original))]
    (try
      (binding [*ns* receiver]
        (clojure.core/refer 'clojure.core)
        (.importClass receiver original)
        (let [form (expand (list 'value-type.core/defvaluetype 'Holder
                                 [(with-meta 'required {:tag simple-name :non-nil true})]))
              visible? (identical? replacement
                                   (Class/forName binary-name false
                                                  (.getClassLoader ^Class replacement)))
              rejections (mapv (fn [_] (reject receiver analysis form)) (range 2))]
          (ns-unmap receiver simple-name)
          (.importClass receiver replacement)
          (evaluate analysis form)
          (let [owner (ns-resolve receiver 'Holder)
                field (.getDeclaredField ^Class owner "required")
                constructor (.getConstructor ^Class owner (into-array Class [Object]))
                instance #(.newInstance (.getConstructor ^Class % (make-array Class 0))
                                        (object-array 0))
                child (instance replacement)
                stale-child (instance original)
                constructors
                {:factory @(ns-resolve receiver '->Holder)
                 :direct (eval '(fn [value] (new Holder value)))
                 :reflective
                 (fn [value]
                   (try
                     (.newInstance constructor (object-array [value]))
                     (catch java.lang.reflect.InvocationTargetException error
                       (throw (.getCause error)))))}]
            {:visible-replacement? visible?
             :rejections rejections
             :replacement-field? (identical? replacement (.getType field))
             :construction
             (update-vals constructors
                          (fn [construct]
                            (mapv #(outcome construct field %)
                                  [child nil stale-child])))})))
      (finally (remove-ns (ns-name receiver))))))

(defn public-stale-sibling
  "Reject a stale sibling import, then accept the visible one, per case.

   Return a map from [expansion analysis] to the case result. Each result
   equals `public-stale-sibling-expected` when the receiver rejects the
   mismatched import without a weaker typed field."
  []
  (into {}
        (for [[expansion expand] [[:macroexpand macroexpand]
                                  [:macroexpand-1 macroexpand-1]]
              analysis [:eval :function :source]]
          [[expansion analysis] (stale-sibling-case expand analysis)])))
