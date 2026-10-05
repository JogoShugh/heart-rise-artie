# Hack log

## 2026-10-04

State at the end of the session: everything is committed locally and nothing
is in progress. The Fielding review is published as a private artifact:
https://claude.ai/artifact/NiZdhyhpwHrhdpDgkMJgWr

Waiting on the author:

- **EVAL-L6:** answer the 5 open questions at the end of `spec/EVAL-L6.md`
  (domains, MacBook chip and RAM, consent to install Ollama, API keys, intent
  review).
- **L6 real-model run:** needs `ANTHROPIC_API_KEY`. Until then, L6 is proven
  only by scripted reasoners, not by inference.
- **RISE:** not built.
- **Rule 4 tension (Fielding review):** `ContentAddressCheck` recognises a
  content-addressed URL by its `/sha256-` path shape. That reads meaning from
  URL structure. Possible fix: carry the digest in an RFC 9530 `Repr-Digest`
  header instead of relying on the path.

Other tensions the Fielding review recorded (no action decided):

- **Rule 1:** the spec is an HTTP-only binding.
- **Rule 2:** the [0.5] `If-Match` narrowing, and `Accept-Profile` is still a
  W3C draft.
- **Typed relations:** action names (`waterCell`, HAL-FORMS `default`) are not
  standardized relations. L6 Reason covers the gap by reading names.
