// The dashboard's own server: serves the page, and forwards /github/... to GitHub's API with the team's token.
// It does nothing else, yet.
import express from "express";
import { createProxyMiddleware } from "http-proxy-middleware";

const app = express();

app.use(express.static("public"));
app.use("/github", createProxyMiddleware({
  target: "https://api.github.com",
  changeOrigin: true,
  headers: { authorization: `Bearer ${process.env.GITHUB_TOKEN}` },
}));

// This machine only: holding a token, the server would act as the team for anyone who could reach it.
app.listen(8500, "127.0.0.1", () => console.log("The dashboard is at http://localhost:8500/bumps.html"));
