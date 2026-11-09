#!/bin/bash
set -eu
cd /checkout
export PATH=/root/.local/bin:$PATH
eval "$(mise env -s bash)"
export JAVA_CMD=$(mise which java)
export VALUE_TYPE_BASELINE_JAVA=$(cat /evidence/baseline.path)
export VALUE_TYPE_VALHALLA_JAVA=$(cat /evidence/valhalla.path)
export VALUE_TYPE_SMOKE_JAVA=$(cat /evidence/ordinary.path)
mkdir -p /evidence/arm64-compatibility
out=/evidence/arm64-compatibility
git rev-parse HEAD > "$out/revision.txt"
run() {
 name=$1; shift
 printf '%q ' "$@" > "$out/$name.command"; echo >> "$out/$name.command"
 start=$SECONDS
 set +e
 timeout -k 10 1200 "$@" > "$out/$name.log" 2>&1
 status=$?
 set -e
 printf '{:exit %s :seconds %s}\n' "$status" "$((SECONDS-start))" > "$out/$name.status.edn"
 ps -eo pid,ppid,args > "$out/$name.processes.txt"
 if [[ -d .scry-results ]]; then cp -r .scry-results "$out/$name.scry"; fi
 cat "$out/$name.status.edn"
 [[ $status = 0 ]] || exit "$status"
}
run fallback-java8 env JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:fallback-test
run fallback-preview-off env JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test
flags=$(bb -e '(require (quote [value-type.jvm-facts :as facts])) (doseq [flag (:jvm-opts (facts/select (facts/read-facts) :valhalla :linux-aarch64 :preview))] (print (str "-J" flag " ")))')
read -r -a jvm_flags <<< "$flags"
run fallback-preview-on env JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure "${jvm_flags[@]}" -M:fallback-test
run cross-jvm env JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test
git diff --exit-code HEAD -- > "$out/tracked-diff.txt"
