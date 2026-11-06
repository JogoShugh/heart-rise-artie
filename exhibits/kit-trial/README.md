# The kit, tried end to end

`kit/` run from a clone, live on `JogoShugh/heart-github-test`, 6 November 2026, as a coworker would run it on a fork:
bootstrap, then each goal walked by Claude (Sonnet 5.5), kept as YAML, and replayed. GitHub was called through the
gateway inside the container, with the caller's own token; the build and deploy services are the simulated ones
in the same container.

## The runs

| # | Command | Seconds | Model calls (planner + reasoner) | What it did |
|---|---|---|---|---|
| 01 | `kit/bootstrap.sh` | 16 | 0 | Six issues from the kept seed script: #2238–2243 |
| 02 | `try.sh plan hello` | 3 | 0 | Read and checked the goal; nothing sent |
| 03 | `try.sh walk hello` | 48 | 11 + 5 | #2244 opened, commented on, closed as completed |
| 04 | `try.sh keep hello` | 3 | 0 | `hello.heart.yaml`, 0 values to review; 5 forms pinned |
| 05 | `try.sh replay hello` | 16 | 0 | #2245, the same, held strictly to the lock file |
| 06 | `try.sh walk triage` | 110 | 11 + 7 | #2238 bug, #2239 question, #2240 enhancement, each then triaged; watch #2246 |
| 07 | `try.sh keep triage` | 2 | 0 | `triage.heart.yaml`: the labelling step kept as a reason step, the rest pinned |
| 08 | `kit/bootstrap.sh` | 17 | 0 | Six more: #2247–2252 |
| 09 | `try.sh replay triage` | 79 | 0 + 3 | #2247 bug, #2248 question, #2249 enhancement; the triaged ones left alone; watch #2253 |
| 10 | `try.sh walk closeout version=1.0` | 173 | 21 + 7 | #2241, #2250 completed with "Fixed in 1.0."; #2242, #2251 not planned; #2243, #2252 duplicate; watch #2254 |
| 11 | `try.sh keep closeout` | 3 | 0 | `closeout.heart.yaml`: each case a `given:` on the labels, its close pinned |
| 12 | `kit/bootstrap.sh` | 17 | 0 | Six more: #2255–2260 |
| 13 | `try.sh replay closeout version=1.1` | 48 | 0 | #2258 completed with "Fixed in 1.1.", #2259 not planned, #2260 duplicate; watch #2261 |
| 14 | `try.sh plan release` | 3 | 0 | Read and checked the goal; nothing sent |
| 15 | `try.sh walk release version=1.1` | 53 | 15 + 7 | A build queued on the build service; 1.1 deployed to staging, us-east, on the deploy service; #2262 "Release 1.1" |
| 16 | `try.sh keep release` | 3 | 0 | `release.heart.yaml`, 1 value to review (the region) |
| 17 | `try.sh replay release version=1.0` | 6 | 0 | The same three systems, release 1.0; #2263 "Release 1.0" |
| 18 | `try.sh replay release version=1.2` | 3 | 0 | Refused by the client before the deploy: "release: 1.2 is not one of the choices" (1.2 failed) |

- A watch ends when someone closes its issue; here the trial's helper closed it once every issue it watched was done.
- Model calls: the planner's decisions and the reasoner's choices, counted from each trace.
- Step 13 was run twice: the first stopped at its first read, GitHub answering the gateway 500; it sent nothing.

## Found and fixed on the way

- **A reasoned step under a Whenever was decided once.** The first triage walk labelled every issue `enhancement`,
  the first issue's label. A step marked `reasoned` is now the model's at every member (d7cda53).
- **A static file's links were ignored.** The workspace is served as `application/json`, and ARTIE read it as data
  only, though it names its profile in `_links.profile`. The media type now picks only a HEART dialect (d270136).
- **A kept script's time default.** `default: { now: … }` gave no value in a script held as data (e45fb45).

## Known limits

- The triage watch matches an issue whose labels are exactly `needs-triage`: the walk decided the check as `is`, not
  `has`. An issue with another label besides isn't triaged.
- The release script reads the region from the app's state (`regions.staging.0`), which `artie generate` marks for
  review; it doesn't read it from the deploy form's choices.
- The build and deploy services are simulated, in memory: each run of the kit starts them afresh.

## Files

- `runs/NN-*.out`: what each command printed; `runs/*.watch`: when the watch issue was closed.
- `runs/*.trace.yaml.gz`: each goal walk and each kept replay, step by step. The seed's is the last bootstrap's.
- `kept/`: the four kept scripts and their lock files, as `artie generate` wrote them.
