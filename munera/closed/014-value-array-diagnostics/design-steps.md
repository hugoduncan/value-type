# Design review follow-ups

- [x] Define error precedence when `array-info` receives null, a non-array or
  a primitive array and inspection capability is unavailable. State whether
  these calls return `:invalid-array` or `:array-inspection-unavailable`, and
  require fresh-JVM checks for that result on Java 8 and with missing exports.
  Both check orders can prevent unsafe native invocation; the current safety
  requirement does not select the public error. Keep the input domain and
  capability requirement unchanged.
