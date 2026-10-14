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
# A service whose .env and section both exist is left as it is. Run it again
# with --renew to make new tokens for all three.
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
  local name=$1 dir=$2 port=$3
  local env_file=$root/$dir/.env origin=http://localhost:$port
  if [ "$renew" != "--renew" ] && [ -f "$env_file" ] && grep -qxF "[$origin]" "$file"; then
    echo "$name: credentials already made ($dir/.env, [$origin] in $file)"
    return
  fi
  local token sha
  token=$(openssl rand -hex 32)
  sha=$(printf %s "$token" | shasum -a 256 | cut -d' ' -f1)
  printf '# Made by scripts/dev-credentials.sh; not committed.\nHEART_AUTH_BEARER_TOKENS=%s\n' "$sha" > "$env_file"
  # The token goes through stdin, never on a command line; the program goes in -c.
  printf %s "$token" | python3 -I -c "$replace_section" "$file" "$origin"
  echo "$name: made a Bearer token for $origin (its SHA-256 in $dir/.env, the token in $file)"
}

# Replaces (or adds) the origin's section in the credentials file with the token read from stdin.
replace_section='
import sys
path, origin = sys.argv[1], sys.argv[2]
token = sys.stdin.read()
if not token:
    sys.exit("no token on stdin")
kept, skipping = [], False
for line in open(path).read().splitlines():
    stripped = line.strip()
    if stripped.startswith("[") and stripped.endswith("]"):
        skipping = stripped == f"[{origin}]"
    if not skipping:
        kept.append(line)
while kept and not kept[-1].strip():
    kept.pop()
kept += ["", f"[{origin}]", f"bearer = {token}"]
open(path, "w").write("\n".join(kept).lstrip("\n") + "\n")
'

make garden server-spring 8080
make basketball domains/basketball 8181
make tickets domains/tickets 8282
