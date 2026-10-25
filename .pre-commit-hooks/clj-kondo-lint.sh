#!/usr/bin/env bash
set -euo pipefail
exec mise exec -- bb clj-kondo:lint
