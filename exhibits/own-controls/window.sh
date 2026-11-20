#!/usr/bin/env bash
# The window (spec/ideas/gateway-pass-through.md, step 5): GitHub has no conditional write, so the conditional rung
# reads the issue again, then writes. Each write says how long each took (Server-Timing); a teammate on GitHub
# itself could land only in between. N writes, no teammate; one JSON line each.
set -euo pipefail
cd "$(dirname "$0")"
SIM=${SIM:-http://localhost:8098}
N=${1:-30}
OUT=runs/window-$(date +%Y%m%d-%H%M%S).jsonl
AUTH="Authorization: Bearer github-token"   # the small GitHub's test token for ada; not a secret
curl -sf -X POST "$SIM/_sim/reset" >/dev/null
curl -sf -X PUT "$SIM/_sim/github/dam" --data conditional >/dev/null
for i in $(seq 1 "$N"); do
  etag=$(curl -sf -D - -o /dev/null -H "$AUTH" -H "X-Gateway-Mode: pass-through" "$SIM/repos/acme/web/issues/7" \
    | tr -d '\r' | awk -F': ' 'tolower($1)=="etag"{print $2}')
  timing=$(curl -sf -D - -o /dev/null -X PATCH -H "$AUTH" -H "X-Gateway-Mode: pass-through" -H "If-Match: $etag" \
    -H "Content-Type: application/json" --data "{\"title\":\"The nightly build is slow ($i)\"}" \
    "$SIM/repos/acme/web/issues/7" | tr -d '\r' | awk -F': ' 'tolower($1)=="server-timing"{print $2}')
  recheck=$(echo "$timing" | sed -E 's/.*recheck;dur=([0-9.]+).*/\1/')
  write=$(echo "$timing" | sed -E 's/.*write;dur=([0-9.]+).*/\1/')
  jq -nc --argjson i "$i" --argjson recheck "$recheck" --argjson write "$write" '{write_no: $i, recheck_ms: $recheck, write_ms: $write}' | tee -a "$OUT"
done
echo "written: $OUT"
