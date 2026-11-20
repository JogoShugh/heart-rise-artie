// Merges a pull request, unless the answer links to what may be done now and merge isn't among it.
//   npm run merge -- OWNER REPO NUMBER                       straight to GitHub
//   API_URL=http://localhost:8400 npm run merge -- ...       through the proxy
import { Octokit } from "octokit";
import parse from "parse-link-header";

const ALLOWED_NOW = "https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now";
const [owner, repo, number] = process.argv.slice(2);

const github = new Octokit({ auth: process.env.GITHUB_TOKEN, baseUrl: process.env.API_URL });
const pull = await github.rest.pulls.get({ owner, repo, pull_number: Number(number) });

const link = parse(pull.headers.link ?? "")?.[ALLOWED_NOW];
if (link) {
  const { data: now } = await github.request(`GET ${link.url}`);
  if (!now.allowed.includes("pulls/merge")) {
    console.log(`#${number} not merged: ${now.notAllowed["pulls/merge"]}`);
    process.exit(0);
  }
}

await github.rest.pulls.merge({ owner, repo, pull_number: Number(number), merge_method: "squash" });
console.log(`#${number} merged`);
