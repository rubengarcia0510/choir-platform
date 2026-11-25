ALTER TABLE singers
    ADD COLUMN height_cm INTEGER;

ALTER TABLE singers
    ADD CONSTRAINT ck_singer_height_positive
    CHECK (height_cm IS NULL OR height_cm > 0);

ALTER TABLE formation_positions
    ADD COLUMN height_cm_snapshot INTEGER;

ALTER TABLE formation_positions
    ADD CONSTRAINT ck_formation_position_height_snapshot_positive
    CHECK (height_cm_snapshot IS NULL OR height_cm_snapshot > 0);
