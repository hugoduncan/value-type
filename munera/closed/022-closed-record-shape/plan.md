# Plan

## Approach

Implement in slices, gate first. The deftype* design
(`ramora/deftype-star-design/`) is the authority. D9 and D10 stay
provisional until the closed gate. Only `embodiment.md` and the user docs
change with the code. The gate adds its record and evidence, and a link
in the "Gate records" list of `ramora/deftype-star-design.md`; a passing
gate also changes the D10 commitment (step 1).

1. **Closed gate (D7).** Run the spike before the implementation. A new
   script `dev/spike/record-value-type/closed-gate.clj` transforms a record
   with `[^long x __meta __extmap ^int __hash ^int __hasheq]`: remove all
   four hidden fields, add the D10 check. The check uses the form of
   step 5 (`aload`, `ifnull`, `new`/`dup`/`ldc`/`invokespecial`/`athrow`
   of `IllegalArgumentException(String)`), for `__meta` then `__extmap`,
   at the start of the main constructor, before the first field write.
   Emit it in `CodeBuilder` instructions; `java.lang.classfile`
   stack-map generation builds the frames, as in the value output of
   step 4. The spike has its own transform (as `gate.clj` and
   `non-nil-gate.clj`), so it must use this form: the check adds the
   first stack map frame in a value-class constructor (current value
   constructors have no branch), and the gate must show that EA3 accepts
   this frame. Verify on EA3 with preview and `-Xverify:all`: value
   class, single field, each construction path, rejection of non-nil
   `__meta`/`__extmap`, flat array. Record method, results, limits and
   SHA-256 in `ramora/deftype-star-design/closed-gate.md`, and the raw
   result in `ramora/deftype-star-design/evidence/closed-gate-ea3-preview.edn`.
   The gate record links the raw result (as `non-nil-gate.md` does).
   Link only the gate record, not the raw result, from
   `ramora/deftype-star-design/embodiment.md` (the intro "Gate records"
   line and "Evidence") and from the "Gate records" list in
   `ramora/deftype-star-design.md`. In `embodiment.md`, also remove the
   "Differences from the design" entry "The closed gate (D7) has not
   run". If the gate passes:
   set D10 in `ramora/deftype-star-design/decisions/closed-record-shape.md`
   to "Commitment: firm (D7 closed gate passed)"; D9 stays provisional
   (its reason is the first caller, not the gate). If the gate fails:
   record evidence, stop for a design decision.

2. **Option in `value-type.core`.** Parse `:value-type/closed` (last
   occurrence applies). Validate in the public-contract order:
   `:mutable-field`, `:record-shape`, `:invalid-option`, metadata keys,
   `:hidden-field`, tag, type. Errors: `:invalid-value` for other values,
   `:record-shape-required` for `true` on a plain shape; data `:option`,
   `:value`, `:class-name`. Remove each occurrence from the `deftype*`
   call for every value (`true`, `false`, `nil`). `policy-expansion`
   selects the receiving expansion for enabled fields **or** the closed
   shape (no transported flag; the receivers re-derive the shape from the
   form). Extend the shape value the capture loader gets to distinguish
   plain, record, closed; both backends get it.

3. **Receiving macros.** `value-field-deftype*` order: `:mutable-field`,
   `:record-shape`, `:invalid-option`, value capability, metadata keys, …
   (`:invalid-option` goes between `record-shape` and
   `require-value-capability!`). `identity-field-deftype*` order:
   `:mutable-field`, `:record-shape`, `:invalid-option`, metadata keys, …
   Both remove the option from their `deftype*` call. Closed forms without
   enabled fields still take the receiving path (empty specifications; the
   identity backend must then still process constructors; the value path
   needs no AOT companion write for them).

4. **Value output.** In `transform/->record-value-class`: closed shape
   removes all four hidden fields; constructor descriptors, delegating
   constructors, `create`, `getBasis` unchanged. Add the D10 check to the
   existing main-constructor rewrite (`transform/constructor-code`),
   **before** the first field write and thus before the non-nil checks:
   non-nil `__meta` or `__extmap` argument → `IllegalArgumentException`.
   Use the check form of step 5 (`aload`, `ifnull`, `new`/`athrow`), in
   `CodeBuilder` instructions; `java.lang.classfile` builds the frames.
   Slots of the two arguments come from `field-slots`.
   Message: add one function, `fields/closed-check-message` (class name →
   text), here; it gives the message for both outputs (step 5 uses it).
   The text is not contract and tests do not check it.
   Extend `value-backend/check-removed-fields` to the ordered vector
   `[__meta __extmap __hash __hasheq]` for closed forms (D4 first-field
   rule) and adjust its error text.

5. **Identity output.** ASM only, Java 8 safe. `identity-backend/prepare`
   gets the shape as its fourth argument, as `value-backend/prepare`
   does (`loader/capture-loader` passes it).
   - Constructor rules. The backend processes the owner constructors
     when the owner has enabled fields **or** the shape is closed, and
     then applies one rule set, as now (D8, shape-independent):
     control-flow rejection (jump, switch, try/catch), "both creates and
     calls the target", no write in a delegating constructor, each
     enabled field written once in each other constructor. With no
     enabled field, the write rules hold with the empty field set.
     There are no closed-only exceptions.
   - Classification (closed owner only). A first read pass
     (`ClassReader` without writer) marks each constructor as delegating
     when it calls `invokespecial <owner>.<init>`. The rewrite pass then adds the
     entry check to each constructor not so marked. A constructor whose
     classification this mark does not define (it also creates an owner
     instance, or it has control flow, for example `this(…)` on one path
     only) gets `:unsupported-shape` in the rewrite pass, so no class
     with a wrong mark is defined.
   - Closed `:unsupported-shape` cases, raised in the first pass (before
     the rewrite), with the `unsupported!` data of `rewrite`: a
     non-delegating constructor with fewer than four parameters, or with
     a parameter at index n − 4 or n − 3 that is not
     `Ljava/lang/Object;`; a closed class with no non-delegating
     constructor. Compiler output does not have these cases.
   - Check form, in each non-delegating constructor, emitted on the
     delegate visitor in `visitCode` (before the first original
     instruction; the checked path rejects jumps in constructors):

     ```
     aload  <slot of parameter n − 4>   ; __meta
     ifnull L1
     new    java/lang/IllegalArgumentException
     dup
     ldc    <message>
     invokespecial java/lang/IllegalArgumentException.<init>(Ljava/lang/String;)V
     athrow
     L1: frame F_SAME
     (same block for parameter n − 3, __extmap, target L2)
     L2: frame F_SAME
     <original code>
     ```

     Local slot of parameter i: 1 + the sum of the sizes of parameters
     0 … i − 1 (`long` and `double` take two). The original constructor
     has no jump and no handler (rejected), so it has no stack map
     frame; the two `F_SAME` frames (locals of the implicit entry frame:
     `uninitializedThis` and the parameters; empty stack) are the only
     frames. `ClassReader` flag 0 gives compressed frames, so `F_SAME`
     agrees with it. `rewrite` keeps `(ClassWriter. 0)`; the method
     visitor sets max stack to max(original, 3) in `visitMaxs`; max
     locals do not change. Clojure 1.12 emits class version 52.
   - Message: `fields/closed-check-message` (step 4). No new runtime
     class: generated classes reference only
     `java/lang/IllegalArgumentException`.
   - Byte signature of the check, for both outputs: a constructor that
     has a `new java/lang/IllegalArgumentException` instruction, before
     its first `requireNonNull` call when it has one. Compiler
     output of a `deftype*` constructor has no such instruction.

6. **Tests.** Option errors and order pairs per Acceptance 2 (only the
   pairs that include `:invalid-option`). The fixture of each
   `:record-shape`/`:invalid-option` pair, for `defvaluetype*` and both
   receiving macros: a form with a `:record-shape` error (an incomplete
   or misplaced hidden field set, for example `[x __meta]`) and an
   invalid option value (`:reason :invalid-value`); it gives
   `:record-shape`. Do not use `true` (`:record-shape-required` needs a
   plain-shape form, which has no `:record-shape` error). Construction
   paths and rejection per Acceptance 3, exception found in the cause
   chain as task 020. Byte inspection per Acceptance 4, with the byte
   signature of step 5: exactly one constructor has the check, and it is
   the main constructor (all fields as parameters). Value bytes:
   `java.lang.classfile` on `transform/->record-value-class` output
   (`test/runtime/value_type/impl/transform_test.clj`, record section).
   Identity bytes: `clojure.asm.ClassReader` on the identity owner
   transform output (Java 8 safe). Direct identity backend tests with
   synthetic class bytes (`clojure.asm.ClassWriter`, as in
   `test/compatibility/value_type/identity_backend_test.clj`) for each
   closed `:unsupported-shape` case of step 5, and for a closed class
   without enabled fields with a constructor that both creates and calls
   the target, and one with a jump. Removed-field
   access per Acceptance 5. Transferred expansions, AOT subprocess,
   fallback policies, Java 8 / preview-off identity per Acceptance 6.
   EA3 flat-array evidence per Acceptance 7. Regression per Acceptance 8.

7. **Docs and quality.** The user docs copy the closed contract from
   `ramora/deftype-star-design/public-contract.md` ("Closed record shape",
   "Errors"); they do not state its rules in a new form. Use one phrase,
   "enabled fields or the closed shape", where a statement now ties
   behaviour to enabled fields only. Do not list the closed
   `:unsupported-shape` cases in user docs (compiler output does not have
   them).
   - `defvaluetype*` docstring (`src/value_type/core.clj`):
     - "Field shapes": add the option. `:value-type/closed true` selects
       the closed shape and requires the record shape; `false`, `nil` and
       an absent option do not select it. The last occurrence applies.
       `defvaluetype*` removes each occurrence from the `deftype*` call.
     - Closed caller obligations: never read or write `__meta` or
       `__extmap`; pass nil for both in explicit constructor calls; do not
       give the type metadata or extension keys through these fields.
     - Closed outputs: value output removes all four hidden fields and
       constructors keep their descriptors; identity output keeps all four
       and `__meta` and `__extmap` hold nil. In both outputs the main
       constructor throws `IllegalArgumentException` for a non-nil
       `__meta` or `__extmap` argument, before the non-nil field checks,
       so `create` and the map literal reject an undeclared key. Wrappers
       and unspecified error text as for the non-nil checks.
     - "With enabled fields, the expansion is a receiving form" →
       "With enabled fields or the closed shape, …".
     - "Errors" order list: add `:invalid-option` `:reason :invalid-value`
       or `:record-shape-required` (data `:option`, `:value`) after
       `:record-shape`.
     - `:removed-field-access`: a captured class reads or writes a removed
       field: `__hash` or `__hasheq`, and for the closed shape also
       `__meta` or `__extmap`.
     - "Errors of identity output with enabled fields" → "with enabled
       fields or the closed shape".
     - "A value expansion without enabled fields evaluated apart …" →
       "with neither enabled fields nor the closed shape"; "With enabled
       fields, separate `eval` … works" → "With enabled fields or the
       closed shape, …".
   - `doc/API.md`: generated from the docstrings by `bb api-docs`
     (`bb/value_type/api_docs.clj`); run it, do not edit the file.
   - `doc/usage.md` "`deftype*` forms" only (the other sections describe
     `defvaluetype`):
     - After the two field shapes: the option paragraph, as in the
       docstring.
     - Numbered order list: insert `:invalid-option` (both reasons) as
       item 3, after `:record-shape`; renumber the items after it.
     - `:unsupported-shape` sentence: "Identity output with enabled fields
       or the closed shape gives …"; the two cases do not change.
     - Caller obligations: add the closed obligations of the docstring.
     - Output paragraph: add the closed outputs and the
       `IllegalArgumentException` behaviour; `:removed-field-access` names
       all four fields for the closed shape.
     - "Representation policy, fallback and AOT" paragraph: the fallback,
       receiving and transferred-expansion statements change as in the
       docstring ("enabled fields or the closed shape"; "neither enabled
       fields nor the closed shape").
   - `README.md` `deftype*` callers sentence: plain, `defrecord`-shaped
     and closed record classes.
   - `CHANGELOG.md`, `mementum/state.md`.
   - `ramora/deftype-star-design/embodiment.md` describes the current
     code (Ramora rule, `ramora/INDEX.md`). Name the sections by their
     headings; state the closed behaviour, not task history:
     - Intro: the status sentence ("D9 and D10 are not implemented
       (task 022); see differences"); the `value-type.core` element
       (add `:invalid-option` to the check list; "With enabled fields"
       → "With enabled fields or the closed shape"); the
       `value-type.impl.runtime` element ("`guarded-deftype*` for forms
       without enabled fields" → "with neither enabled fields nor the
       closed shape").
     - "Declaration checks (D6)": add `:invalid-option` after
       `:record-shape`.
     - "Receiving macros": the expansion for enabled fields or the
       closed shape; `:invalid-option` in both receiver orders; the
       `guarded-deftype*` sentence for forms with neither; removal of
       the option from the `deftype*` calls.
     - "Record value storage and checks (D8)": the closed shape value;
       removal of all four hidden fields in closed value output; the
       D10 check in the main constructor, before the non-nil checks;
       removed-field access for all four fields of closed forms.
     - "Identity delegation rule (D8)": the shape argument; processing
       of constructors when the owner has enabled fields or the shape is
       closed; the classification pass, the entry check and the closed
       `:unsupported-shape` cases.
     - "Evidence": the closed tests in the runtime, fallback and
       cross-JVM suites (the closed gate link comes from step 1).
     - "Differences from the design": remove each entry that no longer
       applies. When no entry remains, remove the section and the intro
       link to it.
   - Run `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint`,
     `bb vale`; cross-JVM suites. Record commands, results, limits in
     `implementation.md`.

## Key decisions

- Gate first; a gate failure stops the task for a design decision; a
  gate pass makes D10 firm (D9 stays provisional).
- No transported closed flag: receivers derive the shape from the form.
- One D10 check site per output: value main-constructor rewrite; identity
  non-delegating constructor entry by parameter position. One check form
  in both outputs: `aload`, `ifnull`, `new`/`athrow` of
  `IllegalArgumentException`; no helper class.
- The identity backend applies one constructor rule set (D8) when the
  owner has enabled fields or the shape is closed.
- `impl.fields` stays the owner of the non-nil rules; the option is not a
  field rule.
- Forms without `:value-type/closed true` keep behaviour; only a `false`
  or `nil` option is removed from the `deftype*` call.

## Risks

- **Gate failure on EA3** (flattening or verification). Mitigated by
  running the gate first; evidence is recorded either way.
- **Identity entry check with `ClassWriter. 0`**: the branch needs
  correct hand-built `F_SAME` frames and max stack (step 5). Verify with
  `-Xverify:all` on Java 8 and on EA3 with preview off. No branch-free
  form exists without a helper class (a `checkcast` gives
  `ClassCastException`), and a helper class is out of scope (packaging,
  Java 8, AOT).
- **Delegation known only after the body is read**: requires a two-pass
  read in the identity backend. The rewrite pass rejects each constructor
  that the first-pass mark does not classify (step 5), so a
  mis-classified class is never defined. D8 scope stays narrow.
- **Order regressions** in the receivers when inserting `:invalid-option`;
  the existing order tests (tasks 017, 020) guard the other pairs.
- **EA3 environment availability** for gate, Acceptance 7 and cross-JVM
  runs; record limits in `implementation.md` if a JVM is unavailable.

## Slice order

1. Closed gate, gate record and D10 commitment.
2. Option validation and removal in `value-type.core`; expansion choice;
   shape plumbing to the loader and backends.
3. Receiving macros: option check order and removal.
4. Value output: check message function, field removal, D10 check,
   removed-field access.
5. Identity output: shape argument, classification pass, constructor
   rules for closed forms, entry check, `:unsupported-shape`.
6. Tests: option/order, construction paths, bytes, access, transfer/AOT,
   fallback, cross-JVM, EA3 evidence, regression.
7. Documentation, embodiment, quality checks.
