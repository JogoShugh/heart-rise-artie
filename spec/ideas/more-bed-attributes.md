# Idea: more about a bed when it is built

**Status:** idea only, 2026-10-12. Nothing here is decided or built. Raised
after `garden:build-bed` (2122e9d), which takes a name and a width and
length in feet.

## Candidates

Each would be a field of `garden:build-bed`'s schema, fixed once the bed is
built, so it would sit on the bed's identity row beside its name and size.

- **Sun:** `full`, `partial` or `shade`. The strongest candidate, because it
  gives the garden a second natural `rule-refused` case: `garden:sow-seed`
  could refuse a crop that needs full sun in a shaded bed. No schema can say
  that, since the seed's schema is the same for every bed.
- **Kind:** `raised`, `in-ground` or `container`.
- **Soil:** `loam`, `clay` or `sandy`. It could change how fast water becomes
  moisture.
- **Depth:** in inches, for a raised bed or a container.
- **Irrigation:** `drip`, `hand` or `none`.
- **Location:** a free-text note, such as "north fence".

## What each would take

- A field with JSON Schema annotations in `BuildBedRequest`, an enum where the
  values are fixed.
- A column in `garden_bed` (a new migration) and in the bed's representation.
- Seed values in `reset-db.sh`.
- A rule, if any, in `GardenBedService`, with a scenario in `garden.feature`.
