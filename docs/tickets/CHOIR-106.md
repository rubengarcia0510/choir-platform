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
| Restricciones de versión y migraciones históricas | `FormationVersionIntegrityConstraintTests` y `ChoirDomainMigrationTests.auditIntegrityAndTransitionPublicationHistoryRemainImmutable`, `upgradeFromPreV18PreservesPublishedAndSelectedHistoricalData` ([integridad](../../src/test/java/com/rubengarcia/choir/FormationVersionIntegrityConstraintTests.java), [migraciones](../../src/test/java/com/rubengarcia/choir/ChoirDomainMigrationTests.java)). |
| Duplicación y selección de versión fuente | `FormationDuplicationSourceVersionSelectionTests` y `FormationDuplicationIndependenceTests` ([selección](../../src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationDuplicationSourceVersionSelectionTests.java), [independencia](../../src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationDuplicationIndependenceTests.java)). |
| Contratos HTTP y errores | `FormationVersionLifecycleControllerIntegrationTests` y `FormationVersionSelectionControllerIntegrationTests` ([lifecycle](../../src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationVersionLifecycleControllerIntegrationTests.java), [selección](../../src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationVersionSelectionControllerIntegrationTests.java)). |
| Límites arquitectónicos | Revisar que los paquetes `formation.application` y `transition.application` no accedan a JDBC/R2DBC ni contengan SQL, y que los controladores deleguen reglas de aplicación. No se encontró una suite automatizada ArchUnit en el proyecto; esta comprobación se registra como inspección estática. |

## Resultados de ejecución

- GitHub Actions [run 499](https://github.com/rubengarcia0510/choir-platform/actions/runs/41306761706): `success` para `caa43ce`, con la suite H2 y PostgreSQL del `main` que ya contiene CHOIR-105. Este run es anterior a las verificaciones adicionales de esta rama.
- La suite local completa no produjo resultado: se detuvo tras exceder el tiempo práctico de ejecución.
- La prueba de migración de esta rama y sus aserciones PostgreSQL siguen pendientes de resultado; ejecutar el run de CI de esta rama antes de considerar CHOIR-106 cerrado.
