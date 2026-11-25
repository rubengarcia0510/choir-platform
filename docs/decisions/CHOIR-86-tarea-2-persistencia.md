# CHOIR-86 — Tarea 2: persistencia de restricciones físicas

Complementa `CHOIR-86-tarea-1-modelo-dominio.md`. Registra la política de
duplicados, las claves foráneas verificadas y el estado de validación.

## Política de duplicados

**Movilidad: una restricción por coreuta y formación.** El ticket CHOIR-86 asigna
a un coreuta específico un requerimiento de ubicación, y modela
`MobilityConstraint` con un único `singerId`. La migración `V40` agrega
`UNIQUE (formation_id, singer_id)`. Un segundo alta para el mismo coreuta en la
misma formación falla con `DataIntegrityViolationException` en el adapter; el servicio
la traduce a `PhysicalConstraintConflictException` (HTTP 409). La misma formación
admite restricciones para otros coreutas, y un coreuta puede tener restricciones
en otras formaciones.

**Visibilidad: sin unicidad todavía.** El ticket define la visibilidad como una
regla relativa entre dos integrantes: el coreuta cuya visibilidad se protege y,
si aplica, el coreuta que puede obstruirla. El modelo de la Tarea 1 solo tiene el
coreuta protegido. Una unicidad por protegido impediría reglas válidas, y una
unicidad por par requiere la columna del obstructor, que no está definida en
la Tarea 1 y cambiaría ese modelo. Queda pendiente de definir la relación antes de
agregar cualquier restricción.

## Claves foráneas verificadas

- `formations(id, choir_id)`: `uq_formation_id_choir UNIQUE (id, choir_id)`,
  creada en `V2`. No la elimina ni la altera ninguna migración posterior.
- `singers(id, choir_id)`: `uq_singer_id_choir UNIQUE (id, choir_id)`, creada en
  `V2`. Ninguna migración posterior la elimina.
- `V5`, `V14`, `V18`, `V26` y `V38` agregan columnas o constraints a `singers` y
  `formations`, pero no reemplazan esas claves. No se requiere migración de FK.

## Estado de validación

- **H2 (`./gradlew test`):** pasó sobre `d1f83bb` y las correcciones posteriores.
  Persistencia de movilidad: 13 pruebas, incluidas las de contexto y unicidad.
- **PostgreSQL 16 (job `test-prod` de CI, perfil `prod`):** se ejecuta sobre cada
  push del PR #112. No está declarado validado en este documento hasta que el job
  esté en verde sobre el commit vigente.
- Las migraciones `V39` y `V40` (FK, `CHECK` y precisión decimal) se ejecutan en
  ambos motores mediante la suite completa.

## Pendientes

1. Definir la relación de visibilidad (protegido y obstructor) y su restricción
   de unicidad. Visibilidad sigue sin implementar.
2. Resuelto: la escala 0 a 100 está aprobada y aplicada en el dominio y en la base
   (V41). Ver `CHOIR-86-coordenadas.md`.

Resuelto: la capa de servicio traduce la violación de unicidad de movilidad a
`PhysicalConstraintConflictException` (HTTP 409), sin exponer detalles de
persistencia. Ver `CHOIR-86-tarea-3-servicios-api.md`.
