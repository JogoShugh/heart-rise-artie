// Stage four bumps under LABEL on deps-main: behind, draft and conflicted (labelled approved), and one fresh bump
// that isn't labelled approved. Only one is fresh, since merging it puts any other behind. Prints roles and numbers:
// "fresh 1 behind 2 draft 3 conflict 4".
import { Octokit } from "octokit";
const gh = new Octokit({ auth: process.env.GITHUB_TOKEN });
const owner = "JogoShugh", repo = "heart-github-test", base = "deps-main";
const label = process.argv[2];
const stamp = new Date().toISOString().replace(/\D/g, "").slice(4, 14);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const b64 = (s) => Buffer.from(s).toString("base64");

async function bump(name, files, { draft = false, labels = [label, "approved"] } = {}) {
  const { data: tip } = await gh.rest.repos.getBranch({ owner, repo, branch: base });
  const branch = `deps/${name}-${stamp}`;
  await gh.rest.git.createRef({ owner, repo, ref: `refs/heads/${branch}`, sha: tip.commit.sha });
  for (const [path, content] of Object.entries(files)) {
    await gh.rest.repos.createOrUpdateFileContents({ owner, repo, branch, path, message: `Bump ${name}`, content: b64(content) });
  }
  const { data: pull } = await gh.rest.pulls.create({ owner, repo, base, head: branch, draft,
    title: `Bump ${name} (${stamp})`, body: "A Dependabot stand-in, for exhibits/allowed-now." });
  if (labels.length) await gh.rest.issues.addLabels({ owner, repo, issue_number: pull.number, labels });
  return pull;
}
async function checked(pull) {
  for (let i = 0; i < 60; i++) {
    const { data: p } = await gh.rest.pulls.get({ owner, repo, pull_number: pull.number });
    const { data: runs } = await gh.rest.checks.listForRef({ owner, repo, ref: p.head.sha, check_name: "deps-check" });
    if (runs.check_runs[0]?.status === "completed") return runs.check_runs[0].conclusion;
    await sleep(5000);
  }
  throw new Error(`deps-check timed out on #${pull.number}`);
}

const dir = "allowed-now";
const conflictPath = `${dir}/lockfile-${stamp}.txt`;
// Branched from the old tip: behind once the base moves, and the conflicted one also clashes with it.
const behind = await bump("left-pad-2.0.1", { [`${dir}/left-pad-2.0.1-${stamp}.txt`]: "left-pad 2.0.1\n" });
const conflict = await bump("lodash-4.17.22", { [conflictPath]: "lodash 4.17.22\n" });
const mover = await bump("base-move", { [conflictPath]: "lodash 4.17.21\n" }, { labels: [] });
await Promise.all([behind, conflict, mover].map(checked));
await gh.rest.pulls.merge({ owner, repo, pull_number: mover.number, merge_method: "squash" });
// Branched from the new tip.
const fresh = await bump("chalk-5.4.2", { [`${dir}/chalk-5.4.2-${stamp}.txt`]: "chalk 5.4.2\n" }, { labels: [label] });
const draft = await bump("debug-4.4.2", { [`${dir}/debug-4.4.2-${stamp}.txt`]: "debug 4.4.2\n" }, { draft: true });
await Promise.all([fresh, draft].map(checked));
// Let GitHub settle mergeable on the conflicted one.
for (let i = 0; i < 20; i++) {
  const { data: p } = await gh.rest.pulls.get({ owner, repo, pull_number: conflict.number });
  if (p.mergeable === false) break;
  await sleep(3000);
}
console.log(`fresh ${fresh.number} behind ${behind.number} draft ${draft.number} conflict ${conflict.number}`);
