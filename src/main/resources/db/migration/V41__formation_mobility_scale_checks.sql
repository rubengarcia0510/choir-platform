-- CHOIR-86: escala de movilidad 0..100 (inclusive).
-- Agrega CHECK de rango sobre los cuatro límites de la región. No modifica
-- datos existentes ni migraciones anteriores. Si existieran filas fuera de
-- rango, la migración falla y no se convierten valores: se resuelve manualmente.
ALTER TABLE formation_mobility_constraints
    ADD CONSTRAINT ck_mobility_min_x_scale CHECK (min_x BETWEEN 0 AND 100);

ALTER TABLE formation_mobility_constraints
    ADD CONSTRAINT ck_mobility_max_x_scale CHECK (max_x BETWEEN 0 AND 100);

ALTER TABLE formation_mobility_constraints
    ADD CONSTRAINT ck_mobility_min_y_scale CHECK (min_y BETWEEN 0 AND 100);

ALTER TABLE formation_mobility_constraints
    ADD CONSTRAINT ck_mobility_max_y_scale CHECK (max_y BETWEEN 0 AND 100);
