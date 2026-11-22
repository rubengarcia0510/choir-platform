# CHOIR-86 — Tarea 2: persistencia de restricciones físicas

Complementa `CHOIR-86-tarea-1-modelo-dominio.md`. Registra la política de
duplicados, las claves foráneas verificadas y el estado de validación.

## Política de duplicados

**Movilidad: una restricción por coreuta y formación.** El ticket CHOIR-86 asigna
a un coreuta específico un requerimiento de ubicación, y modela
`MobilityConstraint` con un único `singerId`. La migración `V40` agrega
`UNIQUE (formation_id, singer_id)`. Un segundo alta para el mismo coreuta en la
misma formación falla con `DataIntegrityViolationException`. La misma formación
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

- **H2 (`./gradlew test`, perfil por defecto):** ejecutado por el responsable,
  pendiente de la salida con los conteos de esta tarea.
- **PostgreSQL 16 (job `test-prod` de CI, perfil `prod`):** se ejecuta con
  `./gradlew test` contra PostgreSQL al abrir o actualizar el PR. No validado
  localmente.
- Las pruebas de integridad de `V39` y `V40` (FK, CHECK y precisión decimal) se
  ejecutan en ambos motores mediante la suite completa.

## Pendientes

1. Definir la relación de visibilidad (protegido y obstructor) y su restricción
   de unicidad.
2. Confirmar si las coordenadas normalizadas de 0 a 100 del ticket son el rango
   del escenario. Los adapters no lo asumen.
3. La capa de servicio debe traducir la violación de unicidad de movilidad en un
   error de negocio y no exponer `DataIntegrityViolationException`.
