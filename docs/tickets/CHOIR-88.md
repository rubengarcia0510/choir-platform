# CHOIR-88 — Probar persistencia, permisos y validación de restricciones

- **Estado:** Pendiente (Fase 1 — Motor de Restricciones)
- **Fuente de verdad:** [CHOIR-88 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-88)
- **Dependencias técnicas previas:** CHOIR-85, CHOIR-86, CHOIR-87
- **Desbloquea:** CHOIR-114, según el vínculo de Jira.
- **Frontend consumidor:** N/A. Las pruebas Angular pertenecen a CHOIR-114.

---

## 1. Objetivo y alcance

Verificar mediante pruebas backend la persistencia, autorización e integridad de bloqueos y restricciones de formación.

Cubrir:
1. Bloqueo, desbloqueo y movimientos permitidos/rechazados sobre posiciones bloqueadas (CHOIR-84 y CHOIR-87).
2. Restricciones de proximidad, movilidad y visibilidad (CHOIR-85 y CHOIR-86).
3. Autorización del director y aislamiento multitenant por `choirId`.
4. Persistencia y ausencia de efectos parciales ante errores, incluido rollback de reemplazos múltiples.
5. Contratos HTTP y casos de uso backend.

Quedan fuera las pruebas de componentes y servicios Angular (CHOIR-114). Las pruebas no deben depender de un optimizador automático todavía inexistente.

## 2. Estado de cobertura revisado

No duplicar las coberturas que ya existen:

- `FormationCatalogPersistenceAdapterTests`: bloqueo/desbloqueo idempotente, rechazo de movimientos sobre posiciones bloqueadas antes de escribir, preservación de bloqueos y validación de proximidad con rechazo atómico.
- `FormationDraftControllerTests`: autenticación y asociación incorrecta entre coro, concierto y repertorio; todavía no acredita por sí sola el contrato HTTP real de un movimiento rechazado con 409.
- `FormationDraftAtomicReplacementIntegrationTests`: rechazo cuando un coreuta posterior no está asignado y preservación de las posiciones originales.
- `FormationDraftForeignChoirAuthorizationIntegrationTests`: aislamiento de borradores entre coros.
- `FormationVisibilityEvaluationServiceTests` y `VisibilityEvaluatorTests`: evaluación de visibilidad, autorización y escenarios no evaluables.
- `FormationVersionSnapshotImmutabilityTests` y pruebas de ciclo/selección de versiones: cobertura existente de snapshots e historial; revisar sus asserts antes de agregar casos.
- Existen pruebas unitarias para servicios y validadores de movilidad, proximidad, bloqueos y composición.

## 3. Plan de implementación

### Paso 1 — Contrato HTTP de rechazo

Archivo: `src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationDraftControllerTests.java`.

- Agregar un caso de integración con director autenticado y datos propios del coro.
- Enviar un reemplazo de posiciones que viole una restricción activa o intente mover una posición bloqueada.
- Verificar HTTP `409 Conflict`, estructura ProblemDetail y colección `violations` con el identificador/código de la violación esperada.
- Verificar que la respuesta no sea un `500` y que las posiciones persistidas sigan iguales.
- No duplicar pruebas de autenticación ya existentes.

### Paso 2 — Atomicidad y rollback de persistencia

Archivos a revisar/modificar según el hueco confirmado:
- `src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationDraftAtomicReplacementIntegrationTests.java`.
- `src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapterTests.java`.

- Añadir un caso donde falle una operación durante el guardado múltiple, después de que haya comenzado el reemplazo.
- Comprobar que el rollback conserva todas las posiciones originales y no deja filas parciales ni altera el borrador.
- Reutilizar fixtures y limpieza existentes.
- No duplicar el escenario ya cubierto de coreuta sin asignación.
- La concurrencia no es un criterio independiente de Jira: solo agregar una prueba concurrente si resulta necesaria para demostrar atomicidad o si se descubre un riesgo concreto en la implementación.

### Paso 3 — Visibilidad y movilidad

Archivos:
- `src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVisibilityEvaluationServiceTests.java`.
- Pruebas existentes de `FormationMobilityEvaluationService` y sus validadores.

- Contrastar los casos actuales con los criterios de Jira: restricciones de visibilidad y movilidad, permisos, movimientos permitidos y rechazados.
- Agregar únicamente escenarios backend faltantes.
- No modificar ni reabrir la composición del composite de CHOIR-85/86/87; la integración de visibilidad en el guardado quedó fuera de CHOIR-87.

### Paso 4 — Límites arquitectónicos

- `build.gradle`: ArchUnit no está declarado actualmente. Incorporar una dependencia exclusivamente de test solo porque Jira exige explícitamente estas verificaciones y no existe una alternativa equivalente ya disponible; mantenerla acotada al classpath de pruebas.
- Crear `src/test/java/com/rubengarcia/choir/architecture/FormationArchitectureTests.java`.
- Verificar que:
  - `formation.domain` no dependa de `transition` ni de adapters.
  - Los validadores y modelos de dominio no dependan de Spring Web, R2DBC ni clases de persistencia.
  - Las integraciones entre dominios no importen adapters, repositorios concretos ni entidades de persistencia de otro dominio.
  - La composición de validadores de CHOIR-85/86/87 permanezca dentro de `formation`.
- Mantener las reglas acotadas a dependencias arquitectónicas reales del repositorio, evitando falsos positivos por clases de prueba o configuración.

### Paso 5 — Validación

- Ejecutar primero las pruebas focalizadas de cada clase modificada.
- Ejecutar la suite backend completa.
- Validar los perfiles de base de datos que el repositorio soporte explícitamente, incluyendo PostgreSQL 16.
- Confirmar que no haya errores HTTP 500 inesperados y que GitHub Actions termine con todos los checks verdes.

## 4. Criterios de aceptación

- [x] Hay pruebas unitarias y de integración backend para los casos relevantes.
- [x] Se cubren movimientos permitidos y rechazados.
- [x] Se verifican permisos y aislamiento por `choirId`.
- [x] Se comprueban persistencia y rollback sin escrituras parciales.
- [x] Se cubren proximidad, movilidad y visibilidad sin depender de un optimizador automático.
- [x] Se verifican los límites arquitectónicos indicados en Jira.
- [x] Las pruebas pasan en los perfiles de base de datos soportados, incluyendo PostgreSQL 16.
- [x] El workflow de GitHub Actions finaliza con éxito en todos los checks.

---

## Paso 2 — atomicidad y rollback del reemplazo de posiciones

**Escenario cubierto.** Un reemplazo de dos posiciones sobre una versión DRAFT con dos posiciones
persistidas. El repositorio real ejecuta el borrado de todas las posiciones de la versión dentro de
la transacción R2DBC de `replaceDraftPositions()`. La inserción que sigue (`saveAll`) falla con un
error simulado.

**Clase y método.** `FormationCatalogPersistenceAdapterTests.failureAfterDeletingDraftPositionsRollsBackAllChanges()`.
El fallo se inyecta con un `BeanPostProcessor` de test (`PositionRepositoryFaultConfiguration`) que
envuelve `FormationPositionRepository` en un proxy: delega todas las llamadas en el repositorio real y
solo convierte `saveAll` en error mientras el test lo activa. El borrado no está simulado: la prueba
registra el orden de ejecución: el borrado completa antes de que `saveAll` se suscriba.

**Garantía que demuestra.** Si la inserción falla después de que el borrado comenzó, la transacción
revierte también el borrado: las posiciones persistidas conservan sus filas, ids y valores exactos, y
no quedan duplicados ni eliminaciones parciales.

**Cobertura existente y su alcance.** Los casos de proximidad y de cantante inactivo en la misma clase
prueban que una validación fallida no escribe nada, porque corre antes del borrado. No prueban que un
error de base durante la escritura revierta el borrado; ese es el criterio que cubre esta prueba.

**Validaciones.**
- Prueba aislada (`failureAfterDeletingDraftPositionsRollsBackAllChanges`): aprobada.
- Clase `FormationCatalogPersistenceAdapterTests`: aprobada.
- Suite completa de Gradle: 748 pruebas, 10 omitidas, 0 fallas, 0 errores.
- PostgreSQL: pendiente de validación por el job de CI.

---

## Paso 3 — visibilidad y movilidad

**Cobertura existente revisada.** Movilidad: validador y evaluador puros, servicios con stubs, y
`FormationMobilityEvaluationIntegrationTests` con adapters reales (permitidos, rechazados, no
evaluables, aislamiento por formación y solo lectura). Visibilidad: evaluador puro, servicio con stubs
(`FormationVisibilityEvaluationServiceTests`) y persistencia de restricciones y roster.

**Hueco detectado.** No existía ninguna prueba de integración de la evaluación de visibilidad con los
adapters reales. El criterio de movimientos permitidos y rechazados, y el de permisos por `choirId`, solo
estaban verificados con stubs para la visibilidad.

**Clase agregada.** `FormationVisibilityEvaluationIntegrationTests`, con 7 casos sobre restricciones,
roster, borrador y autorización reales:
- `reportsOnlyObstructionsThatBreakTheVisibilityRule`: un obstructor adelante, dentro de la tolerancia
  horizontal y más alto es el único hallazgo; uno fuera de alcance, otro detrás, y otro más bajo no lo son.
- `protectedSingerWithoutDraftPositionIsNotEvaluable`: el protegido asignado sin posición produce
  `NOT_EVALUABLE` con `MISSING_PROTECTED_DRAFT_POSITION`, nunca cumplimiento.
- `singerOutsideTheAssignedRosterCannotObstruct`: un cantante sin asignación no genera hallazgos; su posición se reporta como `NOT_EVALUABLE` con `POSITION_NOT_IN_ASSIGNED_ROSTER`, nunca como cumplimiento.
- `constraintsOfAnotherFormationAreNotConsidered`: una restricción de otra formación no se evalúa.
- `inactiveVisibilityConstraintIsIgnored`: una restricción inactiva no genera hallazgos.
- `directorOfAnotherChoirCannotEvaluateVisibility`: un director de otro coro recibe `404 NOT_FOUND`
  de la autorización real, sin evaluar.
- `evaluationNeverModifiesPositionsOrConstraints`: posiciones y restricciones quedan iguales.

**Fuera de alcance de este paso.** La integración de visibilidad en el guardado de posiciones queda
fuera de CHOIR-87, por lo que no se prueba aquí. No se modificó código de producción.

**Validaciones.**
- Clase `FormationVisibilityEvaluationIntegrationTests`: aprobada en Gradle (H2) después de corregir la
  expectativa del caso fuera del roster.
- Suite completa de Gradle (H2): 755 pruebas, 10 omitidas, 0 fallas, 0 errores, en corrida limpia.
- Suite con PostgreSQL 16: pendiente de CI.

**Limitación de estabilidad.** Durante la verificación hubo timeouts intermitentes en pruebas de clases
no relacionadas con visibilidad (`ChoirControllerTests`, `FormationDraftAtomicReplacementIntegrationTests`
y `FormationDraftNullPositionsIntegrationTests`). Una corrida de control sin la clase nueva pasó limpia, y
la corrida final con la clase también. No se confirmó una causa; queda como riesgo de estabilidad a
vigilar en CI, no como defecto atribuido a este cambio.

---

## Paso 4 — límites arquitectónicos

**Decisión sobre ArchUnit.** No se agregó la dependencia. ArchUnit no está declarado en `build.gradle` y
no hay pruebas arquitectónicas previas. Jira pide no incorporar dependencias nuevas sin justificarlas y usar
ArchUnit solo si está disponible. Las reglas se verifican con `FormationArchitectureTests`, un escáner de
fuentes que usa solo JDK y JUnit y analiza los imports declarados de `src/main/java`.
*Limitación:* no resuelve referencias totalmente calificadas dentro del cuerpo de las clases ni
dependencias de bytecode.

**Reglas verificadas.**
- `formation.domain` no depende de `transition` ni de adapters.
- `formation.domain` no depende de Spring Web, HTTP, R2DBC, JDBC ni Spring Data.
- `formation.application` depende de puertos: no importa adapters ni infraestructura R2DBC/JDBC, con la
  excepción conocida descrita abajo.
- Los controladores (`formation.adapter.in`) no importan adapters de persistencia ni repositorios.
- El SQL aparece solo en adapters: no hay sentencias en `domain`, `application` ni controladores.
- Las dependencias entre `formation` y `transition` no usan adapters, repositorios ni entidades en ninguna
  dirección.
- Los validadores de composición (CHOIR-85/86/87) se usan solo dentro de `formation`.

**Defecto de producción detectado (no corregido).** `FormationMobilityConstraintService.isUniqueViolation`
(`formation.application.usecase`, CHOIR-86) importa `io.r2dbc.spi.R2dbcException` y compara
`getSqlState()` para detectar la violación de unicidad. Es un detalle de persistencia en la capa de
aplicación. El paso 4 prohíbe modificar código de producción, así que el caso queda registrado como excepción
en `KNOWN_APPLICATION_INFRASTRUCTURE`. La prueba falla si la excepción deja de ser necesaria, para obligar a
quitarla cuando el defecto se corrija.

**Clases cubiertas.** `formation.domain` (35 fuentes), `formation.application`, `formation.adapter.in`,
`formation.adapter.out` y las dependencias cruzadas con `transition`.

**Validaciones.**
- `FormationArchitectureTests` en Gradle (H2): 8 pruebas, 0 omitidas, 0 fallas, 0 errores.
- Verificación negativa: en una copia temporal se introdujeron cuatro violaciones (web en el dominio,
  repositorio en un controlador, SQL en aplicación y adapter de `transition` en el dominio). Fallaron las cinco
  pruebas correspondientes, y el repositorio real no se modificó.
- Suite completa de Gradle (H2): 763 pruebas, 10 omitidas, 0 fallas, 0 errores.
- PostgreSQL 16: la prueba no depende del motor; se ejecuta en CI junto con la suite, pendiente de validación.

---

## Paso 1 — contrato HTTP 409 de rechazo

**Clase y método.** `FormationDraftControllerTests.authenticatedDirectorReceivesConflictWhenDraftReplacementViolatesProximity()`.
Director autenticado, `PUT` real sobre el endpoint de posiciones de una versión DRAFT, con una restricción de
proximidad activa que el reemplazo incumple. Verifica HTTP 409, `ProblemDetail` (`status`, `title`, `detail`),
la colección `violations` con un elemento `PROXIMITY_VIOLATION` con `constraintId`, `singerOneId`, `singerTwoId`
y `message`, y que las posiciones persistidas no cambian.

**Cobertura HTTP complementaria.** `FormationPlanningExceptionHandlerTests` (7), `FormationConstraintViolationPresenterTests` (7)
y `FormationConstraintViolationExceptionTests` (4).

## Paso 5 — validación final

**SHA validado:** `54b76b9`, con descendencia limpia desde `c309fbd`. No hay cambios en `src/main` acumulados en CHOIR-88.

**Pruebas focalizadas (Gradle, H2).** Una invocación por grupo, todas con `BUILD SUCCESSFUL`:
1. Contrato HTTP 409: `FormationDraftControllerTests`, `FormationPlanningExceptionHandlerTests`,
   `FormationConstraintViolationPresenterTests`, `FormationConstraintViolationExceptionTests`,
   `FormationMobilityConstraintControllerTests`.
2. Rollback y persistencia: `FormationCatalogPersistenceAdapterTests`, `FormationDraftAtomicReplacementIntegrationTests`.
3. Visibilidad y movilidad: `FormationVisibilityEvaluationIntegrationTests`, `FormationMobilityEvaluationIntegrationTests`,
   `FormationVisibilityConstraintPersistenceAdapterTests`, `FormationMobilityConstraintPersistenceAdapterTests`,
   `FormationVisibilityEvaluationServiceTests`.
4. Arquitectura: `FormationArchitectureTests`, 8 pruebas, 0 omitidas, 0 fallas, 0 errores.

*Limitación de esta corrida:* el conteo por clase de los grupos 1 a 3 no se capturó; el resultado de cada grupo es `BUILD SUCCESSFUL`.

**Suite completa (Gradle, H2).** 763 pruebas, 10 omitidas, 0 fallas, 0 errores. Ningún XML contiene `<failure>` ni `<error>`.

**CI en GitHub.** Dos ejecuciones del workflow `CI` sobre `54b76b9` (push y pull_request), ambas `success`.
Los checks del PR #114 muestran 4 checks exitosos y ninguno pendiente ni fallido: `test` (H2) y `test-prod`
(perfil de producción con PostgreSQL 16), cada uno en push y pull_request.

**Criterios de aceptación.** Marcados en la sección 4 con evidencia en los pasos 1 a 5. Alcance de cada uno:
- Movimientos permitidos y rechazados: cubierto para escrituras (bloqueo, proximidad, movilidad y no evaluable). Los rechazos de visibilidad son de evaluación, no de guardado: la visibilidad no está integrada al guardado, fuera de CHOIR-87.
- Permisos y aislamiento por `choirId`: cubierto, incluido el 404 de un director de otro coro en la evaluación de visibilidad.
- Límites arquitectónicos: verificados por imports declarados de `src/main/java`. No se usó ArchUnit; la limitación y la excepción conocida están documentadas en el paso 4.

**Limitaciones y deuda técnica de CHOIR-86.**
- `FormationMobilityConstraintService.isUniqueViolation` lee el SQLSTATE de un `io.r2dbc.spi.R2dbcException` en la capa de aplicación.
  *Impacto:* acoplamiento de la aplicación a un tipo de infraestructura. Un cambio de driver o de SQLSTATE podría convertir un duplicado en un error genérico en lugar de 409.
  *Estado actual:* sin efecto sobre el comportamiento; H2 y PostgreSQL devuelven 23505. Registrado como excepción conocida en `KNOWN_APPLICATION_INFRASTRUCTURE`. No corregido en CHOIR-88.
- Timeouts intermitentes observados en corridas previas, en clases no relacionadas con visibilidad (`ChoirControllerTests`, `FormationDraftAtomicReplacementIntegrationTests`, `FormationDraftNullPositionsIntegrationTests`). Las corridas finales pasaron limpias; no hay causa confirmada.
- Verificación arquitectónica por imports declarados; no analiza referencias calificadas dentro del cuerpo de las clases ni bytecode.

**Estado del paso 5.** Validación ejecutada y documentada. Su cierre queda pendiente de autorización de revisión.
