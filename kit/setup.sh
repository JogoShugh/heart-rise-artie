#!/bin/sh
# Sets up the kit on this machine, step by step, saying what it does as it goes. By default it costs electricity only:
# everything runs in containers here, a small GitHub, GitLab and Jenkins among them, with no account and no key.
# Two things are opt in, each asked about once, or settled ahead in kit/.env (kit/env.example):
#   USE_GITHUB=ask|yes|no      your GitHub account: kept scripts replayed on a repository of yours, on real GitHub
#   USE_ANTHROPIC=ask|yes|no   an Anthropic key: Claude walks goals written in words (billed to your key)
# With ask (the default), setup asks, and offers a key it finds in kit/.env; with yes or no it only says which.
# Run it again any time; it skips what is done already, and keeps what kit/.env holds unless you type something new.
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
ask_no() { # question; no unless answered y, and no when there is no terminal to ask in
  interactive || return 1
  printf '   %s [y/N] ' "$1"; read -r answer
  case $answer in y* | Y*) return 0 ;; *) return 1 ;; esac
}
ending() { printf '%s' "$1" | tail -c 4; }

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
$docker_cmd compose version > /dev/null 2>&1 \
  || stop "Docker has no compose; the release kit needs it. On Ubuntu: sudo apt-get install docker-compose-v2"
ok "compose is there"
memory=$($docker_cmd info --format '{{.MemTotal}}' 2> /dev/null || echo 0)
gigabytes=$((memory / 1000000000))
ok "Docker has $gigabytes GB of memory"
more_memory() { # GB wanted
  [ "$memory" -lt "$1"000000000 ] 2> /dev/null || return 0
  note "Docker has $gigabytes GB of memory; this wants about $1."
  if [ -n "$wsl" ]; then note "Give WSL more in %UserProfile%\\.wslconfig ([wsl2], memory=12GB), then: wsl --shutdown"; fi
  if [ "$os" = Darwin ]; then note "Give Docker Desktop more in its Settings, Resources."; fi
}

# A key, pasted or typed: one * per character as it comes, Backspace takes one back, Enter ends. Enter alone keeps
# what kit/.env holds.
nl=$(printf '\nx'); nl=${nl%x}
cr=$(printf '\r'); bs=$(printf '\b'); del=$(printf '\177'); esc=$(printf '\033')
read_secret() { # NAME "what to paste"
  eval "current=\${$1:-}"
  shown=""; [ -z "$current" ] || shown=" [Enter keeps the one ending …$(ending "$current")]"
  printf '   %s%s: ' "$2" "$shown"
  saved=$(stty -g)
  trap 'stty "$saved"; echo; exit 130' INT TERM
  stty -echo -icanon min 1 time 0
  value=""
  while :; do
    c=$(dd bs=1 count=1 2> /dev/null; echo .)
    c=${c%.}
    case $c in
      "$nl" | "$cr" | '') break ;;
      "$bs" | "$del") if [ -n "$value" ]; then value=${value%?}; printf '\b \b'; fi ;;
      "$esc") # an escape sequence, such as an arrow key or a terminal's paste markers: read to its end, and drop it
        while :; do
          c=$(dd bs=1 count=1 2> /dev/null; echo .); c=${c%.}
          case $c in [A-Za-z~] | '') break ;; esac
        done ;;
      *[![:print:]]*) ;; # other control keys are no part of a key
      *) value=$value$c; printf '*' ;;
    esac
  done
  stty "$saved"; trap - INT TERM
  echo
  value=$(printf '%s' "$value" | tr -d '[:space:]')
  if [ -n "$value" ]; then
    eval "$1=\$value"
    ok "got ${#value} characters, ending …$(ending "$value")"
  elif [ -n "$current" ]; then
    ok "kept the one in kit/.env, ending …$(ending "$current")"
  fi
}
prompt() { # NAME "question": asked only when kit/.env doesn't hold it
  eval "current=\${$1:-}"
  [ -z "$current" ] || return 0
  interactive || stop "$1 is not set, and there's no terminal to ask in. Set it in kit/.env."
  printf '   %s: ' "$2"; read -r value
  [ -z "$value" ] || eval "$1=\$value"
  eval "[ -n \"\${$1:-}\" ]" || stop "$1 is needed."
}
# Settles an opt in: its flag says yes or no, and setup says only that; or, with ask, the person is asked.
opt_in() { # FLAG "what" KEY "the question when kit/.env has no key"
  eval "flag=\${$1:-ask}"
  case $(printf '%s' "$flag" | tr 'A-Z' 'a-z') in
    yes | y | true | on | 1) ok "$2: on ($1=yes)"; return 0 ;;
    no | n | false | off | 0) ok "$2: off ($1=no)"; return 1 ;;
  esac
  if ! interactive; then note "$2: off (no terminal to ask in; $1=yes in kit/.env opts in)"; return 1; fi
  eval "key=\${$3:-}"
  if [ -n "$key" ]; then
    question="kit/.env holds $3, ending …$(ending "$key"). Use it?"
  else
    question=$4
  fi
  if ask_no "$question"; then ok "$2: on"; return 0; fi
  ok "$2: off"; return 1
}

# Settles an opt out: on unless its flag says no, or the person says no.
opt_out() { # FLAG "what" "the question"
  eval "flag=\${$1:-ask}"
  case $(printf '%s' "$flag" | tr 'A-Z' 'a-z') in
    yes | y | true | on | 1) ok "$2: on ($1=yes)"; return 0 ;;
    no | n | false | off | 0) ok "$2: off ($1=no)"; return 1 ;;
  esac
  if ask_yes "$3"; then ok "$2: on"; return 0; fi
  ok "$2: off"; return 1
}
settled() { case $(printf '%s' "${1:-ask}" | tr 'A-Z' 'a-z') in yes | y | true | on | 1 | no | n | false | off | 0) ;; *) return 1 ;; esac; }

say "What runs here, with no account and no key"
ok "the small GitHub, Jenkins, a build and a deploy service, and the release's parties: containers on this machine"
ok "every script they run is kept, and replays with no model"
settled "${USE_GITLAB:-}" \
  || note "GitLab runs too, unless you opt out: it wants about 4 GB more of Docker's memory (about 10 GB in all)."
gitlab=""
if opt_out USE_GITLAB "GitLab" "Run GitLab too?"; then gitlab=yes; more_memory 10; else more_memory 6; fi

say "What's yours to opt in to"
if ! settled "${USE_GITHUB:-}" || ! settled "${USE_ANTHROPIC:-}"; then
  note "kit/.env can settle each ahead: USE_GITHUB, USE_ANTHROPIC = yes, no or ask."
  note "  - your GitHub account: the same kept scripts replayed on a repository of yours, on real GitHub;"
  note "  - an Anthropic key: Claude walks goals written in words, billed to your key."
fi
github=""
if opt_in USE_GITHUB "your GitHub account" GITHUB_TOKEN "Use your GitHub account too?"; then github=yes; fi
anthropic=""
if opt_in USE_ANTHROPIC "an Anthropic key" ANTHROPIC_API_KEY "Use an Anthropic key too?"; then anthropic=yes; fi

if [ -n "$github" ]; then
  say "Your GitHub repository and token"
  [ "${OWNER:-}" = your-github-user ] && OWNER=""
  if [ -z "${OWNER:-}" ] || [ -z "${GITHUB_TOKEN:-}" ]; then
    note "The kit works on a repository of yours: an empty one is best, named kit-trial say."
    note "Create it at https://github.com/new (no README, no template), if you haven't."
  fi
  prompt OWNER "Your GitHub user name"
  REPO=${REPO:-kit-trial}
  if [ -z "${GITHUB_TOKEN:-}" ]; then
    interactive || stop "USE_GITHUB is yes, but kit/.env has no GITHUB_TOKEN, and there's no terminal to ask in."
    note "A token for $OWNER/$REPO alone: https://github.com/settings/personal-access-tokens/new"
    note "  Repository access: Only select repositories, $REPO. Permissions: Issues, Pull requests, Contents: Read and write."
    read_secret GITHUB_TOKEN "Paste the token"
    [ -n "${GITHUB_TOKEN:-}" ] || stop "GITHUB_TOKEN is needed for your GitHub account; or run again and say no to it."
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
  USE_GITHUB=yes
fi

if [ -n "$anthropic" ]; then
  say "Your Anthropic key"
  if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    interactive || stop "USE_ANTHROPIC is yes, but kit/.env has no ANTHROPIC_API_KEY, and there's no terminal to ask in."
    note "A key from console.anthropic.com. Replays of kept scripts never use it, but triage's one reasoned step does."
    read_secret ANTHROPIC_API_KEY "Paste the key"
    [ -n "${ANTHROPIC_API_KEY:-}" ] || stop "ANTHROPIC_API_KEY is needed for Claude; or run again and say no to it."
  else
    ok "the key in kit/.env, ending …$(ending "$ANTHROPIC_API_KEY")"
  fi
  USE_ANTHROPIC=yes
fi

# What was opted in to is kept as yes, so kit/try.sh knows it, and the next setup only says so; the rest as it was.
kept_umask=$(umask)
umask 077
{
  echo "# Written by kit/setup.sh. kit/env.example says what each line is; git and Docker builds never see this file."
  echo "USE_GITLAB=${USE_GITLAB:-ask}"
  echo "USE_GITHUB=${USE_GITHUB:-ask}"
  echo "USE_ANTHROPIC=${USE_ANTHROPIC:-ask}"
  echo "OWNER=${OWNER:-}"
  echo "REPO=${REPO:-kit-trial}"
  echo "GITHUB_TOKEN=${GITHUB_TOKEN:-}"
  echo "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}"
} > "$env_file"
umask "$kept_umask"
ok "saved in kit/.env, readable by you alone"
export OWNER REPO GITHUB_TOKEN ANTHROPIC_API_KEY USE_GITHUB USE_ANTHROPIC

# The kit's own scripts, through sudo while this user isn't yet one of Docker's group.
kit_sh() {
  if [ "$docker_cmd" != docker ]; then
    sudo --preserve-env=OWNER,REPO,GITHUB_TOKEN,ANTHROPIC_API_KEY,USE_GITHUB,USE_ANTHROPIC sh "$here/$@"
  else
    sh "$here/$@"
  fi
}

if [ -n "$gitlab" ]; then
  say "Build and start the release kit: the small GitHub, GitLab and Jenkins"
  note "The first build takes several minutes, and GitLab several more to start; later ones are quick."
  kit_sh release.sh up gitlab < /dev/null
else
  say "Build and start the release kit: the small GitHub and Jenkins"
  note "The first build takes several minutes; later ones are quick."
  kit_sh release.sh up < /dev/null
fi

say "Release five pull requests at the small GitHub, by a kept script, with no model"
kit_sh release.sh run github < /dev/null

if [ -n "$gitlab" ]; then
  say "The same five as GitLab merge requests"
  kit_sh release.sh run gitlab < /dev/null
fi

say "Build the kit, and replay its kept scripts at the small GitHub, with no model"
note "The first build takes several minutes; later ones are quick."
(USE_GITHUB=no; export USE_GITHUB; kit_sh try.sh tour) < /dev/null

if [ -n "$github" ]; then
  if ask_no "Replay the same kept scripts at $OWNER/$REPO on real GitHub now? They open and close issues there."; then
    say "The kept scripts at $OWNER/$REPO, on real GitHub, with no model"
    kit_sh try.sh tour < /dev/null
  fi
  if ask_no "Release the five at $OWNER/$REPO on real GitHub now? It opens pull requests there, on library/* branches."; then
    kit_sh release.sh run github-live < /dev/null
  fi
fi

say "Done"
note "Next, in ${here%/kit}:"
note "  kit/try.sh tour               the kept scripts again, from a fresh small GitHub"
note "  kit/try.sh replay hello       one of them again (hello, closeout, release; stop closeout ends its watch)"
note "  kit/try.sh reset              the small GitHub and the services as first started; down stops them"
note "  kit/release.sh run github     the five releases again, at the small GitHub"
if [ -n "$gitlab" ]; then
  note "  kit/release.sh run gitlab     the same five at GitLab"
fi
note "  kit/release.sh reset          the release's GitHub and GitLab back as first seeded, runs cleared"
note "  kit/release.sh down           stops the release kit; GitLab's data is kept"
if [ -n "$github" ]; then
  note "  kit/release.sh run github-live   the five at $OWNER/$REPO on real GitHub"
fi
if [ -n "$anthropic" ]; then
  note "  kit/try.sh walk hello         Claude walks a goal in words, and kit/try.sh keep hello keeps it"
fi
note "  kit/README.md                 the rest"
if [ -n "$anthropic" ] && ask_no "Walk hello now? Claude uses your key."; then
  kit_sh try.sh walk hello
fi
