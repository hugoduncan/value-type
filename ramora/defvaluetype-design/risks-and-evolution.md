# Risks and evolution

## Assumptions (verified)

A spike on a Valhalla early-access JDK verified all assumptions below.
See [Verification](verification.md). Verify them again on each new
early-access build.

1. The flag values: no `ACC_IDENTITY` (0x0020) for a value class.
   `ACC_STRICT_INIT` (0x0800) for fields.
2. The version rule: major equals the feature release, minor 0xFFFF.
3. The JVM accepts the D6 constructor. The classfile API makes the
   correct stack map data for it.
4. Old stack map frames from Clojure (version 52) stay valid at the new
   version.
5. The reflection API that tells if a class is a value class (for
   example `Class::isValue` or `AccessFlag.IDENTITY`).
6. The compiler order in D3 and D7: the macro runs inside the
   `LOADER` binding, and `deftype*` uses the same binding.
7. Inner function classes that use the type link to the value class.

When an assumption becomes false, change the decision that depends on
it and update this document.

## Risks

| Risk | Effect | Mitigation |
|---|---|---|
| JEP 401 class file changes in a new build. | The JVM rejects the class. | Changes are local to `transform`. D9 wraps the error. |
| Clojure changes `deftype` output or the `LOADER` order. | Install or transform fails. | D6 shape check. D9 runtime check. Pin Clojure 1.12.6 (`deps.edn`). The `deftype` compatibility corpus (`value-type.deftype-compat-test`); when the Clojure version changes, copy the corpus again from the new tag. |
| `macroexpand` from tooling installs a loader. | A loader stays in the tooling binding frame. | The loader changes only the target name. Harmless. |
| Users depend on identity. | `IdentityException` or changed equality. | README section on semantic differences. |
| AOT output is tied to one JDK release. | The class does not load on a different JDK. | Document. Rebuild per JDK release. |

## Expected evolution

- Null-restricted fields, when the JEP is available. This needs field
  metadata and the `LoadableDescriptors` attribute. Change: `core`
  validation and `transform`.
- Abstract value classes. `deftype` cannot express them. This needs a
  new macro.
- A record form (`defvaluerecord`). Records keep mutable hash caches.
  This needs a new design for hash caching.
- An optional fallback to `deftype` (reverses D10 per user choice).
- JEP 401 becomes final. Then the version rule drops the preview minor
  version.
- Clojure supports value classes. Then the library can retire.
