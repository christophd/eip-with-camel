#!/usr/bin/env bash
# Boot every built example and report whether the application actually starts.
#
# This exists because passing tests turned out not to imply a working
# application: the Citrus test dependencies put camel-bean on the test
# classpath, so routes using Simple OGNL started fine under test and then
# failed at runtime with NoSuchLanguageException in an app that never declared
# the dependency. Tests exercise the test classpath; only booting the built
# artifact exercises the runtime one.
#
#   ./scripts/verify-all-runtime.sh            # all examples
#   ./scripts/verify-all-runtime.sh 20 32      # only matching
#
# Requires the base stack up and the Citrus tests not running.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

FILTERS=("$@")
LOG_DIR="${TMPDIR:-/tmp}/eip-runtime-logs"
mkdir -p "$LOG_DIR"

matches() {
  [ ${#FILTERS[@]} -eq 0 ] && return 0
  for f in "${FILTERS[@]}"; do [[ "$1" == *"$f"* ]] && return 0; done
  return 1
}

declare -a OK=() BAD=()

boot() {
  local label="$1" jar="$2"
  local log="$LOG_DIR/${label//\//_}.log"
  printf '  %-44s ' "$label"

  java -jar "$jar" > "$log" 2>&1 &
  local pid=$!
  local started=0
  for _ in $(seq 1 45); do
    if grep -qE 'started in|Started .*Application' "$log"; then started=1; break; fi
    if ! kill -0 $pid 2>/dev/null; then break; fi
    sleep 1
  done
  kill -TERM $pid 2>/dev/null; wait $pid 2>/dev/null

  if [ "$started" -eq 1 ]; then
    printf 'BOOTS\n'; OK+=("$label")
  else
    local why
    why=$(grep -oE 'No language could be found for: [a-z]+|No component found with scheme: [a-z0-9-]+|NoSuchBeanException[^,]*|Caused by: [A-Za-z.]+Exception[^\n]{0,70}' "$log" | head -1)
    printf 'FAILS  %s\n' "${why:-see $log}"; BAD+=("$label")
  fi
}

for root in examples/*/; do
  root="${root%/}"; name="$(basename "$root")"
  [ "$name" = "_infra" ] && continue
  matches "$name" || continue

  for rt in quarkus spring-boot; do
    [ -d "$root/$rt" ] || continue
    if [ "$rt" = "quarkus" ]; then
      jar="$root/$rt/target/quarkus-app/quarkus-run.jar"
    else
      jar="$(ls "$root/$rt"/target/*.jar 2>/dev/null | grep -v -- '-sources\|-plain' | head -1)"
    fi
    [ -n "${jar:-}" ] && [ -f "$jar" ] && boot "$name/$rt" "$jar"
  done
done

echo
echo "Boots: ${#OK[@]}    Fails: ${#BAD[@]}"
if [ ${#BAD[@]} -gt 0 ]; then
  printf '  %s\n' "${BAD[@]}"
  exit 1
fi
