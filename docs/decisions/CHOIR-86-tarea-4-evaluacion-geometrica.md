# CHOIR-86 — Tarea 4: evaluación geométrica de restricciones de movilidad

Evaluación de solo lectura de las restricciones de movilidad activas contra el
borrador de una formación. No modifica posiciones, snapshots ni restricciones.

Estado: implementada en `c9da007`. Pendiente: CHOIR-87 (validadores) y un
contrato HTTP de evaluación, si se requiere.

## Decisiones aplicadas

1. **Versión evaluada:** solo el borrador (`DRAFT`), leído con
   `FormationCatalogPort.findDraftForOccurrence`.
2. **Coordenadas nulas:** `NOT_EVALUABLE` con motivo `MISSING_COORDINATES`. Nunca
   cumplimiento ni incumplimiento. En la base las coordenadas son `NOT NULL`; el
   caso solo puede aparecer en el dominio.
3. **Coreuta con restricción activa y sin posición en el borrador:** `NOT_EVALUABLE`
   con motivo `MISSING_DRAFT_POSITION`.
4. **Coreuta con más de una posición en el borrador:** `NOT_EVALUABLE` con motivo
   `AMBIGUOUS_DRAFT_POSITION`. El borrador no tiene unicidad por coreuta en base,
   así que no se elige una posición arbitraria.
5. **Un hallazgo por posición y restricción**, con todos los límites incumplidos
   (`X_BELOW_MIN`, `X_ABOVE_MAX`, `Y_BELOW_MIN`, `Y_ABOVE_MAX`).
6. **Estado explícito:** `EVALUABLE` solo cuando no hay restricciones no
   evaluables. Una lista vacía de hallazgos con `NOT_EVALUABLE` no es un
   cumplimiento. El invariante se valida en `MobilityEvaluation`.
7. **Límites inclusivos**, en la escala 0 a 100 (validada en la región), sin coordenada Z.
8. **Solo restricciones activas.** Las inactivas no producen hallazgos ni entradas
   no evaluables.
9. **Asociación por `singerId`.** Solo se consideran posiciones del borrador de
   esta formación.
10. **Autorización compartida:** `FormationOccurrenceContextAuthorizer` autoriza la
    ocurrencia y la pertenencia de la formación. Lo usan la gestión de movilidad y
    la evaluación. Preserva el comportamiento anterior.

## Alcance excluido

- Visibilidad, geometría de obstrucciones, orientación escénica y relación
  protegido/obstructor.
- Endpoints HTTP de evaluación (no pedidos en esta tarea).
- Reutilización de `scene` (`SceneRuleEvaluator`) o de hallazgos de `transition`.
- Composite de validadores (CHOIR-87).

## Limitaciones

- No hay endpoint: la evaluación se consume desde el caso de uso hasta que se
  defina su contrato HTTP.
- El caso de borrador inexistente se modela como lista vacía de posiciones: cada
  restricción activa queda `NOT_EVALUABLE`.
