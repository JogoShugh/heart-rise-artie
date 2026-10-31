# Idea: one MCP tool per offered act

**Status:** parked, 2026-10-12. heart-mcp shipped with six fixed verbs instead
(hack log, "HEART through MCP").

## The idea

heart-mcp would expose each act the current resource offers as its own MCP
tool, with the form's JSON Schema as the tool's input schema. After every move
it would send `notifications/tools/list_changed`. An empty bed's tool list would
hold `garden_sow_seed`; once sown, it would hold `garden_water_cell` instead.

## What it would buy

- The host validates fields natively against the input schema, before the
  call reaches heart-mcp.
- State-scoped tools are visible in the host's own tool list, which makes for a
  vivid demo.

## Why it waits

- Hosts differ in how they handle `list_changed`; some read the list once.
  The same server could work in one host and quietly fail in another.
- Tool definitions sit at the front of a model's prompt. Changing them after
  every move discards the host's prompt cache every time.
- API tool names allow only `[a-zA-Z0-9_-]`, so `garden:water-cell` becomes
  `garden_water_cell`, and the relation it names is lost.
- With more than one resource in view, a tool named for an act does not say
  which resource it acts on.
- It rebuilds the catalogue the protocol argues against, just in smaller pieces.

## If it comes back

Offer it as an optional mode on top of the fixed verbs (`HEART_MCP_TOOLS=per-act`),
not as a replacement, and keep ARTIE's check before sending in both modes.
