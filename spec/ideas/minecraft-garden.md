# Idea: the garden as a walkable Minecraft world, from a Paper plugin

**Status:** idea only, parked 2026-10-30. Raised by the author while learning Paper plugins in Kotlin with their son.
Nothing is built. Not to be started before the author asks.

## In one sentence

A Paper server plugin runs ARTIE's library, reads a HEART garden, and builds it as a place to walk around: a plot
per bed, a sign with its name, and, on a right-click, a menu of exactly the acts the bed offers now, the unavailable
ones greyed with their reasons.

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

## How the answers map onto the game

| HEART | In Minecraft |
|---|---|
| Entry point | A fenced area, one plot per bed listed |
| A bed's state | Farmland sized from the bed's members, crops for its plantings, a sign with its name |
| A link | A path or a gate to a neighbouring area; following it is walking there |
| A resource's acts | A chest menu, one item per act: offered in colour, unavailable as a grey barrier with the reason in its lore |
| An act's fields | A Minecraft dialog (1.21.6, exposed by Paper from about 1.21.7): text, toggle, single choice and number-range inputs, from the JSON Schema |
| A list | A chest menu as a grid, with arrow items for the list's next and previous links |
| 422 | The message beside the field, the dialog kept open |
| 412 | The plot rebuilt from a fresh read, and "this changed since you looked" |
| 201 with Location | The new plot built, and the player pointed at it |

## Shape of the plugin

- **Server side only.** A Paper plugin; any unmodified Java client joins, and every player sees one garden. Two players
  acting on one bed meet the ETag rule for real.
- **Network off the tick thread.** Reads and acts run on Paper's async scheduler; block changes go back to the main
  thread.
- **Staying current.** Each loaded plot is revalidated with `If-None-Match` every few seconds; a 304 costs nothing, a
  200 rebuilds the plot.
- **Libraries.** `paper-plugin.yml`'s library loader gives ARTIE's dependencies their own classloader, which should
  avoid clashes with what the server bundles.
- **Generic first.** Every act is a menu item and a dialog. Mapping world actions onto acts (a seed placed on farmland
  is the plant act) needs view hints, the same open question as in the HTML dialect, and comes later.
- **A later treat.** A villager named ARTIE that takes an intent in chat and acts through the reasoner.

## First slice, read-only

One plot and sign per bed from the garden's entry point; a right-click on the sign opens the chest menu of the bed's
acts, the unavailable ones greyed with their reasons. No dialogs and no acts sent yet.

## What the plugin needs from Paper

Commands, events (a right-click on a block), inventory menus, the schedulers (sync and async) and placing blocks. The
author's Paper course covers these; the plugin can wait until it has.
