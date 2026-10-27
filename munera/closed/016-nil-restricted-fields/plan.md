# Plan

Implement the stable [task design](design.md), the
[Ramora contract](../../../ramora/nil-restricted-field-design.md) and its
[verification requirements](../../../ramora/nil-restricted-field-design/verification.md).
This task adds native field enforcement, not constructor-check fallback.

## Approach

Keep four responsibilities separate:

- `core` validates declarations and resolves explicit tags in the defining
  environment. Produce ordered field specifications with the field name, resolved
  Class and storage descriptor. Retain Class identity for loading checks; a binary
  name alone is insufficient. Do not add a global registry.
- The runtime boundary owns a separate lazy native-field capability result. A
  known probe must construct a valid nested value and observe native null
  rejection without explicit null checks. Cache environment capability only,
  not declaration eligibility. Keep optional JDK dependencies out of public loading.
- The capture loader carries specifications to the transform and covers generated
  classes that reference changed fields. Runtime definition and AOT output must
  use the same transformed bytes.
- The transform owns descriptors, annotation emission, affected field accesses and
  constructor casts. The JVM owns nil enforcement; callers supply arguments.

Validate the original Clojure constructor shape before changing storage. Keep
Object parameters for reference arguments and calculate argument slots from the
original signature, including long and double. Preserve constructor order, arity,
strict initialization, unrelated attributes and all unmarked descriptors. Merge
annotation attributes rather than replacing them as the prototype does.

Use the existing structured-error conventions (`:value-type/error`, `:class-name`)
with field context and a reason where applicable. Slice 2 fixes and records exact
new keys and validation precedence before public integration. Keep existing mutable
field errors and compiler wrapping. Narrowly classify demonstrated capability
absence; unexpected linkage, verification and implementation defects remain errors.

Test capability and validation internally before enabling successful public use.
When connecting metadata handling, reject enabled declarations until the native
path is complete. Never route enabled declarations through ordinary fallback.
Check capability at selection and in receiving compilation/evaluation paths so
transferred or discarded macroexpansions cannot bypass it. Disabled metadata keeps
ordinary behavior, including fallback policy and primitive constraints.

Use the experiment as bounded evidence, not production code. Do not add
`LoadableDescriptors` unless a loading fixture demonstrates a need. Do not expand
scope to forward types, mutable fields, diagnostics, layout controls or benchmarks.

## Slice order

1. **Native capability:** add the isolated enforcement probe and lazy runtime
   access. Gate completion on valid construction, native null rejection, safe
   Java 8/preview-disabled loading and visible unexpected failures.
2. **Declaration contract:** implement internal normalization, type eligibility
   and structured errors. Gate completion on metadata, resolution and rejection
   tables, including external classes and disabled controls.
3. **Native runtime integration:** carry specifications through the loader, preserve
   original constructor signatures, transform storage/accesses and connect the
   public macro with capability guards. Gate completion on public construction,
   field access, attributes and ordinary-field regressions.
4. **Compiler and deployment coverage:** verify companions, closures, control flow,
   macroexpansion transfer, class identity and source-free AOT consumers. Gate
   completion on the startup/policy matrix and semantic execution matrix.
5. **Documentation and final verification:** update usage, generated API sources
   and Ramora embodiment; run quality checks and relevant suites. Record exact
   commands, JVM builds, outcomes and evidence limits in `implementation.md`.

Each slice depends on the preceding slice. Run its focused tests before moving
on. Update `steps.md` during implementation and append dated decisions and results
in `implementation.md`; commit each completed implementation action.

## Verification strategy

Use `test/runtime/` for supported runtime contracts and
`test/compatibility/` for baseline-safe process, policy and AOT checks. Reuse
`value-type.jvm-runner` with explicit executable, classpath, flags, policy and
bounded timeouts. Put Java fixtures under `test/resources/`. Use real JVM behavior,
not mocked capability results or source-text assertions.

Select Java 8 and EA3 through `VALUE_TYPE_BASELINE_JAVA` and
`VALUE_TYPE_VALHALLA_JAVA`; select the CLI JVM with `JAVA_CMD`. Follow
[the runtime guide](../../../doc/development/runtime.md). Expected outcomes must
come from the explicit matrix configuration, not the production detector.

Cover Java 8, EA3 without preview and EA3 with preview under silent, warn and error.
Supported field construction must pass without internal-package exports. Cross
`-XX:+UseFieldFlattening` and `-XX:-UseFieldFlattening` with normal execution and
`-Xint` for the field semantic suite. No semantic assertion requires flat storage.
The full runtime suite may retain its existing array export requirement.

For Scry CLI runs, require a successful exit and inspect `.scry-results/`; retain
failure evidence before the next run replaces it. Source-free AOT consumers must
exclude fixture sources and verify their absence, including separately compiled
Clojure consumers and direct Java callers.

## Current handoff

Slices 1–5 are complete. Separate full runtime, compatibility, build and tooling
suites and quality checks pass. Acceptance evidence for criteria 1–8 is recorded
in `implementation.md`. The three implementation-review follow-ups are complete and verified. The design
and approach are unchanged. Both test-review follow-ups are complete: source
transfer retains metadata, and the four-way semantic matrix covers explicit
captured-receiver field reads. The follow-up test review is complete: annotation comparisons ignore order, and
constructor checks use signatures, concrete writes and loaded-class behavior.
Public declaration rejection tests now verify compiler wrapping, structured errors,
enclosing-Class redefinition and metadata-before-type precedence on EA3.
Annotation-value review is complete: isolated and composed transforms retain visible
and invisible annotation types and non-default element values without order constraints.
Transfer isolation review is complete: each supported mode/expansion case has a fresh
receiver, and repeated definitions must return distinct Classes matched by their factories.
Both follow-up documentation review steps are complete: scope, deployment,
compiler decisions and tooling risks now distinguish ordinary definitions from
native restricted fields. No code or design change was needed.
The qualities review is complete: ordinary method bytecode and benchmark evidence
are separate from restricted-field behavior and semantic evidence.
Both code-shaper follow-ups are complete: transforms share ClassFile configuration,
and restricted AOT tests share producer setup without sharing compiled artifacts.
The follow-up code-shaper review is complete: annotation comparisons share one
runtime-only helper; fixtures and expected values remain in each test.
Implementation review and knowledge extraction are complete. The task is closed.

## Risks and blockers

- Stale descriptors in companion or consumer classes can cause linkage failures.
  Test generated code, not just reflected field declarations.
- Retyping can lose attributes or corrupt two-slot argument indexing. Check both
  emitted bytes and observable constructor behavior.
- Clojure class caches and redefinition can resolve a same-name wrong Class.
  Use unique probe names and explicit same-name/different-loader fixtures.
- Internal EA mechanisms can change. A failed invariant is not capability absence
  and must not trigger fallback. Stop if evidence requires changing the contract.
- AOT and macroexpansion guards span compiler phases. Runtime-only guards cannot
  prevent analysis-time definition; verify each receiving phase.
- Matrix execution needs Java 8, EA3, matching EA3 javac and Clojure dependencies.
  Missing tools block verification, not permission to omit acceptance cases.

No design blocker is known. Exact error reasons and precedence are implementation
choices, not missing public semantics. The experiment's closing warning about
`LoadableDescriptors` is historical context; current Ramora decision D5 controls.

## Planning review

Reviewed the plan with `steps.md` as read-only context. All eight acceptance
criteria have ordered actions and verification targets. Type-resolution tests
include aliases supported by Clojure's existing tag resolution; no new alias
syntax is required. Slice 3 public integration and guards must be complete before
an enabled definition can succeed.

Reviewed `design.md` internally and against both Ramora documents and the
experiment report. No blocking ambiguity or inconsistency was found. Error-key
spelling and validation precedence remain bounded implementation decisions in
slice 2. Use the [quality guide](../../../doc/development/quality-checks.md) for
tooling JVM selection and the combined Quickdoc/tooling test aliases.
