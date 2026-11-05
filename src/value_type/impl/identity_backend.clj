(ns value-type.impl.identity-backend
  "Baseline-safe concrete storage and constructor checks for identity output."
  (:require [value-type.impl.fields :as fields])
  (:import (clojure.asm ClassReader ClassVisitor ClassWriter MethodVisitor Opcodes
                        Type)
           (java.util Objects)))

(set! *warn-on-reflection* true)

(def ^:private object-descriptor (Type/getDescriptor Object))

(def ^:private objects-owner (Type/getInternalName Objects))

(def ^:private require-non-null-descriptor
  (Type/getMethodDescriptor (Type/getType Object)
                            (into-array Type [(Type/getType Object)])))

(defn- method-visitor
  "Check constructor control flow and writes; retype selected field references.
  A constructor that calls a constructor of `owner` delegates: it must write
  no selected field and must not also create an `owner` instance (then the
  receiver of the call is not known). Each other constructor writes each
  selected field once."
  [^MethodVisitor delegate owner types field-names method desc constructor?
   unsupported!]
  (let [writes (volatile! #{})
        delegates? (volatile! false)
        creates? (volatile! false)
        reject-control-flow
        (fn []
          (when constructor?
            (unsupported! "unsupported non-nil constructor control flow"
                          {:method method :descriptor desc})))]
    (proxy [MethodVisitor] [Opcodes/ASM6 delegate]
      (visitJumpInsn [opcode label]
        (reject-control-flow)
        (.visitJumpInsn delegate opcode label))
      (visitTableSwitchInsn [minimum maximum default labels]
        (reject-control-flow)
        (.visitTableSwitchInsn delegate minimum maximum default labels))
      (visitLookupSwitchInsn [default keys labels]
        (reject-control-flow)
        (.visitLookupSwitchInsn delegate default keys labels))
      (visitTryCatchBlock [start end handler type]
        (reject-control-flow)
        (.visitTryCatchBlock delegate start end handler type))
      (visitTypeInsn [opcode type]
        (when (and (= opcode Opcodes/NEW) (= type owner))
          (vreset! creates? true))
        (.visitTypeInsn delegate opcode type))
      (visitMethodInsn [opcode target name method-desc interface?]
        (when (and (= opcode Opcodes/INVOKESPECIAL) (= target owner)
                   (= name "<init>"))
          (vreset! delegates? true))
        (.visitMethodInsn delegate opcode target name method-desc interface?))
      (visitInsn [opcode]
        (when (and constructor? (= opcode Opcodes/RETURN))
          (cond
            (and @delegates? @creates?)
            (unsupported! "non-nil constructor both creates and calls the target"
                          {:descriptor desc})

            (and @delegates? (seq @writes))
            (unsupported! "delegating non-nil constructor writes a field"
                          {:descriptor desc :fields @writes})

            (and (not @delegates?) (not= @writes field-names))
            (unsupported! "incomplete non-nil constructor writes"
                          {:descriptor desc
                           :fields (set (remove @writes field-names))})))
        (.visitInsn delegate opcode))
      (visitFieldInsn [opcode target field field-desc]
        (if-let [^Type concrete (when (= target owner) (get types field))]
          (do
            (when-not (and (= field-desc object-descriptor)
                           (contains? #{Opcodes/GETFIELD Opcodes/PUTFIELD}
                                      opcode))
              (unsupported! "unsupported non-nil field reference"
                            {:field field :method method
                             :descriptor field-desc :opcode opcode}))
            (when (= opcode Opcodes/PUTFIELD)
              (when-not constructor?
                (unsupported! "non-nil field write outside constructor"
                              {:field field :method method}))
              (when (contains? @writes field)
                (unsupported! "repeated non-nil constructor write"
                              {:field field :descriptor desc}))
              (vswap! writes conj field)
              (.visitMethodInsn delegate Opcodes/INVOKESTATIC
                                objects-owner "requireNonNull"
                                require-non-null-descriptor false)
              (.visitTypeInsn delegate Opcodes/CHECKCAST
                              (.getInternalName concrete)))
            (.visitFieldInsn delegate opcode target field
                             (.getDescriptor concrete)))
          (.visitFieldInsn delegate opcode target field field-desc))))))

(defn- rewrite
  "Retype selected storage and references; check values at actual field writes.
   Overrides call the next visitor directly; ASM super methods only forward."
  [bytes owner types owner?]
  (let [class-name (.replace ^String owner \/ \.)
        unsupported! (fn [message data]
                       (throw (ex-info (str "Cannot add generated field checks to "
                                            class-name ": unsupported shape ("
                                            message "). The class is not the "
                                            "deftype shape that value-type "
                                            "supports. Fix: use Clojure 1.12.")
                                       (assoc data
                                              :value-type/error :unsupported-shape
                                              :class-name class-name))))
        field-names (set (keys types))
        seen (volatile! #{})
        constructors (volatile! 0)
        writer (ClassWriter. 0)
        visitor
        (proxy [ClassVisitor] [Opcodes/ASM6 writer]
          (visitEnd []
            (when (and owner? (not= @seen field-names))
              (unsupported! "missing non-nil field storage"
                            {:fields (set (remove @seen field-names))}))
            (when (and owner? (seq types) (zero? @constructors))
              (unsupported! "missing non-nil constructor" {}))
            (.visitEnd writer))
          (visitField [access field desc signature value]
            (let [^Type type (when owner? (get types field))]
              (when type
                (vswap! seen conj field)
                (when (or (zero? (bit-and access Opcodes/ACC_FINAL))
                          (not (zero? (bit-and access Opcodes/ACC_STATIC)))
                          (not= desc object-descriptor))
                  (unsupported! "unsupported non-nil field storage"
                                {:field field})))
              (.visitField writer access field
                           (if type (.getDescriptor type) desc)
                           signature value)))
          (visitMethod [access method desc signature exceptions]
            (let [delegate (.visitMethod writer access method desc
                                         signature exceptions)
                  constructor? (boolean (and owner? (= method "<init>")
                                             (seq types)))]
              (when constructor? (vswap! constructors inc))
              (method-visitor delegate owner types field-names method desc
                              constructor? unsupported!))))]
    (.accept (ClassReader. ^bytes bytes) visitor 0)
    (.toByteArray writer)))

(defn prepare
  "Return baseline owner and companion transforms for generated specifications.
  `fields/receiving-specifications` rejects other enforcement modes."
  [target specifications _delegate]
  (let [owner (.replace ^String target \. \/)
        types (into {} (map (juxt fields/emitted-name
                                  #(Type/getType ^Class (:type %))))
                    specifications)]
    {:owner #(rewrite % owner types true)
     :companion #(rewrite % owner types false)}))
