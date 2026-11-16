# Idea: a talk, "Studs and Tubes: Lego-like Development with HEART"

**Status:** outline only. Raised by the author on 2026-11-16. Whether it becomes a talk is the author's call.

**The thesis:** build systems, or wrap the ones you don't control, so they speak one media type, and you get Lego-like
development. Pieces never designed together connect at runtime, because the connector is uniform all the way down.

## Outline

1. **Opening question.**
   - Why does any Lego brick fit any other? A brick from 1958 snaps onto one made last week.
   - The bricks don't know each other. They share one connector: studs on top, tubes underneath.
   - Trivia aside: Lego patented the stud-and-tube coupling in 1958. The coupling is the invention.
2. **Roy Fielding described a Web that already worked.**
   - In 2000, Roy Fielding wrote down why the Web worked and why it scaled, and called the style REST.
   - He described the system already running, the largest anyone had built. He didn't propose a new one.
   - At its center is a uniform interface: one way to connect anything to anything. That is the stud and the tube.
3. **A short diversion: the divorce.**
   - A page on the human Web shows information and controls together: the text, and the links and forms that say what
     you can do next. Fielding describes hypertext as exactly that pairing.
   - The machine Web, the APIs, kept the information and divorced the controls.
   - JSON came back with data and no links or forms. The controls moved into documentation, SDKs and client code
     written ahead of time, so every integration became custom glue again.
4. **The Web still works this way, and everyone still does it, too late.**
   - Browsers follow links and submit forms they have never seen, every day.
   - Everyone builds hypermedia-driven applications already, at the last mile. The browser stitches a screen from many
     unrelated sources, with controls scattered across front-end code, widgets and scripts.
   - It works, but it happens too late. Each backend has already decided its own rules, and the browser reconciles.
   - The point to hammer: the problem is not that we don't do hypermedia. We do it at the wrong layer.
5. **The thesis: do it lower down.**
   - HEART puts information and controls back together between services, not only at the screen.
   - Consistency is distributed lower in the stack:
     - every act is offered by the resource itself;
     - every act is conditional on the version read;
     - every refusal says why.
   - Consistency at a lower level is what scales, for the same reason the Web scaled. Each resource carries its own
     controls, so nobody in the middle coordinates.
6. **The studs and tubes in HEART.**
   - Links: you can go there.
   - Forms: you can do this here, with a target and a schema.
   - Conditional acts: `If-Match` is the clutch power. A piece connects firmly or not at all; stale is a 412.
   - Honest absence: `hr:unavailable` says why an act isn't offered.
   - RISE: watch now, be told later. A brick that snaps on when the answer is ready.
7. **Wrapping what you don't own.**
   - The gateway is an adapter plate: GitHub's or GitLab's API on one side, HEART on the other.
   - The proof: both forges behind the same `scm:` relations, so the Merge Watch release runs on either unchanged.
   - The plate is ours, so the controls are ours.
8. **The demo: plug in a brick at runtime.**
   - A script reads a collection and loops over its items.
   - Mid-run, a service is added behind the gateway with one connector entry.
   - On the next read, a new link appears on each item. The loop follows it and acts on the other side, knowing nothing
     of that domain, only the media type.
9. **Riffs: how much the client knows.**
   - A, the media type only: follows every link and reports. Purest Lego, least purposeful.
   - B, one typed relation (`ci:smoke-test`): knows the piece is a script to run, not what it is about.
   - C, a form's relation (`scm:merge`): acts wherever it's offered, with the body checked against the schema.
   - D, a model at the edge, once: finds the way, and the run is kept and replays for free.
   - E, the safety catch: a pinned form changes, so the replay stops before sending anything and hands back to the model.
10. **Where the analogy breaks.**
    - Connected bricks aren't a castle; connection isn't meaning.
    - Meaning lives in relation types and in the goal.
    - HEART gives the snap, typed relations the shape, the goal the castle.
11. **Why now, and the close.**
    - Agents are about to call everything. Without controls in the representation, every agent re-learns every API at
      token prices; with them, the model is needed only at the edges.
    - Last line: "The Web already knew how to do this. Let's stop doing it at the last mile."

## Open

- Build the runtime plug-in demo for real? The gateway connectors, for-each in scripts and link following exist.
- A deck in the author's voice, Take HEART style, if the talk goes ahead.
- Fielding's own words on information and controls: quote the passage whole, from its source, before any slide uses it.
