#!/bin/sh
# Runs inside the release's container (kit/release/compose.yaml). kit/release.sh calls it.
#   in-release.sh serve          the container's own command: shares the simulator with Jenkins, then waits
#   in-release.sh variants SCM   releases the five variants at SCM: github (the small GitHub), gitlab, or
#                                github-live (OWNER/REPO on real GitHub, with GITHUB_TOKEN); runs go to /work/runs/SCM
#   in-release.sh reset [SCM]    puts github, gitlab or both (the default) back as first seeded, and clears their runs
# No token is printed.
set -eu
here=/artie/exhibits/merge-watch-release
logs=/work/logs
mkdir -p "$logs"

answers() { curl -s -o /dev/null "$1"; }
wait_for() { # url what log
  tries=0
  until answers "$1"; do
    tries=$((tries + 1))
    [ "$tries" -le "${4:-120}" ] || { echo "$2 didn't answer at $1; its log is $3" >&2; exit 1; }
    sleep 2
  done
}

case ${1:-} in
  serve)
    # Jenkins runs the library app's preview with the simulator, from its own read-only copy of this one.
    rm -rf /shared/sims/* && cp -a /artie/heart/heart-sims/build/sims/. /shared/sims/
    exec tail -f /dev/null ;;
  variants)
    scm=${2:-github}
    out=/work/runs/$scm
    cd "$here"
    case $scm in
      github) ;;
      github-live)
        [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${OWNER:-}" ] && [ -n "${REPO:-}" ] \
          || { echo "github-live needs OWNER, REPO and GITHUB_TOKEN (kit/.env)" >&2; exit 2; }
        # The release's scripts read GitHub's token as ARTIE keeps credentials.
        mkdir -p "$HOME/.artie"
        (umask 077; printf '[https://api.github.com]\nbearer = %s\n' "$GITHUB_TOKEN" > "$HOME/.artie/credentials")
        export RELEASE_GITHUB_REPO="$OWNER/$REPO"
        if ! answers http://localhost:8093/; then
          nohup sh github/gateway.sh > "$logs/github-gateway.log" 2>&1 &
          wait_for http://localhost:8093/ "The GitHub gateway" "work/logs/github-gateway.log"
        fi ;;
      gitlab)
        wait_for http://localhost:8929/users/sign_in "GitLab (kit/release.sh up gitlab)" "docker logs merge-release-gitlab" 300
        [ -f gitlab/gitlab.env ] || (umask 077; : > gitlab/gitlab.env)
        sh make-app-repo.sh > /dev/null
        echo "Seeding GitLab with the library app and five merge requests…"
        sh gitlab/seed.sh 2>&1 | grep -v '^remote:'
        if ! answers http://localhost:8094/; then
          nohup sh gitlab/gateway.sh > "$logs/gitlab-gateway.log" 2>&1 &
          wait_for http://localhost:8094/ "The GitLab gateway" "work/logs/gitlab-gateway.log"
        fi ;;
      *) echo "SCM is github, gitlab or github-live" >&2; exit 2 ;;
    esac
    wait_for http://localhost:8171/login "Jenkins" "docker logs heart-release-jenkins"
    RELEASE_SCM=$scm sh variants.sh "$out" 2>&1 | grep -v '^remote:' | sed -E 's/^(pr-[0-9]+): .*"state":"([a-z]+)".*/\1: \2/'
    echo
    for n in 501 502 503 504 505; do
      promoted=$(sed -n 's/.*"promotedAt":\("[^"]*"\|null\).*/\1/p' "$out/pr-$n.dev.json")
      case $promoted in null | '') echo "pr-$n: not promoted to Dev" ;; *) echo "pr-$n: promoted to Dev at $promoted" ;; esac
    done
    echo "Each release's trace: work/runs/$scm/pr-N.yaml" ;;
  reset)
    # The small GitHub lives in the release's parties, which every release starts afresh; the app repository and
    # GitLab's project are what a release leaves changed.
    scm=${2:-all}
    case $scm in github | gitlab | all) ;; *) echo "reset takes github, gitlab or all" >&2; exit 2 ;; esac
    cd "$here"
    sh make-app-repo.sh > /dev/null
    echo "the library app's repository: as first made"
    if [ "$scm" != gitlab ]; then
      rm -rf /work/runs/github
      echo "the small GitHub: fresh at the next release; its runs cleared"
    fi
    if [ "$scm" != github ]; then
      if answers http://localhost:8929/users/sign_in; then
        [ -f gitlab/gitlab.env ] || (umask 077; : > gitlab/gitlab.env)
        sh gitlab/seed.sh 2>&1 | grep -v '^remote:' > /dev/null
        echo "GitLab: acme/web and its five merge requests, as first seeded"
      else
        echo "GitLab isn't up, so there is nothing of it to reset (kit/release.sh up gitlab starts it)"
      fi
      rm -rf /work/runs/gitlab
      echo "GitLab's runs cleared"
    fi ;;
  *)
    sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
