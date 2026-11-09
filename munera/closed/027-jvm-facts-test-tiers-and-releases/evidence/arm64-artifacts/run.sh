#!/bin/bash
set -eu
cd /checkout
export PATH=/root/.local/bin:$PATH
eval "$(mise env -s bash)"
export JAVA_CMD=$(mise which java)
export VALUE_TYPE_BASELINE_JAVA=$(cat /evidence/baseline.path)
export VALUE_TYPE_VALHALLA_JAVA=$(cat /evidence/valhalla.path)
export VALUE_TYPE_SMOKE_JAVA=$(cat /evidence/ordinary.path)
mkdir -p /evidence/arm64-artifacts
out=/evidence/arm64-artifacts
git rev-parse HEAD > "$out/revision.txt"
run() {
 name=$1; shift
 printf '%q ' "$@" > "$out/$name.command"; echo >> "$out/$name.command"
 start=$SECONDS
 set +e
 timeout -k 10 600 "$@" > "$out/$name.log" 2>&1
 status=$?
 set -e
 printf '{:exit %s :seconds %s}\n' "$status" "$((SECONDS-start))" > "$out/$name.status.edn"
 ps -eo pid,ppid,args > "$out/$name.processes.txt"
 if [[ -d .scry-results ]]; then cp -r .scry-results "$out/$name.scry"; fi
 cat "$out/$name.status.edn"
 [[ $status = 0 ]] || exit "$status"
}
run build env JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:build-test -m scry.cli --dir test/build
run release clojure -M:release:release-test -m scry.cli --dir test/release
run notes clojure -M:release -m value-type.release notes "$out/release-notes.md" "$out/selection.txt" --preview
run jar clojure -T:build jar
git diff --exit-code HEAD -- > "$out/tracked-diff.txt"
sha256sum target/*.jar > "$out/jar.sha256"
