🔁 A macro can catch the bytecode of a `deftype` class before the JVM
defines it.

How: at macroexpansion time, `.set` the thread-bound
`clojure.lang.Compiler/LOADER` to a capture loader. Use a `proxy` of
`DynamicClassLoader` that sends each `defineClass` to one delegate
`DynamicClassLoader`. Change the bytes only for the target class name.

Why it works: `Compiler.eval` and `Compiler.compile1` bind `LOADER`
before they macroexpand. `deftype` expands to `(let [] (deftype* ...)
...)`. A `let` does not push a new loader, so `deftype*` uses the
capture loader.

AOT: the compiler writes the `.class` file before it defines the
class. Write the changed bytes again with `Compiler/writeClassFile`.

Limit: `(eval (macroexpand form))` pushes a new loader, so the bytes
are not caught. Always add a runtime check of the result.

After `deftype`, refer to the class by its fully qualified symbol. The
`import` has not run at analysis time.

Verified: Clojure 1.12.1 and 1.12.6. Code: `spike/valhalla/src/spike/vt.clj`.
