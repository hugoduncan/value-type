---
title: Verify development checks against real defects
status: active
category: testing
tags: [quality-checks, clj-kondo, vale, quickdoc, pre-commit, ci]
related: [mementum/knowledge/testing-library-artifacts.md, mementum/memories/valhalla-jdk-selection-java-cmd.md]
depends-on: []
---

# Verify development checks against real defects

Source: task `010-development-quality-checks`, its design, plan, steps and
implementation notes. Review corrections: scope validation `1515409`, owned
Vale configuration tests `6e38ce3`, generator defect probe `c0da0f1`, and
warning-only lint probes `cfdfd17`. Hook safety was implemented in `cad7385`.

## Missing scope must fail, not reduce coverage

Project formatting and lint share a maintained path list in
`bb/value_type/quality.clj`. Filtering this list by file existence silently
reduces coverage when a required file or directory disappears. Validate all
required paths before tool execution or dependency setup. Report missing
paths and stop, including for project formatting repair.

Keep explicit-path formatting separate: checking `src` alone must not
require every project path. Test both a missing directory and a missing file
in a disposable checkout. The project commands must fail without further
file or index changes; explicit-path checks must remain usable.

## Test the configuration that owns the behavior

A test with replacement Vale rules can verify severity handling but cannot
protect the repository's actual exclusions. Keep separate integration tests
that copy the owned `.vale.ini` and vocabulary. With the real command, verify
that lambda instruction lines and fenced code pass, while the same defect
in ordinary `AGENTS.md` prose fails. Compare input bytes after each run.

Likewise, an error fixture does not prove that warnings block lint. Use valid
code with an unused binding. Confirm from structured clj-kondo output that
there are warnings and zero errors, then require both project lint and the
lint hook to fail. Test each failure threshold independently.

Vale suggestions must pass; warnings and errors must fail. `--no-exit` lets
the wrapper apply that policy, but setup errors, invalid JSON, unknown
severities and missing scoped files must still fail. Do not treat absent or
unreadable results as an empty alert set.

## Fresh generated output can still be wrong

`bb api-docs --check` compares output with the current generator. Agreement
cannot prove that the generator retains required public API content or
curated prose. Keep independent content assertions for the exact public
surface, contract, notice and links. Public visibility alone does not make
`check-value-class` part of the documented user API.

To verify this gate, remove a required notice from a disposable copy of the
generator and run the existing tests unchanged. A synthetic failing test
only proves the runner's failure behavior. Test missing/stale output and
invalid CLI arguments through the actual Babashka entry point. Checks must
not write; two successive generations must produce identical bytes.

## Protect the index as well as working files

Formatting hooks may repair files, but must not stage them. A copied hook
that runs `git add` can include previously unstaged edits in a commit.

Use real Git and pre-commit in disposable repositories. Cover partial
staging, conflicting unstaged edits, unrelated edits and excluded paths.
Compare index trees and working-file bytes, not only command exits. For CI
checks, compare against the prepared fixture after each injected defect,
including failure paths. Keep probe logs outside the tracked fixture.

Reusable checks are `test-tooling/hooks.sh`, `test-tooling/ci-failures.sh`
and the real-process tests under `test-quickdoc/`.

## Keep tooling setup separate from runtime evidence

Resolve lint dependency configurations for each relevant alias separately,
then combine classpaths. The `:build` alias replaces dependencies; one merged
basis can omit other configurations. Separate resolution also covers the
different benchmark dependency versions. Use reproducible project config
and test with empty user configuration and cold imports.

The mise tooling JVM is not the library's minimum JVM. Unset a runtime-test
`JAVA_CMD` override for tooling, and do not compose the value-only `:test`
alias into tooling tests. Quality CI does not replace the Java 8 and Valhalla
runtime matrix. Cold Linux container checks establish command behavior on
that platform, not hosted Actions orchestration or another architecture.
