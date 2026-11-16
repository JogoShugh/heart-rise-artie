# Idea: browser scenarios for heart-forms.js

**Status:** deferred by the author on 2026-11-16. Nothing is built.

## Why

- `heart/heart-core/src/main/resources/heart/assets/heart-forms.js` is real browser code. It fills and sends a
  form's dialog in the HTML dialect, and expands URI templates.
- No test runs it, so SonarQube counts every changed line in it as uncovered. In the 2026-11-16 scan, 2 of its lines
  were the only new code left uncovered.

## The idea

- Gherkin first: scenarios in heart-core's `html.feature` (or a new feature) that open a resource's HTML in headless
  Chrome and use its dialogs: fill a form, send it, see the answer, see a greyed act and its reason.
- Run the browser with coverage on (Chrome's V8 coverage, or the script served instrumented), and write lcov.
- Add that lcov to `~/.artie/sonar/scan.sh` beside heart-ts's, so SonarQube counts it.

## Open

- Which browser driver from the JVM tests: Chrome DevTools Protocol directly, or Playwright for Java.
- Whether the scenarios belong to heart-core or to the TCK, so every binding's HTML is checked the same way.
