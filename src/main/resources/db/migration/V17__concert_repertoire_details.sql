ALTER TABLE concert_repertoire
    ADD COLUMN duration_minutes INTEGER;

ALTER TABLE concert_repertoire
    ADD COLUMN stage_requirements VARCHAR(1000);

ALTER TABLE concert_repertoire
    ADD CONSTRAINT ck_concert_repertoire_duration
    CHECK (duration_minutes IS NULL OR duration_minutes > 0);
