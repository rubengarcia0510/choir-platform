# CHOIR-86 — Tarea 3: servicios de aplicación y API REST de movilidad

Complementa las decisiones de las tareas 1 y 2. Esta tarea implementa movilidad.
La visibilidad queda fuera hasta definir su relación.

## Alcance implementado

- `FormationMobilityConstraintService`: alta, listado, modificación y baja de
  restricciones de movilidad.
- `FormationMobilityConstraintController`, con la base del contrato del ticket
  `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/constraints/physical`:
  - `GET` lista las restricciones **activas** de la formación.
  - `POST /mobility` crea (201).
  - `PUT /{constraintId}` modifica (200; 404 si no existe en el contexto).
  - `DELETE /{constraintId}` elimina (204; 404 si no existe en el contexto).
- `PhysicalConstraintConflictException` para una segunda restricción de movilidad
  del mismo coreuta en la formación. Se traduce a HTTP 409 sin detalles SQL.

## Autorización de contexto

Cada operación exige dos comprobaciones, en este orden, antes de tocar el puerto
de persistencia:

1. `FormationOccurrenceAuthorizationPort.requireOwnedOccurrence`: el director es
   dueño de la ocurrencia (coro, concierto y elemento de repertorio). Si no lo
   es, responde 404, igual que el resto del módulo.
2. `FormationCatalogPort.findForOccurrence`: la formación pertenece a esa
   ocurrencia. Si no, `FormationNotFoundException` (404).

La propiedad de la ocurrencia no implica la pertenencia de la formación; por eso
se verifican ambas.

## Decisiones aprobadas

- **Listado de activas.** Se devuelven solo restricciones activas, como indica
  el ticket. El filtro se aplica en el servicio, sobre el resultado del puerto.
- **Operaciones de actualización.** `PUT` se agrega porque la tarea lo pide. El
  ticket no lo define; el patrón es el del controlador de proximidad.
- **Duplicados.** La unicidad `(formation_id, singer_id)` de `V40` se traduce a
  409 solo para la violación de unicidad (SQLSTATE `23505`). Otros errores de
  integridad, como un `CHECK` de rango, no se convierten en conflicto.

## Visibilidad: dependencia pendiente

No hay endpoints ni servicios de visibilidad. El ticket define la visibilidad
como una relación entre un integrante protegido y un obstructor. El modelo actual
(`FormationVisibilityConstraint`) solo representa al protegido, así que exponerlo
contradiría el contrato.

Para retomarla hace falta definir:

1. Cómo se modela el obstructor (columna o tabla de relación) y su unicidad.
2. La orientación escénica y la regla geométrica de obstrucción.
3. El tratamiento de altura ausente (`heightCmSnapshot`).

Hasta entonces, la tabla y el puerto de visibilidad de la tarea 1 y 2 se conservan
sin exponer.

## Pendientes y contradicciones con el ticket

- El ticket no define `PUT`; se agregó siguiendo proximidad.
- `GET` lista solo movilidad en la base `/constraints/physical`, como indica el
  ticket para `GET /`. Cuando exista visibilidad, el listado combinado se definirá
  sin inventar un modelo nuevo.
- Escala 0 a 100 aprobada. Dominio y base validan los límites en [0, 100]; ver
  `CHOIR-86-coordenadas.md`.
- La validación geométrica de movimientos y el composite de CHOIR-87 quedan fuera.
