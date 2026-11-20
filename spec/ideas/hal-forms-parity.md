# Idea: HAL-FORMS beside HAL Schema Forms, for scripts and replay

Status: idea, 2026-10-19. A review, nothing changed. Decide the scope first.

## Where HAL-FORMS stands

HAL-FORMS works. The gateway renders through heart-core like any HEART server, so
`Accept: application/prs.hal-forms+json` gets `_templates`. Checked live on issue #129 of the scratch
repository: 200, 11 templates, the same 11 acts as `_forms`, the same targets, ETag tagged `hf.L0`. The
conformance suite the gateway runs has one HAL-FORMS scenario: a hal-schema-forms client and a hal-forms client
see the same act targets.

It kept up with some recent work: choices from state (`hr:options`) come out as inline HAL-FORMS `options`, and a
connector's `rename` carries over, since templates are built from the same schema.

## What it doesn't have

Everything about scripts and replay in the last few days was built and tested on HAL Schema Forms only.

1. **Templates are keyed differently.** `_forms` keys an act by its relation (`github:comment`); `_templates` by
   the act's name (`issue-comment`). A script that says `act("github:comment")` can't run against HAL-FORMS. The
   conformance scenario compares targets only, so it doesn't notice. Older than this week.
2. **No replay guard.** A HAL-FORMS template has no integrity, so artie generate pins nothing for it. Lenient
   marks every act unpinned and protects nothing; strict refuses every act; `on-change: values` never applies.
   Nothing says so.
3. **No prompts from descriptions.** heart-core makes a property's `prompt` from its schema `title` only. The
   gateway's fields carry a description and no title, so a HAL-FORMS client sees a bare `body`, type text. The
   act's description is dropped too; HAL-FORMS has no member for it.
4. **Not exercised.** No live run, report or generated script in these days used HAL-FORMS.

## Choices

- **Smallest:** HAL-FORMS stays the on-ramp for browsing. Write down that scripts, lock files and guards are HAL
  Schema Forms only, and have artie run say so when it reads at HAL-FORMS.
- **Parity, if HAL-FORMS scripts are in scope:**
  - Key templates by relation, so one script runs in either dialect.
  - The client hashes each template itself (a canonical form of the template object). A lock file only needs to
    notice a change, so a client-side digest is enough; HAL-FORMS needs no new member.
  - Fall back to the description for a property's `prompt` when its schema has no title.
  - One conformance scenario that runs a script, generated and guarded, against HAL-FORMS.
