🔁 To add a branch to a constructor in the ASM identity backend, emit the
stack map frames and the max stack yourself.

Facts (`src/value_type/impl/identity_backend.clj`):

- `rewrite` uses `(ClassWriter. 0)` and `ClassReader` flag 0: ASM computes
  no frames and no max stack. Clojure 1.12 writes class version 52, so a
  branch without a frame fails verification.
- Supported constructors have no frames (`method-visitor` rejects jumps).
  An entry check before `invokespecial` can give each jump target a
  compressed `F_SAME` frame (local 0 stays `uninitializedThis`). Set max
  stack to `max(original, 3)` for `new`/`dup`/`ldc`.
- Emit the check on the delegate visitor, not through the checked path,
  or the jump rules reject it.
- Delegation is known only after the body is read. Classify constructors
  in a first read pass (`constructor-marks`), then rewrite.
- A parameter index is not a local slot: compute slots from the
  descriptor (`long`/`double` take two). Test with a `long` parameter.

Verify with `-Xverify:all` on Java 8 and on EA3 with preview off.

Source: task `022-closed-record-shape` (D10 identity check, Slice 5).
