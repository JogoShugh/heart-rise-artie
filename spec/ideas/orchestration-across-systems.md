# Idea: one script across GitHub, Jenkins and Vault

**Status:** idea only. Raised by the author on 2026-10-26. Nothing is built beyond what "What exists" names. It
builds on spec/ideas/explore-capture-across-hosts.md (section 5, "Across hosts") and spec/ideas/jenkins-gateway.md,
and adds what the merge watch taught: waiting words, pins, and keeping reasoning to one line.

## The scenario

A release, across three systems that know nothing of each other:

1. A pull request labelled "release" is merged on GitHub.
2. The script starts a Jenkins build for that commit and waits for it.
3. If the build passes, it asks Vault for a short-lived deploy credential.
4. It starts the Jenkins deploy job, which uses that credential.
5. It comments on the pull request, or opens an issue if something failed.

## HEART was never one-server

The Web was never one server: a resource on one site links to another, and the browser follows it. Fielding's first
rule lets any URI scheme appear in any link, and HEART's core picks how to interact by the address, not by
configuration (SPEC §6.0). ARTIE already keeps credentials per origin, picks the binding by scheme, and follows
absolute links to another host. The protocol allows crossing systems; what is missing is the orchestration on top.

## Three shapes of address, one entry point

The script may see one gateway with three connectors (one origin), three gateways (three origins), or a mix of
native HEART servers and gateways. The script shouldn't care. One entry point of the author's own, a small
workspace resource linking to each system's bookmark, keeps the single-bookmark rule: the systems become links in a
representation, not configuration in the script. Moving Jenkins behind another gateway changes one link and nothing
in the script.

## How systems connect

- **By links.** A GitHub check run's `details_url` points at a Jenkins build. A connector rule in the GitHub gateway
  rewrites it into the Jenkins gateway's resource for that build, so crossing is following a link. Configuration
  only.
- **By values.** Nothing in GitHub links to Vault. The script carries a value across: the commit SHA read from the
  pull request fills the Jenkins build form. The capture records where the value came from ("read from GitHub's
  state at step 3"), so the replay reads it again rather than freezing it in.

## No shared transaction

There is no transaction across three vendors. GitHub may accept a comment while Jenkins refuses a job; Vault may
issue a credential and the deploy then fail. Within one system, If-Match guards each act; across systems no ETag
spans them. Proposed:

- **Every act across hosts is idempotent or has a compensation written beside it:** revoke the lease if the deploy
  doesn't start; the release comment says "failed" rather than nothing.
- **Reversible acts first, irreversible last.** A goal can name a point of no return, and the script refuses to
  pass it unless every earlier step succeeded.
- **Re-read before acting across a gap.** After ten minutes in Jenkins, read the pull request again and act with its
  fresh ETag. Cross-system staleness becomes the single-system 412 already handled.
- **The trace shows partial success openly,** system by system.

This is the saga pattern, with one difference: every compensation is itself an act the server offers, and offers
only when legal. A lease that has expired offers no revoke, and the reason says why.

## Waiting looks the same everywhere

A Jenkins queue item becoming a build, a GitHub check finishing, a Vault lease nearing expiry: each is an act that
makes a resource, then a wait until a condition holds or a time runs out. The goal words already exist: once, until,
whenever, give up after. One way of waiting and one way of handling refusal across three vendors. When systems can
call back instead of being polled, that is what RISE was designed for (SPEC §7, on hold); orchestration across
systems is the strongest reason to take it off hold later.

## Vault: orchestrating secrets without a model holding one

Vault is both a credential source for ARTIE (`VaultCredentials.kt`, SPEC §6.13) and a system the script works. The
rule that no model is shown a credential must hold here too:

- A Vault gateway never puts a secret value in a representation. It offers metadata and acts: this lease exists, it
  expires at noon, you may renew or revoke it.
- A form that needs the credential takes a reference to the Vault resource, not the secret.
- ARTIE resolves the reference below the reasoner, just above the wire, where it adds credentials today.

The model can orchestrate secrets without ever holding one. Most agent setups would hand it the token.

## Pins and drift, per system

A script across three systems has pins for each. If Jenkins changes its build form, the script stops at the Jenkins
step and says so, while the GitHub and Vault parts stay trusted. Drift is local and visible, and the self-repair
loop (explore-capture-across-hosts.md, section 3) re-plans that step only, from the original words.

## Beside existing orchestrators

Jenkins pipelines, GitHub Actions, Argo, Temporal and Airflow already orchestrate across systems. The difference is
where the knowledge lives. A Jenkinsfile that calls GitHub and Vault has hard-coded their endpoints, rules and
quirks: the WSDL problem again, spread across YAML. A HEART script holds kept decisions, one bookmark and its pins;
the rules come from each system at the moment of acting. The same script runs from a laptop, from CI, or driven by
Claude.

HEART doesn't replace a workflow engine's durability. Temporal remembers where a workflow was if the machine dies;
the script's journal does a small version of that. HEART supplies the interface; an engine may still supply
durability, and the two could be combined.

## The goal file

The front matter gives one start, the workspace. The body names systems in plain words: "In Jenkins, start the build
for that commit", "In Vault, get a deploy credential that lasts one hour". The reasoner maps "In Jenkins" to the
workspace's link once, and that is kept. Perhaps one line stays reasoned: the release note written from the merged
pull requests, which is real free text (spec/ideas/reasoning-that-earns-its-keep.md).

## What exists

- Credentials per origin; binding by scheme; following links across hosts.
- ARTIE reading credentials from Vault.
- The waiting words (once, until, whenever, give up after) and the journal.
- The GitHub gateway.

## What is missing

- The Jenkins gateway (spec/ideas/jenkins-gateway.md).
- A Vault gateway that offers references in place of secrets.
- The workspace bookmark.
- Connector rewriting of outside links.
- Tagging where a value came from, in the capture.
- Compensations and points of no return in the goal language.
- Per-system pins and partial re-planning.

## In one sentence

Crossing systems changes the credentials and the failure policy, not the language: HEART makes three vendors look
like one Web, and the script is honest that there is no transaction across them.
