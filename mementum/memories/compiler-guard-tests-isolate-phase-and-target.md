🔁 A matching error does not prove which guard or compiler phase rejected
a definition. Isolate the required phase and target.

For transferred value expansions, compile `(fn [] <expansion>)` without
invoking it. Require `CompilerException` with the expected immediate cause
data and class name. A helper that compiles and invokes in one `eval` can
pass when the analysis guard is absent and only the emitted runtime guard
rejects. Cover both public expansion forms and applicable macro shapes.

For guard order, a fixture can fail at an earlier definition. A native-field
`Holder` fixture that first defines `Leaf` does not prove that `Holder`
emits the value-class guard before the native-field guard. Inspect the
receiving expansion under a compiler loader binding, or isolate the target
and assert its class name.

Verify test sensitivity by removing the analysis guard or swapping emitted
guards. In task 026, these mutations failed the new checks (`0704a209`,
`e1ccf25e`). Examples: `ordinary_transfer_analysis_test.clj` and
`value_receiving_test.clj`.
