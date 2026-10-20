# Verification (spike)

Date: 2026-10-20. Code: `spike/valhalla/`.

Environment:

- JDK `openjdk-27-jep401ea3+1-1`, macOS aarch64, `--enable-preview`.
- Clojure 1.12.1 and 1.12.6.

## Results

| # | Assumption | Result | Evidence |
|---|---|---|---|
| 1 | Value class: no `ACC_IDENTITY` (0x0020). Fields: `ACC_STRICT_INIT` (0x0800) + `ACC_FINAL`. | True | `javap` on a `javac` value class: class flags 0x0011, field flags 0x0810. `ClassFile/ACC_IDENTITY` = 0x20, `ClassFile/ACC_STRICT_INIT` = 0x800. |
| 2 | Version: feature major, minor 0xFFFF. | True | `javac` output 71.65535. `ClassFile/latestMajorVersion` = 71. `ClassFile/PREVIEW_MINOR_VERSION` = 0xFFFF. |
| 3 | The JVM accepts the D6 constructor. The classfile API supports Valhalla. | True | The `javac` constructor has the same shape: `putfield`s, then `Object.<init>`. The spike makes the constructor with `java.lang.classfile` (`transformClass`, `reify` of `ClassTransform`). It verifies with `long`, `double` and `Object` fields. No stack map frames are needed. |
| 4 | Clojure stack map frames (v52) stay valid at v71. | True | A method with a branch passes through unchanged and verifies. |
| 5 | Reflection API. | True | `Class::isValue` and `Class::isIdentity` exist. |
| 6 | Compiler order for D3 and D7. | True | The capture loader catches the class on the paths `require`, `eval`, top-level `do`, redefinition and AOT `compile`. |
| 7 | Inner function classes link to the value class. | True | An `fn` in a method that captures `this` runs. |

The numbers follow the list in
[Risks and evolution](risks-and-evolution.md).

## Other observations

- AOT: the written `.class` file is the value class. A new JVM loads it
  from `classes/` without the source, and it is a value class.
- Without `--enable-preview`, the define fails with
  `UnsupportedClassVersionError: Preview features are not enabled for
  spike/use/Rect (class file version 71.65535). Try running with
  '--enable-preview'`. The message already names the fix. D9 can keep
  the cause and add only the `:value-type/error` key.
- `(eval (macroexpand form))`: `Compiler/LOADER` is bound by the outer
  top-level form, so `install!` returns true. But `eval` pushes a new
  loader, and the class is an identity class. The D9 check throws. So
  "installed" does not mean "caught". D9 is the only guard.
- The expansion must use the fully qualified class symbol after
  `deftype`. The `import` has not run when the compiler analyzes the
  `let`. `clojure.core/deftype` does the same.
- Value semantics in Clojure: `identical?` and `=` are true for equal
  fields. Fields with equal vectors that are not the same object give
  `identical?` false and `=` false. `locking` throws
  `IdentityException`. `WeakReference` throws `IdentityException`.
- `javac` does not write a `LoadableDescriptors` attribute for a class
  with only `long` and `Object` fields. The transform does not need it.
- `AccessFlag.IDENTITY` and `AccessFlag.STRICT_INIT` report no
  locations in reflection. Use `Class::isValue`, not access flag sets.
