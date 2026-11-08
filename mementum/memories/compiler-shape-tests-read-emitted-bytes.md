🔁 Test compiler shape data against the class bytes that Clojure emits,
not against other constants derived from the same data.

Compile a real `defrecord` with `*compile-files*` and a temporary
`*compile-path*`. Read instance field names, descriptors and order, and
constructor parameter counts with `clojure.asm.ClassReader`. Compare these
facts with `impl.compiler` shape data and index functions. Read the bytes
inside `support/with-temp-dir` so cleanup runs after the read.

Do not use the order of `Class/getDeclaredFields`: the JDK does not specify
it. The production backends use class-file order, so reflection order is
not a valid test oracle. A check that one derived constant equals another
can pass when both encode the same wrong compiler assumption.

Evidence: task 026 test reviews replaced constant checks, then reflection
order checks. `98f718b4` reads emitted bytes; `9b3bea0f` adds cleanup.
Changing `hidden-field-index` made the test fail. Example:
`test/runtime/value_type/impl/compiler_test.clj`.
