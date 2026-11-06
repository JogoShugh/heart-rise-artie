# The kit, tried end to end

`kit/` run from a clone, live on `JogoShugh/heart-github-test`, 6 November 2026, as a coworker would run it on a fork:
`kit/try.sh up`, a bootstrap, then each goal walked by Claude (Sonnet 5.5), kept as YAML, and replayed. GitHub was
called through the gateway in the kit, with the caller's own token; the build and deploy services are the simulated
ones in the same kit. Each state was screenshotted in each system as it happened: the report is
`docs/reports/kit-trial/`.

## The runs

| # | Command | Seconds | Model calls (planner + reasoner) | What it did |
|---|---|---|---|---|
| 01 | `kit/bootstrap.sh` | 16 | 0 | Six issues from the kept seed script: #2287–2292; #2287 labelled `needs-triage` and `documentation` |
| 02 | `try.sh plan hello` | 0 | 0 | Read and checked the goal; nothing sent |
| 03 | `try.sh walk hello` | 49 | 11 + 5 | #2293 opened, commented on, closed as completed |
| 04 | `try.sh keep hello` | 0 | 0 | `hello.heart.yaml`; 5 forms pinned |
| 05 | `try.sh replay hello` | 13 | 0 | #2294, the same, held strictly to the lock file |
| 06 | `try.sh walk triage` | 109 | 11 + 7 | #2287 bug, #2288 question, #2289 enhancement, each then triaged; watch #2295 |
| 07 | `try.sh keep triage` | 1 | 0 | `triage.heart.yaml`: the match as `has: ${inbox}`, the labelling step reasoned, the rest pinned |
| 08 | `try.sh walk closeout version=1.0` | 156 | 22 + 7 | #2290 completed with "Fixed in 1.0.", #2291 not planned, #2292 duplicate; watch #2296 |
| 09 | `try.sh keep closeout` | 0 | 0 | `closeout.heart.yaml`: each case a `given:` on the labels, its close pinned |
| 10 | `kit/bootstrap.sh` | 22 | 0 | Six more: #2297–2302 |
| 11 | `try.sh replay triage` | 79 | 0 + 3 | #2297 bug, #2298 question, #2299 enhancement; watch #2303 |
| 12 | `try.sh replay closeout version=1.1` | 63 | 0 | #2300 completed with "Fixed in 1.1.", #2301 not planned, #2302 duplicate; watch #2304 |
| 13 | `try.sh plan release` | 0 | 0 | Read and checked the goal; nothing sent |
| 14 | `try.sh walk release version=1.1` | 52 | 15 + 7 | A build queued; 1.1 deployed to staging, us-east; #2305 "Release 1.1" |
| 15 | `try.sh keep release` | 1 | 0 | `release.heart.yaml`, the app kept as `app`; 1 value to review (the region) |
| 16 | `try.sh replay release version=1.0` | 6 | 0 | The same three systems, release 1.0; #2306 "Release 1.0" |
| 17 | `try.sh replay release version=1.2` | 0 | 0 | Refused by the client before the deploy: "release: 1.2 is not one of the choices" (1.2 failed) |

- Every issue the bootstraps opened was worked by a goal; none is left waiting.
- Runs 01–15 and 17 ran with the kit up; 16 in a kit of its own, as every command does when the kit is down. Each
  release replay began on fresh build and deploy services.
- A watch ends when someone closes its issue; here the trial's helper closed it once every issue it watched was done.
- Model calls: the planner's decisions and the reasoner's choices, counted from each trace.

## Known limits

- The release script reads the region from the app's state (`${app.regions.staging.0.name}`), which
  `artie generate` marks for review; it doesn't read it from the deploy form's choices.
- A replay refused at the deploy has queued its build already: run 17 queued a build of 1.2.
- The build and deploy services are simulated, in memory: `kit/try.sh down` forgets them.

## Files

- `runs/NN-*.out`: what each command printed; `runs/*.watch`: when the watch issue was closed.
- `runs/*.trace.yaml.gz`: each goal walk and each kept replay, step by step, with every request and answer. The
  seed's is the last bootstrap's; `release-kept`'s is the 1.2 replay's.
- `kept/`: the four kept scripts and their lock files, as `artie generate` wrote them.
