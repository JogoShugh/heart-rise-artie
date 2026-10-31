-- Youth basketball scoring. A game's identity and rosters are set up here;
-- everything that happens in a game is an event in heart_event, in the stream
-- ('game', id). The heart_* tables are heart-eventsourcing's schema.sql.

CREATE TABLE game (
    id         BIGSERIAL PRIMARY KEY,
    label      TEXT NOT NULL,
    home_name  TEXT NOT NULL,
    away_name  TEXT NOT NULL
);

CREATE TABLE player (
    game_id  BIGINT  NOT NULL REFERENCES game(id) ON DELETE CASCADE,
    side     TEXT    NOT NULL CHECK (side IN ('home', 'away')),
    number   INTEGER NOT NULL,
    name     TEXT    NOT NULL,
    PRIMARY KEY (game_id, side, number)
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
