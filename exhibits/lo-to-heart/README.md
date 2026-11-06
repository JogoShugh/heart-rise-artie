# From LO to HEART: the series code

The code for the series planned in `spec/ideas/series-lo-to-heart.md`, one step per part.

## Part 0: the dashboard fragment, behind its own server

- `public/bumps.html`: the fragment. It lists the open pull requests labelled `bumps`, and offers Merge only where
  our rule allows: not behind its base. The rule is in its own JavaScript.
- `server.ts`: the dashboard's own server. It serves the page and forwards `/github/...` to GitHub's API with the
  team's token, so the page never holds one. It does nothing else, yet. It listens on this machine only.

```
npm install
GITHUB_TOKEN=... npm start      # http://localhost:8500/bumps.html
npm run check                    # headless Chrome: show, click Merge, show again
```

GitHub's API allows calls from any page (CORS), so the server isn't there for that. It's there for the token, and
for the rate limit: 60 requests an hour without one. The run of 2026-11-01 is in `runs/`.
