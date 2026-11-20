# The Current Norm: Agentic Communication with APIs

The norm is one of two patterns, and they're more similar than they look:

**Pattern A — Tool calling against a pre-loaded schema.** You take an OpenAPI spec (or write tool definitions by hand), inject them into the agent's context at startup, and the LLM calls functions from that fixed menu. OpenAI function calling, Anthropic tool use, LangChain tools, AutoGen — they all work this way. The server never tells the agent what it can do. The agent already "knows" from what was loaded before the session started.

**Pattern B — MCP.** Anthropic's Model Context Protocol, now broadly adopted. It standardizes the transport layer for tool definitions — instead of pasting JSON into a system prompt, tools are served over a protocol. But the fundamental shape is identical: the client connects, loads the tool manifest upfront, and works from that fixed set for the lifetime of the session. The server doesn't vary the manifest based on state.

Both patterns have the same root characteristic: the client knows the interface before it talks to the server. The schema is out-of-band relative to the resource state. The server never says "here's what you can do right now given where we are" — it says "here's what you can do, full stop."

## So what is novel in H.E.A.R.T.?

HATEOAS itself is 25 years old. HAL+JSON has been around since ~2011. The concept of server-driven affordances isn't new. What's new is the specific combination:

1. Applying it to LLM agents specifically — where the token cost argument makes the optimization economically meaningful
2. The hash-based registry making the warm/cold distinction concrete and measurable
3. Formalizing Sync-Reason-Act as the agent processing model
4. The claim that structural hallucination (calling endpoints that don't exist or aren't available) is mechanically eliminated, not just reduced

The honest version of the novelty claim is: this is a well-understood web architecture principle that the agentic AI industry has almost entirely ignored, being applied with a specific optimization layer that makes it practical for LLM contexts. That's a real gap, but it's not the same as inventing something from scratch.
