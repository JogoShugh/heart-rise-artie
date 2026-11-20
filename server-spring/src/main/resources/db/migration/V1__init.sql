-- V1__init.sql
--
-- Event-sourced model. garden_bed is now JUST the aggregate root's
-- identity -- it holds no state that could be derived from replaying
-- events, on purpose. garden_bed_event is the actual source of truth:
-- append-only, never updated, never deleted. Current state, and state
-- at any past point in time, are both computed by folding events --
-- see GardenBedProjector.kt. garden_bed_snapshot exists purely as a
-- performance optimization (avoid refolding a bed's entire history on
-- every read) and is, by design, disposable: truncate it entirely and
-- the system is still 100% correct, just slower until it repopulates.

CREATE TABLE garden_bed (
    id          BIGSERIAL PRIMARY KEY,
    label       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The event log. The UNIQUE constraint on (garden_bed_id, sequence_no)
-- IS the concurrency control mechanism: appending event N+1 for a bed
-- requires knowing what N currently is, and two concurrent writers who
-- both think they're appending event 6 will have exactly one of them
-- succeed -- the other's INSERT violates this constraint and fails,
-- exactly the same "someone else moved first" signal the old ETag/
-- If-Match check gave, just enforced natively by the database instead
-- of compared in application code.
CREATE TABLE garden_bed_event (
    id              BIGSERIAL PRIMARY KEY,
    garden_bed_id   BIGINT NOT NULL REFERENCES garden_bed(id) ON DELETE CASCADE,
    sequence_no     INTEGER NOT NULL,
    event_type      TEXT NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_garden_bed_event_sequence UNIQUE (garden_bed_id, sequence_no)
);

CREATE INDEX idx_garden_bed_event_bed_id ON garden_bed_event(garden_bed_id, sequence_no);

-- Disposable read-side optimization: the folded state AS OF a given
-- sequence number, so a normal "what's this bed's state right now"
-- query only has to fold events AFTER the latest snapshot, not the
-- bed's entire history since event 1. One row per bed; overwritten
-- (not appended to) as newer snapshots are taken.
CREATE TABLE garden_bed_snapshot (
    garden_bed_id   BIGINT PRIMARY KEY REFERENCES garden_bed(id) ON DELETE CASCADE,
    sequence_no     INTEGER NOT NULL,
    state           JSONB NOT NULL,
    snapshotted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed a few beds -- as pure identities only. Their state (EMPTY,
-- PLANTED, whatever) comes entirely from the events inserted below,
-- never from a column on this table.
INSERT INTO garden_bed (id, label) VALUES
    (1, 'bed-1'),
    (2, 'bed-2'),
    (3, 'bed-3');

-- Give bed-2 and bed-3 some history, so the API has something
-- interesting to show immediately -- these are exactly the kind of
-- rows the real service layer would produce via sowSeed()/waterCell(),
-- inserted directly here only because this is fixture/seed data.
INSERT INTO garden_bed_event (garden_bed_id, sequence_no, event_type, payload, occurred_at) VALUES
    (2, 1, 'SeedSown', '{"seedType": "tomato", "depthCm": 3}'::jsonb, now() - INTERVAL '5 days'),
    (2, 2, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '4 days'),
    (2, 3, 'CellWatered', '{"milliliters": 200}'::jsonb, now() - INTERVAL '2 days'),
    (3, 1, 'SeedSown', '{"seedType": "basil", "depthCm": 1}'::jsonb, now() - INTERVAL '12 days'),
    (3, 2, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '10 days'),
    (3, 3, 'CellWatered', '{"milliliters": 500}'::jsonb, now() - INTERVAL '7 days');

-- Reset the sequence for garden_bed.id past our manually-assigned seed
-- IDs, so the next bed created via the API doesn't collide with them.
SELECT setval('garden_bed_id_seq', 3, true);
