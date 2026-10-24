# Idea: crop rotation as the garden's rule-refused example

**Status:** idea only, 2026-10-12. Nothing here is decided or built. Raised
by Roy Bott's review (finding 4: the garden never shows `rule-refused`). For
now the garden refuses over-watering instead, with harvest using up the
water; this idea is the alternative to come back to.

## The rule

`garden:sow-seed` refuses to sow the crop that was just harvested from the
same bed: "basil was the last crop in this bed; sow something else". The
answer is 422 of type `rule-refused` at `/seedType`.

## Why it fits

- It is a real gardening rule (crop rotation), not one made up for the test.
- It depends on the bed's history, so no schema can say it: `seedType`'s
  schema is the same for every bed.
- It never leaves a bed stuck. Any other seed is accepted, which is exactly
  SPEC §6.2's advice for `rule-refused`: choose different values.

## What it would take

- `GardenBedState` gains `lastCrop`, folded from the existing
  `BedHarvested.cropName`. No new event.
- `GardenBedService.sowSeed` throws `RuleRefusedException.at("/seedType", …)`.
- The ALPS description of `sow-seed` says the rule.
- Live scenarios and fixtures that sow the same crop twice on one bed change
  seed.
