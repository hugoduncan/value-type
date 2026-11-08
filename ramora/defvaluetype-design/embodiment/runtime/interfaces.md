# Runtime interfaces

Part of the [runtime embodiment](../runtime.md).

## Public

```clojure
(defvaluetype Name [fields*] options* specs*)
```

- Syntax and options: the same as `clojure.core/deftype`.
- Result: the class `Name`, the factory `->Name`. The fallback
  path returns an identity class: an ordinary `deftype` class without enabled
  non-nil fields, or a class with generated constructor checks with them.
  Enabled fields require an explicit tag that names an already defined concrete
  class: identity, value or external.
- Strict failure: an `Error` on every attempted fallback.
  Direct rejection uses plain java.lang.Error. Source compiler operations
  wrap it in CompilerException; failed AOT initializer retries can throw
  NoClassDefFoundError. These phase behaviours are verified by production tests on the recorded matrix.
- Errors: `ex-info` with `:value-type/error` and `:class-name`;
  `:value-type/error` in `#{:invalid-field :mutable-field :not-installed
  :not-value-class :value-capability-unavailable
  :field-capability-unavailable :invalid-field-enforcement :jvm-rejected
  :unexpected-constructor :unsupported-shape}`. The `defvaluetype` docstring
  Errors list ([API](../../../../doc/API.md)) gives the phase and data of each. A
  `deftype` syntax error (for example a reserved field name) gives the
  `deftype` error.

```clojure
(check-value-class cls) ;=> nil, or throws
;; Called by the value expansion. :not-value-class when cls is
;; not a value class. :not-installed comes from the receiving
;; macro at macroexpansion, not from this function.
```

## Internal

```clojure
;; value-type.impl.transform
(->value-class identity-class-bytes storage class-loader)
;=> bytes
;; storage maps emitted field names to {:storage-type ClassDesc
;; :native? boolean}. :native? false selects constructor null checks
;; instead of native annotations.
;; class-loader (or nil) resolves dynamic types for stack maps.
;; throws ex-info {:value-type/error :unexpected-constructor
;;                  :class-name "binary.Name"}
;; when D6 does not match. Check steps, in order: count of
;; constructors, parameters, code (super call, field writes).

;; value-type.impl.capability
(require-value-capability! class-name) ;=> nil, or throws
;; ex-info {:value-type/error :value-capability-unavailable
;;          :class-name "ns.Name" :reason :disabled}
;; when the cached value-class result is unavailable (example: disabled).
;; First form of each value expansion.

;; value-type.impl.compiler (requires only Clojure)
(capture-loader target parent prepare check define-target
                rewrite-companions?) ;=> ClassLoader
(install-capture! make-loader) ;=> true, or false when LOADER
;; is not thread-bound
(define-class loader class-name bytes src) ;=> Class
(probe-loader) ;=> ClassLoader
;; shape data: record-hidden-fields, hidden-field-count,
;; hidden-field-index, hidden-field-position, record-constructors,
;; deftype-constructor; tags: resolve-tag; munge-name

;; value-type.impl.loader
(install! class-name specifications representation shape)
;=> true when installed, false when LOADER is not thread-bound
;; representation is :value or :identity; :identity requires
;; :generated enforcement.
;; shape is :plain for an ordinary deftype, :record for a record
;; shape, or :closed for a record shape with :value-type/closed true.
;; Both backends receive shape.
;; Ordered specifications retain :field, exact :type Class and
;; :enforcement. Backends derive descriptors from :type.
;; :generated selects constructor checks; :native selects native
;; enforcement.
;; The capture loader throws ex-info {:value-type/error :jvm-rejected
;; :class-name "ns.Name"} with the LinkageError as cause when the JVM rejects
;; the define or the link. Errors of ->value-class pass through.
```

The loader passes its delegate through `value-backend/prepare` as the
class-loader argument, so stack-map generation can resolve dynamically
defined field types.
