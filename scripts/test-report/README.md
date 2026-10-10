# Test baseline report

> **Historical (marked 2026-10-09).** This shows the design as of early
> October 2026. Since then the forms link carries `integrity`, schemas are
> referenced with `$ref` and `hr:integrity`, and the client reads nothing
> from a URL (the hash in the path is no longer checked). Current evidence:
> `server-spring/journal.html`, the client and server Cucumber suites, and
> `scripts/live-client-check.sh`. How to bring it up to date is in
> `hacklog.md`, "Old exhibits".

`build_report.py` builds `test-report.html` from the result files beside it.
