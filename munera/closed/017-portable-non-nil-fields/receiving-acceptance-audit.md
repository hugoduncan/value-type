# Public receiving acceptance audit

## Scope

This audit reconciles the first three slice 4 checklist items with public
cross-process tests. It does not complete acceptance 6 or the runtime matrix.

`test/compatibility/value_type/field_transfer_test.clj` uses real JVM producers
and independent receivers. `field_transfer_checks.clj` serializes metadata with
both `macroexpand` and `macroexpand-1`. Receivers use eval, metadata-preserving
source loading, or function analysis followed by invocation.

## Supported receiving compilation

- `supported-public-transfer-test` transfers native Long and generated String
  value owners. All three policies pass on preview-enabled EA3 in normal and
  interpreter execution without warnings.
- `identity-public-transfer-test` transfers Java 8-generated String identity
  owners to Java 8 and EA3 with preview off/on. Silent and warn retain identity
  equality and generated enforcement in normal and interpreter execution. Warn
  emits one warning across repeated installation.
- `mixed-public-transfer-test` transfers native Long/generated String owners to
  EA3 under every policy and execution mode. Each constrained position rejects
  nil and wrong-type arguments independently.

Supported cases install two distinct owner Classes. Factories, direct Clojure
constructors and reflective constructors retain concrete field types, annotation
selection and valid reads. Nil raises NullPointerException; wrong types raise
ClassCastException after reflective invocation wrapping is removed. Independent
receivers cannot reuse the producer's compiler loader.

## Incompatible receiving compilation

- `unsupported-public-transfer-test` rejects fixed native and generated value
  expansions on Java 8 and preview-disabled EA3 under every policy and execution
  mode. Native Long fails local native-tag eligibility; generated String fails
  value capability validation.
- `strict-identity-public-transfer-test` rejects fixed identity expansions on
  all three JVM configurations, including capable receivers, in both execution
  modes.
- `unsupported-mixed-public-transfer-test` rejects fixed mixed expansions on
  Java 8 and preview-disabled EA3 under every policy and execution mode.

Each rejection case repeats both expansion entry points through all three
analysis paths, checks the root error and confirms no owner installation or
warning. These tests do not assert factory absence on rejection.

## Limits

These fixtures establish fixed receiving selection, not controlled native
absence. They do not establish discarded-expansion timing, exact child Class
identity after redefinition, companion hierarchy resolution or source-free AOT.
Those remain separate checklist items and evidence. Test-host flags do not
expand the explicit child JVM matrix. No mocks or production changes are needed
for these three checklist items.

The focused verification command and outcome are recorded in implementation.md.

## Isolated receiving policy

The slice 4 warning/policy item also uses `transfer_test.clj`. The constrained
concurrent test now runs both silent and warn policies. Both public expansion
entry points distribute fixed generated Long fields across eight synchronized
namespaces, with two owner definitions per namespace. Each policy runs in two
independent runtimes, in normal and interpreter execution. Silent produces no
stderr; warn produces exactly one warning per runtime. These checks establish
warning scope, not constructor nil/type rejection.

The constrained strict test rejects two attempts in each of eight concurrent
namespaces, without owner installation, factory binding or warnings. It covers
both expansion entry points and execution modes. The public field-transfer tests
above separately cover silent/warn repeated receiving installation and repeated
strict rejection through eval, source loading and function analysis. Together,
these fixtures close the isolated receiving-policy item, not controlled native
absence or the full acceptance matrix.

The complete transfer namespace passes on Java 8 and EA3 preview off/on:
4 tests / 85 assertions per configuration. Each host selects the corresponding
child receiver; identity producers use Java 8. See implementation.md for commands.
