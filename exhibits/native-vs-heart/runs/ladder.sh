#!/usr/bin/env bash
# The care ladder (spec/ideas/off-the-rails.md): the triage task by each arm, over https, ROUNDS times, interleaved.
# Python arms: plain and careful, direct to GitHub and through the gateway passed through. HEART arms: the
# heart-script off the rails with blind writes, the gateway restarted for each with GATEWAY_CARE as the arm says.
# Usage: ladder.sh ROUNDS ARM... ; arms: py-direct py-via careful-direct careful-via heart:<care> careful-heart
# careful-heart: ARTIE at its most careful (--guard strict --validators last), the gateway taking every kind of
# care and refusing any act without a validator.
# artie-bare: heart-script.bare.kts, which artie generate --bare made from a careful-heart run: its GitHub calls,
# sent straight to GitHub by ARTIE, with no gateway and every guardrail off (spec/ideas/bare-runs.md).
# artie-goal: ARTIE careful (--guard lenient --validators last) running heart-script-goal.kts, which acts only on
# what the resource still offers; the gateway as for careful-heart.
# MEDDLE=M1,M2,... runs meddler.py beside each run, a teammate moving the world under it, and judges the run by
# what it kept of the teammate's moves (checker.py respect); a line per run goes to meddle.tsv instead.
# EXCHANGES=1 keeps each ARTIE step's exchanges, with their traceparent, in its trace (artie run --exchanges).
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

start_gateway() { # care [blind writes: allowed, or empty for none]
  stop_gateway
  (cd "$ROOT/heart/heart-gateway" && JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.12-graal" \
    GATEWAY_TLS_KEYSTORE="$TLS/gateway.p12" GATEWAY_TLS_PASSWORD="$(cat "$TLS/password")" \
    HEART_ACTS_WITHOUT_VALIDATOR="${2-allowed}" GATEWAY_CARE="$1" \
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
  local meddler=""
  if [ -n "${MEDDLE-}" ]; then
    python3 "$HERE/../meddler.py" JogoShugh heart-github-test "$S" "$MEDDLE" "$HERE/meddle-$name-$S.json" \
      >"$HERE/meddle-$name-$S.out" 2>&1 &
    meddler=$!
    sleep 3  # the meddler finds the newest number first
  fi
  start=$(perl -MTime::HiRes=time -e 'printf "%.1f", time')
  case "$arm" in
    py-direct)       python3 "$HERE/native-script.py" JogoShugh heart-github-test bug "$S" ;;
    py-via)          SSL_CERT_FILE="$TLS/gateway.pem" GITHUB_API=https://localhost:8392 \
                       python3 "$HERE/native-script-via-gateway.py" JogoShugh heart-github-test bug "$S" ;;
    careful-direct)  python3 "$HERE/native-script-careful.py" JogoShugh heart-github-test bug "$S" ;;
    careful-via)     SSL_CERT_FILE="$TLS/gateway.pem" GITHUB_API=https://localhost:8392 \
                       python3 "$HERE/native-script-careful.py" JogoShugh heart-github-test bug "$S" ;;
    artie-goal)      (cd "$ROOT/client/scripts" && . ./.env && \
                       JAVA_OPTS="-Djavax.net.ssl.trustStore=$TLS/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
                       artie run "$HERE/heart-script-goal.kts" owner=JogoShugh stamp="$S" gateway=https://localhost:8392/ \
                       --rung L2 --guard lenient --validators last --pins "$HERE/heart-script.pins.yaml" \
                       --trace "$HERE/$name-$S.yaml" ${EXCHANGES:+--exchanges}) ;;
    artie-bare)      (cd "$ROOT/client/scripts" && . ./.env && \
                       artie run "$HERE/heart-script.bare.kts" owner=JogoShugh stamp="$S" \
                       --trace "$HERE/$name-$S.yaml" ${EXCHANGES:+--exchanges}) ;;
    careful-heart)   (cd "$ROOT/client/scripts" && . ./.env && \
                       JAVA_OPTS="-Djavax.net.ssl.trustStore=$TLS/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
                       artie run "$HERE/heart-script.kts" owner=JogoShugh stamp="$S" gateway=https://localhost:8392/ \
                       --rung L2 --guard strict --validators last --pins "$HERE/heart-script.pins.yaml" \
                       --trace "$HERE/$name-$S.yaml" ${EXCHANGES:+--exchanges}) ;;
    heart:*)         (cd "$ROOT/client/scripts" && . ./.env && \
                       JAVA_OPTS="-Djavax.net.ssl.trustStore=$TLS/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
                       artie run "$HERE/heart-script.kts" owner=JogoShugh stamp="$S" gateway=https://localhost:8392/ \
                       --rung L2 --guard off-the-rails --validators none --pins "$HERE/heart-script.pins.yaml" \
                       --trace "$HERE/$name-$S.yaml" ${EXCHANGES:+--exchanges}) ;;
  esac >"$out" 2>&1
  rc=$?
  end=$(perl -MTime::HiRes=time -e 'printf "%.1f", time')
  if [ -n "$meddler" ]; then
    for _ in $(seq 30); do kill -0 "$meddler" 2>/dev/null || break; sleep 1; done
    kill "$meddler" 2>/dev/null; wait "$meddler" 2>/dev/null
    sleep 10
    local verdict
    verdict=$(python3 "$HERE/../checker.py" respect JogoShugh heart-github-test "$S" "$HERE/meddle-$name-$S.json" "$rc" \
      2>/dev/null | python3 -c 'import json,sys; r=json.load(sys.stdin); print("%s\t%s\t%s\t%s" % (r["verdict"],
      ",".join(r["landed"]) or "-", ",".join(r["late"]) or "-", "; ".join(r["damage"] + ["undone: " + u for u in r["left_undone"]]) or "-"))' \
      2>/dev/null || echo "no verdict")
    printf '%s\t%s\t%s\t%s\t%.1f\t%s\n' "$name" "$S" "$MEDDLE" "$rc" "$(echo "$end - $start" | bc)" "$verdict" \
      | tee -a "$HERE/meddle.tsv"
    return
  fi
  local check
  sleep 10  # GitHub's lists catch up with its writes a few seconds late
  check=$(python3 "$HERE/../checker.py" check JogoShugh heart-github-test "$S" 2>/dev/null | python3 -c "import json,sys; r=json.load(sys.stdin); print(\"%d/%d\" % (r[\"held\"], r[\"of\"]))" 2>/dev/null || echo "no check")
  printf '%s\t%s\t%s\t%.1f\t%s\n' "$name" "$S" "$rc" "$(echo "$end - $start" | bc)" "$check" | tee -a "$HERE/ladder.tsv"
}

for round in $(seq "$ROUNDS"); do
  for arm in "$@"; do
    case "$arm" in
      careful-heart|artie-goal) start_gateway "" "" ;;
      heart:every) start_gateway "" ;;
      heart:*) start_gateway "${arm#heart:}" ;;
      *-via) start_gateway "" ;;
    esac
    run "$arm" "$(date +%m%d-%H%M%S)"
    sleep 2
  done
done
