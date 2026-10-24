-- V3__bed_size.sql
--
-- A bed is built with a size in feet (decided 2026-10-12), fixed like its
-- label, so it lives on the bed's identity row, not in its events. Beds
-- built before this are taken to be 4 by 8 feet.

ALTER TABLE garden_bed ADD COLUMN width_ft INTEGER NOT NULL DEFAULT 4;
ALTER TABLE garden_bed ADD COLUMN length_ft INTEGER NOT NULL DEFAULT 8;
ALTER TABLE garden_bed ALTER COLUMN width_ft DROP DEFAULT;
ALTER TABLE garden_bed ALTER COLUMN length_ft DROP DEFAULT;
