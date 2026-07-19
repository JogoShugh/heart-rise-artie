Review the following H.E.A.R.T. client code against these non-negotiable rules:

PROTOCOL BOUNDARY
- Step definitions must talk only to HeartRiseClient (enter, act) — never directly to HeartResponseParser or any internal
- HeartResponseParser must not appear outside the heart package

GHERKIN
- Feature files use protocol-neutral language: "client", "affordance", "manifest", "service" — no domain terms
- Assertions use data tables or Scenario Outlines, not repetitive Then/And chains
- Feature file named after the protocol concern, not a slice number

CODE
- No !! operators anywhere — ?: error("...") instead
- No magic strings for JSON key access — constants from Model.kt only
- No custom wrapper classes around types kotlinx already provides (JsonObject covers all JSON Schema needs)
- Step definition classes receive ClientContext via constructor, not self-contained state

DESIGN
- Check against ADR.md — does anything contradict a recorded decision?
- enter(url) is the only place a URL appears in client code
- act(rel, payload) never accepts a URL

Report findings as: PASS / FAIL per category, with specific line references for any failures.

$ARGUMENTS
