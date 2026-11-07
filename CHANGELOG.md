# Changelog

## [Unreleased]

- Initial release. Add `defvaluetype` and `defvaluetype*` for JEP 401 value
  classes with `deftype` fallback, `:non-nil` fields and experimental
  `value-type.array` functions for null-restricted arrays.
- Add the `defvaluetype*` option `:value-type/closed true`: a closed record
  shape with no `__meta` and `__extmap` in value output. The main
  constructor rejects a non-nil `__meta` or `__extmap` argument, so `create`
  and the map literal reject undeclared keys.
- Add `value-type.capability/report`, a read-only EDN report of value-class,
  native non-nil field, array allocation and array inspection capability.
