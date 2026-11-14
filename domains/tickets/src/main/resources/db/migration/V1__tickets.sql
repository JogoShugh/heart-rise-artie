-- Support tickets. A ticket's number and subject are kept here, so tickets
-- can be found by subject; everything that happens to a ticket, its opening
-- included, is an event in heart_event, in the stream ('ticket', id). A
-- ticket's row and its opening are written in one transaction. The heart_*
-- tables are heart-eventsourcing's schema.sql.

CREATE TABLE ticket (
    id       BIGSERIAL PRIMARY KEY,
    subject  TEXT NOT NULL
);

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
