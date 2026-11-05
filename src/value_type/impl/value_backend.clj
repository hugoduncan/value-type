(ns value-type.impl.value-backend
  "Optional value-class transforms for a capture loader."
  (:require [value-type.impl.class-file :as class-file]
            [value-type.impl.field-access :as field-access]
            [value-type.impl.fields :as fields]
            [value-type.impl.transform :as transform])
  (:import (java.lang.classfile ClassModel CodeModel MethodModel)
           (java.lang.classfile.instruction FieldInstruction)
           (java.lang.constant ClassDesc)))

(set! *warn-on-reflection* true)

;;; Removed-field access check

(defn- removed-field-accesses
  "Return the set of removed field names that `bs` reads or writes on
   the class `target`."
  [^bytes bs ^String target]
  (let [^ClassModel model (.parse (class-file/configured nil) bs)
        owner (ClassDesc/of target)]
    (into #{}
          (for [^MethodModel m (.methods model)
                ^CodeModel code (some-> (.code m) (.orElse nil) vector)
                e (.elementList code)
                :when (field-access/owner-field-access? owner e)
                :let [field (.stringValue (.name ^FieldInstruction e))]
                :when (transform/hash-field? field)]
            field))))

(defn- check-removed-fields
  "Return `bs` unchanged, or throw `:invalid-field` with
   `:reason :removed-field-access` when `bs` reads or writes a removed
   hash field of `target`."
  ^bytes [^bytes bs ^String target]
  (let [accessed (removed-field-accesses bs target)]
    (when-let [field (some accessed transform/hash-field-names)]
      (throw
       (ex-info
        (str "Invalid access to removed field " target "/" field
             ": value output of defvaluetype* removes __hash and __hasheq."
             " Fix: compute hashes from entries and do not read or write"
             " __hash or __hasheq.")
        {:value-type/error :invalid-field
         :class-name target
         :field (symbol field)
         :reason :removed-field-access})))
    bs))

(defn prepare
  "Return owner and companion transforms for ordered field specifications.
   `shape` is `:plain` (a `deftype` class) or `:record` (a record-shaped
   `deftype*` class). Both shapes give concrete storage, constructor checks
   and concrete field references for the specified fields. For `:record`,
   owner (after the transform) and companion bytes that read or write
   `__hash`/`__hasheq` of `target` throw `:invalid-field` with
   `:reason :removed-field-access`.
   Load this namespace only after selecting value output."
  [target specifications delegate shape]
  (let [storage (into {} (map (juxt fields/emitted-name
                                    (fn [{:keys [^Class type enforcement]}]
                                      {:storage-type (ClassDesc/ofDescriptor
                                                      (.descriptorString type))
                                       :native? (= :native enforcement)})))
                      specifications)
        rewrite #(if (empty? storage)
                   %
                   (field-access/rewrite-class % target storage delegate))]
    (case shape
      :record
      {:owner #(check-removed-fields
                (transform/->record-value-class % storage delegate) target)
       :companion #(check-removed-fields (rewrite %) target)}
      :plain
      {:owner #(transform/->value-class % storage delegate)
       :companion rewrite})))
