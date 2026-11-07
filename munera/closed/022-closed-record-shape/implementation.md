# Implementation

## Design review — architecture (2026-11-04)

- Sources: `ramora/META.md` and `doc/architecture.md` do not exist. The
  review used `AGENTS.md`, `ramora/INDEX.md`, the deftype* design (D2–D10,
  embodiment) and the current code (`identity_backend.clj`, `loader.clj`,
  `value_backend.clj`, `transform.clj`).
- Finding rationale: `identity-backend/rewrite` sets `constructor?` only when
  `types` is not empty, and checks at `putfield` sites. `identity-backend/
  prepare` takes `target specifications _delegate`, with no shape. Thus D10
  identity output needs a new entry-check path in the identity backend; the
  value path only adds to the existing main-constructor rewrite.
- Note, not filed (scope lists only the embodiment link): the
  `ramora/deftype-star-design.md` "Gate records" list also links the other
  gate records; add `closed-gate.md` there when the gate record is written.
- architectural review added 1 new design step

## Design review — ambiguity (2026-11-04)

- Option parse fact: `Compiler.DeftypeParser` reads leading keyword/value
  pairs into a map, so a repeated key keeps the last value and gives no
  error.
- ambiguity review added 3 new design steps

## Design review — inconsistency (2026-11-04)

- Checked, consistent: the `create` facts in context.md, the gate and
  evidence paths (`dev/spike/record-value-type/`,
  `ramora/deftype-star-design/evidence/*-ea3-preview.edn`), the
  `value-type.array` `:flat?` diagnostic, the public-contract error order,
  and the qualities text on layout and closed risks.
- inconsistency review added 1 new design step

## Notes for the design-step slice

- Authority: `ramora/deftype-star-design/` is the design authority. Fix
  D4/D5/D6/D9/D10 text there first (`decisions/closed-record-shape.md`,
  `decisions/policy-and-non-nil.md`, `decisions/interface-and-shape.md`,
  `public-contract.md`), then make `design.md` agree. Do not leave the same
  rule in two forms.
- Ramora rule (`ramora/INDEX.md`): decision files hold contracts and
  rationale, not status. Status and gaps go only in `embodiment.md`
  "Differences from the design".
- Keep D8 firm where possible. If the identity entry check needs shape
  knowledge, state it as a narrow addition (argument positions of
  non-delegating constructors), not as exact record-shape validation in ASM.
- Error-order steps: one canonical order list (the public-contract table
  and its order paragraph). Other places name it and do not repeat it.
- Code facts for the identity step: `src/value_type/impl/identity_backend.clj`
  (`method-visitor`, `rewrite`, `prepare`), `src/value_type/impl/loader.clj`
  (`capture-loader`, `install!` docstring on shape),
  `src/value_type/core.clj` (`install-receiving!`, `value-field-deftype*`,
  `identity-field-deftype*`, `record-shape`).
- AGENTS.md: write in ASD-STE100; no backward compatibility; keep the public
  surface minimal.

## Design steps applied (2026-11-04)

- All five design-review steps done. The authority changed first:
  D4 (`:field` order), D5 (shape from the form, public-contract order,
  `:invalid-option` before value capability), D6 (order header), D8
  (closed entry check is the only shape use in the identity backend), D9
  (`false`/`nil`, last occurrence, removal of each occurrence), D10 (check
  sites), public contract. `embodiment.md` is not changed: it describes
  the current code; update it ("Receiving macros", "Identity delegation
  rule", "Differences") when the code changes.
- Identity entry check, code facts: `rewrite` uses `(ClassWriter. 0)` (no
  frame or max computation). The check has a branch, so it needs an
  explicit stack map frame (local 0 is `uninitializedThis` at entry) and a
  larger max stack, or a branch-free form. `method-visitor` rejects jumps
  in constructors, so emit the check on the delegate visitor, not through
  the checked path. Delegation is known only after the body is read, so a
  first read pass must classify constructors before the entry check.
- `value-backend/check-removed-fields` picks `:field` with
  `(some accessed transform/hash-field-names)`; an ordered vector
  `__meta __extmap __hash __hasheq` gives the D4 rule. Its error text names
  only the hash fields.
- `loader/capture-loader` writes companion class files during AOT only
  when `specifications` is not empty; closed forms without enabled fields
  have empty specifications.

## Design review — architecture, pass 2 (2026-11-04)

- Sources: `ramora/META.md` and `doc/architecture.md` still do not exist.
  The review used `AGENTS.md`, `ramora/INDEX.md` and the updated deftype*
  decisions (D1–D10) and public contract.
- Checked, fits: shape derived from the form in the receivers (no
  transported flag), one check site per output, D8 shape use limited to
  the entry check, `impl.fields` keeps the non-nil rules, one new option.
- Finding: D1 text still states an unchanged `deftype*` call and
  expansion choice by enabled fields only; D9 and D10 contradict it.
- architectural review added 1 new design step

## Design review — ambiguity, pass 2 (2026-11-04)

- Code fact: `value-receiving` checks native field capability only when a
  specification has `:native` enforcement, so closed forms without enabled
  fields skip it; no design text is needed.
- ambiguity review added 1 new design step

## Design review — inconsistency, pass 2 (2026-11-04)

- Not filed: `embodiment.md` "Declaration checks (D6)" has no
  `:invalid-option`; it describes current code and changes with it.
- inconsistency review added 1 new design step

## Notes for the second design-step slice (2026-11-04)

- Open steps: D1, `context.md` + `public-contract.md`, Acceptance 2
  pairs. Fix the D1 and context steps together: both tie the receiving
  expansion only to enabled fields. Use one phrase for the rule
  ("enabled fields or the closed shape") in D1, D5, D10 and `context.md`.
- Files: `ramora/deftype-star-design/decisions/interface-and-shape.md`
  (D1), `ramora/deftype-star-design/context.md` ("Included"),
  `ramora/deftype-star-design/public-contract.md` ("Errors"), then
  `design.md` ("Purpose and priorities" D-list, Acceptance 2).
- Code fact: `core/policy-expansion` selects the receiving form by
  `(seq specifications)`; the D1 text describes that selector.
- `:unsupported-shape` decision: keep it consistent with
  `doc/usage.md` (lines ~111, ~467), which the task updates.
- Acceptance 2: existing receiver order tests are in
  `test/runtime/value_type/value_receiving_test.clj`; name only new pairs,
  do not re-require pairs tasks 017/020 cover.

## Second design-step slice applied (2026-11-04)

- All design steps done. Authority changes: D1 (closed shape selects the
  receiving expansion; `deftype*` call minus `:value-type/closed`),
  `context.md` "Included" (closed shape keeps receiving analysis),
  `public-contract.md` "Errors" (closed `:unsupported-shape` cases; compiler
  output of a valid form does not have them).
- Docs: the closed `:unsupported-shape` cases cannot occur with compiler
  output, so `doc/usage.md` (~line 467) and `doc/API.md` need not list them.
- Code facts for Acceptance 2: in `value-field-deftype*`, `record-shape` is
  an argument of `value-receiving`, so it runs before
  `require-value-capability!` (first line of `value-receiving`); the option
  check goes between them. Metadata keys run in
  `fields/receiving-specifications`. Value-capability error key:
  `:value-capability-unavailable`. Existing order tests:
  `receiving-star-order-test` (`test/runtime/value_type/value_receiving_test.clj`),
  identity receiver in `test/compatibility/value_type/identity_receiving_test.clj`
  and `test/runtime/value_type/deftype_star_record_test.clj`.
- A `:record-shape`/`:invalid-option` order test must use an invalid
  option value: `:record-shape-required` needs a plain-shape form.

## Design review — architecture, pass 3 (2026-11-04)

- Checked, fits: D1/D4/D5/D8/D9/D10 and `context.md` now use one rule
  ("enabled fields or the closed shape") for the receiving expansion and
  the identity transform; no conflict with the defvaluetype or non-nil
  designs (their rules apply to `defvaluetype` only).
- Code fact: closed value forms without enabled fields need no AOT
  companion write; companions are only checked, not rewritten, and the
  compiler writes their class files itself.
- no architectural review feedback

## Design review — ambiguity, pass 3 (2026-11-04)

- Checked, clear: a non-vector `fields` is `:plain` (`core/record-shape`),
  so `:value-type/closed true` on it gives `:record-shape-required`; the
  README and `doc/usage.md` each have one `deftype*` passage to update.
- no ambiguity review feedback

## Design review — inconsistency, pass 3 (2026-11-04)

- Checked, consistent with design.md: D1/D4/D5/D7–D10, `context.md`,
  `purpose.md`, `qualities.md`, `public-contract.md` (order, closed errors,
  caller obligations), `deps.edn` (Clojure 1.12.6), and
  `dev/spike/record-value-type/` (`gate.clj`, `non-nil-gate.clj`, README).
- no inconsistency review feedback

## Design review pass 3 — handoff (2026-11-04)

- Review converged: pass 3 added no design steps; `design-steps.md` has no
  open items. Next lifecycle step: plan (`create-task-plan`), not a
  design-step slice.
- For the plan: the authority (`ramora/deftype-star-design/`) already holds
  the D9/D10 text; only `embodiment.md` and the user docs change with the
  code. Do not re-edit decision files unless the closed gate fails.

## Plan review — ambiguity (2026-11-04)

- Checked, clear: option parse and removal (leading keyword/value pairs,
  `Compiler.DeftypeParser`), order insertion points, `policy-expansion`
  selector, shape plumbing (`loader/capture-loader` passes `shape` only to
  the value backend now), spike-local gate precedent (`gate.clj`,
  `non-nil-gate.clj` do not use the production transform), EA3 `:flat?`
  test precedent (`test/runtime/value_type/array_info_test.clj`).
- Code facts for the identity check: `src/` has no Java sources;
  Clojure 1.12 emits class version 52 (frames required). Parameter index
  n − 4 / n − 3 is not the local slot: compute slots from the descriptor
  (`long`/`double` take two; the `[^long x]` fixture tests this).
- ambiguity review added 2 new design steps

## Plan review — inconsistency (2026-11-04)

- Checked, consistent: plan/steps against design.md scope, contract,
  architecture, gate paths and Acceptance 1–9; the extra "Gate records"
  link (prior note) and embodiment sections (prior notes).
- inconsistency review added 1 new design step

## Notes for the plan-review design-step slice (2026-11-04)

- Open steps: the 3 plan-review items in `design-steps.md`. They change
  plan.md and steps.md. Change the authority
  (`ramora/deftype-star-design/decisions/policy-and-non-nil.md` D8,
  `decisions/closed-record-shape.md` D10) only if a rule changes there.
  The bytecode form of the check is plan text: D10 does not specify it.
  A new helper class would change packaging and Java 8 behaviour, so it
  would need design text too.
- Principle for the classification step: D8 says "the rule does not
  depend on the shape". Prefer one set of constructor rules each time
  the backend processes constructors (enabled fields or closed). Do not
  add closed-only exceptions.
- Code sites: `src/value_type/impl/identity_backend.clj`. The
  `constructor?` flag in `rewrite` requires `(seq types)`.
  `method-visitor` holds the control-flow, creates/delegates and write
  rules. The value check goes in `transform/constructor-code`
  (`src/value_type/impl/transform.clj`), before the first field write.
  `java.lang.classfile` builds the frames there.
- Fixture for the order step: `core/record-shape` gives `:record-shape`
  for an incomplete or misplaced hidden set, for example `[x __meta]`.
- Test homes for the order pairs:
  `test/runtime/value_type/value_receiving_test.clj`
  (`receiving-star-order-test`),
  `test/compatibility/value_type/identity_receiving_test.clj`,
  `test/runtime/value_type/deftype_star_record_test.clj`.

## Plan-review design steps applied (2026-11-04)

- All 3 plan-review steps done in plan.md (steps 4–6, key decisions,
  risks) and steps.md (Slices 4–6). No authority change: D8 already says
  the constructor rule does not depend on the shape; the check form and
  the message function are plan text.
- Choices: one check form in both outputs (`aload`, `ifnull`,
  `new`/`ldc`/`athrow` of `IllegalArgumentException(String)`), no helper
  class; identity frames are two compressed `F_SAME` frames, max stack
  max(original, 3). Message: new `fields/closed-check-message`
  (`fields` already holds `record-hidden-fields` and `direct-fix` text);
  the text is not contract.
- Code facts: `rewrite` reads with `ClassReader` flag 0, so frames pass
  through compressed; supported constructors have no frames (jumps and
  handlers are rejected). `value-backend/prepare` already takes `shape`
  as fourth argument; `identity-backend/prepare` ignores its third
  (`_delegate`). `transform/->record-value-class` takes
  `[bytes storage class-loader]` (no shape yet); record byte tests call it with `nil nil`
  (`test/runtime/value_type/impl/transform_test.clj` ~line 805).
- Synthetic-bytes test precedent: `field-reference-bytes`,
  `altered-storage-bytes`, `shape-failure` in
  `test/compatibility/value_type/identity_backend_test.clj`.

## Plan review — ambiguity, pass 2 (2026-11-04)

- Checked, clear: identity check form (stack 3, two `F_SAME` frames,
  `ClassReader` flag 0), first-pass classification against the D8 rules
  in `method-visitor`, value check slots (`field-slots`), byte signature,
  order fixtures, expansion inspection precedent (`macroexpand-1` of
  receiving macros in `value_receiving_test.clj`).
- Not filed, implementer choice: how `->record-value-class` and
  `value-backend/prepare` get the closed shape (new argument or `:closed`
  shape value); option removal also from the `runtime/guarded-deftype*`
  form (it passes its arguments to `deftype*`; receiving forms keep the
  option).
- Test fact: the `:test` alias runs with `--enable-preview`, so the
  `:invalid-option`/`:value-capability-unavailable` pair needs the
  fallback or cross-JVM suite (precedent:
  `test/compatibility/value_type/deftype_star_transfer_test.clj`
  `capability-unavailable`).
- ambiguity review added 1 new design step

## Plan review — inconsistency, pass 2 (2026-11-04)

- Checked, consistent: plan/steps against design.md contract, receiver
  orders, Acceptance 2 pairs, Acceptance 3–8, scope and doc list. Not
  filed: Acceptance 9 says "packaging, tooling" but its own list names
  only the suites and `bb` checks; plan and steps follow the list (the
  plan adds no runtime class, so packaging does not change).
- inconsistency review added 1 new design step

## Notes for the plan-review pass-2 design-step slice (2026-11-04)

- Open steps: 2 items in `design-steps.md` (doc content; steps/plan
  alignment). Both change only plan.md (step 7, "Risks") and steps.md
  (Slices 4, 5, 7). No authority change: `public-contract.md` already
  holds the closed contract; docs copy from it, they do not restate rules
  in new form.
- Doc step sources: `src/value_type/core.clj` (`defvaluetype*`
  docstring: "With enabled fields, the expansion is a receiving form",
  "Errors" list, "A value expansion without enabled fields…"),
  `doc/usage.md` "`deftype*` forms" (~line 427: numbered order list,
  `:unsupported-shape` sentence, caller obligations, the "Representation
  policy, fallback and AOT" paragraph), `README.md` line ~10.
  `doc/API.md` comes from `bb api-docs` (`bb/value_type/api_docs.clj` via
  the `:quickdoc` alias); `bb api-docs --check` fails when it is stale.
- Use one phrase, "enabled fields or the closed shape", as in D1/D5/D10
  and `context.md`. Keep the closed `:unsupported-shape` cases out of the
  user docs (prior decision).
- Alignment step: pick one JVM list for `-Xverify:all` and write it in
  both files. Java 8 defines app classes with verification on, so the
  flag is a guard, not the only check; the cross-JVM identity item in
  Slice 6 already covers EA3 with preview off.

## Plan-review pass-2 design steps applied (2026-11-04)

- Both pass-2 steps done in plan.md (steps 4, 5, 7, slice order) and
  steps.md (Slices 4, 5, 7). No authority change.
- `fields/closed-check-message` moved to plan step 4 / Slice 4; step 5 and
  Slice 5 use it. `-Xverify:all` list in both files: Java 8 and EA3 with
  preview off.
- Doc content: plan step 7 holds the per-statement list; steps.md Slice 7
  names it. Only the usage.md "`deftype*` forms" section changes; other
  usage.md sections that say "enabled non-nil fields" (~lines 89–94, 133)
  describe `defvaluetype` and stay. Negated phrase: "neither enabled
  fields nor the closed shape".
- Unchanged, still true for closed forms: docstring and usage.md
  `:unexpected-constructor` text (closed classes keep the record
  constructors). `CHANGELOG.md` has an `## [Unreleased]` section.

## Plan review — ambiguity, pass 3 (2026-11-04)

- Rationale for the gate step: `transform/constructor-code` emits no
  branch now, so production value constructors have no stack map frame.
  The D10 check adds the first frame (`uninitializedThis`, all strict
  fields unset) before the field writes; only a gate with the same form
  shows that EA3 `java.lang.classfile` and the verifier accept it.
- Not filed, mechanical: `value-backend/removed-field-accesses` filters
  with `transform/hash-field?`; the closed `check-removed-fields` change
  must also extend this filter.
- ambiguity review added 1 new design step

## Plan review — inconsistency, pass 3 (2026-11-04)

- Checked, consistent: plan/steps against design.md (option order, receiver
  orders, Acceptance 2–8 pairs and paths), the pass-3 gate step, doc lists
  (docstring "fallback" text is in usage.md only), `-Xverify:all` JVM list.
- Not filed, overlap only: Slice 2 ("pass it to both backends") and Slice 5
  (identity `prepare` fourth argument) both name the identity shape
  argument; do it once, in Slice 2 or 5.
- inconsistency review added 1 new design step

## Notes for the plan-review pass-3 design-step slice (2026-11-04)

- Open steps: 2 items in `design-steps.md` (gate check form; D10
  commitment). Both change only plan.md (Approach, step 1) and steps.md
  (Slice 1). Do not change D9/D10 rule text in the authority.
- Gate form: name plan step 5 as the source of the check form; do not copy
  the bytecode listing into step 1 or Slice 1. Spike precedent for a
  main-constructor rewrite with `CodeBuilder`:
  `dev/spike/record-value-type/non-nil-gate.clj` (~line 75). The spike does
  not use the production transform, so its SHA-256 in
  `ramora/deftype-star-design/closed-gate.md` covers only `closed-gate.clj`.
- Commitment: after a passing gate, change only the D10 "Commitment:" line
  in `ramora/deftype-star-design/decisions/closed-record-shape.md` (~line
  88). Precedent wording: `decisions/policy-and-non-nil.md` lines 76, 114;
  `decisions/interface-and-shape.md` line 84. `decisions.md` and
  `ramora/deftype-star-design.md` list no commitments; leave them. Also
  update the pass-3 handoff note sense: decision files change on gate
  failure (design decision) or on gate pass (D10 commitment only).

## Plan-review pass-3 design steps applied (2026-11-04)

- Both pass-3 steps done in plan.md (Approach, step 1, key decisions,
  slice order) and steps.md (Slice 1). No authority change now.
- Gate spike: uses the plan step 5 check form, emitted with `CodeBuilder`
  in its own main-constructor rewrite (precedent `constructor-code` in
  `dev/spike/record-value-type/non-nil-gate.clj`, ~line 60); frames from
  `java.lang.classfile`.
- Supersedes the pass-3 handoff note "Do not re-edit decision files unless
  the closed gate fails": on a gate pass, change only the D10
  "Commitment:" line (`decisions/closed-record-shape.md` line 88) to
  "firm (D7 closed gate passed)"; D9 (line 38) stays provisional.

## Plan review — ambiguity, pass 4 (2026-11-04)

- Checked, clear: `policy-expansion` change (`modes` is `[]` for closed
  forms without enabled fields, in both branches), receiver check sites
  (`record-shape` is an argument of `value-receiving`), value check slots
  (`field-slots` over all fields, main constructor by descriptor),
  identity `rewrite`/`method-visitor` rules with an empty field set, gate
  precedents (`non-nil-gate.clj` paths, README command with
  `--add-exports`).
- Not filed, test reading: with `true`, the direct expansion of
  `defvaluetype*` is a receiving form. That form keeps the option (D9) and
  has no `deftype*` call. Check removal for `true` and for a repeated
  option at the receiving-macro expansion. For `false`/`nil`, check the
  `deftype*` and `runtime/guarded-deftype*` forms of `defvaluetype*`.
- Not filed, implementer choice: no alias or child-JVM runner passes
  `-Xverify:all` (only the spike README commands do). Run it ad hoc and
  record the command (Acceptance 9).
- no ambiguity review feedback

## Plan review — inconsistency, pass 4 (2026-11-04)

- Embodiment facts (`ramora/deftype-star-design/embodiment.md`): sections
  are intro (status, element list, "Gate records" line), "Differences from
  the design", "Declaration checks (D6)", "Receiving macros", "Record
  value storage and checks (D8)", "Identity delegation rule (D8)" and
  "Evidence". Each has text that the closed code makes false or
  incomplete.
- Checked, consistent: option order and removal, receiver orders, check
  sites and form, `-Xverify:all` JVM list, test fixtures and doc lists
  across design.md, plan.md and steps.md.
- inconsistency review added 1 new design step

## Notes for the plan-review pass-4 design-step slice (2026-11-04)

- Open steps: 1 (embodiment list and gate links). Change only plan.md
  (step 1, step 7) and steps.md (Slices 1, 7). Do not edit
  `ramora/deftype-star-design/embodiment.md` now: it describes the
  current code and changes in Slice 7. No authority (decision) change.
- Name embodiment sections by their headings; do not copy their future
  text into the plan. The plan states what changes; the docs/contract
  text comes from `public-contract.md`.
- Precedent for no "Differences" section: `ramora/defvaluetype-design/embodiment.md`,
  `ramora/non-nil-field-design/embodiment.md` and
  `ramora/value-type-array-design/embodiment.md` have none. `ramora/INDEX.md`
  (lines 8–13) puts status and gaps only in embodiment sections. Thus after a
  full implementation, remove the section and the intro sentence that links it.
- Gate-link precedent: `ramora/deftype-star-design/non-nil-gate.md` line 8
  links `evidence/non-nil-gate-ea3-preview.edn`; `embodiment.md` (line 19
  intro, line 80 "Evidence") and `ramora/deftype-star-design.md` "Gate
  records" (line 58) link only gate records.

## Plan-review pass-4 design step applied (2026-11-04)

- Done in plan.md (Approach, step 1, step 7) and steps.md (Slices 1, 7).
  No authority change; `embodiment.md` not edited yet.
- Gate links: the gate record links its raw result; `embodiment.md`
  (intro "Gate records" line, "Evidence") and the
  `ramora/deftype-star-design.md` "Gate records" list link only the gate
  record. Slice 1 also removes the embodiment "Differences" entry "The
  closed gate (D7) has not run"; the other entries go in Slice 7.
- Embodiment (Slice 7): the intro `value-type.core` element also lists
  the D6 checks (line 8) and needs `:invalid-option`, as does
  "Declaration checks (D6)". When "Differences" is empty, remove it and
  the intro status sentence link (lines 3–5).

## Plan review — ambiguity, pass 5 (2026-11-04)

- Checked, clear: the pass-4 embodiment text (plan steps 1, 7; Slices 1,
  7) against `embodiment.md` headings and intro lines 3–17; the new
  status sentence follows from the "Differences" removal rule (all five
  entries no longer apply after the task). The identity-backend intro
  element ("the D8 delegation rule") stays true; no plan text needed.
- no ambiguity review feedback

## Plan review — inconsistency, pass 5 (2026-11-04)

- Checked, consistent: the embodiment lists of plan steps 1/7 and Slices
  1/7; plan step 7 quotes against the current `defvaluetype*` docstring
  (lines ~413–442) and usage.md "`deftype*` forms" (~459–496).
- Not filed, mechanical: with a new shape value, the `value-backend/prepare`
  and `loader/install!` docstrings (":plain or :record") change with it;
  Slice 2 names only `install!`.
- no inconsistency review feedback

## Plan review pass 5 — handoff (2026-11-04)

- Plan review converged: pass 5 added no design steps; `design-steps.md`
  has no open items. Next lifecycle step: implementation (Slice 1, closed
  gate), not a design-step slice.
- Gate start points: `dev/spike/record-value-type/non-nil-gate.clj`
  (spike-local transform, `CodeBuilder` constructor rewrite) and the
  README "non-nil gate" section (command form);
  `ramora/deftype-star-design/non-nil-gate.md` (record layout).

## Slice 1 — closed gate (2026-11-04)

- Gate passed on EA3 preview, `-Xverify:all`: 25 cases, `:failed []`.
  Script SHA-256 `8bd5bcdc…2e29` (full value in `closed-gate.md`).
  D10 is now firm; D9 stays provisional.
- The gate needs `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`
  (flat-array case); the README command has it.
- `CodeBuilder` check form (`aload`, `ifnull` label, `new_`/`dup`/`ldc`/
  `invokespecial`/`athrow`, `labelBinding`) verifies in a value-class
  constructor with generated frames. Slice 4 can reuse this form
  (`closed-check!` in the spike).
- Spike hint: `.ldc` with a string literal needs `(identity "…")` for a
  `^ConstantDesc` hint (metadata on a literal string is a reader error).
- The closed class body must not touch `__meta`/`__extmap` (the gate body
  returns nil from `meta` and `this` from `withMeta`). The compiled
  constant goes through `create` with the `entrySet` view, so only
  declared keys appear.
- Embodiment: only the "closed gate has not run" difference entry is
  removed; the other four go in Slice 7.

## Slices 2–3 — option and receivers (2026-11-04)

- `core/declared-shape` = `check-fields` → `record-shape` → `closed-shape`;
  returns `:plain`, `:record` or `:closed`. All three macros call it, so the
  public and receiver orders get `:invalid-option` at the right place
  (value receiver: before `require-value-capability!` inside
  `value-receiving`; metadata keys come later from `receiving-specifications`).
- `option-pairs` reads leading keyword/value pairs as `deftype*` does;
  `remove-closed-option` strips every occurrence. `defvaluetype*` passes
  the original options to the receiving macro forms (receivers re-derive
  the shape and strip); it strips them from the plain `deftype*` and
  `guarded-deftype*` forms.
- `policy-expansion`: `(or (seq specifications) (= :closed shape))` selects
  the receiving form in both representations.
- Loader passes `shape` to `identity-backend/prepare` (4th arg, ignored
  until Slice 5). Test call sites pass `:plain`.
- Interim state, NOT safe: `value-backend/prepare` has no `:closed` case
  (a closed value define fails with "No matching clause"); identity output
  of a closed form has no D10 check yet. Slices 4 and 5 fix this. Do not
  release between slices.
- Tests: runtime `deftype_star_test` (errors, last occurrence, order,
  removal for false/nil/repeated), `value_receiving_test`; compatibility
  `identity_receiving_test` (identity receiver errors/order and the
  value-receiver `:invalid-option`/capability pair, only without value
  capability).
- Verified: runtime suite on EA3 preview with the `jdk.internal.value`
  export (158 tests pass; without the export the array tests fail — env,
  not code); fallback suite on Java 8 (71) and EA3 preview off (70 before
  the capability test was added); `bb clj-fmt:check`, `bb clj-kondo:lint`.

## Slice 4 — value output (2026-11-04)

- `transform/->record-value-class` now takes `[bs storage class-loader closed?]`
  (one arity; test call sites pass `false`). `closed?` removes
  `closed-field-names` (all four hidden fields, D4 order) and adds the D10
  check to the main constructor only.
- `constructor-code` takes a fifth argument `checks` (`[slot message]`
  pairs, nil for none); `nil-check!` emits the plan step 5 form before the
  field writes, so before the `requireNonNull` checks. Frames come from
  `java.lang.classfile`.
- `fields/closed-check-message` takes the binary class name; the identity
  backend (Slice 5) must use it too.
- `value-backend/prepare` accepts `:closed`; `check-removed-fields` takes
  the ordered removed-field vector (`transform/hash-field-names` or
  `transform/closed-field-names`). Error text now names the removed fields
  of the shape.
- Tests: `transform_test` (closed structure, check before null check,
  round trip, IAE vs NPE order), `removed_field_test`
  (`closed-owner-access-test`: owner/companion access, D4 `:field` order,
  only declared field kept).
- Verified: runtime suite on EA3 preview + `jdk.internal.value` export:
  163 tests, 4976 assertions pass. `bb clj-fmt:check`, `bb clj-kondo:lint`
  clean.
- Still open: identity output (Slice 5) has no D10 check; closed identity
  forms are unsafe until Slice 5.

## Slice 5 — identity output (2026-11-04)

- `identity-backend/rewrite` takes `[bytes owner types owner? closed?]`;
  `prepare` passes `(= shape :closed)` to the owner transform only.
- Classification: `delegating-constructors` (read pass, marks descriptors
  with `invokespecial <owner>.<init>`); `closed-checks` (second read pass,
  `SKIP_CODE`) maps each non-delegating descriptor to `[slots message]`
  and raises the closed `:unsupported-shape` cases before the rewrite.
  Message parts: "closed constructor without Object __meta and __extmap
  parameters", "closed class without non-delegating constructor".
- `method-visitor` gets a `check` argument; `visitCode` emits
  `emit-closed-check!` (plan step 5 form, `IFNULL` + `F_SAME`);
  `visitMaxs` sets max stack to max(original, 3).
- Constructor rules now apply when `(or (seq types) closed?)`.
- Tests: `identity_backend_test/closed-identity-entry-check` (synthetic
  class with a `long` first parameter, so slot computation is tested;
  load, valid/IAE, delegating constructor, companion has no check, six
  unsupported cases).
- Smoke-verified with `-Xverify:all` on Java 8 and Java 25 (identity):
  real closed `defvaluetype*` forms, with and without a non-nil field;
  IAE comes before NPE.
- Verified: fallback suite Java 8 (72 tests, 2207 assertions) and EA3
  preview off with `-Xverify:all` (72, 2207); runtime suite EA3 preview +
  export (163, 4976); fmt and kondo clean. The fallback suite refuses
  Java 25 (unsupported configuration, env).
- Next: Slice 6 construction-path, byte-inspection (real compiler output),
  transfer/AOT, cross-JVM and EA3 flat-array tests; Slice 7 docs.

## Slice 6a — closed construction tests (2026-11-04)

- `deftype_star_record_test`: `closed-construction-test`,
  `closed-check-before-non-nil-test`. Definers: identity through the
  receiving macro (`identity-closed*` → `vt/identity-field-deftype*`,
  empty modes when no field is enabled), value through `vt/defvaluetype*`
  when capable. Variants: no enabled field (`[^long l a]`) and one
  `:non-nil` String field.
- Fixture `closed-form`: closed record methods must not touch `__meta` or
  `__extmap` (value output rejects with `:removed-field-access`), so
  `meta` returns nil, `withMeta` returns `this`, entries are the declared
  fields only. The existing `record-form` cannot be reused.
- Field-set check uses non-static fields (identity classes have
  `const__N` statics).
- Paths: declared/meta-extmap/main ctor (reflective and direct),
  `create`, map and positional literal, compiled constant, caller
  `assoc` via declared ctor. IAE for non-nil `__meta`/`__extmap` on the
  4- and 6-arity paths, and for an undeclared key on `create` and the map
  literal.
- Verified: runtime suite EA3 preview + export: 165 tests, 5114
  assertions. The two closed tests also pass on Java 8 and EA3 preview
  off with `-Xverify:all` (identity only; `-Sdeps` paths
  `src test/runtime`, test-vars). The rest of this namespace requires
  value capability, so the cross-JVM step stays open for a suite test.
- Next: byte inspection on real compiler output, receiving-macro
  `deftype*` call expansion, transfer/AOT/fallback policies, cross-JVM
  suite, EA3 flat-array evidence; Slice 7 docs.

## Slice 6b — byte inspection and receiver expansion (2026-11-04)

- Value bytes were already covered by `transform_test/closed-record-*`
  (fields, descriptors, delegating ctors, check only in main, IAE before
  null check).
- Identity bytes on real compiler output:
  `identity_backend_test/closed-identity-compiler-output-bytes` compiles a
  raw record `deftype*` (`*compile-files*`, temp dir) and applies
  `(:owner (prepare owner specs nil :closed))`, with no and one enabled
  field. Identity ctor descriptors keep `Object` for tagged fields, so
  the main descriptor is the all-`Object` one.
- `deftype_star_test/receiving-closed-option-removal-test`: both
  receivers, values `true`/`false`/`nil`/repeated. Macroexpanding a
  receiver needs a bound `Compiler/LOADER` (else `:not-installed`); the
  test binds a fresh `DynamicClassLoader`. The value receiver runs only
  with value capability.
- Verified: fallback suite Java 8 and EA3 preview off `-Xverify:all`
  (73 tests, 2215 assertions); runtime suite EA3 preview + export (166,
  5130); fmt, kondo clean.
- Next: transfer/AOT/fallback-policy tests, cross-JVM suite test, EA3
  flat-array evidence, regression item; Slice 7 docs.

## Slice 6c — transfer, AOT, cross-JVM, EA3 layout (2026-11-04)

- Transfer (`deftype_star_transfer_checks/test`): `shapes` now
  `[:plain :record :closed]`; closed cells add `:value-type/closed true`.
  New fact `:meta-rejected?` (nil plain, false record, true closed).
  New `consume-data` keeps root ex-data. New test
  `deftype*-closed-value-transfer-test`: value producer, no enabled field;
  closed transfers (value, 0 hash fields, D10 check) or gives
  `:value-capability-unavailable`; plain/record value expansions without
  enabled fields do not transfer (not captured) — expected, existing
  behaviour. Closed identity cells under `error` policy match the plain
  fixed-fallback rejection.
- AOT (`deftype_star_aot_checks`): fixtures `Closed`, `ClosedNonNil`;
  `owners` drives the incompatible-receiver test. Identity artifact test
  covers Java 8 producer on baseline, preview-off and preview receivers.
- EA3 layout: `array_info_test/closed-record-layout-test`, fixture
  `ArrayClosed` in `array_test_support` (kondo ignore for the
  `defvaluetype*` form; no lint-as exists for it). `:flat? true` observed.
- Verified: runtime EA3 preview + export (167 tests, 5163); fallback
  Java 8 (74, 2550) and EA3 preview off `-Xverify:all` (74, 2550);
  cross-JVM EA3 preview (99, 4833); fmt, kondo clean.
- Next: Slice 7 (docstring, API.md, usage.md, README, CHANGELOG, state,
  embodiment, `bb api-docs --check`, `bb vale`).

## Slice 7 — docs and quality (2026-11-04)

- Docstring, `doc/usage.md` ("`deftype*` forms"), README, CHANGELOG,
  `mementum/state.md` and `embodiment.md` describe the closed shape.
  `doc/API.md` regenerated by `bb api-docs`. The embodiment section
  "Differences from the design" and its intro link are removed (no entry
  remains). User docs do not list the closed `:unsupported-shape` cases.
- Verified: `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint`
  (0 errors, 0 warnings), `bb vale` clean; tooling suite (11 tests, 241);
  runtime suite EA3 preview + `jdk.internal.value` export (167, 5163).
  Code change in this slice is docstring only, so the fallback and
  cross-JVM runs of Slice 6c stand.
- All steps done; the task is ready for implementation review.

## Implementation review (2026-11-04)

- Checked, matches design: option parse/order/removal, shape derived from
  the form in the receivers, one D10 check site per output, D4 `:field`
  order, closed `:unsupported-shape` cases, tests for Acceptance 2–8.
  `bb clj-fmt:check` and `bb clj-kondo:lint` re-run clean; suites not
  re-run.
- implementation review added 3 steps to be addressed

## Review follow-up (2026-11-04)

- addressed 3 review steps.
- `identity_backend`: `constructor-marks` collects constructor descriptors
  (class order) and delegation marks in one read; the `SKIP_CODE` pass is
  removed. Docstring and `doc/usage.md` text reflowed (content unchanged);
  `doc/API.md` regenerated. `fields/record-hidden-fields` docstring names
  the closed shape.
- Verified: `bb api-docs --check`, `bb vale`, `bb clj-fmt:check`,
  `bb clj-kondo:lint` clean; fallback Java 8 (74, 2550), EA3 preview off
  `-Xverify:all` (74, 2550); runtime EA3 preview + export (167, 5163).

## Implementation review, pass 2 (2026-11-04)

- Suites not re-run; review read the code diff since `3718441c`.
- implementation review added 2 steps to be addressed

## Review follow-up, pass 2 (2026-11-04)

- addressed 2 review steps.
- `transform/constructor-code` and `loader/install!` docstrings reflowed
  (content unchanged).
- `->record-value-class` computes the binary class name once
  (`asInternalName`, as in `unexpected-constructor!`) and passes it through
  `record-value-class-transform` to `closed-checks`; the `subs` parse of
  `descriptorString` is removed.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime suite
  EA3 preview + `jdk.internal.value` export (167, 5163). Value output
  change only, so the fallback and cross-JVM runs stand.

## Implementation review, pass 3 (2026-11-04)

- Suites not re-run; review read the code diff since `fcdccb54` and the
  task docs.
- implementation review added 2 steps to be addressed

## Review follow-up, pass 3 (2026-11-04)

- addressed 2 review steps.
- `loader/install!` capture-loader paragraph refilled (content unchanged).
- `embodiment.md` intro `value-type.core` element and "Receiving macros"
  refilled to at most 80 columns (content unchanged).
- Verified: `bb clj-fmt:check`, `bb vale` clean. Text only; suites not
  re-run.

## Implementation review, pass 4 (2026-11-04)

- Suites not re-run; review read the code diff since `3718441c`.
- implementation review added 2 steps to be addressed

## Review follow-up, pass 4 (2026-11-04)

- addressed 2 review steps.
- `identity-backend/prepare`: the companion transform returns its bytes
  unchanged when there are no specifications.
- `transform/nil-check!` renamed to `require-nil!`; docstrings name the D10
  checks "checks that … are nil" and the field checks "non-null checks".
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime suite
  EA3 preview + `jdk.internal.value` export (167, 5163); fallback suite on
  Temurin 8 and EA3 preview off (74, 2550).

## Implementation review, pass 5 (2026-11-04)

- Suites not re-run; review read the code diff since `9d89120b`.
  `bb clj-fmt:check` and `bb clj-kondo:lint` clean.
- no implementation review feedback

## Test review (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 3 steps to be addressed

## Test review follow-up (2026-11-04)

- addressed 3 test review steps.
- `closed-identity-entry-check`: the companion case uses one enabled-field
  specification and asserts that the companion bytes are rewritten.
- `closed-identity-compiler-output-bytes`: `:mode` → `:enforcement`.
- `meta-rejected?` (with the plain-shape guard) is now in
  `cell-registry`; the transfer and AOT checks use it.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; fallback
  `identity-backend-test` + `deftype-star-transfer-test` on Temurin 8 and
  EA3 preview (16, 840 each); cross-JVM `deftype-star-aot-test` +
  `cell-registry-test` (6, 251).

## Test review, pass 2 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 2 steps to be addressed

## Test review follow-up, pass 2 (2026-11-04)

- addressed 2 test review steps.
- `value_receiving_test`: `star-error` takes `& opts`;
  `star-option-error` removed. `identity_receiving_test`: one helper
  `macro-error` takes the macro symbol; `star-error` is its partial for
  `identity-field-deftype*`; `star-option-error` and `value-star-error`
  removed.
- `closed-record-structure-test` asserts that the main constructor is the
  only constructor with a `new IllegalArgumentException`;
  `closed-identity-compiler-output-bytes` asserts the same, and with an
  enabled field that the first `:iae` comes before the first `:nn`. No
  instruction counts.
- Removed a stray line ("Temurin 8 and EA3 preview off (74, 2550).") from
  the last step text in `steps.md`.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime
  `value-receiving-test` + `transform-test` on EA3 preview + export
  (40, 1504); fallback `identity-receiving-test` + `identity-backend-test`
  with `-Xverify:all` on Temurin 8 and EA3 preview off (19, 711 each).

## Test review, pass 3 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 3 steps to be addressed

## Test review follow-up, pass 3 (2026-11-04)

- addressed 3 test review steps.
- `identity_backend_test`: `closed-check-constructors` removed;
  `class-structure` moved above `closed-identity-entry-check`, which takes
  the checked constructors from it (descriptors with `:iae`).
- `deftype_star_transfer_test`: `transfer-cells` takes the shapes to run;
  the closed value transfer test runs `[:closed]` only;
  `not-transferred` removed.
- `check-closed`: exact field set (declared names, plus the four hidden
  names for identity output); `clojure.set` require removed.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; fallback
  `identity-backend-test` + `deftype-star-transfer-test` with
  `-Xverify:all` on Temurin 8 and EA3 preview off (16, 731 each);
  `deftype-star-transfer-test` on EA3 preview + export (4, 584); runtime
  `deftype-star-record-test` on EA3 preview + export (13, 471).

## Test review, pass 4 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 1 step to be addressed

## Test review follow-up, pass 4 (2026-11-04)

- addressed 1 test review step.
- `meta-rejected?` docstring states the one-field fixture assumption
  (constructor of parameter count 3, called with `[42 {:m 1} nil]`; nil
  for a plain shape or another field count). Code does not change.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; fallback
  `deftype-star-transfer-test` with `-Xverify:all` on Temurin 8 (4, 584);
  cross-JVM `deftype-star-aot-test` + `cell-registry-test` on Temurin 8
  (6, 251). EA3 not run: no EA3 JDK found on this host.

## Test review, pass 5 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 3 steps to be addressed

## Test review follow-up, pass 5 (2026-11-04)

- addressed 3 test review steps.
- `identity-closed*` removed; `closed-definers` uses `identity-receiving*`,
  whose docstring now covers closed forms without enabled fields.
- `closed-value-class` removed; `record-value-class` takes `storage` and
  `closed?`.
- `deftype*-transferred-expansion-test` comment: closed cells are identity
  receiving expansions that keep the D10 check.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime
  `transform-test` + `deftype-star-record-test` on Java 25 with preview:
  same result before and after the change (36 pass, 7 fail, 4 error; all
  from `Class.isValue` missing). EA3 not run: no EA3 JDK on this host.

## Test review, pass 6 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 1 step to be addressed

## Test review follow-up, pass 6 (2026-11-04)

- addressed 1 test review step.
- `option-error` removed; `expansion-error` takes `& opts` and the closed
  option tests use it.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean;
  `deftype-star-test` on Java 25 with preview: 11 tests, 85 assertions,
  0 failures, 0 errors.

## Test review, pass 7 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 3 steps to be addressed

## Test review follow-up, pass 7 (2026-11-04)

- addressed 3 test review steps.
- `root-cause` removed; `closed-identity-entry-check` uses
  `registry/root`.
- `meta-rejected?` uses the private `chain` of `cell-registry`.
- `cause-chain`/`thrown-in-chain` (transform test) and
  `cause-chain-classes`/`rejection` (record test) removed; `rejection` is
  now in `value-type.test-support` and both files use it.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; Temurin 8
  `-Xverify:all`: `identity-backend-test`, `deftype-star-transfer-test`,
  `deftype-star-aot-test`, `cell-registry-test` (22 tests, 982
  assertions, 0 failures, 0 errors); runtime `transform-test` +
  `deftype-star-record-test` on Java 25 with preview: same result before
  and after the change (47 tests, 657 pass, 39 fail, 12 error; all from
  `Class.isValue` missing). EA3 not run: no EA3 JDK on this host.

## Test review, pass 8 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 2 steps to be addressed

## Test review follow-up, pass 8 (2026-11-04)

- addressed 2 test review steps.
- `check-closed` asserts that `getBasis` gives the declared fields, in
  both outputs.
- `identity_backend_test.clj`: one private `checked-constructors` helper
  next to `class-structure`; both closed tests use it.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; Temurin 8
  `-Xverify:all`: `identity-backend-test` (12 tests, 147 assertions,
  0 failures, 0 errors); `deftype-star-record-test` on Java 25 with
  preview: 315 pass, 33 fail, 0 error (before: 313 pass, 33 fail; all
  failures from `Class.isValue` missing). EA3 not run: no EA3 JDK on
  this host.

## Test review, pass 9 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- no test review feedback

## Test review, pass 10 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c`.
- test review added 1 step to be addressed (pass 8 review put its note in
  `steps.md`)

## Test review follow-up, pass 10 (2026-11-04)

- addressed 1 test review step.
- No edit needed: `steps.md` at HEAD has no note "- test review added 2
  steps to be addressed"; the pass 8 review (`7af1b0fc`) put it only in
  `implementation.md`.

## Test review, pass 11 (2026-11-04)

- Suites not re-run; review read the test diff since `3718441c` (no test
  change since pass 9).
- no test review feedback

## Docs review (2026-11-04)

- `bb api-docs --check` and `bb vale` clean.
- docs review added 1 step to be addressed

## Docs review follow-up (2026-11-04)

- addressed 1 docs review step (`doc/development/runtime.md` "Verify
  `deftype*` forms": closed coverage, `Closed`/`ClosedNonNil` fixtures,
  limits, closed gate, flat-array evidence location). `bb vale` clean.
- Removed a stray line "this host." from the step text in `steps.md`.

## Docs review, pass 2 (2026-11-04)

- `bb api-docs --check` and `bb vale` clean.
- no docs review feedback

## Code-shaper review (2026-11-04)

- Suites not re-run; review read the `src` diff since `3718441c`.
- code-shaper review added 5 steps to be addressed

## Code-shaper review follow-up (2026-11-04)

- addressed 5 code-shaper review steps.
- `policy-expansion`: one `receiving?` local; docstring names the rule.
- D10 check: one shape `[slots message]` and one emitter name
  `require-nil!` in `transform` and `identity-backend`;
  `transform/closed-checks` → `closed-check` (one check), names from
  `closed-field-names` minus `hash-field?`.
- `identity-backend/closed-checks`: `doseq` validation, then a pure map;
  `hidden-indexes` shared.
- `identity-backend/rewrite`: `role` ∈ `:companion`, `:owner`,
  `:closed-owner` replaces `owner? closed?`.
- Verified: runtime EA3 preview + export (167 tests, 5167); fallback
  Java 8 and EA3 preview off `-Xverify:all` (74, 2442 each); cross-JVM
  (99, 4833); `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` clean;
  `bb api-docs` regenerated (source line links only).

## Code-shaper review, pass 2 (2026-11-04)

- Suites not re-run; review read the `src` diff since `3718441c`.
- code-shaper review added 2 steps to be addressed

## Implementation review, pass 6 (2026-11-06)

- Suites not re-run; review read the `src` diff since `3718441c` at HEAD
  (uncommitted work in the tree not reviewed). `bb clj-fmt:check` and
  `bb clj-kondo:lint` clean.
- no new implementation review feedback; the 2 code-shaper pass 2 steps
  are still open

## Code-shaper review follow-up, pass 2 (2026-11-06)

- addressed 2 code-shaper review steps.
- `identity-backend/require-nil!` docstring reflowed (text unchanged).
- `transform/removed-field-names` (`closed?` → ordered vector) is the one
  source of the removed-field set; `record-value-class-transform` and
  `value-backend/prepare` use it; `closed-field?` removed.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; fallback suite
  Temurin 8 `-Xverify:all` (74, 2442); `transform-test` +
  `deftype-star-record-test` on Java 25 with preview: 659 pass, 39 fail,
  12 error, same as HEAD (all from `Class.isValue` missing). EA3 not run:
  no EA3 JDK on this host.

## Implementation review, pass 7 (2026-11-06)

- Suites not re-run; review read the `src` diff since `3718441c`.
  `bb clj-fmt:check` and `bb clj-kondo:lint` clean.
- no implementation review feedback

## Test review, pass 12 (2026-11-06)

- Suites not re-run; review read the test diff since `3718441c` (no test
  change since pass 11) and the `src` diff since pass 11.
- test review added 1 step to be addressed

## Test review follow-up, pass 12 (2026-11-06)

- addressed 1 test review step.
- Comments of `deftype*-enabled-value-transfer-test` and
  `deftype*-closed-value-transfer-test` now state only what the cells
  assert (no hash fields; closed cells reject a non-nil `__meta`).
  Comment change only; `bb clj-fmt:check` clean; suites not re-run.

## Test review, pass 13 (2026-11-06)

- Suites not re-run; review read the test diff since pass 12.
- test review added 1 step to be addressed

## Test review follow-up, pass 13 (2026-11-06)

- addressed 1 test review step.
- `consume` docstring lists `:meta-rejected?`; `evaluate-twice` docstring
  paragraph filled. Docstring change only; `bb clj-fmt:check` clean;
  suites not re-run.

## Test review, pass 14 (2026-11-06)

- Suites not re-run; review read the test diff since pass 13.
- test review added 1 step to be addressed

## Test review follow-up, pass 14 (2026-11-06)

- addressed 1 test review step.
- `construct` docstring names the record and closed shapes. Docstring
  change only; `bb clj-fmt:check` clean; suites not re-run.

## Test review, pass 15 (2026-11-06)

- Suites not re-run; review read the test diff since pass 14 and the
  test docstrings that this task changed.
- test review added 1 step to be addressed

## Test review follow-up, pass 15 (2026-11-06)

- addressed 1 test review step.
- `consume` docstring paragraph (`deftype_star_aot_checks.clj`) filled.
  Docstring change only; `bb clj-fmt:check` clean; suites not re-run.

## Test review, pass 16 (2026-11-06)

- Suites not re-run; review read the test diff since pass 15.
- no test review feedback

## Test review, pass 17 (2026-11-06)

- Suites not re-run; review read the task test diff since `09e85640^`
  (no test change since pass 16).
- no test review feedback

## Docs review, pass 3 (2026-11-06)

- `bb api-docs --check` and `bb vale` clean; review read the doc diff
  since pass 2 (`984d233d`; `doc/API.md` source line links only).
- no docs review feedback

## Code-shaper review, pass 3 (2026-11-06)

- Suites not re-run; review read the `src` diff since `3718441c`.
- code-shaper review added 2 steps to be addressed

## Code-shaper review follow-up, pass 3 (2026-11-06)

- addressed 2 code-shaper review steps.
- `transform/hash-field-names`, `hash-field?`, `closed-field-names` are
  private; `removed-field-names` is the one public source.
- `core/hidden-fields-fix` (private, derived from
  `fields/record-hidden-fields`) gives the fix text of the
  `:record-shape` and `:record-shape-required` messages.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean;
  `transform-test` + `deftype-star-test` on Java 25 with preview: 429
  pass, 6 fail, 12 error, same as HEAD (`Class.isValue` missing). EA3 not
  run.
- code-shaper review pass 4: added 2 steps to be addressed.

## Code-shaper review follow-up, pass 4 (2026-11-06)

- addressed 2 code-shaper review steps.
- `transform/removed-field-names` and `->record-value-class` take the
  shape (`:record`, `:closed`), not `closed?`; `value-backend/prepare`
  passes `shape` on.
- private `transform/binary-name` gives the binary class name for
  `unexpected-constructor!` and `->record-value-class`.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime suite
  on EA3 (`27-jep401ea3+1-1`, from
  `../closed-record/.../target/valhalla/jdk-27.jdk`) with preview and the
  export: 167 tests, 5167 assertions, 0 failures, 0 errors.

## Code-shaper review, pass 5 (2026-11-06)

- Suites not re-run; review read the `src` diff since `fb62fbe2` and the
  closed paths of `core`, `transform`, `value_backend`, `identity_backend`.
- code-shaper review added 2 steps to be addressed

## Code-shaper review follow-up, pass 5 (2026-11-06)

- addressed 2 code-shaper review steps.
- `identity-backend/rewrite`: `:unsupported-shape` prefix is "Cannot
  rewrite class"; parts "unsupported constructor control flow" and
  "constructor both creates and calls the target". Tests updated.
- `value-backend/check-removed-fields`: when `__meta`/`__extmap` are
  removed, the fix adds "a closed record has no metadata and no extension
  map".
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; Temurin 8
  `-Xverify:all` `identity-backend-test`: 12 tests, 147 assertions, 0
  failures. `removed-field-test` not run: needs EA3 (Java 25 gives 6
  unknown, as at HEAD); no EA3 JDK on this host.

## Code-shaper review, pass 6 (2026-11-06)

- Suites not re-run; review read the pass-5 diff and the closed paths of
  `core`, `transform`, `value_backend`, `identity_backend`.
- code-shaper review added 1 step to be addressed

## Code-shaper review follow-up, pass 6 (2026-11-06)

- addressed 1 code-shaper review step.
- `value-backend/check-removed-fields` takes `shape`; it gets the names
  from `transform/removed-field-names` and adds the closed fix when the
  shape is `:closed`. `prepare` passes `shape`; the `removed` local and
  the literal `#{"__meta" "__extmap"}` are gone.
- Verified: `bb clj-fmt:check`, `bb clj-kondo:lint` clean; runtime suite
  on EA3 (`27-jep401ea3+1-1`, `~/.local/jdks/jdk-27.jdk`) with preview
  and the export: 167 tests, 5167 assertions, 0 failures, 0 errors.

## Code-shaper review, pass 7 (2026-11-06)

- Suites not re-run; `bb clj-kondo:lint` and `bb clj-fmt:check` clean.
- code-shaper review found no new issues
