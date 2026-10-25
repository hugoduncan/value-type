#!/usr/bin/env bash
set -euo pipefail
# pre-commit detects edits and leaves staging to the contributor.
# Never fall back to project-wide fixing when no filenames were supplied.
if (( $# == 0 )); then
  exit 0
fi
exec mise exec -- bb clj-fmt fix "$@"
