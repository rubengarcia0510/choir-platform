# CHOIR-102 — Publicación con advertencia informativa de visibilidad

## Política aprobada

`DIRECTOR_VISIBILITY_NOT_EVALUABLE` se conserva en las evaluaciones como
`NOT_EVALUABLE` y se persiste en el historial, pero se clasifica explícitamente
como `INFORMATIONAL` para la publicación. No implica que la visibilidad física
haya sido calculada o comprobada.

La clasificación `publicationImpact` es parte de cada advertencia:

- `BLOCKING` es el valor predeterminado para todas las advertencias.
- `INFORMATIONAL` solo se asigna a la advertencia específica de visibilidad del
  director.
- El estado original `NOT_EVALUABLE` y la advertencia se mantienen inmutables.
- La migración no modifica advertencias históricas: las filas existentes
  conservan el valor predeterminado `BLOCKING`. Para publicar un borrador con
  una evaluación antigua, debe generarse una nueva evaluación bajo la política
  aprobada.

La publicación se permite si las únicas advertencias no evaluables son
informativas. Cualquier resultado de regla `NOT_EVALUABLE` y toda advertencia
`NOT_EVALUABLE` con impacto `BLOCKING` sigue impidiendo publicar.
Las advertencias `VIOLATED` continúan requiriendo aceptación explícita, exacta
y única. Se rechazan referencias inexistentes, duplicadas, satisfechas o no
evaluables.

## Instrucciones de implementación y revisión

- Mantener SQL únicamente en adapters y cambiar el esquema mediante migración
  aditiva compatible con H2 y PostgreSQL.
- Preservar evaluaciones, advertencias, decisiones y evidencia histórica; no
  reconstruir snapshots desde datos actuales ni actualizar registros anteriores.
- Cubrir publicación permitida con advertencia informativa y su persistencia en
  evaluación/historial.
- Cubrir reglas obligatorias no evaluables, otras advertencias no evaluables
  bloqueantes, violaciones aceptadas, y aceptaciones inexistentes o duplicadas.
- Ejecutar la suite completa, `git diff --check` y CI local/producción (H2 y
  PostgreSQL).
- Solicitar revisión de QA. No hacer merge hasta recibir QA y autorización del
  owner; el PR debe apuntar a `feature/CHOIR-24`, nunca a `main`.
