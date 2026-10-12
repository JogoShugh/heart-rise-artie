-- V4__built_before_first_event.sql
--
-- A bed has no past from before it was built (decided 2026-10-12), so its
-- created_at must not come after its first event. Seed beds were given
-- events dated days before the row itself was inserted; move each such
-- bed's created_at back to its first event.

UPDATE garden_bed b
SET created_at = e.first_at
FROM (
    SELECT stream_id, min(occurred_at) AS first_at
    FROM heart_event
    WHERE stream = 'garden-bed'
    GROUP BY stream_id
) e
WHERE e.stream_id = b.id AND e.first_at < b.created_at;
