🔁 Observe capability state before calling code that can change it.

`value-type.capability/report` forces all capability results. Thus a report
call can load optional adapters and hide a missing operation-side load or
break an isolation observation. Requiring the report namespace is different
from calling `report`.

In child-JVM checks, bind observations and actions sequentially in a `let`:
initial namespace/delay state, tested operation, post-operation state, then
report. Build the returned map from those bound values. Do not use result-map
entry order to sequence discovery and observation. Moving map entries must
not change what the test measures.

Use an ordinary Object array for initial inspection checks. Do not first load
support namespaces with top-level value definitions: those definitions trigger
value-class discovery and contaminate the initial state.

Evidence: task 029 plan handoff, receiving-runtime AOT correction and test
review follow-up `8d389132`. The maintained and temporarily permuted result
maps both passed the existing loading profiles after explicit sequencing.
