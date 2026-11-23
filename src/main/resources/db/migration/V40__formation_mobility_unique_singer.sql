-- CHOIR-86: una sola restricción de movilidad por coreuta y formación.
-- El ticket asigna a un coreuta un requerimiento de ubicación; la visibilidad
-- no se restringe aquí porque su modelo (relación protegido/obstructor) está
-- pendiente. Esta tabla se introdujo en V39 y no tiene datos publicados, por lo
-- que la restricción puede agregarse sin deduplicar.
ALTER TABLE formation_mobility_constraints
    ADD CONSTRAINT uq_formation_mobility_singer
    UNIQUE (formation_id, singer_id);
