# Idea: the Garden, square-foot gardening on HEART

**Status:** idea only, saved 2026-11-07. Drafted as a plan the same day, then kept as an idea at the author's
word. Nothing is built. Not to be started before the author asks.

A HEART domain for people who grow food in square-foot beds: a backyard or
front-yard gardener alone, or a community garden group. Organic by default; veganic is an option a garden or a
bed sets. Decisions marked **D?** are the author's; each carries a recommendation.

## Where things stand

- **StarbornAG** (`~/repos/starbornes`, the author's own garden app) already models this: a `Bed` is a grid of
  square-foot cells, and each `BedCell` is the aggregate root with its own event stream (stream id = cell id).
  Its care actions are plant, water, fertilize, mulch and harvest; its soil rules (`BedCell.targets`) say
  planting needs an empty cell and a harvest applies only where that plant grows. It has HAL Schema Forms
  work and SSE (`SseBedEventPublisher`). It is not built on the HEART library. This idea borrows its model
  and vocabulary and leaves that repository untouched.
- **The garden-bed service** (`server-spring`, `com.artie.gardenbed`) is HEART's first small example: the
  whole bed is the aggregate, with one crop, moisture and growth points, on `PostgresEventStore`. It has no
  cells, no care schedule and no growing standard. This idea is a separate domain beside it (**D?**).
- **The tickets domain** (`domains/tickets`) is the shape to copy: Spring Boot, `heart-spring-boot-starter`,
  `heart-eventsourcing` with `PostgresEventStore`, Flyway, Testcontainers, no controllers, its own feature
  file plus the HEART conformance features.
- **HEART's ETag hashes the rendered representation** (`StateETag.of`), so when time alone changes which acts
  are offered, the ETag changes with no event.
- **Offers** (`Declaration.kt`) are keyed by a state key the type lists up front; `whyAbsent` takes the key
  and the relation, not the state. Reasons are shared per key, so a reason can't carry one cell's date.
- **The caller** (`Caller.kt`) is the request's credential, nothing more: offers that depend on who asks
  don't exist yet.
- **No SSE in HEART.** Clients revalidate with `If-None-Match`; a 304 costs nothing.
- **The web view** lays nested state out as titled pairs and tables (`HtmlValues.kt`, 255bb518).

## Who it is for

| Who | Wants |
|---|---|
| Backyard gardener | What needs doing today; logging with dirty hands; yields by season |
| Front-yard gardener | The same, plus a QR sign per bed for neighbours and a share box for surplus |
| Community garden | Beds with stewards, shared beds, a watering rota, a seed library, tool lending, donations |
| Coordinator | Lays the garden out, assigns beds, sets the garden's rules and frost dates |
| ARTIE | The same garden as a client: "today's chores" as a goal, walked once, kept, replayed |

## The domain

### Growing standard: organic or veganic (configuration)

- A garden sets its standard: **organic** (the default) or **veganic**. A bed may set its own, overriding
  the garden's; otherwise it inherits. The bed's **effective standard** is in its state and every cell's.
- **Organic:** no synthetic fertilizers or pesticides. Allows compost, manure-based compost, plant teas,
  bone, blood or fish meal, worm castings, mulches, row covers.
- **Veganic:** no animal-derived inputs: plant-based compost, leaf mould, green manure and cover crops,
  chop-and-drop, plant teas, seaweed, rock dust, biochar. Worm castings are a further setting (**D?**).
- Inputs carry the standards they meet. Fertilize and mulch offer only inputs that meet the effective
  standard, so a veganic bed is never offered bone meal, and nothing has to refuse it after the fact.

### Aggregates

Each has its own event stream and is one HEART resource, brought into being by a domain act, never by create.

| Aggregate | State | Acts |
|---|---|---|
| **Garden** | Name, time zone, hardiness zone, frost dates, growing standard, rules, members | Set frost dates, set standard, prepare a bed, invite a member, record rain |
| **Bed** | Name, rows × columns (feet), orientation, sun, trellis side, standard (own or inherited), stewards, resting or active | Assign or release a steward, set standard, add a trellis, rest under a cover crop, wake; care across many cells |
| **Cell** (1 ft², the root of growing) | Label (A1), current planting (crop, cultivar, count, date, stage), care schedule, soil notes, rotation history | Sow, plant (seedling), water, fertilize, weed, prune, thin, harvest, mulch, protect, observe, clear |
| **Crop and cultivar** (reference) | Family, plants per square (1, 4, 9, 16), days to maturity, sowing windows from frost dates, trellis, companions, care defaults | Curated; a community may add cultivars |
| **Seed lot** | Crop, cultivar, source, year, amount, germination test, parent lot | Test germination, take seed, save seed (a new lot from a harvest), offer for swap |
| **Seedling tray** | Cells sown indoors, stage up to ready | Sow tray, pot up, harden off, mark ready |
| **Compost bin, rain barrel** | Filling, cooking, finished; level | Add, turn, sieve; draw, refill |
| **Member, rota task, work day, tool** | Role, beds stewarded; claimed or open; sign-ups; lent or in | Invite, claim, hand back, sign up, lend, return |
| **Observation, harvest record** | Scope (cell, bed, garden), kind, photo or transcript; weight or count, destination | Observe; record where a harvest went (kitchen, share box, food bank) |

A planting's stages: sown, sprouted, growing, harvestable, spent; clear empties the cell. Harvesting doesn't
empty it (as StarbornAG).

### The care schedule

For each cell and each act: last done, every N days, due on (last done + N, calendar days in the garden's
time zone).

1. Never done, or due: the act is offered.
2. Not due: the act is absent, with a reason.
3. N is chosen when acting (`again`, 0 to 365). Left blank, it falls back to this cell's last N, then the
   crop's default (from the catalog), then the act's own default.
4. Rain recorded for the garden waters every outdoor cell; cells under cover are left out.

### Acts over many cells

A bed's acts apply one act to the cells chosen ("watered Jupiter", "mulched rows 2 and 4"). The cells are a
multiple choice from the bed's state, only those where the act is allowed and due. One bed act is one event
on each chosen cell's stream, in one transaction: all or none. The bed's ETag covers its cells' summaries,
so a stale bed is refused (412).

### Rules a schema can't say

| Rule | Reason, as said |
|---|---|
| Sow or plant needs an empty cell | "Plant: C3 already grows carrots" |
| Spacing, from the catalog | "Sow: carrots take 16 per square; 20 were named" |
| Sowing window, from frost dates | "Sow outdoors: beans wait until after the last frost, May 10" |
| Rotation (refuse or warn, **D?**) | "Plant: a nightshade grew here last season" |
| Trellis | "Plant: pole beans need the trellis on row A" |
| Harvest needs something harvestable | "Harvest: lettuce isn't ready" |
| Inputs meet the effective standard | Not refused: only meeting inputs are offered |
| Stewardship | "Plant: only Jupiter's stewards plant here" |
| Bed resting | "Sow: Saturn rests under clover until spring" |

### Roles (community)

Coordinator: everything. Steward: everything on their beds. Member: care on shared beds and claimed rota
tasks, observations, harvests. Visitor: read, share box, a bed's QR sign.

### Views

Today (what's due, overdue first), the bed map (the grid), a cell's history across seasons, the calendar
(what to sow this week), the seed library, the rota board, yields by crop, season and method, the share box.

## The HEART shape

| Resource | Address |
|---|---|
| Service bookmark | `/` (found a garden) |
| Garden | `/gardens/{garden}` |
| Bed | `/gardens/{garden}/beds/{bed}`, its cells embedded and linked, its grid |
| Cell | `/gardens/{garden}/beds/{bed}/cells/{label}` |
| Crop | `/crops/{crop}`, `/crops/{crop}/cultivars/{cultivar}` |
| Seed lot, tray, bin, barrel | `/gardens/{garden}/seeds/{lot}` and the like |
| Inputs | `/inputs{?standard}`, the inputs that meet a standard |
| Views | `/gardens/{garden}/today`, `/gardens/{garden}/calendar` |

- **Offer keys:** a cell's stage and the set of its acts due now (about 160 keys at most). Later, the
  caller's role (see below).
- **Choices from state (`hr:options`):** inputs for fertilize and mulch by link, `/inputs{?standard}` with
  `standard` from the bed's effective standard; due cells for a bed act by pointer (`/due/water`), several
  allowed; later, seed lots that passed and have seed left, ready seedlings, crops in their window.
- **Concurrency:** every act carries `If-Match`. Two people harvest one square, claim one rota task or lend
  one tool: the second is refused (412) and reads the new state.

### What the garden may ask of HEART itself

Each is its own decision; none blocks phase 1.

1. **Reasons with a resource's own words** ("Water: due on 9 Nov"). Today reasons are shared per key.
   Options: keep shared reasons and show due dates in the state (recommended for now), or let `whyAbsent`
   read the state and give up shared explanations for those acts. **D?**
2. **`hr:default`:** a pointer into the owning resource's state for a property's default, as `hr:options`
   points for choices, so the browser and dialogs prefill the remembered interval. Until then the server
   applies it when `again` is blank. **D?**
3. **Offers by caller:** a key that includes the caller's role, for stewardship and roles (phase 5).
4. **A change stream:** SSE, modelled on StarbornAG's, plus a tick when acts come due, since that happens
   with no event. Until then, revalidation.
5. **A grid in the web view:** a list of lists rendered as a grid, generic, so a bed's grid looks like the
   bed with no garden-specific HTML (phase 2).

## Architecture

- `domains/garden`: Spring Boot, `heart-spring-boot-starter`, `heart-eventsourcing` on
  `PostgresEventStore` (R2DBC), Flyway, Testcontainers; packages as tickets (`domain`, `declaration`,
  `store`, `config`). One stream per garden, bed, cell (and later lot, tray, member).
- **Clock:** injected. Tests set it ("Given it is 7 November", "When 2 days pass"). A demo setting makes a
  garden day a few real minutes (`garden.day=PT2M`).
- **Identity:** a token per member (HEART's credentials hold users and tokens); acts record who.
- **Tests:** `garden.feature` for the garden's own rules, Gherkin tables throughout; the HEART conformance
  features; the whole server on Postgres in Testcontainers; red before any `src/main` write.
- **Gate:** `.claude/bdd-gates.json` lists each domain in its lint and check commands. The author adds
  `domains/garden` beside basketball and tickets before phase 1 is committed.

## Phases

Small steps; the author picks each next one. Each starts with its feature, seen to fail.

| # | Phase | Done when |
|---|---|---|
| 1 | **Garden, bed, cell.** Found a garden, prepare a bed (its grid of cells), the cell acts with the care schedule and soil rules; crop as text; the growing standard on garden and bed, with a small fixed list of inputs | In a browser, Jupiter (4 × 8) shows its cells; watering A1 with `again` 2 makes Water absent there until two days pass; a veganic bed's fertilize offers no animal inputs; a stale act is refused |
| 2 | **Bed acts over many cells**, and the grid in the web view | "Water every due cell in Jupiter" is one act; a stale bed is refused |
| 3 | **Catalog:** crops, cultivars, families, plants per square, care defaults, frost dates and sowing windows | Spacing and windows refuse with reasons; defaults come from the crop |
| 4 | **Seed library and nursery** | Sow offers only lots that passed and have seed; plant offers ready seedlings |
| 5 | **Community:** members, roles, stewardship, rota tasks | Acts offered depend on who asks; two members claiming one task, the second is refused |
| 6 | **Journal and yields**, then compost, rain barrel, tools, share box | Today, the calendar and yields as views |
| 7 | **ARTIE:** today's chores as a goal, walked once by Claude, kept, replayed | The kept script waters every due cell with no model calls, and stops with the reason when something changed |

## Decisions for the author

1. Many gardens from day one (`/gardens/{garden}/…`)? Recommended: yes, since changing addresses later
   costs more than one path segment now.
2. Rotation: refuse or warn? Recommended: a garden setting, warn by default.
3. Worm castings under veganic: allowed, or a setting? Recommended: a setting, off.
4. How a cell empties: `clear` (spent plants to compost), with `thin` for culling seedlings? Recommended: yes.
5. Plants that span squares (a squash over 2 × 2): a planting anchored on one cell that holds its
   neighbours. Recommended: after phase 3.
6. StarbornAG: stay separate, with this service as the HEART version it could move onto later? Recommended:
   yes.
7. The HEART asks above, each when its phase needs it.
8. The garden-bed service in `server-spring`: keep it as HEART's small example beside this domain, or retire
   it once this one covers what it shows? Recommended: keep it; it stays small on purpose.

## A further idea: Minecraft

The proof of concept (`docs/reports/minecraft-poc/`) shows HEART resources as dialogs in plain Minecraft.
The garden would fit it well: one cell as one block (crop growth stage and soil moisture from the cell's
state), a bed as a patch of farmland, a compost bin as a composter, a rain barrel as a cauldron. Breaking a
ripe crop would be the harvest act, a bucket on dry soil the water act, a right-click the cell's dialog; a
gesture whose act isn't offered would be cancelled, with the reason shown. The walkable-world version, and the
question of who knows that a bed is farmland, is its own idea: `spec/ideas/minecraft-garden.md`.

## Not in scope

Voice and photo logging (StarbornAG's); machine-learned yields; weather services; Bedrock; animals.
