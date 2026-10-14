-- CHOIR-40: enforce choir ownership through composite foreign keys.
-- Backfill tenant IDs from authoritative parent rows before adding constraints.

ALTER TABLE concerts
    ADD CONSTRAINT uq_concert_id_choir UNIQUE (id, choir_id);

ALTER TABLE concert_repertoire
    ADD COLUMN choir_id BIGINT;

UPDATE concert_repertoire cr
SET choir_id = (
    SELECT c.choir_id
    FROM concerts c
    WHERE c.id = cr.concert_id
);

ALTER TABLE concert_repertoire
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE concert_repertoire
    ADD CONSTRAINT fk_cr_concert_choir
        FOREIGN KEY (concert_id, choir_id)
        REFERENCES concerts (id, choir_id);

ALTER TABLE concert_repertoire
    ADD CONSTRAINT fk_cr_item_choir
        FOREIGN KEY (repertoire_item_id, choir_id)
        REFERENCES repertoire_items (id, choir_id);


ALTER TABLE formation_versions
    ADD COLUMN choir_id BIGINT;

UPDATE formation_versions fv
SET choir_id = (
    SELECT f.choir_id
    FROM formations f
    WHERE f.id = fv.formation_id
);

ALTER TABLE formation_versions
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE formation_versions
    ADD CONSTRAINT uq_fv_id_choir UNIQUE (id, choir_id);

ALTER TABLE formation_versions
    ADD CONSTRAINT fk_fv_formation_choir
        FOREIGN KEY (formation_id, choir_id)
        REFERENCES formations (id, choir_id);


ALTER TABLE formation_positions
    ADD COLUMN choir_id BIGINT;

ALTER TABLE formation_positions
    ADD COLUMN singer_choir_id BIGINT;

UPDATE formation_positions fp
SET choir_id = (
    SELECT fv.choir_id
    FROM formation_versions fv
    WHERE fv.id = fp.formation_version_id
);

UPDATE formation_positions fp
SET singer_choir_id = (
    SELECT s.choir_id
    FROM singers s
    WHERE s.id = fp.singer_id
);

ALTER TABLE formation_positions
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE formation_positions
    ADD CONSTRAINT fk_fp_version_choir
        FOREIGN KEY (formation_version_id, choir_id)
        REFERENCES formation_versions (id, choir_id);

ALTER TABLE formation_positions
    ADD CONSTRAINT fk_fp_singer_choir
        FOREIGN KEY (singer_id, singer_choir_id)
        REFERENCES singers (id, choir_id)
        ON DELETE SET NULL;


ALTER TABLE transition_versions
    ADD COLUMN choir_id BIGINT;

UPDATE transition_versions tv
SET choir_id = (
    SELECT t.choir_id
    FROM transitions t
    WHERE t.id = tv.transition_id
);

ALTER TABLE transition_versions
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE transition_versions
    ADD CONSTRAINT fk_tv_transition_choir
        FOREIGN KEY (transition_id, choir_id)
        REFERENCES transitions (id, choir_id);

ALTER TABLE transition_versions
    ADD CONSTRAINT fk_tv_from_version_choir
        FOREIGN KEY (from_formation_version_id, choir_id)
        REFERENCES formation_versions (id, choir_id);

ALTER TABLE transition_versions
    ADD CONSTRAINT fk_tv_to_version_choir
        FOREIGN KEY (to_formation_version_id, choir_id)
        REFERENCES formation_versions (id, choir_id);


ALTER TABLE concert_cast_snapshots
    ADD COLUMN choir_id BIGINT;

ALTER TABLE concert_cast_snapshots
    ADD COLUMN source_singer_choir_id BIGINT;

UPDATE concert_cast_snapshots cs
SET choir_id = (
    SELECT c.choir_id
    FROM concerts c
    WHERE c.id = cs.concert_id
);

UPDATE concert_cast_snapshots cs
SET source_singer_choir_id = (
    SELECT s.choir_id
    FROM singers s
    WHERE s.id = cs.source_singer_id
);

ALTER TABLE concert_cast_snapshots
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE concert_cast_snapshots
    ADD CONSTRAINT fk_ccs_concert_choir
        FOREIGN KEY (concert_id, choir_id)
        REFERENCES concerts (id, choir_id);

ALTER TABLE concert_cast_snapshots
    ADD CONSTRAINT fk_ccs_singer_choir
        FOREIGN KEY (source_singer_id, source_singer_choir_id)
        REFERENCES singers (id, choir_id)
        ON DELETE SET NULL;


ALTER TABLE absences
    ADD COLUMN choir_id BIGINT;

UPDATE absences a
SET choir_id = (
    SELECT s.choir_id
    FROM singers s
    WHERE s.id = a.singer_id
);

ALTER TABLE absences
    ALTER COLUMN choir_id SET NOT NULL;

ALTER TABLE absences
    ADD CONSTRAINT fk_absence_singer_choir
        FOREIGN KEY (singer_id, choir_id)
        REFERENCES singers (id, choir_id);

ALTER TABLE absences
    ADD CONSTRAINT fk_absence_concert_choir
        FOREIGN KEY (concert_id, choir_id)
        REFERENCES concerts (id, choir_id);
