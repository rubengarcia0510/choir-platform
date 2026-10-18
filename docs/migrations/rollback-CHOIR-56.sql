-- CHOIR-56: rollback manual de las migraciones V5 a V11.
--
-- ADVERTENCIA:
-- 1. Realizar y verificar un backup antes de ejecutar este script.
-- 2. Este rollback elimina datos de planes, divisiones, asignaciones,
--    ajustes y snapshots vocales agregados por estas migraciones.
-- 3. No reconstruye datos históricos ni modifica el historial de Flyway.
-- 4. Ejecutar solo como procedimiento de recuperación planificado.
-- 5. Validar primero en una copia de la base de datos de destino.
--
-- Ejecutar en PostgreSQL como una única transacción.
-- No ejecutar si existen funcionalidades posteriores que dependan
-- de las tablas o columnas eliminadas.

BEGIN;

-- V11: estado de revisión de los planes.
ALTER TABLE vocal_plans
    DROP COLUMN IF EXISTS last_validated_at,
    DROP COLUMN IF EXISTS validated_population_signature,
    DROP COLUMN IF EXISTS review_reason,
    DROP COLUMN IF EXISTS review_required;

-- V10: asignaciones independientes por obra.
DROP TABLE IF EXISTS work_vocal_assignments;

ALTER TABLE vocal_plans
    DROP CONSTRAINT IF EXISTS uq_vocal_plan_id_work_choir;

-- V9: snapshots históricos de asignaciones por obra.
DROP TABLE IF EXISTS concert_vocal_assignment_snapshots;

-- V8: datos vocales añadidos a snapshots y posiciones.
ALTER TABLE formation_positions
    DROP CONSTRAINT IF EXISTS ck_formation_position_division;

ALTER TABLE formation_positions
    DROP COLUMN IF EXISTS division_number_snapshot,
    DROP COLUMN IF EXISTS voice_section_name_snapshot,
    DROP COLUMN IF EXISTS voice_section_id_snapshot;

ALTER TABLE concert_cast_snapshots
    DROP CONSTRAINT IF EXISTS ck_cast_snapshot_division;

ALTER TABLE concert_cast_snapshots
    DROP COLUMN IF EXISTS division_number_snapshot,
    DROP COLUMN IF EXISTS voice_section_name_snapshot,
    DROP COLUMN IF EXISTS voice_section_id_snapshot;

-- V7: asignaciones y ajustes específicos de concierto.
DROP TABLE IF EXISTS concert_vocal_assignments;
DROP TABLE IF EXISTS concert_vocal_adjustments;

ALTER TABLE vocal_plan_divisions
    DROP CONSTRAINT IF EXISTS uq_vocal_division_id_choir;

ALTER TABLE concerts
    DROP CONSTRAINT IF EXISTS uq_concert_id_choir;

-- V6: planes y divisiones vocales.
DROP TABLE IF EXISTS vocal_plan_divisions;
DROP TABLE IF EXISTS vocal_plans;

-- V5: secciones vocales y vínculo del coreuta con su sección.
ALTER TABLE singers
    DROP CONSTRAINT IF EXISTS fk_singer_vocal_section_same_choir;

ALTER TABLE singers
    DROP COLUMN IF EXISTS voice_section_id;

DROP TABLE IF EXISTS vocal_sections;

COMMIT;
