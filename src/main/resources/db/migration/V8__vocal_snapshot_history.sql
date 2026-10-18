-- Los snapshots anteriores no almacenaban sección ni división por contexto.
-- No se reconstruyen desde la ficha actual del cantante: eso inventaría historia.
-- No se agrega FK a secciones vocales mutables.
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
