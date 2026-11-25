# CHOIR-86 — Modelar restricciones configurables de movilidad y visibilidad

- **Estado de implementación en backend:** parcial; movilidad y evaluación de visibilidad implementadas en esta rama.
- **Fuente de verdad:** [CHOIR-86 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-86)
- **Dependencia técnica previa:** CHOIR-84 (Bloqueos — Completado en PR #83)
- **Desbloquea:** CHOIR-87 (Validación centralizada) y CHOIR-88 (Suite de pruebas)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-86-FRONTEND)

---

## 1. Objetivo y alcance

Modelar y persistir restricciones físicas y espaciales del elenco dentro de una formación:

1. **Restricción de movilidad:** asignar a un coreuta específico un requerimiento de ubicación accesible (por ejemplo, primera fila, sin escalón o coordenadas de fácil acceso).
2. **Restricción de visibilidad:** evaluar si la posición de un coreuta protegido queda obstruida por otro coreuta más alto situado delante, hacia el público.

La evaluación de visibilidad implementada es una aproximación geométrica basada en coordenadas X/Y y snapshots de altura. No simula una línea visual física completa.

---

## 2. Contrato de entrada y modelo de dominio

### Modelo de dominio

* `MobilityConstraint`:
  * `singerId`: coreuta con restricción.
  * Una condición espacial determinista basada en las coordenadas normalizadas del escenario (`x`/`y` entre `0` y `100`).

* `VisibilityConstraint`:
  * Identifica al coreuta cuya visibilidad se protege.
  * Los posibles obstructores se determinan comparando las posiciones del resto de los coreutas activos asignados a la ocurrencia; no se configuran parejas manualmente.
  * La evaluación utiliza `heightCmSnapshot` de las posiciones del DRAFT, no la altura actual del perfil.

### Contrato HTTP

* Base: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/constraints/physical`
* `POST /mobility`: configura una restricción de movilidad para un coreuta.
* `GET /`: lista las restricciones de movilidad activas implementadas.
* `PUT /{constraintId}`: actualiza una restricción de movilidad.
* `DELETE /{constraintId}`: elimina una restricción.

**Visibilidad:** la gestión de restricciones de visibilidad tiene endpoint HTTP desde CHOIR-152 (`FormationVisibilityConstraintController`, base `.../formations/{formationId}/visibility-constraints`: `GET /`, `POST /`, `PUT /{constraintId}` y `DELETE /{constraintId}`). La evaluación de visibilidad todavía no está expuesta por HTTP. No asumir que `POST /visibility` ni un listado combinado están disponibles.

El frontend debe esperar a que el contrato HTTP que necesite esté implementado y mergeado antes de integrar la UI. La matriz del roadmap debe referenciar esta base (`physical`), no una ruta alternativa `/constraints/mobility`.

---

## 3. Implementación

### Movilidad

- Modelo de dominio, persistencia y migraciones.
- API REST para crear, listar, actualizar y eliminar restricciones de movilidad.
- Evaluación geométrica de solo lectura sobre el borrador de la formación.

### Altura y snapshots

- `heightCm` es un atributo opcional del perfil del coreuta.
- `heightCmSnapshot` conserva la altura utilizada al construir cada posición.
- Los snapshots se conservan al crear borradores desde versiones publicadas y al duplicar formaciones.
- Cambios posteriores en el perfil no modifican snapshots históricos.

### Evaluación de visibilidad

La regla implementada genera un hallazgo por cada combinación protegida/obstructor que cumpla:

1. `abs(X_obstructor - X_protegido) <= 2`.
2. `Y_obstructor > Y_protegido`.
3. `heightCmSnapshot_obstructor > heightCmSnapshot_protegido`.

Se evalúan todas las posiciones disponibles. Los datos requeridos ausentes producen `NOT_EVALUABLE`; no se asume cumplimiento.

La evaluación:
- Se ejecuta sobre posiciones del DRAFT.
- Usa el roster de coreutas activos asignados a la obra en el concierto.
- Incluye asignados sin posición, que producen un resultado no evaluable.
- Mantiene el SQL en el adapter de persistencia.
- No modifica `vocalAssignmentsForPlanning`.

La regla es una aproximación geométrica. No representa anchura corporal, ángulo de visión, posición de ojos ni una simulación de línea visual real.

---

## 4. Manejo de errores y mensajes estructurados

La evaluación de movilidad produce hallazgos de dominio. El código `MOBILITY_VIOLATION` de la especificación original queda para la integración de validadores de CHOIR-87; la evaluación implementada en CHOIR-86 no lo emite.

La evaluación de visibilidad informa `EVALUABLE` o `NOT_EVALUABLE`, junto con hallazgos y motivos explícitos de datos insuficientes. No se declara que una restricción se cumple cuando faltan datos necesarios.

---

## 5. Criterios de aceptación y estado

- [x] Escala de coordenadas normalizada entre 0 y 100, inclusive, y orientación documentada.
- [x] Modelo y persistencia de restricciones de movilidad.
- [x] API REST de movilidad bajo autorización del director.
- [x] Altura opcional del coreuta y snapshot histórico por posición.
- [x] Evaluación geométrica de movilidad sobre el DRAFT.
- [x] Evaluador de dominio de visibilidad y regla geométrica documentada.
- [x] Servicio de aplicación de visibilidad con autorización, posiciones DRAFT y roster de asignaciones activas.
- [x] API HTTP de gestión de restricciones de visibilidad (CHOIR-152).
- [ ] Endpoint HTTP de evaluación de visibilidad.
- [ ] Integración de las evaluaciones en el composite de CHOIR-87.
- [ ] Integración de la funcionalidad en la interfaz frontend.
- [ ] Emisión de los códigos de validación centralizados definidos para CHOIR-87.

El CI #657 terminó en SUCCESS para el commit
`8f517e239a87dfb158016830750639128bfbdaa0` de
`feature/CHOIR-86-physical-constraints`.

---

## 6. Alcance pendiente

- Exponer por HTTP el caso de uso de evaluación de visibilidad, si lo requiere el contrato definitivo.
- Integrar los validadores en CHOIR-87.
- Integrar el flujo correspondiente en el frontend una vez disponible el contrato HTTP.
- Mantener documentada la limitación de la aproximación geométrica y no presentarla como simulación física de línea visual.
