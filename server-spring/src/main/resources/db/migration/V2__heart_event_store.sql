-- V2__heart_event_store.sql
--
-- The beds' events move to the HEART library's event store
-- (heart-eventsourcing; spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A5.8). Its
-- tables keep one stream per resource; a bed's stream is ('garden-bed', id).
-- garden_bed stays: it is the bed's identity and label, not derived from events.
--
-- The tables below are heart-eventsourcing's schema.sql, as the library ships it.

CREATE TABLE IF NOT EXISTS heart_event (
    id           BIGSERIAL PRIMARY KEY,
    stream       TEXT        NOT NULL,
    stream_id    BIGINT      NOT NULL,
    sequence_no  INTEGER     NOT NULL,
    event_type   TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_heart_event_sequence UNIQUE (stream, stream_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS heart_snapshot (
    stream          TEXT        NOT NULL,
    stream_id       BIGINT      NOT NULL,
    sequence_no     INTEGER     NOT NULL,
    state           JSONB       NOT NULL,
    snapshotted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (stream, stream_id)
);

-- Every event keeps its bed, number, type, payload and time. The payloads were
-- already only the event's own fields, which is what the library stores.
INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at)
SELECT 'garden-bed', garden_bed_id, sequence_no, event_type, payload, occurred_at
FROM garden_bed_event
ORDER BY garden_bed_id, sequence_no;

-- Snapshots are a disposable cache: not copied; the next one is taken at the
-- next fifth event. The old tables go, now that their rows are copied.
DROP TABLE garden_bed_snapshot;
DROP TABLE garden_bed_event;
