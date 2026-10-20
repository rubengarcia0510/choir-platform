ALTER TABLE formations
    ADD COLUMN concert_id BIGINT;

ALTER TABLE formations
    ADD COLUMN repertoire_item_id BIGINT;

ALTER TABLE formations
    ADD CONSTRAINT ck_formation_repertoire_association_complete
    CHECK (
        (concert_id IS NULL AND repertoire_item_id IS NULL)
        OR
        (concert_id IS NOT NULL AND repertoire_item_id IS NOT NULL)
    );

ALTER TABLE formations
    ADD CONSTRAINT fk_formation_concert_repertoire
    FOREIGN KEY (concert_id, repertoire_item_id)
    REFERENCES concert_repertoire (concert_id, repertoire_item_id);

ALTER TABLE formations
    ADD CONSTRAINT fk_formation_concert_choir
    FOREIGN KEY (concert_id, choir_id)
    REFERENCES concerts (id, choir_id);

ALTER TABLE formations
    ADD CONSTRAINT fk_formation_repertoire_choir
    FOREIGN KEY (repertoire_item_id, choir_id)
    REFERENCES repertoire_items (id, choir_id);

CREATE INDEX idx_formations_concert_repertoire
    ON formations (concert_id, repertoire_item_id);
