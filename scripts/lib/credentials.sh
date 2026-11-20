# Sourced by the scripts that start or call a HEART service.
#
#   service_env DIR          exports the service's .env (made by dev-credentials.sh),
#                            so bootRun starts with the credentials it admits.
#   auth_header ORIGIN       prints "Authorization: Bearer ..." for ORIGIN from ARTIE's
#                            credentials file. Pass it to curl as a file, never as an
#                            argument:  curl -H @<(auth_header http://localhost:8080) ...
#   answering URL            true once anything answers at URL, a 401 included.

service_env() {
  set -a
  # shellcheck disable=SC1091
  . "$1/.env"
  set +a
}

auth_header() {
  python3 -I - "${ARTIE_HOME:-$HOME/.artie}/credentials" "$1" <<'EOF'
import sys
path, origin = sys.argv[1], sys.argv[2]
section = None
for line in open(path):
    line = line.strip()
    if line.startswith("[") and line.endswith("]"):
        section = line[1:-1]
    elif section == origin and line.startswith("bearer") and "=" in line:
        print("Authorization: Bearer " + line.split("=", 1)[1].strip())
        sys.exit(0)
sys.exit(f"no bearer for {origin} in {path}; run scripts/dev-credentials.sh")
EOF
}

answering() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$1")" != "000" ]
}
