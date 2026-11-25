# Skill vs ARTIE: the full release, three ways

One real job: release a merge request to Dev once it can be merged and its build passes its smoke test
(`exhibits/merge-watch-release/release.goal.md`). One release does all of this:

1. Opens the merge request on GitLab.
2. Asks the watcher to say when it can be merged.
3. Squash-merges it.
4. Has Jenkins build the merged commit and deploy it.
5. Runs the smoke test on that deployment: five scenarios a library member would try.
6. Then either promotes the commit to Dev, or says on the merge request why it didn't.

The systems are real GitLab and real Jenkins, both in Docker, plus the watcher and Dev. All of them run on this
machine, so the only thing that costs money is the model.

Merge requests 501 to 505 are the five variants. 504's smoke test fails, so it must not be promoted.

## The three ways

| Way | What runs | Model |
|---|---|---|
| `walk.sh` | ARTIE walks the goal cold, with Claude finding the way; the first walk is kept as a script | once per walk |
| `kept.sh` | the release desk's ARTIE replays that kept script | none |
| `skill.sh` | Claude Code with the artie-by-hand skill and curl does the release itself, every time | every step, every time |

Each release starts from GitLab reseeded and the parties fresh. Every way uses the same model, claude-sonnet-5-5.

**Waiting in the skill way.** The skill can't be told when something happens, since no host keeps replies for
it. So it asks with no reply address and reads the watch or the build again until it's done. Its Bash allows only
`curl`, so the model decides every one of those reads itself: no loop can do the waiting for it, and each read costs
a model turn.

## What is measured

- **Right or wrong,** read from GitLab and Dev, never from the client (`check` in `lib.sh`):
  - the merge request is merged by squashing;
  - Dev runs the merged commit, for every variant but 504;
  - 504 is not promoted, and its merge request says why.
- **Cost:** dollars, tokens and model turns, plus requests sent and seconds per release.

## Run it

```sh
sh exhibits/merge-watch-release/jenkins/run.sh
sh exhibits/merge-watch-release/gitlab/run.sh
sh exhibits/merge-watch-release/gitlab/gateway.sh &   # GitLab through the HEART gateway, on 8094
sh exhibits/skill-vs-artie/walk.sh 5
sh exhibits/skill-vs-artie/kept.sh 5
sh exhibits/skill-vs-artie/skill.sh 5
```

Each script appends one line per release to `runs/WAY.jsonl`.
