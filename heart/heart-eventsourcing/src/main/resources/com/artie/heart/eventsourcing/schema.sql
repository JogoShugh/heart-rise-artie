-- heart-eventsourcing: the event store's tables (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A5.8).
-- One stream per resource. The UNIQUE constraint on (stream, stream_id, sequence_no)
-- is the concurrency control: of two writers appending the same number, one wins.
-- Snapshots are a disposable shortcut: truncate them and every state still folds.
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
