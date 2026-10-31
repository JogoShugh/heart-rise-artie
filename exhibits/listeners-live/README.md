# Listeners, live

The last slice of spec/PLAN-LISTENERS.md: one watch over the scratch repository `JogoShugh/heart-github-test`,
through the HEART gateway, run twice on the same timeline. Once as three listeners, each on its own. Once as
today's single loop, the same handlers in turn at each look. How long each takes to react is read from GitHub's
own times.

The watch (`watch.kts`) has three handlers:

- **veracode**: when a stand-in veracode-bot says a scan FAILED on the scan issue, label it `security-hold` and say
  so, once;
- **alerts**: answer each issue labelled `live-alert` once;
- **triage**: handle each new comment on the triage issue slowly (15 seconds), as a model call is slow.

## The pieces

- `stage.kts` opens the watch's issue, the scan issue and the triage issue.
- `play.kts` plays the timeline, from when the watch has made its first look:
  - a failed scan at 20 s, a passed one at 80 s, and a failed one again at 140 s;
  - alerts at 50 s and 110 s;
  - a triage comment every 30 s, from 10 s;
  - then it closes the watch's issue, at 200 s, which ends the watch.

  The stand-in bot comments as the account the run is given, and starts its words with "veracode-bot:".
- `watch.kts mode=listeners` or `mode=loop` is the watch.
- `reactions.kts` reads, for each event, when GitHub says the watch answered it.
- `runs/`: each run's output, trace, timeline and reactions. `l` runs are listeners; `g` runs are the single loop.

## Running it

From this folder, with `artie` on the path (`. ../../client/scripts/.env`), the gateway on `localhost:8392`, and
its token in `ARTIE_TOKEN_LOCALHOST_8392`:

```
artie run stage.kts owner=JogoShugh                       # prints watch=… scan=… triage=…
artie run watch.kts owner=JogoShugh watch=W scan=S triage=T mode=listeners --rung L2 --trace runs/live-l1.yaml
artie run play.kts owner=JogoShugh watch=W scan=S triage=T out=runs/play-l1.yaml
artie run reactions.kts owner=JogoShugh play=runs/play-l1.yaml out=runs/reactions-l1.yaml
```

GitHub can't reach a laptop, so the gateway's hub (webhooks waking held reads, slice 6) is off here: every poll
is a read the gateway answers at once, a 304 while nothing changed.

## The runs of 2026-10-25

| Event | Three listeners (l1) | One loop (g1) |
|---|---|---|
| A failed scan, at 20 s | held 16 s later | held 27 s later |
| An alert, at 50 s | answered 9 s later | answered 42 s later |
| An alert, at 110 s | answered 22 s later | answered 26 s later |
| A passed scan, at 80 s | left alone | left alone |
| A failed scan again, at 140 s | already held: said nothing again | already held: said nothing again |

In the loop each slow triage held up the scan and the alerts behind it; as a listener it held up only itself.
Neither run said anything twice (the guard checked as they ran). One run each, over a phone's hotspot: a first
look, not a benchmark. The page: `docs/reports/listeners-live/index.html`, made by `report.kts` from
`results.yaml`.
