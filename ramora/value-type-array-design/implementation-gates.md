# Array implementation gates

No unanswered JVM question blocks the proposed API on the tested EA3 scope.
Explicit-seed allocation is implemented in task 013, with final acceptance
passed. Diagnostics are implemented in task 014;
[final combined acceptance](../../munera/closed/014-value-array-diagnostics/acceptance.md)
passed within the recorded JVM scope. Task 014 remains open for review.
Sequence construction remains pending. Preserve these boundaries:

- Reject null before native diagnostics. Primitive and non-array rejection is
  public validation, not native behavior. Test the wrapper without crashing
  the shared test JVM.
- Separate discovery, invocation access and value-allocation prerequisites.
  Preview-off and Java 8 dependent allocations are explicitly unresolved
  because strict value fixtures cannot load; they are not native rejections.
- Reject primitive components independently of value status. Do not promise
  eligibility for untested runtime classes or require flat storage.
- Test namespace loading, capability errors, sequence realization, public
  length validation, fallback-policy independence and packaged consumers.
  Task 013 tests cover allocation loading, errors, lengths, policy independence
  and packaged consumers. Task 014 adds input-first validation, independent
  inspection access checks, direct property comparisons, both layout controls
  and JAR-only diagnostics. Sequence realization remains unimplemented. Direct
  probes alone do not verify public library paths.

Native layout selection and the null-crash mechanism remain unresolved. They
are not needed to preserve the proposed contract. Explaining either mechanism
requires matching native source or a separate JVM investigation. Concurrent
stress, copy guarantees and broader JDK support need separate scope and tests;
no evidence here selects new semantics for them.
