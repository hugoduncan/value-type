# Changelog

## [Unreleased]

- Initial release. Add `defvaluetype` and `defvaluetype*` for JEP 401 value
  classes with `deftype` fallback, `:non-nil` fields and experimental
  `value-type.array` functions for null-restricted arrays.
- Add `value-type.capability/report`, a read-only EDN report of value-class,
  native non-nil field, array allocation and array inspection capability.
