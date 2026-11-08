# CHOIR-120 — Implementar evaluación de secuencias temporales

**Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-120
**Repositorio:** https://github.com/rubengarcia0510/choir-platform

## Objetivo

Implementar la evaluación funcional de reglas MOVEMENT con
OrderingMode.SEQUENTIAL. No entregar únicamente un análisis o propuesta.

## Clases existentes y modificaciones obligatorias

### 1. SceneRuleEvaluator

Archivo:
`src/main/java/com/rubengarcia/choir/scene/domain/SceneRuleEvaluator.java`

Modificar `evaluate(Input input)`:

- Eliminar el retorno incondicional TEMPORAL_SEQUENCE_UNAVAILABLE
  para MOVEMENT + SEQUENTIAL.
- Incorporar la evaluación de secuencias temporales explícitas.
- Mantener la evaluación espacial existente para los demás casos.
- No utilizar posiciones FROM/TO como evidencia temporal.

Incorporar un método privado:

`evaluateSequentialMovement(Input input, SceneRuleCondition condition)`

Debe:
- Validar que existan pasos temporales.
- Validar que estén completos y ordenados.
- Comprobar que los participantes correspondan a la regla.
- Devolver SATISFIED para una secuencia válida.
- Devolver VIOLATED para una secuencia completa que incumpla la regla.
- Devolver NOT_EVALUABLE cuando falten datos o sean inconsistentes.

Modificar el record `Input` para incluir la información temporal explícita.
Actualizar todos sus constructores y usos de producción y tests.

### 2. SceneRuleCondition

Archivo:
`src/main/java/com/rubengarcia/choir/scene/domain/SceneRuleCondition.java`

Modificar el record para representar explícitamente la secuencia esperada
de movimientos de una regla.

Agregar un modelo inmutable de paso esperado que identifique:
- Orden del paso.
- Participantes involucrados.
- Acción o tipo de movimiento esperado.

Validar datos incompletos y órdenes inválidos.

Preservar los campos y comportamientos actuales de las condiciones espaciales
y de salida.

### 3. SceneRuleRevisionSnapshot

Archivo:
`src/main/java/com/rubengarcia/choir/scene/domain/SceneRuleRevisionSnapshot.java`

Asegurar que la secuencia esperada forme parte del snapshot inmutable
de la revisión evaluada.

No reconstruir la definición histórica a partir de la regla actualmente activa.

### 4. TransitionPlanningServiceImpl

Archivo:
`src/main/java/com/rubengarcia/choir/transition/application/usecase/TransitionPlanningServiceImpl.java`

Modificar:

- `evaluateTransitionVersion(long transitionId, long transitionVersionId)`
- `evaluateTransitionVersion(long transitionId, long transitionVersionId, List<CountGroupAssignment> assignments)`
- `evaluateAndSave(...)`

Incorporar la recuperación de los pasos temporales asociados a la versión
de transición.

Antes de evaluar:
- Verificar transitionVersionId.
- Verificar fromFormationVersionId.
- Verificar toFormationVersionId.
- Rechazar datos pertenecientes a otra transición o versiones.

En `evaluateAndSave(...)`, construir el Input con los pasos temporales
y conservar la persistencia de TransitionRuleEvaluationSnapshot.

No introducir SQL en este servicio.

### 5. Contratos y persistencia nuevos

Crear:

`src/main/java/com/rubengarcia/choir/transition/domain/TransitionMovementStep.java`

Representar como mínimo:
- transitionVersionId.
- fromFormationVersionId.
- toFormationVersionId.
- Identificador del participante.
- Número de secuencia.
- Referencia temporal explícita.
- Acción o tipo de movimiento.

Crear:

`src/main/java/com/rubengarcia/choir/transition/application/port/out/TransitionMovementStepRepository.java`

Contrato:

`Flux<TransitionMovementStep> findByTransitionVersionId(long transitionVersionId);`

Crear el adapter:

`src/main/java/com/rubengarcia/choir/transition/adapter/out/persistence/R2dbcTransitionMovementStepRepository.java`

Implementar la lectura y persistencia de pasos temporales con SQL
exclusivamente en el adapter.

Crear la migración Flyway correspondiente con claves foráneas,
restricciones de integridad e índices.

La captura de movimientos debe integrarse al flujo de aplicación existente.
Si no existe un endpoint adecuado, agregar el contrato HTTP y su caso de uso.
No generar pasos artificiales ni inferirlos desde posiciones estáticas.

### 6. Tests del evaluador

Archivo existente:

`src/test/java/com/rubengarcia/choir/scene/domain/SceneRuleEvaluatorSequentialOrderingTests.java`

Reemplazar `sequentialMovementRemainsNotEvaluableWithoutTemporalData()`
y agregar pruebas para:

- Secuencia válida: SATISFIED.
- Secuencia completa incumplida: VIOLATED.
- Pasos ausentes: NOT_EVALUABLE.
- Pasos incompletos: NOT_EVALUABLE.
- Participantes desconocidos.
- Pasos duplicados.
- Orden temporal ambiguo.
- Datos de otra transición o versión.
- No inferir secuencia desde posiciones.

Mantener y verificar:

`sequentialCastDivisionEvaluatesItsSpatialConstraintWithoutTemporalData()`

### 7. Tests de integración

Agregar pruebas para:

- Capturar y persistir pasos temporales.
- Recuperarlos por transitionVersionId.
- Verificar la asociación con las versiones FROM y TO.
- Transportarlos hasta SceneRuleEvaluator.
- Guardar el resultado de evaluación.
- Consultar la evidencia histórica.
- Verificar que modificaciones posteriores no alteren publicaciones anteriores.
- Rollback ante fallos de persistencia.

Validar H2 y PostgreSQL.

## Reglas de arquitectura

- Mantener arquitectura hexagonal.
- Lógica de evaluación en dominio.
- Orquestación en aplicación.
- SQL exclusivamente en adapters.
- No introducir dependencias de infraestructura en dominio.
- Mantener compatibilidad con el comportamiento actual de EXIT y CAST_DIVISION.
- No permitir publicar evaluaciones NOT_EVALUABLE.
- No modificar evidencia histórica ya publicada.

## Documentación

Actualizar:

`docs/transition-version-publication-contract.md`

Documentar captura, persistencia, asociación a versiones, evaluación
y preservación de evidencia temporal.

## Criterios de aceptación

- Una secuencia completa válida produce SATISFIED.
- Una secuencia completa incumplida produce VIOLATED.
- Datos ausentes o incompletos producen NOT_EVALUABLE con advertencia.
- No se infiere temporalidad desde posiciones.
- Se utilizan las versiones concretas de la transición.
- Las publicaciones históricas conservan su evidencia original.
- Tests unitarios e integración pasan.
- H2 y PostgreSQL quedan validados.
- Documentación actualizada.

## Entrega

Implementar todos los cambios, ejecutar las pruebas, corregir los fallos,
crear commit, subir la rama y abrir un PR contra main.

No detenerse después de investigar ni entregar solamente una propuesta.
