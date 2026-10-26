# Changelog

## [Unreleased]

- Add `defvaluetype` with value classes on supported Valhalla JVMs and
  ordinary `deftype` fallback on other JVMs.
- Add source-only JAR packaging and isolated consumer checks on Java 8
  and Valhalla.
- Add development quality checks and generated API documentation.
- Add release tooling for deployment credential checks and changelog
  validation, version stamping and release-note selection.
- Add local release preparation and notes-export commands with complete-history
  checks. Preparation commits only the changelog and does not publish.
- Add a manual release workflow with default preview, explicit JVM checks and
  fail-stop publication, plus maintainer release instructions.
