# Transform and AOT decisions

Scope: the value-class path unless stated otherwise. Ordinary definitions have
no enabled non-nil fields. Definitions with enabled non-nil fields also
follow the [portable field contract](../../non-nil-field-design.md).

## D5. Transformation with `java.lang.classfile` (technology)

- Choice: value-class byte transformation uses the JDK classfile API. Identity
  output for definitions with enabled non-nil fields uses Clojure's bundled
  ASM, as field decision D3 specifies. Inputs include optional storage
  descriptors and a class loader for hierarchy resolution during stack-map
  generation. The capture loader supplies its delegate.
  For ordinary definitions it makes these changes:
  1. Version: running feature release, minor 0xFFFF.
  2. Class flags: remove `ACC_SUPER`/`ACC_IDENTITY`. Keep the others.
  3. Instance fields: add `ACC_STRICT_INIT`.
  4. Constructor: replace the code (D6).
  Ordinary non-constructor methods and unrelated attributes pass through.
  Non-nil fields also get concrete descriptors. Native enforcement adds a
  merged `NullRestricted` annotation; generated enforcement adds constructor nil
  checks. Existing annotation values and unrelated field elements remain.
  Selected Object field references in owner and companion methods change to
  concrete descriptors; other references remain unchanged.
- Rejected for value-class output: ASM (can lag Valhalla, extra dependency).
  `clojure.asm` (old fork, no Valhalla flags). This does not reject a baseline
  backend for ordinary identity bytecode.
- Rationale: the JDK API moves with the JDK. Existing stack map frames
  stay valid at a higher version.
- Commitment: firm.

## D6. Make a new constructor from the field list (data model)

- Choice: the new constructor code is: for each field, `aload_0`, load
  argument, `putfield`; then `aload_0`, `invokespecial Object.<init>`;
  `return`. For selected non-nil field references, insert `checkcast` before
  `putfield` and use the concrete storage descriptor. Native storage rejects
  nil where selected; otherwise an explicit constructor check rejects it. The cast
  enforces type compatibility. Identity constructors retain their ordinary superclass
  initialization order rather than the value-class order above. Constructor reference parameters
  remain Object. Argument order, arity and slots come from the original field
  model, including two-slot long and double arguments.
- Rejected: move instructions in the existing code. Fragile.
- Rationale: constructor generation uses backend-produced stack maps and the
  original class-file field model. Generated checks can require additional code.
- Check: the transform fails when the old constructor has a different
  shape. This finds a change in Clojure output early.
- Commitment: firm.

## D7. AOT output (deployment)

- Choice: on every transformed path, when `*compile-files*` is true,
  the capture loader writes
  the changed bytes with `Compiler/writeClassFile`. This replaces the
  file that the compiler wrote before.
- Rationale: the compiler writes the file before it defines the class.
- Ordinary fallback AOT keeps the compiler identity-class bytes. Fallback for
  definitions with enabled non-nil fields writes transformed identity bytes
  with typed storage and checks. Representation and enforcement are fixed at
  compilation. The fallback load guard is specified in D13.
- Commitment: firm. Source-free AOT tests must verify both representations
  and fallback receiving-policy guards.
