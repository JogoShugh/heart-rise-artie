#!/bin/sh
# Shared by the three ways' scripts: where things are, the release's parties started fresh, GitLab reseeded, each
# variant's smoke inputs, and the check of what a release left behind, read from the systems themselves.
# Sourced, not run.
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
release="$root/exhibits/merge-watch-release"
H=http://localhost
. "$release/jenkins/jenkins.env.example"
. "$release/gitlab/gitlab.env"
export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_HOOK_SECRET RELEASE_HOST=localhost
export RELEASE_GITHUB_CONNECTOR=github-scm RELEASE_REPOSITORY="$release/work/library-app"
export RELEASE_SCRIPT="${RELEASE_SCRIPT:-$release/release.heart.yaml}"
mac_java=$HOME/.sdkman/candidates/java/25.3.4+1.r25-graal
[ -n "${JAVA_HOME:-}" ] || [ ! -d "$mac_java" ] || export JAVA_HOME="$mac_java"
parties_bin="$root/client/artie-release/build/install/artie-release/bin/artie-release"
artie_bin="$root/client/artie-cli/build/install/artie/bin/artie"

# The library app's repository as first made, and GitLab's acme/web and its five merge requests as first seeded.
fresh() {
  sh "$release/make-app-repo.sh" > /dev/null
  sh "$release/gitlab/seed.sh" 2>&1 | grep -v '^remote:' > /dev/null
}

# The release's parties, started fresh (the watcher, Jenkins' wrapper, Dev, the desk and its replies); LOG gets theirs.
parties_up() { # LOG
  "$parties_bin" > "$1" 2>&1 &
  parties=$!
  until curl -sf -o /dev/null -H 'Authorization: Bearer desk-token' "$H:8160/"; do sleep 1; done
}
parties_down() {
  [ -z "${parties:-}" ] || { kill "$parties"; wait "$parties" 2> /dev/null || true; parties=""; }
}

# Each variant's smoke inputs, as variants.sh gives them: book, author, problem, comment.
inputs_of() { # PULL
  case $1 in
    501) echo "book=Charlotte's Web|author=E. B. White|problem=damaged|comment=Spine cracked." ;;
    502) echo "book=The Hobbit|author=J. R. R. Tolkien|problem=wrong-book|comment=This is the sequel." ;;
    503) echo "book=Matilda|author=Roald Dahl|problem=lost|comment=Not on the shelf at home." ;;
    504) echo "book=Charlotte's Web|author=E. B. White|problem=missing-pages|comment=Pages 40 to 44." ;;
    505) echo "book=The Hobbit|author=J. R. R. Tolkien|problem=damaged|comment=Water damage on the cover." ;;
  esac
}

# What the release should have done: 504's smoke test fails, so it is not promoted; every other one is.
expected_of() { # PULL
  [ "$1" = 504 ] && echo "not promoted" || echo "promoted"
}

gitlab_api() { curl -sf -H "PRIVATE-TOKEN: $GITLAB_TOKEN" "$H:8929/api/v4/projects/acme%2Fweb/$1"; }

dev_now() { curl -sf -H 'Authorization: Bearer env-token' -H 'Accept: application/json' "$H:8190/environments/1"; }

# Keeps Dev's environment 1, as Dev serves it, in FILE every second while Dev is up: for runs that stop the parties
# themselves when they end (artie-release capture), so the check still reads what Dev last said.
dev_watch() { # FILE
  ( while :; do now=$(dev_now 2> /dev/null) && printf '%s\n' "$now" > "$1.next" && mv "$1.next" "$1"; sleep 1; done ) &
  dev_watcher=$!
}
dev_unwatch() { [ -z "${dev_watcher:-}" ] || { kill "$dev_watcher" 2> /dev/null || true; dev_watcher=""; }; }

# What the release left behind, read from GitLab and Dev, never from the client: one line of JSON. With DEV_FILE,
# Dev's last state as dev_watch kept it.
check() { # PULL
  mr=$(gitlab_api "merge_requests/$1")
  notes=$(gitlab_api "merge_requests/$1/notes?sort=asc&per_page=100")
  if [ -n "${DEV_FILE:-}" ]; then dev=$(cat "$DEV_FILE" 2> /dev/null || echo '{}'); else dev=$(dev_now || echo '{}'); fi
  node -e '
    const [pull, expected, mr, notes, dev] = process.argv.slice(1);
    const m = JSON.parse(mr), n = JSON.parse(notes), d = JSON.parse(dev);
    // What the goal promotes: the merge commit of the merge request, which holds the squashed change.
    const sha = m.merge_commit_sha || m.squash_commit_sha;
    const said = n.filter(x => !x.system).map(x => x.body);
    const promoted = d.promotedAt != null && d.commit === sha;
    const saidWhy = said.some(b => /Not promoted to Dev/.test(b));
    const got = promoted ? "promoted" : (saidWhy ? "not promoted" : "neither");
    const right = m.state === "merged" && m.squash === true && got === expected && !(promoted && saidWhy);
    console.log(JSON.stringify({ pull: +pull, expected, got, right, merged: m.state === "merged",
      squashed: m.squash === true, devCommit: d.commit, mergedCommit: sha, comments: said }));
  ' "$1" "$(expected_of "$1")" "$mr" "$notes" "$dev"
}
