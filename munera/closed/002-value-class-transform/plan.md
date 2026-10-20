# Plan: 002 Value class transform

## Approach

Build `value-type.impl.transform/->value-class` from the spike
(`spike/valhalla/src/spike/vt.clj`), with two additions the spike does
not have: the input check of scope item 3, and the error contract.
Then build the test namespace in three groups that match Verification
1-3: structure, round trip, errors.

Key decisions:

1. Transform mechanics follow the spike: parse with `ClassFile/of`,
   use `transformClass` with a `ClassTransform` that rewrites the
   version, the class flags, the instance field flags and the
   constructor code, and passes all other elements through with
   `(.with b e)`. The default shared constant pool keeps the code
   bytes of other methods equal to the input, which Verification 1
   needs. The constructor is not built with `.withMethod` as in the
   spike (see decision 5).
2. The input check runs on the parsed `ClassModel` before the
   transform. It implements scope item 3 in order: constructor count,
   parameter descriptors, code shape. The code comparison strips
   `Label`/`LineNumber`/`LocalVariable`/`LocalVariableType` elements,
   then compares position by position against the expected list built
   from the instance fields. Loads match by
   `TypeKind.from(desc).asLoadable()` and slot, not by opcode. The
   first-difference position selects the message part (`super call`
   for positions 1-2, `field writes` later).
3. Flags: private vars `ACC_IDENTITY` (0x20), `ACC_STRICT_INIT`
   (0x800) (no JDK 25 constant). The instance-field test uses
   `ClassFile/ACC_STATIC`. Version from `ClassFile/latestMajorVersion` and
   `ClassFile/PREVIEW_MINOR_VERSION`. No Valhalla-only API in the
   namespace, so it loads on any JDK ≥ 24 (A-18 plus Testability).
4. Error: one throwing helper that builds the `ex-info` with
   `:value-type/error :unexpected-constructor`, `:class-name`, and a
   message that contains the class name, the exact part string, the
   cause and `Clojure 1.12`.
5. Constructor rebuild: generate the code from the field list (design
   decision "Rebuild the constructor"): field writes in declaration
   order with `loadLocal`/`slotSize`, then the super call, then
   `return`. Change only the code (scope item 2): use
   `ClassBuilder.transformMethod` on the `<init>` method with a
   `MethodTransform`. It replaces the `CodeModel` element with
   `(.withCode mb <new code>)` and passes each other method element
   (flags, attributes such as `Exceptions` or `Signature`) through
   with `(.with mb e)`. The method name and descriptor stay.
   Rejected: the spike `.withMethod` + `.withCode`. It makes a new
   method and drops each other constructor method element. (Checked
   on JDK 25: with `transformMethod`, an `Exceptions` attribute on the
   input constructor stays in the output.)
6. Test helpers (facts from implementation.md):
   - Fixture bytes: `eval` a `deftype`/`defrecord` form with
     `*compile-files*` true and `*compile-path*` bound to a fresh temp
     directory; read the class file. Unique class names via `gensym`.
   - Built mismatch fixtures (Verification 3) use `ClassFile/of` with
     `.withField`/`.withMethodBody`; default stack maps accept a field
     write before the super call.
   - Round-trip loader: `proxy` of `DynamicClassLoader` with a fresh
     `DynamicClassLoader` parent; override `loadClass`; define with
     the public `defineClass(String, byte[], Object)`; keep an atom
     cache (a proxy cannot call protected `findLoadedClass`). Inner
     `$...` classes load from the fixture's `*compile-path*`
     directory. Create instances with
     `clojure.lang.Reflector/invokeConstructor`.
   - The `deftype` comparison instance comes from the `Class` that
     `eval` returned, not a lookup by name (static cache is
     overwritten by the round trip).
   - Structure tests read flag values via
     `@#'value-type.impl.transform/ACC_IDENTITY` etc.
   - Pass-through comparison: parsed attributes are `BoundAttribute`
     objects; `=` is identity. Compare names, descriptors and flags as
     strings and ints. Compare the code of other methods by bytes
     (shared constant pool keeps them equal).
   - Attribute comparison (class attributes, and the attributes of
     each instance field and each static field): the lists of
     attribute names (`(.stringValue (.attributeName a))`), in order,
     are equal. Then compare each attribute pair by kind:
     `SourceFileAttribute` by `(.stringValue (.sourceFile a))`;
     `SourceDebugExtensionAttribute` by `.contents` bytes
     (`java.util.Arrays/equals`). An attribute of another kind fails
     the test, so a new kind is not ignored. Then add a rule for that
     kind to the test helper.
   - Static-constants fixture: a keyword in a method body makes a
     static field and a `<clinit>` that pass through.
   - `identical?` on value objects compares an `Object` field by
     reference: give both value objects the same `Object` instance.
   - No `NaN` in values compared with `=`.
   - `.isValue` on a non-Valhalla JDK is a reflective call with a
     warning; the test namespace still loads.
7. Verification: run with `clojure -M:test -m scry.cli` and `JAVA_CMD`
   set to the `java` executable of the Valhalla EA JDK, as an absolute
   path: `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
   (`~` does not expand in all callers). Also a REPL `scry.core/run`
   check of `[:summary :var-count]`.

## Risks

- Clojure 1.12.6 `deftype` output could differ from the observed
  shape on some fixture (for example extra code attributes). The check
  would then reject a design fixture. Only the constructor code of the
  five-field fixture was checked for the design. Action when a design
  fixture fails the check: stop; record the actual constructor code
  elements in implementation.md; raise a design question. Do not
  change the check rule (scope item 3) or the fixture list
  (Verification 1) in the implementation.
- The round-trip loader is sensitive to class resolution order. The
  design-checked recipe (child-first `loadClass`, parent
  `DynamicClassLoader`, `$`-prefix match from the compile dir) is
  known to work on JDK `27-jep401ea3`; follow it exactly.
- A new Valhalla build can change flag values. The structure tests
  cannot detect this; the round-trip tests will fail loudly. Accepted
  in the design.
- `scry` only finds namespaces ending in `-test`; a misnamed test file
  would silently not run. Acceptance 2 (`var-count` check) guards this.

## Slice order

1. Transform namespace: flags, pass-through transform, constructor
   rebuild (no input check yet); loads with no reflection warnings.
2. Input check and error contract in `->value-class`.
3. Test helpers: fixture compiler, built-fixture helpers, round-trip
   loader.
4. Structure tests (Verification 1) for the five fixtures.
5. Round-trip tests (Verification 2).
6. Error tests (Verification 3).
7. Full-suite run on the Valhalla JDK, REPL var-count check,
   docstrings, `mementum/state.md` update.
