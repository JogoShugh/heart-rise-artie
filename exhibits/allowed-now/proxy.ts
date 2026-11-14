// A proxy in front of GitHub's REST API. Every request goes through unchanged. A pull request's answer gets one more
// Link header, the kind GitHub already sends for the next page, to what may be done to that pull request now.
import express from "express";
import { createProxyMiddleware, responseInterceptor } from "http-proxy-middleware";
import { Octokit } from "octokit";

const ALLOWED_NOW = "https://jogoshugh.github.io/heart-rise-artie/rels/allowed-now";
const PULL = /^\/repos\/[^/]+\/[^/]+\/pulls\/\d+$/;
// With GITHUB_TOKEN set, the proxy holds the credential: callers need none, and can reach GitHub only through it.
const TOKEN = process.env.GITHUB_TOKEN;

const app = express();

// What may be done to a pull request now, as GitHub operation names, and why not the rest.
app.get("/allowed-now/repos/:owner/:repo/pulls/:number", async (req, res) => {
  const { owner, repo, number } = req.params;
  const github = new Octokit({ auth: TOKEN ?? req.headers.authorization?.split(" ")[1] });
  const { data: pull } = await github.rest.pulls.get({ owner, repo, pull_number: Number(number) });
  const { data: compare } = await github.rest.repos.compareCommits({
    owner, repo, base: pull.base.ref, head: pull.head.sha,
  });

  const notAllowed: Record<string, string> = {};
  if (pull.state !== "open") notAllowed["pulls/merge"] = "The pull request is closed.";
  else if (pull.draft) notAllowed["pulls/merge"] = "A draft can't be merged; mark it ready for review first.";
  else if (pull.mergeable === false) notAllowed["pulls/merge"] = "The branch has conflicts; resolve them first.";
  // Our own rule, which this repository's settings don't enforce.
  else if (compare.behind_by > 0) notAllowed["pulls/merge"] = "The branch is behind its base; update it first.";
  if (compare.behind_by === 0) notAllowed["pulls/update-branch"] = "The branch is already up to date.";

  const allowed = ["pulls/merge", "pulls/update-branch"].filter((operation) => !(operation in notAllowed));
  res.json({ allowed, notAllowed });
});

// Everything else goes to GitHub as it is; a pull request comes back with the extra link.
app.use(createProxyMiddleware({
  target: "https://api.github.com",
  changeOrigin: true,
  headers: TOKEN ? { authorization: `Bearer ${TOKEN}` } : {},
  selfHandleResponse: true,
  on: {
    proxyRes: responseInterceptor(async (body, _proxyRes, req, res) => {
      if (req.method === "GET" && PULL.test(req.url ?? "")) {
        res.setHeader("Link", `</allowed-now${req.url}>; rel="${ALLOWED_NOW}"`);
      }
      return body;
    }),
  },
}));

// This machine only: holding a token, the proxy would act as you for anyone who could reach it.
app.listen(8400, "127.0.0.1", () => console.log("In front of GitHub at http://localhost:8400"));
