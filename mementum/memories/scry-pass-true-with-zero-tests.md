❌ scry `:pass? true` does not prove that tests ran.

In scry 0.1.61, `scry.core/run` gives `:pass?` true when the count of
failures and errors is zero. When no test var runs, `:pass?` is also
true. Always check `[:summary :var-count]` too.

Example: `(scry.core/run {:namespaces ['value-type.environment-test]})`
must give `:pass? true` and `:var-count 3`. Update the expected count
when tests are added or removed.

The CLI is different: `clojure -M:test -m scry.cli` exits 0 only when
at least one test var runs and all tests pass.

Discovery: with no arguments, `scry.cli` finds only the namespaces
under `test` whose names match `#".*-test$"`. A test namespace with a
different name does not run.

`scry.core` is not loaded at REPL start. Require it first.

Source: scry `src/scry/capture.clj` (`build-result`) and
`src/scry/clojure_test.clj`. Task `001-scaffold-project`.
