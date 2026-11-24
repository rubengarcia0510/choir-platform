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

- [ ] Hay pruebas unitarias y de integración backend para los casos relevantes.
- [ ] Se cubren movimientos permitidos y rechazados.
- [ ] Se verifican permisos y aislamiento por `choirId`.
- [ ] Se comprueban persistencia y rollback sin escrituras parciales.
- [ ] Se cubren proximidad, movilidad y visibilidad sin depender de un optimizador automático.
- [ ] Se verifican los límites arquitectónicos indicados en Jira.
- [ ] Las pruebas pasan en los perfiles de base de datos soportados, incluyendo PostgreSQL 16.
- [ ] El workflow de GitHub Actions finaliza con éxito en todos los checks.
