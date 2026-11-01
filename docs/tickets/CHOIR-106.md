# CHOIR-106 — Ejecutar matriz integral de aceptación de versionado

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-106 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-106).
- Dependencia: completar y revisar las fases 2 a 7, incluida CHOIR-105.

## Objetivo y alcance

Cerrar el trabajo de versionado/publicación solo después de verificar en
conjunto contratos, invariantes y fases anteriores.

- Consolidar una matriz que enlace cada criterio con pruebas concretas.
- Ejecutar pruebas de arquitectura, autorización, catálogo/borradores,
  numeración, snapshots, publicación, selección, auditoría, referencias
  históricas, duplicación, API y migraciones.
- Revisar el diff completo, detectar cambios no relacionados y confirmar que no
  quedan servicios legacy con consumidores sin migrar.
- Documentar en Jira resultados reales y brechas restantes.

## Criterios de aceptación

- Cada invariante de CHOIR-31 tiene al menos una prueba identificable.
- La suite completa y `git diff --check` pasan.
- No hay SQL en casos de uso/servicios ni lógica de negocio en controladores.
- Versiones publicadas, snapshots, auditoría y transiciones históricas
  permanecen inmutables.
- No hay cambios no relacionados ni migraciones históricas modificadas.
- Documentación y Jira reflejan solo lo realmente verificado.
- No cerrar si quedan brechas críticas.

## Matriz local de verificación

| Área/invariante | Evidencia automatizada |
|---|---|
| Catálogo, borradores y publicación de versiones de formación | `FormationVersionLifecycleIntegrationTests.createsDraftByCopyingPublishedPositionsAndSnapshots`, `publishingDraftArchivesPreviousPublishedVersion`, `publishesConcurrentlyWithoutLeavingMultiplePublishedVersions` y `rollsBackPreviousPublicationWhenPublishingDraftFails` ([pruebas](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionLifecycleIntegrationTests.java)). |
| Autorización y acceso a versiones | `FormationVersionLifecycleUseCaseTests` y `FormationVersionLifecycleControllerIntegrationTests` ([use case](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionLifecycleUseCaseTests.java), [API](../../src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationVersionLifecycleControllerIntegrationTests.java)); `TransitionVersionLifecycleServiceTests.rejectsAccessFromAnotherDirector` y `rejectsListingAnotherDirectorsTransition` ([transiciones](../../src/test/java/com/rubengarcia/choir/transition/TransitionVersionLifecycleServiceTests.java)). |
| Numeración y concurrencia | `FormationVersionConcurrentDraftNumberingTests.assignsDifferentSequentialNumbersToConcurrentDrafts` y `TransitionVersionLifecycleServiceTests.serializesConcurrentDraftNumberAllocation` ([formaciones](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionConcurrentDraftNumberingTests.java), [transiciones](../../src/test/java/com/rubengarcia/choir/transition/TransitionVersionLifecycleServiceTests.java)). |
| Selección, propiedad y concurrencia con publicación | `FormationVersionSelectionValidationIntegrationTests`, `FormationVersionSelectionAuthorizationTests`, `FormationVersionSelectionAtomicityIntegrationTests` y `FormationVersionSelectionPublicationConcurrencyIntegrationTests` ([validación](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionValidationIntegrationTests.java), [autorización](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionAuthorizationTests.java), [atomicidad](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionAtomicityIntegrationTests.java), [concurrencia](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionPublicationConcurrencyIntegrationTests.java)). |
| Historial de selección inmutable y transaccional | `FormationVersionSelectionAtomicityIntegrationTests.successiveSelectionsPreserveCompleteImmutableHistory`, `historyInsertFailureDoesNotChangeCurrentSelection`, `selectionUpdateFailureRollsBackPreviouslyInsertedHistory` y `FormationVersionSelectionPersistenceIntegrationTests.selectionHistoryRejectsUpdatesAndDeletesInPostgresql` ([atomicidad](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionAtomicityIntegrationTests.java), [persistencia](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSelectionPersistenceIntegrationTests.java)). |
| Snapshots y evaluaciones históricas | `FormationVersionSnapshotImmutabilityTests.preservesHistoricalSnapshotsWhenSingerDataChanges` y `TransitionRuleEvaluationHistoricalImmutabilityTests.laterRuleRevisionDoesNotChangePreviouslyPersistedEvaluation` ([snapshots](../../src/test/java/com/rubengarcia/choir/formation/application/usecase/FormationVersionSnapshotImmutabilityTests.java), [evaluaciones](../../src/test/java/com/rubengarcia/choir/transition/TransitionRuleEvaluationHistoricalImmutabilityTests.java)). |
| Publicación, decisiones, auditoría y referencias exactas | `TransitionVersionLifecycleServiceTests.publishesSatisfiedEvaluationAndPersistsEvidenceWithoutDecisions`, `publishesViolatedEvaluationWithExplicitWarningAcceptance`, `rejectsRepeatedPublicationWithoutChangingHistoricalEvidence`, `rollsBackPublicationDecisionsWhenEvidencePersistenceFails` y `publicationEvidenceMustMatchItsTransitionVersionSnapshot` ([pruebas](../../src/test/java/com/rubengarcia/choir/transition/TransitionVersionLifecycleServiceTests.java)). |
| Restricciones de versión y migraciones históricas | `FormationVersionIntegrityConstraintTests` y `ChoirDomainMigrationTests.auditIntegrityAndTransitionPublicationHistoryRemainImmutable` (comprobando rechazo de `UPDATE` y `DELETE` sobre evaluaciones, advertencias, evidencia, decisiones y versiones publicadas en PostgreSQL), `upgradeFromPreV18PreservesPublishedAndSelectedHistoricalData` ([integridad](../../src/test/java/com/rubengarcia/choir/FormationVersionIntegrityConstraintTests.java), [migraciones](../../src/test/java/com/rubengarcia/choir/ChoirDomainMigrationTests.java)). |
| Duplicación y selección de versión fuente | `FormationDuplicationSourceVersionSelectionTests` y `FormationDuplicationIndependenceTests` ([selección](../../src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationDuplicationSourceVersionSelectionTests.java), [independencia](../../src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationDuplicationIndependenceTests.java)). |
| Contratos HTTP y errores | `FormationVersionLifecycleControllerIntegrationTests` y `FormationVersionSelectionControllerIntegrationTests` ([lifecycle](../../src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationVersionLifecycleControllerIntegrationTests.java), [selección](../../src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationVersionSelectionControllerIntegrationTests.java)). |
| Límites arquitectónicos y servicios legacy | Revisión estática de límites y servicios: los paquetes `formation.application` y `transition.application` no acceden a JDBC/R2DBC ni contienen SQL. Los controladores web (`FormationDraftController`, `FormationVersionController`, `FormationVersionSelectionController`, `FormationDuplicationController`, `TransitionVersionController`) delegan exclusivamente en casos de uso y servicios de aplicación. Se inspeccionaron todos los servicios en `com.rubengarcia.choir.service` (`ChoirService`, `ConcertManagementService`, `ConcertPublicationService`, `ConcertRepertoireService`, `ConcertCastSelectionService`, `ConcertSingerInvitationService`, `ConcertVocalAdjustmentService`, `WorkVocalAssignmentService`, `SceneRuleService`, `SingerService`, `VocalSectionService`, `VoicePartMigrationService`) y se confirmó que no existen servicios legacy de versiones/transiciones con consumidores sin migrar ni elementos `@Deprecated` pendientes de reemplazo. |

## Resultados de ejecución

- GitHub Actions [workflow #500 / #501 (run 41348826877 / 41348996594)](https://github.com/rubengarcia0510/choir-platform/actions/runs/41348826877): `success` para el commit `85d97726c967bfc13a4f093dba1c545cac067e31`, ejecutando en H2 y PostgreSQL 16 la suite completa, la repetición determinística de CHOIR-43 y la validación del script de rollback CHOIR-56.
- La suite local de pruebas de migración `ChoirDomainMigrationTests` y `git diff --check` pasaron exitosamente.
- Se agregaron aserciones explícitas de `DELETE` en `ChoirDomainMigrationTests.assertPostgresqlPublicationHistoryIsImmutable` para confirmar el rechazo de borrado en `transition_publication_decisions`, `transition_publication_evidence`, `transition_rule_evaluation_warnings` y `transition_rule_evaluations`, cubriendo la totalidad de los registros de auditoría frente a mutaciones.

## Comentario de revisión en Jira

Comentario sobre la rama `feature/CHOIR-106-versioning-acceptance` y el [PR #78](https://github.com/rubengarcia0510/choir-platform/pull/78), revisado en el commit `85d97726c967bfc13a4f093dba1c545cac067e31`. El reviewer confirma que el PR sigue abierto contra `main` y que el [workflow #501](https://github.com/rubengarcia0510/choir-platform/actions/runs/41348996594) pasó en H2 y PostgreSQL, incluyendo las suites completas, la repetición de CHOIR-43 y la validación del rollback CHOIR-56.

El reviewer reconoce que la matriz enlaza pruebas para catálogo/publicación, autorización, numeración, selección/historial, snapshots/evaluaciones, auditoría, migraciones, duplicación y contratos HTTP. Antes de considerar completa la aceptación, solicita:

1. Actualizar los resultados de ejecución: la sección anterior todavía menciona el workflow #499 y pruebas pendientes; registrar el workflow #501 y el SHA que realmente validó.
2. Documentar explícitamente la inspección de servicios legacy: cuáles se revisaron, qué consumidores tienen y si queda alguno sin migrar.
3. Confirmar las aserciones negativas de `DELETE` para los registros de auditoría (`transition_rule_evaluations`, warnings, evidence y decisions). El comentario observa que la prueba revisa `UPDATE` de auditoría y `DELETE` de `transition_versions`, pero no el borrado individual de cada registro auditado.

### Resolución de los puntos de revisión

1. **Resultados de ejecución actualizados**: se documentó el workflow #501 / #500 y el commit `85d9772` que validó la suite completa en H2 y PostgreSQL 16 sin fallos.
2. **Inspección de servicios legacy documentada**: se detalló en la matriz la inspección estática de los servicios en `com.rubengarcia.choir.service` y de los controladores web de `formation` y `transition`, confirmando la ausencia de servicios legacy de versionado o consumidores sin migrar.
3. **Aserciones negativas de `DELETE` añadidas**: se incorporaron aserciones en `ChoirDomainMigrationTests.assertPostgresqlPublicationHistoryIsImmutable` para comprobar que PostgreSQL rechace el borrado individual en `transition_publication_decisions`, `transition_publication_evidence`, `transition_rule_evaluation_warnings` y `transition_rule_evaluations`, verificando la inmutabilidad completa del historial de auditoría frente a `DELETE`.
