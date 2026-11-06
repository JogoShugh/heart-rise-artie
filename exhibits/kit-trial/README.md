# The kit, tried end to end

`kit/` run from a clone, live on `JogoShugh/heart-github-test`, 6 November 2026, as a coworker would run it on a fork:
bootstrap, then each goal walked by Claude (Sonnet 5.5), kept as YAML, and replayed. GitHub was called through the
gateway inside the container, with the caller's own token; the build and deploy services are the simulated ones
in the same container.

## The runs

| # | Command | Seconds | Model calls (planner + reasoner) | What it did |
|---|---|---|---|---|
| 01 | `kit/bootstrap.sh` | 18 | 0 | Six issues from the kept seed script: #2264–2269; #2264 labelled `needs-triage` and `documentation` |
| 02 | `try.sh plan hello` | 3 | 0 | Read and checked the goal; nothing sent |
| 03 | `try.sh walk hello` | 54 | 11 + 5 | #2270 opened, commented on, closed as completed |
| 04 | `try.sh keep hello` | 3 | 0 | `hello.heart.yaml`, 0 values to review; 5 forms pinned; the goal's Verify kept once |
| 05 | `try.sh replay hello` | 15 | 0 | #2284, the same, held strictly to the lock file |
| 06 | `try.sh walk triage` | 95 | 11 + 7 | #2264 bug, #2265 question, #2266 enhancement, each then triaged; watch #2272 |
| 07 | `try.sh keep triage` | 3 | 0 | `triage.heart.yaml`: the match kept as `has: ${inbox}`, the labelling step as a reason step, the rest pinned |
| 08 | `try.sh walk closeout version=1.0` | 141 | 23 + 8 | #2267 completed with "Fixed in 1.0.", #2268 not planned, #2269 duplicate; watch #2273 |
| 09 | `try.sh keep closeout` | 2 | 0 | `closeout.heart.yaml`: each case a `given:` on the labels, its close pinned |
| 10 | `kit/bootstrap.sh` | 18 | 0 | Six more: #2274–2279 |
| 11 | `try.sh replay triage` | 80 | 0 + 3 | #2274 bug, #2275 question, #2276 enhancement; the triaged ones left alone; watch #2280 |
| 12 | `try.sh replay closeout version=1.1` | 47 | 0 | #2277 completed with "Fixed in 1.1.", #2278 not planned, #2279 duplicate; watch #2281 |
| 13 | `try.sh plan release` | 3 | 0 | Read and checked the goal; nothing sent |
| 14 | `try.sh walk release version=1.1` | 60 | 15 + 7 | A build queued on the build service; 1.1 deployed to staging, us-east, on the deploy service; #2285 "Release 1.1" |
| 15 | `try.sh keep release` | 3 | 0 | `release.heart.yaml`, the app kept as `app`; 1 value to review (the region) |
| 16 | `try.sh replay release version=1.0` | 6 | 0 | The same three systems, release 1.0; #2286 "Release 1.0" |
| 17 | `try.sh replay release version=1.2` | 3 | 0 | Refused by the client before the deploy: "release: 1.2 is not one of the choices" (1.2 failed) |

- Every issue the bootstraps opened was worked by a goal; none is left waiting.
- A watch ends when someone closes its issue; here the trial's helper closed it once every issue it watched was done.
- Model calls: the planner's decisions and the reasoner's choices, counted from each trace.

## Known limits

- The release script reads the region from the app's state (`${app.regions.staging.0.name}`), which `artie generate` marks for
  review; it doesn't read it from the deploy form's choices.
- A replay refused at the deploy has queued its build already: run 17 queued a build of 1.2.
- The build and deploy services are simulated, in memory: each run of the kit starts them afresh.

## Files

- `runs/NN-*.out`: what each command printed; `runs/*.watch`: when the watch issue was closed.
- `runs/*.trace.yaml.gz`: each goal walk and each kept replay, step by step. The seed's is the last bootstrap's.
- `kept/`: the four kept scripts and their lock files, as `artie generate` wrote them.
