❌ Edited prose lines that exceed the width of the file get review
follow-ups. `bb vale`, `bb clj-fmt:check` and `bb clj-kondo:lint` do not
check line length, so they pass.

Task `021-capability-report` needed six review follow-up steps to
rewrap lines in docstrings, `doc/`, `ramora/` and `mementum/` (one line was
124 columns).

Rule: after you edit a paragraph or docstring, rewrap the changed lines to
the paragraph width of the file (78 columns in this project). Table rows
stay as they are. Check before commit:

```
awk 'length>78{print FILENAME": "FNR": "length}' <edited files>
```

Then compare each hit with the lines near it; frontmatter, tables, links
and code can be longer.
