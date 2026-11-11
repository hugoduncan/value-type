# Risks and evolution

## Assumptions (verified)

A spike on a Valhalla early-access JDK verified all assumptions below.
See [Verification](verification.md). Verify them again on each new
early-access build. Record a changed format rule as a row of the D15 format
table, and the build in the D21 facts file. A build enters D21 only after the
D22 forced-JIT row passes.

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
| JEP 401 class file changes in a new build. | The JVM rejects the class. | D15 format table: one row, read by the probe and the transform. A build with no row uses the default row; the probe defines a real value class, so a wrong row fails visibly. D9 wraps user-class definition errors. |
| Clojure changes `deftype` output or the `LOADER` order. | Install or transform fails. | D19 compiler boundary: one namespace changes. D6 shape check with "Fix: use Clojure 1.12". D9 runtime check. D23 snapshots show each output change. Pin Clojure 1.12.6 (`deps.edn`). The `deftype` compatibility corpus (`value-type.deftype-compat-test`); when the Clojure version changes, copy the corpus again from the new tag. |
| Clojure changes or removes bundled `clojure.asm`. | Public namespaces fail to load on every JVM. | D16: the probes use `java.lang.classfile`. Identity output with enabled non-nil fields still needs `clojure.asm`. |
| The JVM gives wrong value semantics (`==`, field values, arrays). | Silent wrong results. | D17 self-check: fallback by policy, clear array failure, AOT check at initialization. D18 switch returns to identity by configuration. D22 forced-JIT row before a build is claimed. A JIT-only defect that no test exercises stays hidden. |
| Untested `master` code reaches consumers. | A change that only the release suites reject is installed. | D24: consumers install release tags or the Clojars version. |
| `macroexpand` from tooling installs a loader. | A loader stays in the tooling binding frame. | D20 defers capture installation for every value definition to receiving analysis. Both `macroexpand` and `macroexpand-1` of a public macro retain the receiving form without installing capture. Full expansion reaches receiving analysis and needs a compiler loader binding. Verify both expansion forms in fresh receivers: a new JVM, or a cell with its own `LOADER` binding. |
| Users depend on identity. | `IdentityException` or changed equality. | README section on semantic differences. |
| Value-class AOT output is tied to one JDK release. | The class does not load on a different JDK. | Document. Rebuild per JDK release; no load-time conversion. |
| Optional APIs load before capability detection. | Public namespace fails before fallback. | D12 dependency boundary; isolated consumer tests without those APIs. |
| API presence is mistaken for usable support. | Preview-disabled or incompatible JVM fails late. | Verify D12 detection against real JVMs; retain defect visibility. |
| Fallback changes identity and equality semantics. | A library behaves differently across JVMs. | Document both modes; application-owned strict policy. |
| Concurrent fallback attempts duplicate diagnostics. | Warning noise. | D13 atomic warning state and concurrency tests. |
| Application policy is set after dependency loading. | Definitions use the wrong policy. | D10: the first fallback decision or report call reads the policy. The report shows the policy in effect. No changes after the first read. |
| A dependency was AOT-compiled as an identity class. | Strict application can load a namespace containing fallback inside a function. | D13 guard enforces policy at form evaluation: during initialization for top-level forms, on invocation for forms inside functions. Direct class access that bypasses the form remains unguarded. |
| Compiler wraps the strict Error. | Callers see a different outer exception type. | Source compilation wraps plain Error in CompilerException. Failed AOT retries can throw NoClassDefFoundError. |

## Fallback constraints

API presence alone is insufficient; the
strict-field probe must succeed. Unknown API, verification and invariant
failures remain errors, except the classified absences of D12. The AOT policy guard runs where the form is evaluated.
Top-level forms enforce policy during namespace initialization. Forms inside
functions allow namespace loading, even under strict policy, and enforce policy
on invocation. Direct class access that bypasses the guarded form is outside
this guard. Failed initializer retries do not re-enter it.

## Expected evolution

- Non-nil fields: see the [field design](../non-nil-field-design.md).
  `:non-nil` names a portable constraint. Native and baseline-safe bytecode backends
  share field specifications. Constructor checks and source-free AOT must
  preserve the constraint in both representations.
- Abstract value classes. `deftype` cannot express them. This needs a
  new macro.
- A record form (`defvaluerecord`). Records keep mutable hash caches.
  This needs a new design for hash caching.
- JEP 401 becomes final. Then the version rule drops the preview minor
  version: add a D15 format row.
- Clojure supports value classes. Then the library can retire.
