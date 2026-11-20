Add a new entry to ADR.md capturing a design decision made during this session.

FORMAT — append to the existing numbered list in ADR.md:

## N. <short title>

**Context:** What problem or question prompted this decision.

**Options considered:** What alternatives were on the table and why each was rejected or not chosen.

**Decision:** What was decided.

**Rationale:** Why — including any Fielding/protocol alignment, type-system enforcement angle, or prior ADR this builds on.

RULES:
- Be specific about what was rejected and why — vague "we considered X" entries are useless
- If the decision enforces a Fielding principle at the type or architecture level, say so explicitly
- Link to related ADR entries by number where relevant
- Do not pad — if the rationale is simple, keep it short

The decision to record: $ARGUMENTS
