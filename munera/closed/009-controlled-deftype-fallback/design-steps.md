# Design follow-ups

- [x] Define whether expanding `defvaluetype` without evaluating the
  expansion counts as an attempted fallback. State whether expansion-only
  tooling emits and consumes the one warning or throws the strict `Error`,
  and how later evaluation of the expansion applies policy. The current
  contract distinguishes requiring the namespace from fallback, but does
  not make this distinction for `macroexpand`. Make the expected behaviour
  testable without changing the scope.
