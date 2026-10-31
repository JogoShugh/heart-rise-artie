# Idea: the garden as a walkable Minecraft world, from a Paper plugin

**Status:** idea only, parked 2026-10-30. Raised by the author while learning Paper plugins in Kotlin with their son.
Nothing is built. Not to be started before the author asks.

## In one sentence

A Paper server plugin runs ARTIE's library, reads a HEART garden, and builds it as a place to walk around: a plot
per resource, a sign with its name, and, on a right-click, a menu of exactly the acts it offers now, the unavailable
ones greyed with their reasons. Drawing a bed as farmland needs knowledge the plugin lacks; where that knowledge
should live is the open question below.

## Why it matters

It is a fourth generic client of the same answers, beside ARTIE's CLI, the agent and the HTML dialect
(spec/ideas/html-dialect-for-people.md). If one garden renders as a terminal, a browser page and a walkable world,
with no garden code in any of them, that is the plainest evidence there is for HEART's claim: the client is coupled
to the media type, not to the site. It is also a way to explain HEART to a child, in a world the child already knows.

## Prior art, as far as known

People do drive Minecraft from remote state, but each one is hand-built for a single API:

| Project | What it does | How it differs |
|---|---|---|
| Dockercraft (Docker, 2015) | Each container is a building with a lever to start or stop it | Written against Docker's API; a new API means new code |
| KubeCraftAdmin and similar | Pods are animals; killing one deletes the pod | Same: one API, hard-coded |
| ComputerCraft (CC: Tweaked) | In-game computers fetch HTTP and draw on monitors | A program per dashboard, written by the player |
| DeluxeMenus and similar | Chest menus built from YAML with placeholders | Local configuration, not the server's answer |
| Twitch and Discord bridges | Outside events spawn mobs or post chat | Events only; no affordances |
| Minecraft's own dialogs (1.21.6) | The server sends a screen's definition; the client draws it | Server-driven UI, but within one game server |

None of them, as far as known, renders an arbitrary remote service's controls from the service's own answers. That
is the new part: the plugin knows HAL Schema Forms and nothing about gardens.

## What a generic plugin can draw

Knowing only HAL Schema Forms, the plugin can draw only generic things. That is a browser showing HTML without CSS:
everything works, nothing looks like a garden.

| HEART | In Minecraft |
|---|---|
| A resource | A plot with a sign: its title, and its state's members on the sign or in a book |
| A list | One plot per item listed |
| A link | A path or a gate to a neighbouring plot; following it is walking there |
| A resource's acts | A chest menu, one item per act: offered in colour, unavailable as a grey barrier with the reason in its lore |
| An act's fields | A Minecraft dialog (1.21.6, exposed by Paper from about 1.21.7): text, toggle, single choice and number-range inputs, from the JSON Schema |
| A list | A chest menu as a grid, with arrow items for the list's next and previous links |
| 422 | The message beside the field, the dialog kept open |
| 412 | The plot rebuilt from a fresh read, and "this changed since you looked" |
| 201 with Location | The new plot built, and the player pointed at it |

## Farmland and a court need knowledge from somewhere

Raised by the author: a bed drawn as farmland with crops, or a game drawn as a court with players, needs someone to
know that a bed is farmland. The question is who.

| Option | Who knows "a bed is farmland" | Cost |
|---|---|---|
| A. Domain code in the plugin | The plugin, as in Dockercraft | Generic no more: a new domain is new code |
| B. View hints in the answers | The server, in a small generic vocabulary (region, grid, cell, marker, counter); the plugin knows only the vocabulary | The protocol gains a presentation vocabulary, to be scoped with care |
| C. A linked view resource | The server, in a mapping document it links, as a page links a stylesheet: "beds are 4 by 8 regions, plantings are cells holding crop items" | Fielding's optional code-on-demand constraint, as data; one more read, cacheable |
| D. A local pack | A mapping file per domain the player installs, as a resource pack | Client knowledge again, though data rather than code |

**Leaning: C, written in B's vocabulary.** It is the split that makes the Web work. HTML says what things are, CSS says
how they look, and the browser knows only HTML and CSS, never the site. Here the HEART answer says what things are,
the linked view says how they look in a block world, and the plugin knows only the view vocabulary. The server owns
its look; a new domain needs no change to the plugin.

Basketball in that vocabulary: the court is a region, the players are markers (armor stands with name tags), the score
is a counter on the sidebar scoreboard, and the acts stay menus and dialogs. The plugin still knows nothing of
basketball.

The honest limit: the view vocabulary is a second protocol, and it is the hard part. It is also the view-hints question
already open for the HTML dialect (spec/ideas/html-dialect-for-people.md), so it should be answered once for both: a
vocabulary that a browser and a block world can each draw.

## Shape of the plugin

- **Server side only.** A Paper plugin; any unmodified Java client joins, and every player sees one garden. Two players
  acting on one bed meet the ETag rule for real.
- **Network off the tick thread.** Reads and acts run on Paper's async scheduler; block changes go back to the main
  thread.
- **Staying current.** Each loaded plot is revalidated with `If-None-Match` every few seconds; a 304 costs nothing, a
  200 rebuilds the plot.
- **Libraries.** `paper-plugin.yml`'s library loader gives ARTIE's dependencies their own classloader, which should
  avoid clashes with what the server bundles.
- **Generic first.** Every act is a menu item and a dialog. The domain look (option C above) comes later, and with it
  any mapping of world actions onto acts (a seed placed on farmland is the plant act).
- **A later treat.** A villager named ARTIE that takes an intent in chat and acts through the reasoner.

## First slice, read-only

Generic only: one plot and sign per resource the garden's entry point lists; a right-click on the sign opens the chest
menu of that resource's acts, the unavailable ones greyed with their reasons. No dialogs, no acts sent and no domain
look yet.

## What the plugin needs from Paper

Commands, events (a right-click on a block), inventory menus, the schedulers (sync and async) and placing blocks. The
author's Paper course covers these; the plugin can wait until it has.
