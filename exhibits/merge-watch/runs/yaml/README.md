# The merge watch from generated YAML, 5 November 2026

Claude's goal run (goal-claude) and the local-model runs of the scripts artie generate wrote from it
(../../yaml/), on the scratch repository through a gateway on localhost:8393. Each NAME has its staging
(NAME.stage.out), its output (NAME.out) and its trace, compressed (NAME.trace.yaml.gz: gunzip to read it, or to
regenerate the scripts from goal-claude's). results.txt is a line per run; summary.yaml the rows the report reads.

- first-batch/: the runs lost to GitHub answering 500 for a few minutes, and held to the old pins; not counted.
- spurious/: a kept run retried when it had already passed, its first trace overwritten; not counted.

The report: docs/reports/local-models-yaml/, made by ../../yaml/report.mjs.
