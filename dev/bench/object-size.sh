#!/usr/bin/env bash
# Run from the repository root. Clojure uses JAVA_CMD when it is set.
set -euo pipefail

mkdir -p target/object-size
rm -f target/object-size/{results.edn,raw.edn,stdout.log,stderr.log}
exec >target/object-size/stdout.log 2>target/object-size/stderr.log

# Use a file so dependency and agent messages cannot become part of the path.
agent_file=$(mktemp)
trap 'rm -f "$agent_file"' EXIT
CRITERIUM_AGENT_PATH_FILE="$agent_file" clojure -M:object-size -e '
(require (quote [criterium.agent :as agent]))
(let [path (agent/extract-agent!)]
  (when-not path
    (throw (ex-info "No Criterium native agent for this platform" {})))
  (spit (System/getenv "CRITERIUM_AGENT_PATH_FILE") path))'
agent_path=$(cat "$agent_file")
if [[ "$agent_path" != /* || ! -f "$agent_path" ]]; then
  echo "Criterium agent path is absent or invalid: $agent_path" >&2
  exit 1
fi
clojure "-J-agentpath:$agent_path" -M:object-size -m value-type.object-size
