-- Datos vocales históricos: no referenciar secciones mutables mediante FK.
ALTER TABLE concert_cast_snapshots ADD COLUMN voice_section_id_snapshot BIGINT;
ALTER TABLE concert_cast_snapshots ADD COLUMN voice_section_name_snapshot VARCHAR(80);
ALTER TABLE concert_cast_snapshots ADD COLUMN division_number_snapshot INTEGER;
ALTER TABLE concert_cast_snapshots ADD CONSTRAINT ck_cast_snapshot_division
    CHECK (division_number_snapshot IS NULL OR division_number_snapshot > 0);

ALTER TABLE formation_positions ADD COLUMN voice_section_id_snapshot BIGINT;
ALTER TABLE formation_positions ADD COLUMN voice_section_name_snapshot VARCHAR(80);
ALTER TABLE formation_positions ADD COLUMN division_number_snapshot INTEGER;
ALTER TABLE formation_positions ADD CONSTRAINT ck_formation_position_division
    CHECK (division_number_snapshot IS NULL OR division_number_snapshot > 0);

-- Completar sección histórica cuando la ficha y su sección aún existen.
UPDATE concert_cast_snapshots
SET voice_section_id_snapshot = (
        SELECT s.voice_section_id FROM singers s
        WHERE s.id = concert_cast_snapshots.source_singer_id
    ),
    voice_section_name_snapshot = (
        SELECT vs.name FROM singers s
        JOIN vocal_sections vs ON vs.id = s.voice_section_id
        WHERE s.id = concert_cast_snapshots.source_singer_id
    )
WHERE source_singer_id IS NOT NULL
  AND EXISTS (
        SELECT 1 FROM singers s
        WHERE s.id = concert_cast_snapshots.source_singer_id
          AND s.voice_section_id IS NOT NULL
    );

UPDATE formation_positions
SET voice_section_id_snapshot = (
        SELECT s.voice_section_id FROM singers s
        WHERE s.id = formation_positions.singer_id
    ),
    voice_section_name_snapshot = (
        SELECT vs.name FROM singers s
        JOIN vocal_sections vs ON vs.id = s.voice_section_id
        WHERE s.id = formation_positions.singer_id
    )
WHERE singer_id IS NOT NULL
  AND EXISTS (
        SELECT 1 FROM singers s
        WHERE s.id = formation_positions.singer_id
          AND s.voice_section_id IS NOT NULL
    );
-- No inferir divisiones anteriores: antes no se capturaban por contexto.
