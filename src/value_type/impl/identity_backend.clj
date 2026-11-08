(ns value-type.impl.identity-backend
  "Baseline-safe concrete storage and constructor checks for identity output."
  (:require [value-type.impl.compiler :as compiler]
            [value-type.impl.fields :as fields])
  (:import (clojure.asm ClassReader ClassVisitor ClassWriter MethodVisitor Opcodes
                        Type)
           (java.util Objects)))

(set! *warn-on-reflection* true)

(def ^:private object-descriptor (Type/getDescriptor Object))

(def ^:private objects-owner (Type/getInternalName Objects))

(def ^:private require-non-null-descriptor
  (Type/getMethodDescriptor (Type/getType Object)
                            (into-array Type [(Type/getType Object)])))

(defn- parameter-slots
  "Return the local slots of the parameters of the instance method
  descriptor `desc`; `long` and `double` take two slots."
  [desc]
  (->> (Type/getArgumentTypes ^String desc)
       (reductions (fn [slot ^Type type] (+ slot (.getSize type))) 1)
       butlast
       vec))

(defn- require-nil!
  "Emit the D10 check: throw `IllegalArgumentException` with `message` when
  the parameter in one of `slots` is not nil. Each jump target gets an
  `F_SAME` frame; the original constructor has no frame (control flow is
  rejected)."
  [^MethodVisitor delegate slots message]
  (doseq [slot slots]
    (let [skip (clojure.asm.Label.)]
      (.visitVarInsn delegate Opcodes/ALOAD (int slot))
      (.visitJumpInsn delegate Opcodes/IFNULL skip)
      (.visitTypeInsn delegate Opcodes/NEW "java/lang/IllegalArgumentException")
      (.visitInsn delegate Opcodes/DUP)
      (.visitLdcInsn delegate message)
      (.visitMethodInsn delegate Opcodes/INVOKESPECIAL
                        "java/lang/IllegalArgumentException" "<init>"
                        "(Ljava/lang/String;)V" false)
      (.visitInsn delegate Opcodes/ATHROW)
      (.visitLabel delegate skip)
      (.visitFrame delegate Opcodes/F_SAME 0 nil 0 nil))))

(defn- method-visitor
  "Check constructor control flow and writes; retype selected field references.
  A constructor that calls a constructor of `owner` delegates: it must write
  no selected field and must not also create an `owner` instance (then the
  receiver of the call is not known). Each other constructor writes each
  selected field once. `check` is nil or `[slots message]`: the D10 check
  to emit at the entry of this constructor (see `require-nil!`)."
  [^MethodVisitor delegate owner types field-names method desc constructor?
   check unsupported!]
  (let [writes (volatile! #{})
        delegates? (volatile! false)
        creates? (volatile! false)
        reject-control-flow
        (fn []
          (when constructor?
            (unsupported! "unsupported constructor control flow"
                          {:method method :descriptor desc})))]
    (proxy [MethodVisitor] [Opcodes/ASM6 delegate]
      (visitCode []
        (.visitCode delegate)
        (when-let [[slots message] check]
          (require-nil! delegate slots message)))
      (visitMaxs [stack locals]
        (.visitMaxs delegate (if check (max stack 3) stack) locals))
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
            (unsupported! "constructor both creates and calls the target"
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

(defn- constructor-marks
  "Return `[constructors delegating]` for `bytes`: the constructor
  descriptors in class order, and the set of those that call
  `invokespecial <owner>.<init>` (D8 delegation mark)."
  [bytes owner]
  (let [constructors (volatile! [])
        marked (volatile! #{})]
    (.accept (ClassReader. ^bytes bytes)
             (proxy [ClassVisitor] [Opcodes/ASM6]
               (visitMethod [_access method desc _signature _exceptions]
                 (when (= method "<init>")
                   (vswap! constructors conj desc)
                   (proxy [MethodVisitor] [Opcodes/ASM6]
                     (visitMethodInsn [opcode target name _desc _interface?]
                       (when (and (= opcode Opcodes/INVOKESPECIAL)
                                  (= target owner) (= name "<init>"))
                         (vswap! marked conj desc)))))))
             ClassReader/SKIP_DEBUG)
    [@constructors @marked]))

(defn- hidden-indexes
  "Return the parameter indexes of `__meta` and `__extmap` (the hidden
  fields with no tag) of a constructor with `n` parameters."
  [n]
  (into []
        (keep-indexed (fn [k [_ tag]]
                        (when (nil? tag) (compiler/hidden-field-index n k))))
        compiler/record-hidden-fields))

(defn- closed-checks
  "Return a map of non-delegating constructor descriptor to its D10 check
  `[slots message]`: the slots of the `__meta` and `__extmap`
  parameters (`hidden-indexes`). Reject shapes that the check cannot address."
  [bytes owner class-name unsupported!]
  (let [[constructors delegating] (constructor-marks bytes owner)
        checked (remove delegating constructors)
        message (fields/closed-check-message class-name)]
    (when (empty? checked)
      (unsupported! "closed class without non-delegating constructor" {}))
    (doseq [desc checked
            :let [types (Type/getArgumentTypes ^String desc)
                  n (count types)]]
      (when (or (< n compiler/hidden-field-count)
                (some #(not= object-descriptor
                             (.getDescriptor ^Type (aget types (int %))))
                      (hidden-indexes n)))
        (unsupported! "closed constructor without Object __meta and __extmap parameters"
                      {:descriptor desc})))
    (into {}
          (for [desc checked
                :let [n (count (Type/getArgumentTypes ^String desc))]]
            [desc [(mapv (parameter-slots desc) (hidden-indexes n))
                   message]]))))

(defn- rewrite
  "Retype selected storage and references; check values at actual field writes.
  For a closed owner, add the D10 check at the entry of each non-delegating
  constructor. `role` is `:companion`, `:owner` or `:closed-owner`.
  Overrides call the next visitor directly; ASM super methods only
  forward."
  [bytes owner types role]
  (let [owner? (not= role :companion)
        closed? (= role :closed-owner)
        class-name (.replace ^String owner \/ \.)
        unsupported! (fn [message data]
                       (throw (ex-info (str "Cannot rewrite class "
                                            class-name ": unsupported shape ("
                                            message "). The class is not the "
                                            "deftype shape that value-type "
                                            "supports. Fix: use Clojure 1.12.")
                                       (assoc data
                                              :value-type/error :unsupported-shape
                                              :class-name class-name))))
        checks (when closed?
                 (closed-checks bytes owner class-name unsupported!))
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
                                             (or (seq types) closed?)))]
              (when constructor? (vswap! constructors inc))
              (method-visitor delegate owner types field-names method desc
                              constructor? (get checks desc) unsupported!))))]
    (.accept (ClassReader. ^bytes bytes) visitor 0)
    (.toByteArray writer)))

(defn prepare
  "Return baseline owner and companion transforms for generated specifications.
  `fields/receiving-specifications` rejects other enforcement modes.
  `shape` is `:plain`, `:record` or `:closed`. Without specifications,
  the companion transform returns its bytes unchanged."
  [target specifications _delegate shape]
  (let [owner (.replace ^String target \. \/)
        types (into {} (map (juxt fields/emitted-name
                                  #(Type/getType ^Class (:type %))))
                    specifications)]
    {:owner #(rewrite % owner types
                      (if (= shape :closed) :closed-owner :owner))
     :companion #(if (empty? types)
                   %
                   (rewrite % owner types :companion))}))
