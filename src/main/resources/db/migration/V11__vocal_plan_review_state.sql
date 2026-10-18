-- CHOIR-63: persistir la revisión del plan sin modificar sus divisiones
-- ni las asignaciones de coreutas.

ALTER TABLE vocal_plans
    ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE vocal_plans
    ADD COLUMN review_reason VARCHAR(2000);

ALTER TABLE vocal_plans
    ADD COLUMN validated_population_signature VARCHAR(64);

ALTER TABLE vocal_plans
    ADD COLUMN last_validated_at TIMESTAMP;
