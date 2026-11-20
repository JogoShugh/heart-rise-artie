// Score a run from GitHub itself: read the stage line, print "role=merged|open" for each pull request.
import { Octokit } from "octokit";
import { readFileSync } from "node:fs";
const gh = new Octokit({ auth: process.env.GITHUB_TOKEN });
const words = readFileSync(process.argv[2], "utf8").trim().split("\n").pop().split(" ");
const out = [];
for (let i = 0; i < words.length; i += 2) {
  const { data: p } = await gh.rest.pulls.get({ owner: "JogoShugh", repo: "heart-github-test", pull_number: Number(words[i + 1]) });
  out.push(`${words[i]}=${p.merged ? "merged" : "open"}`);
}
console.log(out.join(" "));
