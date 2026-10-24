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

Also run the method-call benchmark again on each new early-access
build, and update [Benchmark](benchmark.md).

When an assumption becomes false, change the decision that depends on
it and update this document.

## Risks

| Risk | Effect | Mitigation |
|---|---|---|
| JEP 401 class file changes in a new build. | The JVM rejects the class. | Review both `transform` and the capability probe. Unknown probe failures propagate; D9 wraps user-class definition errors. |
| Clojure changes `deftype` output or the `LOADER` order. | Install or transform fails. | D6 shape check. D9 runtime check. Pin Clojure 1.12.6 (`deps.edn`). The `deftype` compatibility corpus (`value-type.deftype-compat-test`); when the Clojure version changes, copy the corpus again from the new tag. |
| `macroexpand` from tooling installs a loader. | A loader stays in the tooling binding frame. | The loader changes only the target name. Harmless. |
| Users depend on identity. | `IdentityException` or changed equality. | README section on semantic differences. |
| Value-class AOT output is tied to one JDK release. | The class does not load on a different JDK. | Document. Rebuild per JDK release; no load-time conversion. |
| Optional APIs load before capability detection. | Public namespace fails before fallback. | D12 dependency boundary; isolated consumer tests without those APIs. |
| API presence is mistaken for usable support. | Preview-disabled or incompatible JVM fails late. | Verify D12 detection against real JVMs; retain defect visibility. |
| Fallback changes identity and equality semantics. | A library behaves differently across JVMs. | Document both modes; application-owned strict policy. |
| Concurrent fallback attempts duplicate diagnostics. | Warning noise. | D13 atomic warning state and concurrency tests. |
| Application policy is set after dependency loading. | Definitions use the wrong policy. | Startup configuration; no runtime policy changes. |
| A dependency was AOT-compiled as an identity class. | Strict application can load a namespace containing fallback inside a function. | D13 guard enforces policy at form evaluation: during initialization for top-level forms, on invocation for forms inside functions. Direct class access that bypasses the form remains unguarded. |
| Compiler wraps the strict Error. | Callers see a different outer exception type. | Source compilation wraps plain Error in CompilerException. Failed AOT retries can throw NoClassDefFoundError. |

## Fallback evidence and remaining assumptions

Production tests verify D10–D13 on Clojure 1.12.6, Java 8 and Valhalla
27-jep401ea3+1-1 with preview enabled and disabled. See
[fallback verification](fallback.md#verification) and task 009 implementation.md.
No additional minimum JDK is imposed. Other JVMs and future EA formats are
not established by this matrix. API presence alone is insufficient; the
strict-field probe must succeed. Unknown API, verification and invariant
failures remain errors. The AOT policy guard runs where the form is evaluated.
Top-level forms enforce policy during namespace initialization. Forms inside
functions allow namespace loading, even under strict policy, and enforce policy
on invocation. Direct class access that bypasses the guarded form is outside
this guard. Failed initializer retries do not re-enter it.

## Expected evolution

- Null-restricted fields, when the JEP is available. This needs field
  metadata and the `LoadableDescriptors` attribute. Change: `core`
  validation and `transform`.
- Abstract value classes. `deftype` cannot express them. This needs a
  new macro.
- A record form (`defvaluerecord`). Records keep mutable hash caches.
  This needs a new design for hash caching.
- JEP 401 becomes final. Then the version rule drops the preview minor
  version.
- Clojure supports value classes. Then the library can retire.
