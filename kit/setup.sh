#!/bin/sh
# Sets up the kit on this machine, step by step, saying what it does as it goes, and asking for what only you know:
# your GitHub user, the repository the kit works on, your GitHub token and your Anthropic key. Run it again any time;
# it skips what is done already, and keeps what kit/.env holds unless you type something new.
#   kit/setup.sh
# Ubuntu (WSL 2 on Windows, or Linux) and macOS. On Windows, kit/windows/setup.ps1 gets you here.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
env_file="$here/.env"

step=0
say() { step=$((step + 1)); printf '\n\033[1m%s. %s\033[0m\n' "$step" "$1"; }
ok() { printf '   \033[32mok\033[0m  %s\n' "$1"; }
note() { printf '   %s\n' "$1"; }
stop() { printf '\n\033[31mStopped:\033[0m %s\n' "$1" >&2; exit 1; }
interactive() { [ -t 0 ]; }
ask_yes() { # question; yes unless answered n
  interactive || return 0
  printf '   %s [Y/n] ' "$1"; read -r answer
  case $answer in n* | N*) return 1 ;; *) return 0 ;; esac
}

# What kit/.env holds already, so Enter keeps it.
. "$here/env.sh"

say "Where this runs"
os=$(uname -s)
wsl=""
if [ "$os" = Linux ] && grep -qi microsoft /proc/version 2>/dev/null; then wsl=yes; fi
case $os in
  Darwin) ok "macOS" ;;
  Linux) if [ -n "$wsl" ]; then ok "Ubuntu in WSL"; else ok "Linux"; fi ;;
  *) stop "this is $os. On Windows, run kit/windows/setup.ps1 in PowerShell; it sets up Ubuntu and runs this there." ;;
esac
case $here in
  /mnt/*) stop "this copy is on the Windows drive ($here), where builds are slow and scripts may not run. Clone it in
Ubuntu's home instead: cd ~ && git clone --branch claude/auth-branch-4bnmhc https://github.com/JogoShugh/heart-rise-artie.git" ;;
esac
ok "the repository is at ${here%/kit}"

say "git and curl"
missing=""
for tool in git curl; do command -v "$tool" > /dev/null 2>&1 || missing="$missing $tool"; done
if [ -z "$missing" ]; then
  ok "both installed"
elif [ "$os" = Linux ] && command -v apt-get > /dev/null 2>&1; then
  note "installing$missing (sudo asks for your Ubuntu password)"
  sudo apt-get update -qq && sudo apt-get install -y -qq $missing > /dev/null
  ok "installed$missing"
else
  stop "install$missing, then run this again."
fi

say "Docker"
docker_cmd=docker
if docker info > /dev/null 2>&1; then
  ok "Docker answers"
elif [ "$os" = Darwin ]; then
  stop "Docker isn't running. Install Docker Desktop (docker.com/products/docker-desktop), start it, and run this again."
else
  if ! command -v docker > /dev/null 2>&1; then
    note "Docker isn't installed in this Ubuntu. Either:"
    note "  - Docker Desktop on Windows, with WSL integration on for Ubuntu (Settings, Resources, WSL integration);"
    note "    then close this terminal, open a new one, and run this again; or"
    note "  - Docker inside Ubuntu, installed now."
    ask_yes "Install Docker inside Ubuntu now?" || stop "turn on Docker Desktop's WSL integration, then run this again."
    note "installing docker.io, docker-buildx and docker-compose-v2 (sudo asks for your password)"
    sudo apt-get update -qq
    sudo apt-get install -y -qq docker.io docker-buildx docker-compose-v2 > /dev/null
    ok "installed"
  fi
  if ! sudo -n true 2> /dev/null && ! docker info > /dev/null 2>&1; then note "sudo asks for your password to start Docker"; fi
  if ! docker info > /dev/null 2>&1 && ! sudo docker info > /dev/null 2>&1; then
    note "starting Docker"
    sudo service docker start > /dev/null 2>&1 || sudo systemctl start docker > /dev/null 2>&1 || true
    sleep 3
  fi
  if docker info > /dev/null 2>&1; then
    ok "Docker answers"
  elif sudo docker info > /dev/null 2>&1; then
    # Docker runs, but this user isn't in its group yet: added now, used through sudo until the next login.
    id -nG | grep -qw docker || { sudo usermod -aG docker "$USER"; note "added $USER to the docker group (it counts from your next terminal)"; }
    docker_cmd="sudo docker"
    ok "Docker answers (through sudo, this time)"
  else
    stop "Docker doesn't answer. In WSL with Docker Desktop: is it running, with WSL integration on for Ubuntu?"
  fi
fi
$docker_cmd buildx version > /dev/null 2>&1 \
  || stop "Docker has no BuildKit (buildx); the kit's build needs it. On Ubuntu: sudo apt-get install docker-buildx"
ok "BuildKit is there"
memory=$($docker_cmd info --format '{{.MemTotal}}' 2> /dev/null || echo 0)
if [ "$memory" -lt 6000000000 ] 2> /dev/null; then
  note "Docker has $((memory / 1000000000)) GB of memory; the build wants about 6."
  if [ -n "$wsl" ]; then note "Give WSL more in %UserProfile%\\.wslconfig ([wsl2], memory=8GB), then: wsl --shutdown"; fi
fi

say "Your GitHub repository and keys"
note "The kit works on a repository of yours: an empty one is best, named kit-trial say."
note "Create it at https://github.com/new (no README, no template), if you haven't."
prompt() { # NAME "question" [secret]; Enter keeps what kit/.env holds
  eval "current=\${$1:-}"
  if ! interactive; then
    [ -n "$current" ] || stop "$1 is not set, and there's no terminal to ask in. Set it in kit/.env."
    return
  fi
  shown=$current
  if [ -n "${3:-}" ] && [ -n "$current" ]; then shown="kept, ends …$(printf '%s' "$current" | tail -c 4)"; fi
  printf '   %s%s: ' "$2" "${shown:+ [$shown]}"
  if [ -n "${3:-}" ]; then stty -echo; read -r value; stty echo; echo; else read -r value; fi
  [ -z "$value" ] || eval "$1=\$value"
  eval "[ -n \"\${$1:-}\" ]" || stop "$1 is needed."
}
[ "${OWNER:-}" = your-github-user ] && OWNER=""
prompt OWNER "Your GitHub user name"
REPO=${REPO:-kit-trial}
prompt REPO "The repository's name"
note "A token for that repository alone: https://github.com/settings/personal-access-tokens/new"
note "  Repository access: Only select repositories, $REPO. Permissions: Issues, Pull requests, Contents: Read and write."
prompt GITHUB_TOKEN "Paste the token (it isn't shown)" secret
note "An Anthropic key (console.anthropic.com) lets Claude walk goals; replays don't need it. Enter skips it."
if interactive; then
  shown=""; [ -n "${ANTHROPIC_API_KEY:-}" ] && shown=" [kept, ends …$(printf '%s' "$ANTHROPIC_API_KEY" | tail -c 4)]"
  printf '   Paste the Anthropic key (it isn'"'"'t shown)%s: ' "$shown"
  stty -echo; read -r value; stty echo; echo
  [ -z "$value" ] || ANTHROPIC_API_KEY=$value
fi

note "checking the token can reach $OWNER/$REPO"
answer=$(curl -s -w '\n%{http_code}' -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO")
code=$(printf '%s' "$answer" | tail -n 1)
case $code in
  200) ok "the token reaches $OWNER/$REPO" ;;
  401) stop "GitHub refused the token (401): expired, deleted, or pasted wrong. Make a new one and run this again." ;;
  404) stop "GitHub has no $OWNER/$REPO this token can see. Check the names, and that the token's access includes it." ;;
  *) stop "GitHub answered $code for $OWNER/$REPO." ;;
esac

umask 077
{
  echo "# Written by kit/setup.sh. kit/env.example says what each line is; git and Docker builds never see this file."
  echo "OWNER=$OWNER"
  echo "REPO=$REPO"
  echo "GITHUB_TOKEN=$GITHUB_TOKEN"
  echo "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}"
} > "$env_file"
ok "saved in kit/.env, readable by you alone"
export OWNER REPO GITHUB_TOKEN ANTHROPIC_API_KEY

say "Build the kit and seed $OWNER/$REPO"
note "The first build takes several minutes; later ones are quick."
if [ "$docker_cmd" != docker ]; then
  # Docker through sudo, until the next login makes this user one of its group.
  sudo --preserve-env=OWNER,REPO,GITHUB_TOKEN,ANTHROPIC_API_KEY sh "$here/bootstrap.sh" < /dev/null
else
  sh "$here/bootstrap.sh" < /dev/null
fi

say "Done"
note "Next, in ${here%/kit}:"
note "  kit/try.sh walk hello     Claude opens an issue, comments on it, and closes it"
note "  kit/README.md             the other goals, keeping a walk as a script, and the release kit"
if [ -n "${ANTHROPIC_API_KEY:-}" ] && ask_yes "Walk hello now?"; then
  if [ "$docker_cmd" != docker ]; then
    sudo --preserve-env=OWNER,REPO,GITHUB_TOKEN,ANTHROPIC_API_KEY sh "$here/try.sh" walk hello
  else
    sh "$here/try.sh" walk hello
  fi
fi
