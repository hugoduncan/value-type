# 002 Value class transform

## Intent

Make the pure function that changes the bytes of a Clojure `deftype`
class into the bytes of a JEP 401 value class. The JVM must accept the
output when the instance fields of the input are final. When the input
does not have the expected shape, the function must fail with a clear
error and never return wrong bytes.

This is decisions D5 and D6 in `ramora/defvaluetype-design`. The
loader (D3, D4, D7) and the macro (D1, D8, D9) are later tasks. They
call this function.

## Problem

The Clojure compiler makes an identity class: version 52, the flag
`ACC_SUPER`, a constructor that calls `Object.<init>` first. A value
class needs a different version, different flags and a different
constructor order. `spike/valhalla/src/spike/vt.clj` shows that the
change is possible. The spike code has no input checks and no tests.

## Context

- Design: `ramora/defvaluetype-design/decisions.md` (D5, D6),
  `embodiment.md` (namespace `value-type.impl.transform`, boundary
  rules), `verification.md` (spike results).
- Verified class file facts (JDK `27-jep401ea3`, see "Flag values are
  literals"):
  `ClassFile/ACC_IDENTITY` = 0x20, `ClassFile/ACC_STRICT_INIT` =
  0x800, `ClassFile/PREVIEW_MINOR_VERSION` = 0xFFFF,
  `ClassFile/latestMajorVersion` = 71. A `javac` value class
  constructor writes the fields, then calls `Object.<init>`.
- Observed `deftype` output (Clojure 1.12.6): one constructor. Its
  code is `aload_0`, `invokespecial java/lang/Object.<init>()V`, then
  for each field in declaration order `aload_0`, load argument,
  `putfield`, then `return`. Instance fields are `public final`.
  Static fields hold constants.
- Test fixtures: binding `*compile-files*` to true and `*compile-path*`
  to a temporary directory around `eval` of a `deftype` form writes
  the class file (checked for this design). This uses only public
  API. `defrecord` forms work the same way.
- Test run: `clojure -M:test -m scry.cli` with `JAVA_CMD` set to the
  Valhalla `java`. scry finds only namespaces whose names end in
  `-test`. A REPL run must also check `[:summary :var-count]`.

## Scope

In scope:

1. The namespace `value-type.impl.transform` with one public function
   `->value-class`: bytes in, bytes out.
2. The output changes only these parts of the input:
   - version: `ClassFile/latestMajorVersion` of the running JDK,
     minor version `ClassFile/PREVIEW_MINOR_VERSION`;
   - class flags: the input flags without `ACC_IDENTITY`;
   - each instance field: the input flags plus `ACC_STRICT_INIT`;
   - the constructor code: for each instance field in declaration
     order, `aload_0`, load argument, `putfield`; then `aload_0`,
     `invokespecial java/lang/Object.<init>()V`; then `return`.
   All other parts pass through: this class, superclass, interfaces,
   static fields, the name, descriptor and attributes of each instance
   field, other methods and their code, class attributes, the
   constructor name, descriptor and flags.
   An instance field is a field without `ACC_STATIC`.
3. An input check before the change. The check does these steps in
   this order. It stops at the first mismatch and throws `ex-info`
   (see "Error contract"). Each step names its message part.
   1. Count of constructors (message part `count of constructors`):
      the input has exactly one method named `<init>`.
   2. Parameters (message part `parameters`): the list of constructor
      parameter descriptors is equal to the list of instance field
      descriptors, in declaration order.
   3. Code: remove labels, line numbers and local variable entries
      (`LocalVariable`, `LocalVariableType`) from the constructor code
      elements. Compare the result, position by position, with this
      expected list (`n` instance fields, field `i` from 0 in
      declaration order):
      - position 1: `aload 0`;
      - position 2: `invokespecial` of `java/lang/Object.<init>()V`
        (owner `java/lang/Object`, not an interface method);
      - for each field `i`: `aload 0`; a load of slot `s(i)` with the
        type kind `TypeKind.from(descriptor of field i).asLoadable()`;
        `putfield` with owner this class and the name and descriptor
        of field `i`. `s(0)` = 1. `s(i+1)` = `s(i)` + 2 when field `i`
        is `long` or `double`, else `s(i)` + 1;
      - last position (`3n + 3`): `return` with no value.
      A load matches by type kind and slot, not by opcode (`aload_0`
      equals `aload 0`). Each other code element (for example an
      exception table entry or a code attribute) has a position in the
      list, so it is a mismatch. The first difference is the first
      position where the elements differ or where only one list has an
      element. A first difference at position 1 or 2 gives the message
      part `super call`. A later first difference gives `field writes`.
      This includes a missing `return` and extra elements.
   The super call check also proves that the superclass is `Object`.
4. A test namespace `value-type.impl.transform-test` (see
   "Verification").
5. `mementum/state.md`: update the status and the next step.

## Out of scope

- The capture loader, AOT file writing and the macro.
- Field validation (mutable fields, reserved names). The macro does
  this (D8). A non-final field in the input makes the JVM reject the
  class. The macro task (`value-type.core`) wraps that error (D9).
- The `LoadableDescriptors` attribute and null-restricted fields.
- Inputs that are already value classes, abstract classes, records.
- A version parameter. The running JDK gives the version.
- Changes to `spike/valhalla/` and to the README.

## Decisions

### Rebuild the constructor; do not move instructions

The check proves that the input constructor is the `deftype` shape.
So the function can make the new code from the field list alone. This
is simpler than an edit of the instruction list (D6). The constructor
loses its line number entries. The constructor has no user code, so
this has no effect on debugging.

### Check the whole constructor shape

- Choice: compare the full instruction sequence with the expected
  shape.
- Rejected: check only the count of constructors. A change in Clojure
  output (for example a new field write or a different super call)
  would then give a class with different behaviour. The intent forbids
  wrong bytes.
- One check covers the superclass too: the super call must be
  `java/lang/Object.<init>()V`.

### Flag values are literals

- Choice: `value-type.impl.transform` defines `ACC_IDENTITY` (0x20)
  and `ACC_STRICT_INIT` (0x800) as private constant vars. The version
  comes from `ClassFile/latestMajorVersion` and
  `ClassFile/PREVIEW_MINOR_VERSION`. These two are in the
  `java.lang.classfile` API of each JDK from 24, also without Valhalla.
- Rejected: the fields `ClassFile/ACC_IDENTITY` and
  `ClassFile/ACC_STRICT_INIT`. Only a Valhalla JDK has them. With
  them, the namespace and its unit tests do not load on other JDKs.
  This degrades the Testability target in
  `ramora/defvaluetype-design/qualities.md` ("The transform is
  testable without a Valhalla JVM. Unit tests run on bytes.").
- Rationale: literals keep the Testability target. The spike
  transform with literals runs on JDK 25 without Valhalla (checked for
  this design). The Changeability target stays: the flag values are
  only in `transform`.
- Tests: `value-type.impl.transform-test` does not write 0x20 or
  0x800. It reads the private vars of `transform`
  (`@#'value-type.impl.transform/ACC_IDENTITY` and
  `@#'value-type.impl.transform/ACC_STRICT_INIT`). So the structure
  tests check the transform logic, not the flag values.
- Risk: a new Valhalla build can change a flag value. The structure
  tests do not find this change. The round-trip tests fail: the JVM
  rejects the class, or `Class::isValue` is false. The values in
  "Context" are verified on JDK `27-jep401ea3`.

### Get fixture bytes from the Clojure compiler

- Choice: a test helper evaluates a `deftype` or `defrecord` form
  with `*compile-files*` bound to true and `*compile-path*` bound to a
  new temporary directory. It reads the class file.
- Rejected: a hand-written copy of `deftype` bytes. It does not follow
  changes in Clojure output, and such a change is the risk that the
  check guards against. Rejected: AOT `compile` of a fixture
  namespace. It needs a source file and the classpath.
- Exception: a mismatch fixture that Clojure cannot make (for example
  one constructor with a different order of instructions) is built
  with `java.lang.classfile` in the test.

### Round-trip test loader

- Choice: for each round trip, a new child-first test loader: a
  `proxy` of `clojure.lang.DynamicClassLoader`. Its parent is a new
  `DynamicClassLoader`. The test loader overrides `loadClass`:
  - the fixture class name: define the output bytes;
  - a name that starts with the fixture class name and `$` (an inner
    class, for example the inner `fn`): define the class file from the
    `*compile-path*` directory of the fixture;
  - each other name: load it from the parent. The parent finds Clojure
    classes and, through the static class cache, the protocol
    interfaces that `eval` defined. So the value class implements the
    same interfaces as the `deftype` class.
  The test loader defines with the public
  `DynamicClassLoader.defineClass(String, byte[], Object)` and keeps
  its own map of defined classes. A `proxy` cannot call the protected
  `ClassLoader` methods.
- Rationale: the inner `fn` class resolves the fixture class name
  through its defining loader. That is the test loader, so it links to
  the value class. Checked for this design on JDK `27-jep401ea3`:
  `Class::isValue` is true, a protocol method gives the same result as
  on the `deftype` instance, and the inner `fn` returns an instance
  `identical?` to `this`.
- Rejected: define only the output class in a new loader. The inner
  `fn` class that Clojure defined links to the identity class in the
  `eval` loader. Then a method that uses the inner `fn` can throw
  `ClassCastException` (the reason that D3 rejects "define the class
  again").

### Unique fixture class names

`DynamicClassLoader` keeps a static class cache by name. The `eval` of
a fixture puts its classes in this cache. The round-trip test loader
puts the value class and the inner classes in the cache by the same
names. So each fixture has a unique class name (for example with
`gensym`). Then a class of one test cannot replace a class that
another test uses.

## Error contract

`->value-class` throws `ex-info` when the input check fails. The data
has:

- `:value-type/error` `:unexpected-constructor`;
- `:class-name`: the binary name of the input class, a string.

The message gives the class name, the part, the cause and the fix:

- the part: the message part of the check step that fails, as an
  exact string: `count of constructors`, `parameters`, `super call` or
  `field writes`. Scope item 3 gives the part for each mismatch;
- the cause: the constructor is not the `deftype` constructor shape
  that this library supports;
- the fix: use the supported Clojure version, `Clojure 1.12` (exact
  string).

`value-type.core` does not wrap this error, so `defvaluetype` users
see this message (Error clarity target in
`ramora/defvaluetype-design/qualities.md`).
Other input errors (for example bytes that are not a class file) are
the exceptions of `java.lang.classfile`. They are not wrapped.

## Invariants

- For accepted input whose instance fields are final, the output is a
  value class that the running JVM defines with no error.
- The function is pure: the output depends only on the input bytes
  and the running JDK version.
- `value-type.impl.transform` does not depend on
  `clojure.lang.Compiler` or `clojure.lang.DynamicClassLoader`
  (assertion A-18).
- The namespace loads with no reflection warnings.

## Verification

The test namespace must cover:

1. Structure. The fixtures are:
   - a `deftype` with no fields;
   - one `deftype` with five instance fields in this order: `int`,
     `long`, `double`, `boolean`, `Object` (hints `^int`, `^long`,
     `^double`, `^boolean`, and no hint). A field follows each
     two-slot field, so the parameter slots are 1, 2, 4, 6, 7 (the
     rule `s(i+1)` of scope item 3). The `boolean` field has an `int`
     load (`asLoadable`);
   - a `deftype` with a protocol method that has a branch;
   - a `deftype` with an inner `fn` that uses `this`;
   - a `deftype` with static constants.
   For each fixture, the test checks:
   - the version, the class flags and the instance field flags are as
     in scope item 2. The flag values come from the private vars of
     `transform` (see "Flag values are literals");
   - this class, superclass, interfaces, static fields, the name,
     descriptor and attributes of each instance field, other methods
     (name, descriptor, flags, code bytes), class attributes, and the
     constructor name, descriptor and flags are the same as the input;
   - the constructor code is the new shape.
2. Round trip. For each fixture of Verification 1, a round-trip test
   loader (see "Round-trip test loader") defines the output class.
   `Class::isValue` is true. A new instance has the field values. Two
   instances with the same field values are `identical?`.
   For each method that the fixture form defines, the test calls the
   method with the same arguments on a value object and on a
   `deftype` instance with the same field values:
   - when the method returns the receiver (the inner `fn` fixture),
     each result is `identical?` to its own receiver;
   - each other method returns a value that is not a fixture instance
     and contains no fixture instance. The two results are `=`.
   Field values, arguments and results are not `NaN` (Clojure `=` on
   `NaN` is false).
3. Errors. Each input below throws `ex-info` with
   `:value-type/error :unexpected-constructor` and the correct
   `:class-name`. The test also asserts that the message contains the
   expected part (exact string) and `Clojure 1.12`.
   - a `defrecord` input (more than one constructor): `count of
     constructors`;
   - a built input with one `long` instance field and one constructor
     with the descriptor `(I)V`: `parameters`;
   - a built input with one `long` instance field and one constructor
     `(J)V` that writes the field before the super call (the order
     that `->value-class` writes): `super call`;
   - a built input with one `long` instance field and one constructor
     `(J)V` in the `deftype` shape, but the value of the field write is
     `lconst_0`, not a load of slot 1: `field writes`.

## Acceptance criteria

1. The tests in "Verification" exist and pass on the Valhalla JDK with
   `clojure -M:test -m scry.cli` (exit 0). The full suite passes.
2. A REPL run of `value-type.impl.transform-test` with `scry.core/run`
   gives `:pass? true` and a `[:summary :var-count]` equal to the count
   of `deftest` vars in `value-type.impl.transform-test`. This count is
   more than 0.
3. Loading `value-type.impl.transform` gives no reflection warnings.
4. The namespace requires and imports nothing from the Clojure
   compiler (checked by review).
5. Each public var has a docstring that gives the input, the output and
   the error contract.
6. `mementum/state.md` shows the transform as done and gives the next
   step (the loader task).
