// Merges every open pull request with a label, except where the answer links to what may be done now and merge
// isn't among it. The same job as the merge-dependency-bumps skill, kept as a script.
//   API_URL=http://localhost:8400 npm run merge-bumps -- OWNER REPO LABEL
import { Octokit } from "octokit";
import parse from "parse-link-header";

const ALLOWED_NOW = "https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now";
const [owner, repo, label] = process.argv.slice(2);

const github = new Octokit({ auth: process.env.GITHUB_TOKEN, baseUrl: process.env.API_URL });
const issues = await github.paginate(github.rest.issues.listForRepo, { owner, repo, labels: label, state: "open" });

for (const { number } of issues.filter((issue) => issue.pull_request)) {
  const pull = await github.rest.pulls.get({ owner, repo, pull_number: number });
  const link = parse(pull.headers.link ?? "")?.[ALLOWED_NOW];
  if (link) {
    const { data: now } = await github.request(`GET ${link.url}`);
    if (!now.allowed.includes("pulls/merge")) {
      console.log(`#${number} not merged: ${now.notAllowed["pulls/merge"]}`);
      continue;
    }
  }
  await github.rest.pulls.merge({ owner, repo, pull_number: number, merge_method: "squash" });
  console.log(`#${number} merged`);
}
