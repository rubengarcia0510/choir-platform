# CHOIR-86 — Tarea 5.1 y 5.2: evaluador de visibilidad

## Estado

Evaluador de dominio puro implementado en `feature/CHOIR-86-visibility-evaluation`.
**No integrado** en el composite de validación (CHOIR-87) ni expuesto por API. No
hay servicio de aplicación todavía. No declarar la visibilidad validada en pantallas
reales ni con datos de producción.

## Contrato de entrada

```
VisibilityEvaluator.evaluate(
    List<FormationVisibilityConstraint> constraints,
    List<FormationPosition> draftPositions,
    Set<Long> assignedSingerIds)
```

- `assignedSingerIds`: coreutas asignados a la ocurrencia y formación, tengan o no
  posición en el DRAFT. Es la lista de referencia; el evaluador no consulta
  repositorios.
- Solo se evalúan restricciones activas.

## Regla geométrica y altura

Una combinación (posición protegida, posición de obstructor) produce un hallazgo si:

1. `|X_obstructor - X_protegido| <= 2` (tolerancia fija, inclusiva);
2. `Y_obstructor > Y_protegido` (delante; y = 100 es el frente);
3. `heightCmSnapshot_obstructor > heightCmSnapshot_protegido` (estricto).

La altura es siempre `heightCmSnapshot` de la posición, nunca la altura del perfil.

## Múltiples posiciones

- Cada posición del protegido se evalúa por separado. Tener varias posiciones no es
  ambiguo.
- Cada posición de cada obstructor se compara con cada posición protegida válida.
- Cada combinación que cumple las tres condiciones produce un `VisibilityFinding`
  independiente.
- No se elige arbitrariamente una posición.

## Coreutas asignados sin posición

- Un coreuta asignado distinto del protegido que no tiene posición en el DRAFT produce
  `MISSING_OBSTRUCTOR_DRAFT_POSITION`. No se asume que está detrás, lejos o que no
  obstruye.
- Un protegido sin posición produce `MISSING_PROTECTED_DRAFT_POSITION`.
- Un coreuta que no está en `assignedSingerIds` no afecta la evaluación si no tiene
  posiciones.

## Posiciones de coreutas no asignados

Una posición cuyo `singerId` no está en `assignedSingerIds` no genera hallazgos. Se
registra como `POSITION_NOT_IN_ASSIGNED_ROSTER` (no evaluable), porque ese coreuta
podría obstruir y la lista de asignados es inconsistente con el DRAFT.

## Datos insuficientes

| Motivo | Cuándo |
|---|---|
| `MISSING_PROTECTED_DRAFT_POSITION` | El protegido no tiene posición. |
| `MISSING_PROTECTED_COORDINATES` | Una posición protegida no tiene X o Y. Esa posición no se compara. |
| `MISSING_PROTECTED_HEIGHT` | Una posición protegida no tiene snapshot de altura. Esa posición no se compara. |
| `MISSING_OBSTRUCTOR_DRAFT_POSITION` | Un coreuta asignado no tiene posición. |
| `MISSING_OBSTRUCTOR_COORDINATES` | Una posición de obstructor no tiene X o Y. |
| `MISSING_OBSTRUCTOR_HEIGHT` | Un obstructor dentro de la tolerancia y delante no tiene snapshot de altura. |
| `POSITION_NOT_IN_ASSIGNED_ROSTER` | Hay una posición de un coreuta no asignado. |

Un obstructor sin altura que está fuera de la tolerancia o detrás no impide completar
la evaluación. Los hallazgos válidos se conservan aunque otras comparaciones sean no
evaluables. El estado global es `NOT_EVALUABLE` si existe al menos una comparación
requerida no resuelta.

## Resultado

- `VisibilityEvaluation`: estado, hallazgos y no evaluables, con listas inmutables.
  El invariante exige `EVALUABLE` solo sin no evaluables.
- `VisibilityFinding`: identifica la restricción, los dos coreutas, las dos posiciones,
  las dos alturas y la distancia horizontal.
- No hay motivos de ambigüedad: se eliminaron porque ya no tienen caso de uso.

## Limitaciones conocidas

- La tolerancia de 2 y la regla de altura estricta son decisiones funcionales, no
  validadas con datos reales del escenario.
- No hay simulación de línea visual, geometría corporal, orientación adicional ni
  altura inferida.
- Un obstructor con varias posiciones válidas genera varios hallazgos: el número de
  hallazgos refleja combinaciones, no coreutas.
- Pendiente: servicio de aplicación (roster completo, posiciones del DRAFT,
  autorización de ocurrencia y llamada al evaluador), endpoints y la integración con
  CHOIR-87. Esta documentación no modifica el documento compuesto de CHOIR-87.

## Servicio de aplicación de visibilidad

`FormationVisibilityEvaluationService` (caso de uso `FormationVisibilityEvaluationUseCase`):

1. Autoriza el contexto con `FormationOccurrenceContextAuthorizer` (ocurrencia propia y
   formación dentro de la ocurrencia). Si falla, no lee nada más.
2. Lee las restricciones de visibilidad de la formación (`FormationVisibilityConstraintPort`).
3. Lee las posiciones del borrador de esta formación (`FormationCatalogPort.findDraftForOccurrence`).
   Si no hay borrador, propaga `FormationVersionNotFoundException`: no devuelve una evaluación
   con posiciones vacías.
4. Construye el roster con los coreutas activos asignados a la obra en el concierto, leídos de
   `concert_vocal_assignments` por `FormationVisibilityRosterPort.findActiveAssignedSingerIds`.
   No depende del estado de publicación ni de snapshots publicados.
5. Llama a `VisibilityEvaluator.evaluate(constraints, positions, roster)`.

Roster:

- Fuente: `concert_vocal_assignments` filtrada por coro, concierto y obra, unida a `singers`
  por `singer_id` y `choir_id`, solo con `singers.active = TRUE`. Devuelve IDs distintos.
- Funciona con conciertos publicados y no publicados.
- Un coreuta activo asignado sin posición DRAFT produce `MISSING_OBSTRUCTOR_DRAFT_POSITION`
  (`NOT_EVALUABLE`).
- Un coreuta inactivo no forma parte del roster. Si conserva una posición DRAFT, produce
  `POSITION_NOT_IN_ASSIGNED_ROSTER` y no genera hallazgos.
- El SQL vive solo en el adapter. `vocalAssignmentsForPlanning` no se modificó.

Estado: el servicio y sus tests (Mockito, y el test de integración del adapter de roster) están
escritos; la verificación en Gradle queda pendiente de ejecutar en el repositorio. No hay endpoint HTTP ni integración con
CHOIR-87. Visibilidad sigue sin declararse validada.
