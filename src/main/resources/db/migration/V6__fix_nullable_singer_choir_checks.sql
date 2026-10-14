-- CHOIR-47: require auxiliary choir IDs when singer references are present.
-- SQL CHECK constraints accept UNKNOWN, so the auxiliary ID must be
-- explicitly NOT NULL for a non-null singer reference.

ALTER TABLE formation_positions
    DROP CONSTRAINT ck_fp_singer_same_choir;

ALTER TABLE formation_positions
    ADD CONSTRAINT ck_fp_singer_same_choir
    CHECK (
        (singer_id IS NULL AND singer_choir_id IS NULL)
        OR (
            singer_id IS NOT NULL
            AND singer_choir_id IS NOT NULL
            AND singer_choir_id = choir_id
        )
    );

ALTER TABLE concert_cast_snapshots
    DROP CONSTRAINT ck_ccs_singer_same_choir;

ALTER TABLE concert_cast_snapshots
    ADD CONSTRAINT ck_ccs_singer_same_choir
    CHECK (
        (source_singer_id IS NULL AND source_singer_choir_id IS NULL)
        OR (
            source_singer_id IS NOT NULL
            AND source_singer_choir_id IS NOT NULL
            AND source_singer_choir_id = choir_id
        )
    );
