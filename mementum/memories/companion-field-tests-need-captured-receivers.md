❌ A closure that uses a bare `deftype` field symbol can capture its value
instead of reading the owner's field. Such a fixture can pass without testing
companion-to-owner field-reference rewriting.

For that contract, read fields through an explicit captured receiver inside
the nested function, for example `(.-required this)`. Exercise escaping
functions, both control-flow branches and nullable controls. Do not require
a particular closure layout. For persisted-byte checks, establish that the
companion actually contains an owner-field reference before claiming that
rewriting was verified.

Use this fixture in every execution matrix that claims companion coverage;
an AOT-only fixture does not establish runtime matrix coverage.

Source: task `016-nil-restricted-fields`, persisted AOT non-vacuity failure
(`ddaa87b`) and runtime fixture correction (`e186f54`). Verified on Clojure
1.12.6 and Valhalla EA3 with flattening on/off and normal/interpreter execution.
