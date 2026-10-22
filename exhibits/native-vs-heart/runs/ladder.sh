#!/usr/bin/env bash
# The care ladder (spec/ideas/off-the-rails.md): the triage task by each arm, over https, ROUNDS times, interleaved.
# Python arms: plain and careful, direct to GitHub and through the gateway passed through. HEART arms: the
# heart-script off the rails with blind writes, the gateway restarted for each with GATEWAY_CARE as the arm says.
# Usage: ladder.sh ROUNDS ARM... ; arms: py-direct py-via careful-direct careful-via heart:<care>
# Writes each run's output to <arm>-<stamp>.out beside this script and a line per run to ladder.tsv.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
TLS="$HOME/.cache/artie/tls"
ROUNDS="$1"; shift
GITHUB_TOKEN="$(awk '/\[https:\/\/api.github.com\]/{f=1} f&&/bearer/{print $3; exit}' "$HOME/.artie/credentials")"
export GITHUB_TOKEN ARTIE_TOKEN_LOCALHOST_8392="$GITHUB_TOKEN"
GATEWAY_PID=""

stop_gateway() { [ -n "$GATEWAY_PID" ] && kill "$GATEWAY_PID" 2>/dev/null && wait "$GATEWAY_PID" 2>/dev/null; GATEWAY_PID=""; }
trap stop_gateway EXIT

start_gateway() { # care
  stop_gateway
  (cd "$ROOT/heart/heart-gateway" && JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.12-graal" \
    GATEWAY_TLS_KEYSTORE="$TLS/gateway.p12" GATEWAY_TLS_PASSWORD="$(cat "$TLS/password")" \
    HEART_ACTS_WITHOUT_VALIDATOR=allowed GATEWAY_CARE="$1" \
    GATEWAY_RECORD="$HOME/.cache/artie/runs/upstream.record.yaml" UPSTREAM_URL=https://api.github.com \
    UPSTREAM_SPEC="$HOME/.cache/artie/ghdesc/api.github.com.json" \
    GATEWAY_CONNECTOR=build/install/heart-gateway/connectors/github UPSTREAM_AUTH=caller PORT=8392 \
    exec build/install/heart-gateway/bin/heart-gateway >"$HOME/.cache/artie/runs/gateway.log" 2>&1) &
  GATEWAY_PID=$!
  for _ in $(seq 60); do
    curl -s -o /dev/null --cacert "$TLS/gateway.pem" https://localhost:8392/ && return 0
    sleep 1
  done
  echo "the gateway didn't start" >&2; exit 1
}

run() { # arm stamp
  local arm="$1" S="$2" start end rc
  local name="${arm//[:=, ]/_}"
  local out="$HERE/$name-$S.out"
  start=$(perl -MTime::HiRes=time -e 'printf "%.1f", time')
  case "$arm" in
    py-direct)       python3 "$HERE/native-script.py" JogoShugh heart-github-test bug "$S" ;;
    py-via)          SSL_CERT_FILE="$TLS/gateway.pem" GITHUB_API=https://localhost:8392 \
                       python3 "$HERE/native-script-via-gateway.py" JogoShugh heart-github-test bug "$S" ;;
    careful-direct)  python3 "$HERE/native-script-careful.py" JogoShugh heart-github-test bug "$S" ;;
    careful-via)     SSL_CERT_FILE="$TLS/gateway.pem" GITHUB_API=https://localhost:8392 \
                       python3 "$HERE/native-script-careful.py" JogoShugh heart-github-test bug "$S" ;;
    heart:*)         (cd "$ROOT/client/scripts" && . ./.env && \
                       JAVA_OPTS="-Djavax.net.ssl.trustStore=$TLS/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
                       artie run "$HERE/heart-script.kts" owner=JogoShugh stamp="$S" gateway=https://localhost:8392/ \
                       --rung L2 --guard off-the-rails --validators none --pins "$HERE/heart-script.pins.yaml" \
                       --trace "$HERE/$name-$S.yaml") ;;
  esac >"$out" 2>&1
  rc=$?
  end=$(perl -MTime::HiRes=time -e 'printf "%.1f", time')
  local check
  sleep 10  # GitHub's lists catch up with its writes a few seconds late
  check=$(python3 "$HERE/../checker.py" check JogoShugh heart-github-test "$S" 2>/dev/null | python3 -c "import json,sys; r=json.load(sys.stdin); print(\"%d/%d\" % (r[\"held\"], r[\"of\"]))" 2>/dev/null || echo "no check")
  printf '%s\t%s\t%s\t%.1f\t%s\n' "$name" "$S" "$rc" "$(echo "$end - $start" | bc)" "$check" | tee -a "$HERE/ladder.tsv"
}

for round in $(seq "$ROUNDS"); do
  for arm in "$@"; do
    case "$arm" in
      heart:every) start_gateway "" ;;
      heart:*) start_gateway "${arm#heart:}" ;;
      *-via) start_gateway "" ;;
    esac
    run "$arm" "$(date +%m%d-%H%M%S)"
    sleep 2
  done
done
