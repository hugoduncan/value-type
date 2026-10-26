# Value-array semantic verification

Task 012 observed Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`, macOS
aarch64. Java `1.8.0_492-b09` is an unsupported-runtime control. These results
apply to the recorded build and fixtures, not all value classes or future JDKs.
No production API is implemented.

[Reproduction instructions](../../spike/value-array-semantics/README.md) describe
the bounded matrix. The [aggregate result](evidence/matrix-selected-slots/validation.edn)
records coverage and unresolved dependencies. Exact commands, versions,
environment, raw output and source hashes are retained in
[the matrix directory](evidence/matrix-selected-slots/). Two unchanged-source matrix runs
produced identical summaries and hashes. Expected rejection and fatal outcomes
are successful observations, not successful API calls.

## 1. Signatures and access

[Installed signatures](evidence/matrix-selected-slots/startup/signatures.txt) expose
`Object[] newNullRestrictedAtomicArray(Class, int, Object)` and three boolean
queries taking Object: `isFlatArray`, `isNullRestrictedArray`, `isAtomicArray`.
The API is internal to `jdk.internal.value.ValueClass`. Discovery does not
require exports; invocation requires
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.

[Startup cases](evidence/matrix-selected-slots/startup/validation.edn) separate discovery,
strict value creation and zero-length allocation:

| Configuration | Outcome |
|---|---|
| Preview and exports | Value creation and allocation pass. |
| Preview, no exports | Value creation passes; allocation rejects with IllegalAccessException. |
| No preview, exports | Object-array diagnostic works; strict value creation fails. Dependent allocation remains unresolved. |
| Neither | Discovery works, diagnostic access fails, strict value creation fails. Dependent allocation remains unresolved. |
| Java 8 | Optional API discovery records ClassNotFoundException; strict value creation fails. Dependent allocation remains unresolved. |

Thus a successful diagnostic is not proof of value-allocation capability.
Preview-off results do not establish a native allocator rejection: no value
fixture was available. Cause chains are in the individual startup EDN files.

## 2. Empty arrays and seeds

[Allocation cases](../../spike/value-array-semantics/allocation.clj) and
[results](evidence/matrix-selected-slots/semantics/default/stores/allocation.edn) use identifiers
such as `:allocation/long-0-valid` and `:allocation/long-0-null`.
Valid seeds permit lengths 0, 1 and 3. Null and incompatible seeds reject even
at length zero. Length -1 rejects with a valid seed. All these allocation
rejections retain InvocationTargetException → IllegalArgumentException chains.
Every accepted array has its component, length and all initial slots checked.
An empty array has no indexed access case.

The installed signatures and [bundled source](evidence/matrix-selected-slots/startup/ValueClass.java)
contain no seed-free overload of the selected restricted atomic allocator.
This supports the proposed explicit-seed empty constructor. It does not prove
that no other JDK allocation route exists. No maximum-length or exhaustion
allocation was attempted.

## 3. Component eligibility and layout

The four value classes are zero-field, one-long, two-long and one-Object-field.
The reference fixture has both null and non-null field variants. All accept
valid seeds. A null field is not a null array element. Identity and primitive
long components reject. In particular, `long.class.isValue()` returns true,
but allocation with its boxed seed rejects as a type mismatch. Value status
alone is not an eligibility check.

[Diagnostic results](evidence/matrix-selected-slots/semantics/default/diagnostics/diagnostics.edn)
record each property independently. At lengths 0, 1 and 3, restricted arrays
are null-restricted and atomic. Their observed flat status is:

| Component | Restricted flat | Ordinary typed array flat (length 3) |
|---|---|---|
| Zero-field | true | true |
| One-long | true | false |
| Two-long | false | false |
| One-Object-field, either payload | true | true |

Eligibility therefore does not require flat storage. These cases do not
establish eligibility for all runtime value classes, or explain native layout
selection. That mechanism remains unresolved.

## 4. Contents and stores

[Store cases](../../spike/value-array-semantics/stores.clj) use fresh arrays
for each access path, fixture, length, write kind and selected index. For example,
`:store/long-clojure-3-valid-1` checks Clojure access; the corresponding `java`
case uses [ordinary Java Object[] bytecode](../../spike/value-array-semantics/ArrayAccess.java).
[Results](evidence/matrix-selected-slots/semantics/default/stores/stores.edn) contain 120 checks.

Clojure `alength`, `aget`, `aset` and Java length/read/store operations preserve
initial contents at lengths 1 and 3. Each valid write replaces only the
selected slot; all other slots retain their seed values. Each index has an
independent null and wrong-type write attempt, followed by a check of all slots. Null stores throw
NullPointerException; incompatible stores throw ArrayStoreException, without
reflection wrappers. Failed writes preserve all slots. Reference replacements
cross null/non-null field values. Zero-field replacement passes but cannot
show a distinct payload. These checks do not cover clone or copy guarantees.

## 5. Diagnostic domains and failures

Cases use IDs such as `:diagnostic/long-restricted-0-isAtomicArray`.
Ordinary Object, identity and value arrays report atomic true and
null-restricted false. Primitive long arrays report false for all three
queries. A String also returns false for all three queries; this is not
proof that String is a valid public diagnostic input.

**Each null query crashes EA3 with SIGSEGV and exit 134.**
[Isolated records](evidence/matrix-selected-slots/semantics/default/diagnostics/isolated.edn)
link the process evidence, including fatal logs. Each query runs in a separate
JVM. There is no returned false value or Java exception for these cases.
The native crash mechanism is unresolved. Public input validation must not
send null to these methods. The proposed rejection of primitive arrays and
non-arrays remains a library contract, not the observed native behavior.

## 6. Flattening and atomic access

The [disabled-layout evidence](evidence/matrix-selected-slots/semantics/disabled/) adds
`-XX:-UseArrayFlattening` to preview and exports. All flat queries return false.
[Comparison](evidence/matrix-selected-slots/semantics/comparison.log) confirms equal contents,
store exception classes, null restriction and atomic diagnostics. Exception
messages differ and remain in raw results. Null-query crashes also persist.

The bundled Java declaration describes a layout designed for atomic access;
it does not supply proof of the native implementation. The sequential probes
confirm outcomes, not concurrent behavior. Slots are mutable: a store replaces
a whole element. Value objects remain immutable; a reference field can still
refer to mutable state. Atomic element access does not make a read-modify-write
sequence atomic and does not establish safe publication or a synchronization
protocol. Callers must coordinate shared access.

The [historical one-long measurements](../defvaluetype-design/typed-array.md)
apply only to their recorded payload, lengths, flags and benchmark setup.
This semantic matrix measures no storage or speed benefit for wider, empty or
reference-field layouts. Flat status alone is not such a measurement.
