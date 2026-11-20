# Idea: a new system deployed during a run, followed by its links

**Status:** idea only. Raised by the author on 2026-10-27 as a closing demo for "Wayfinding to Rails". Nothing is
built. It builds on spec/ideas/explore-capture-across-hosts.md (section 5, "Across hosts"),
spec/ideas/jenkins-gateway.md, and spec/ideas/off-the-rails.md (the guard ladder and look-then-leap).

## The demo in one paragraph

The merge watch is running. Partway through, a brand new system is deployed: a build service that records whether
each pull request's build passed. Nobody touches ARTIE, the script or the goal. The GitHub gateway starts linking
each pull request to its build in the new system, on another host. At its next look, ARTIE sees the link, follows
it to a server it has never seen, reads the build, and holds back the bumps whose build failed, saying why, while
it still merges the rest. Everything it does on the new system is checked as on the old one: the representation's
profile, each form's integrity, each schema, each validator, and the server's own refusals.

That is Fielding's claim, shown live: a client coupled to media types, not to a service, follows a new service the
moment the old one links to it. An OpenAPI client, or the Python, needs new code and a redeploy.

## The pieces

1. **The new system: a small build service.** A HEART server of its own, on another port (say 8393), built with
   heart-core like the garden: a `build` resource per pull request head, with `state` (passed, failed, running), a
   `rerun` act offered only when failed, and reasons for absence. Seeded from a file, or flipped by hand during the
   demo. (Later, the Jenkins gateway could stand in for it.)
2. **The link between the systems.** The GitHub gateway's pull request gains a link, `ci:build`, to
   `https://localhost:8393/builds/{head sha}`: one line in the connector's `links`, an absolute template filled from
   the pull request's state. Its profile describes the relation, so a reasoner knows what it means.
3. **The deployment, mid-run.** Start the build service, then restart the gateway with the link in its config (or
   reload it, if the gateway learns to). A look that fails during the restart is noted and retried at the next look,
   as today.
4. **The goal.** One line more in merge.goal.md, written before the new system exists: "If its build failed, leave it,
   and say so once." Before the deployment, no pull request links a build, so the line holds for none of them.
5. **Trust for the new host.** ARTIE's credentials name hosts (`~/.artie/credentials`); the new host gets its own entry,
   or none if it is open. Following a link to a host the run was never told about should need one setting, an
   allow-list of hosts, so the demo can show a link to an unknown host refused too.

## What each rung would show

The same continuum as the rest of the piece, now for something that didn't exist when the script was made:

| Rung | When the build link appears |
|---|---|
| Wayfinding (the goal run) | The reasoner sees `ci:build` and its description, follows it, and leaves the failed bump. The most adaptive. |
| Kept script, lenient | The script's `leaveWhere` names `ci:build`, which the lock file doesn't pin: lenient follows it, marked unpinned in the trace. |
| Kept script, strict | Refuses the unpinned relation, says so, and keeps watching. With `repin: propose` it says what it would add to the lock file; a person approves, and the next run follows it, pinned. |
| look-then-leap | The manifest's digest changed (the gateway's forms did); `--look used` leaps if the forms it uses didn't, `--look everything` stays lenient. |
| bare | Can't follow anything: its addresses are fixed. It needs new code to learn about the build service. |

So "full safe mode" is honest in two ways. Strict follows nothing it wasn't recorded with: it notices the new
system, stays safe, and asks. Lenient and wayfinding follow it at once, and every act on the new system is still
checked by its server and by ARTIE's generic checks.

## What a kept script needs

A kept script can only act on what it names. For the watch to use a system that didn't exist when it was made, its
words must name the relation (`ci:build`), from the goal line written ahead, even though no run has followed it yet.
The generator would then write that line from the goal alone, which it doesn't do today: it writes only what a run
did. Two ways:

- **Re-derive after the deployment:** run the goal again (wayfinding), generate again, review the diff. The honest
  default.
- **Written ahead:** let the generator keep a goal line no run reached, as a condition on a relation from the
  profile, unpinned, for lenient to follow and strict to refuse until re-pinned.

## Open

- Where the build service comes from: a new heart-core domain (small), or the Jenkins gateway (bigger, real CI).
- Whether the gateway reloads its config without a restart.
- Cross-host links in the gateway's connector: absolute templates, and whether the gateway checks the target exists.
- The host allow-list: where it lives (the config, the lock file, the credentials file).
