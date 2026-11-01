(ns value-type.identity-loader-checks
  "Baseline-safe stale sibling Class checks shared by in-process and child
   JVMs."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.transfer-checks :as transfer])
  (:import (clojure.asm ClassWriter Opcodes)
           (clojure.lang DynamicClassLoader)))

(defn class-bytes
  "Return the bytes of a public no-argument Class named binary-name, with
   super Class Object."
  [^String binary-name]
  (let [writer (ClassWriter. 0)]
    (.visit writer Opcodes/V1_8 Opcodes/ACC_PUBLIC
            (.replace binary-name \. \/) nil "java/lang/Object" nil)
    (let [constructor (.visitMethod writer Opcodes/ACC_PUBLIC "<init>" "()V" nil nil)]
      (.visitCode constructor)
      (.visitVarInsn constructor Opcodes/ALOAD 0)
      (.visitMethodInsn constructor Opcodes/INVOKESPECIAL "java/lang/Object" "<init>" "()V" false)
      (.visitInsn constructor Opcodes/RETURN)
      (.visitMaxs constructor 1 1)
      (.visitEnd constructor))
    (.visitEnd writer)
    (.toByteArray writer)))

(defn instance
  "Return a new instance of c through its public no-argument constructor."
  [^Class c]
  (.newInstance (.getConstructor c (make-array Class 0)) (object-array 0)))

(defn named-class
  "Define a public no-argument Class in a fresh sibling loader."
  [binary-name]
  (let [parent (DynamicClassLoader. (.getContextClassLoader (Thread/currentThread)))]
    (.defineClass parent binary-name (class-bytes binary-name) nil)))

(def cases
  "The [expand mode] cases of the stale sibling checks, in order:
   transfer-checks/expands by transfer-checks/modes."
  (vec (for [expand transfer/expands
             mode transfer/modes]
         [expand mode])))

(def public-stale-sibling-expected
  "Result of `public-stale-sibling` for each expand and mode case."
  (merge
   {:visible-replacement? true
    :rejections (registry/each-attempt {:warnings ""
                                        :failure {:value-type/error :invalid-field
                                                  :field 'required
                                                  :reason :field-class-mismatch}
                                        :receiver-owner? true
                                        :absent? true})
    :replacement-field? true
    :constructions (checks/each-construction
                    {:stored-input? true :rejections checks/checked-rejections})}
   registry/new-definition))

(defn- reject
  [receiver mode form]
  (let [warnings (java.io.StringWriter.)
        failure (binding [*err* warnings]
                  (try
                    (transfer/evaluate-form form mode)
                    nil
                    (catch clojure.lang.Compiler$CompilerException thrown
                      (ex-data (.getCause thrown)))))]
    {:warnings (str warnings)
     :failure (dissoc failure :class-name)
     :receiver-owner? (= (registry/class-name receiver 'Holder) (:class-name failure))
     :absent? (registry/absent? receiver 'Holder)}))

(defn stale-sibling-case
  "Run one stale sibling case in receiver (a namespace that the caller
   owns): define the sibling Classes under the receiver name, reject the
   stale import twice, then define Holder against the visible import.
   expand is :macroexpand or :macroexpand-1 (transfer-checks/expands);
   mode is :eval, :analysis or :source (transfer-checks/modes). The result
   equals `public-stale-sibling-expected`."
  [receiver expand mode]
  (let [expander (transfer/expanders expand)
        binary-name (registry/class-name receiver (str "Child" (gensym)))
        original (named-class binary-name)
        replacement (named-class binary-name)
        simple-name (symbol (.getSimpleName ^Class original))]
    (binding [*ns* receiver]
      (clojure.core/refer 'clojure.core)
      (.importClass receiver original)
      (let [form (expander (list 'value-type.core/defvaluetype 'Holder
                                 [(with-meta 'required {:tag simple-name :non-nil true})]))
            visible? (identical? replacement
                                 (Class/forName binary-name false
                                                (.getClassLoader ^Class replacement)))
            rejections (registry/twice (fn [] (reject receiver mode form)))]
        (ns-unmap receiver simple-name)
        (.importClass receiver replacement)
        (let [defined (transfer/evaluate-form form mode)
              owner (ns-resolve receiver 'Holder)
              field (.getDeclaredField ^Class owner "required")
              child (instance replacement)
              stale-child (instance original)]
          (merge
           {:visible-replacement? visible?
            :rejections rejections
            :replacement-field? (identical? replacement (.getType field))
            :constructions
            (checks/input-results receiver owner field child stale-child)}
           (registry/defined receiver 'Holder
             (if (class? defined) defined owner)
             child)))))))

(defn public-stale-sibling
  "Reject a stale sibling import, then accept the visible one, per case.

   Return a map from each entry of `cases` to the case result. Each result
   equals `public-stale-sibling-expected` when the receiver rejects the
   mismatched import without a weaker typed field."
  []
  (into {}
        (for [[expand mode :as case-key] cases]
          [case-key
           (let [receiver (create-ns (gensym "identity.sibling"))]
             (try
               (stale-sibling-case receiver expand mode)
               (finally (remove-ns (ns-name receiver)))))])))

(defn stale-sibling-strict-case
  "Run `stale-sibling-case` in receiver under error policy with no value
   support. The fallback guard rejects the definition; return
   registry/failed-definition of Holder, or registry/unexpected-success
   when nothing rejects."
  [receiver expand mode]
  (registry/failed-call #(registry/failed-definition % receiver 'Holder)
                        #(stale-sibling-case receiver expand mode)))
