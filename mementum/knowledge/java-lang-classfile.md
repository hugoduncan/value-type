---
title: java.lang.classfile traps for value-class transforms
status: active
category: bytecode
tags: [classfile, valhalla, jep401, deftype, transform]
related: [mementum/knowledge/testing-clojure-generated-classes.md, mementum/memories/clojure-compiler-loader-hook.md]
depends-on: []
---

# java.lang.classfile traps

Source: task `002-value-class-transform`
(`src/value_type/impl/transform.clj` and its tests), and task
`003-capture-loader` (strict-field verification). Verified on
JDK 25 and JDK `27-jep401ea3`.

## Valhalla-only constants stop the namespace from loading

`ClassFile/ACC_IDENTITY` (0x20) and `ClassFile/ACC_STRICT_INIT`
(0x800) exist only on a Valhalla JDK. A reference to them makes the
namespace and all its tests fail to load on JDK 25. Use private
literal vars in one namespace. Tests read them with
`@#'ns/ACC_IDENTITY`. `ClassFile/latestMajorVersion` and
`ClassFile/PREVIEW_MINOR_VERSION` exist from JDK 24; they are safe
within the optional transformer, not in the baseline-loadable path.

Task `009-controlled-deftype-fallback` verified loading on Java 8, which
lacks ClassFile entirely. Keep all eager ClassFile dependencies behind
positive capability detection. API presence also does not prove preview
is enabled. See [controlled fallback boundaries](controlled-fallback-boundaries.md)
for the known-format probe and narrow absence catches (commit `c7f224f`).

## Change only the code of a method: `transformMethod`

`ClassBuilder.withMethod` + `.withCode` makes a new method. It drops
each other method element (`Exceptions`, `Signature`, annotations).
Use `ClassBuilder.transformMethod` with a `MethodTransform` that
replaces only the `CodeModel` (`.withCode`) and passes other elements
through with `.with`. Flags, name and descriptor stay.

## Parsed attributes have no value equality

Parsed attributes are `BoundAttribute`: `=` is identity, `toString`
gives only the name. Compare by kind:

- name: `(.stringValue (.attributeName a))` (a `Utf8Entry`);
- `SourceFileAttribute`: `(.stringValue (.sourceFile a))`;
- `SourceDebugExtensionAttribute`: `.contents` bytes.

Fail on an unknown kind, so a new kind is not ignored. With the
default shared constant pool, the code bytes (`.codeArray` of the
`CodeAttribute` in `(.code m)`) of pass-through methods stay equal.

## Building test classes

`ClassFile/of` with default options:

- stack maps accept a field write on `uninitializedThis` before the
  super call (the value-class constructor order);
- dead code is patched: an unreachable `return` becomes `athrow`;
- built code has no `LineNumber` elements.

## Bytes that fail verification (strict fields)

To test a `VerifyError` path, build a value class whose constructor
calls `Object.<init>` before the `putfield` of an
`ACC_FINAL|ACC_STRICT_INIT` field. Use no `ACC_IDENTITY` and the
preview version (`latestMajorVersion`, `PREVIEW_MINOR_VERSION`).

- `(ClassFile/of)` with default options throws
  `IllegalArgumentException` ("Unset fields mismatch") at build.
- With `ClassFile$StackMapsOption/DROP_STACK_MAPS`, the build
  succeeds. The JVM gives `VerifyError` ("All strict final fields
  must be initialized before super()") when it verifies the class.
- `DynamicClassLoader.defineClass` does not verify: the define
  succeeds. `Class.getDeclaredMethods` links and verifies (HotSpot),
  and does not initialize the class. `Lookup.defineClass` verifies at
  define.

Source: task `003-capture-loader` (`super-before-write-class` in
`test/runtime/value_type/impl/loader_test.clj`). Verified on JDK
`27-jep401ea3`.

## Clojure 1.12 `deftype` class file

- Class attributes: `SourceFile`, `SourceDebugExtension` only.
- Instance fields: `public final`, no attributes. Only a primitive
  hint (`^long`, `^double`, ...) changes the field type. A class hint
  (`^String s`) gives an `Object` field. A field is mutable (not
  final) when its metadata has a truthy `:volatile-mutable` or
  `:unsynchronized-mutable` value; `{:volatile-mutable false}` gives
  a final field.
- Each method has only `Code`. A static `getBasis` method is added.
  A keyword or other constant gives a static `const__N` field and
  `<clinit>`.
- One constructor: `aload_0`, `invokespecial Object.<init>()V`, then
  for each field `aload_0`, load, `putfield`, then `return`. Code
  elements also have `LineNumber`; no `LocalVariable`.
- Load slots: `long` and `double` take two slots; `boolean` loads as
  `int` (`TypeKind/asLoadable`).

## Retype strict storage and its writes in one build

When changing an Object field to concrete native storage, compose the field
change, strict initialization, constructor casts and field-reference rewriting
before ClassFile generates stack maps. A two-build attempt failed with
`Unset fields mismatch`: the intermediate strict-field descriptors and writes
did not agree. Do not suppress verification to permit that intermediate form.

Keep reference constructor parameters as Object. Compute argument slots from
the original signature, including two-slot long/double arguments, then cast
selected references before concrete writes. Rewrite matching owner-field
references in both owner and captured companion classes. Persist the same
transformed bytes that runtime definition uses.

Source: task `016-nil-restricted-fields`, implementation slice 3d (`da9c81d`)
and companion integration (`c8408e6`), on Clojure 1.12.6 and EA3.

## Stack-map hierarchy resolution needs the compiler's loader

The default ClassFile hierarchy resolver can fail at a branch merge involving
a dynamically defined field Class. Supply a `ClassHierarchyResolver` made with
`ofClassLoading` and the capture loader's delegate. Owner and companion
transforms must share this configuration; otherwise one path can still fail.
Keep the configuration helper behind the optional JDK boundary, so public
namespace loading remains safe on Java 8. A nil loader retains JDK defaults.

Source: task 016's public method fixture exposed the failure (`425010f`);
`c50f247` consolidated the configuration in `impl.class-file/configured`.
Runtime methods, captured-receiver reads and source-free AOT verify the paths.
