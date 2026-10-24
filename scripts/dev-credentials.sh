#!/usr/bin/env bash
# Makes development credentials for the three HEART services, once each.
# Every service requires a credential and will not start without one.
#
# For each service, a random Bearer token:
#   - its SHA-256 goes in the service's .env (HEART_AUTH_BEARER_TOKENS), which
#     docker compose reads and the scripts export before bootRun;
#   - the token itself goes in ARTIE's credentials file ($ARTIE_HOME/credentials,
#     by default ~/.artie/credentials, readable by you only), in the section for
#     the service's origin, where ARTIE finds it.
# And for each service, a random pre-shared key for coaps (DTLS):
#   - the key goes in the service's .env (HEART_COAPS_PORT, HEART_COAPS_PSK), as
#     itself, since DTLS needs it;
#   - the same key goes in ARTIE's credentials file, in the coaps origin's section.
# A service whose .env and sections all exist is left as it is. Run it again
# with --renew to make new ones for all three.
# It never prints a token. Opens nothing on screen.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
folder=${ARTIE_HOME:-$HOME/.artie}
file=$folder/credentials
renew=${1:-}

umask 077
mkdir -p "$folder"
chmod 700 "$folder"
touch "$file"
chmod 600 "$file"

make() {
  local name=$1 dir=$2 port=$3 coaps_port=$4
  local env_file=$root/$dir/.env origin=http://localhost:$port secure=coaps://localhost:$coaps_port
  if [ "$renew" != "--renew" ] && [ -f "$env_file" ] && grep -qxF "[$origin]" "$file" \
     && grep -q '^HEART_COAPS_PSK=' "$env_file" && grep -qxF "[$secure]" "$file"; then
    echo "$name: credentials already made ($dir/.env, [$origin] and [$secure] in $file)"
    return
  fi
  local token sha key
  token=$(openssl rand -hex 32)
  sha=$(printf %s "$token" | shasum -a 256 | cut -d' ' -f1)
  key=$(openssl rand -base64 24)
  printf '# Made by scripts/dev-credentials.sh; not committed.\nHEART_AUTH_BEARER_TOKENS=%s\nHEART_COAPS_PORT=%s\nHEART_COAPS_PSK=artie:%s\n' \
    "$sha" "$coaps_port" "$key" > "$env_file"
  # Secrets go through stdin, never on a command line; the program goes in -c.
  printf 'bearer = %s' "$token" | python3 -I -c "$replace_section" "$file" "$origin"
  printf 'psk_identity = artie\npsk_key = %s' "$key" | python3 -I -c "$replace_section" "$file" "$secure"
  echo "$name: made a Bearer token for $origin and a pre-shared key for $secure ($dir/.env, $file)"
}

# Replaces (or adds) the origin's section in the credentials file with the lines read from stdin.
replace_section='
import sys
path, origin = sys.argv[1], sys.argv[2]
lines = sys.stdin.read()
if not lines:
    sys.exit("nothing on stdin")
kept, skipping = [], False
for line in open(path).read().splitlines():
    stripped = line.strip()
    if stripped.startswith("[") and stripped.endswith("]"):
        skipping = stripped == f"[{origin}]"
    if not skipping:
        kept.append(line)
while kept and not kept[-1].strip():
    kept.pop()
kept += ["", f"[{origin}]"] + lines.splitlines()
open(path, "w").write("\n".join(kept).lstrip("\n") + "\n")
'

make garden server-spring 8080 5784
make basketball domains/basketball 8181 5785
make tickets domains/tickets 8282 5786
