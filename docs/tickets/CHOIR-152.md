# CHOIR-152 — Exponer restricciones de visibilidad mediante API

## Alcance

API REST para gestionar restricciones de visibilidad de una formación. Reutiliza el modelo
`FormationVisibilityConstraint`, el puerto `FormationVisibilityConstraintPort` y el adapter de persistencia
existentes, sin cambios de esquema ni de SQL. No implementa geometría de visibilidad ni integra la visibilidad
en el guardado de posiciones.

## Componentes

- `FormationVisibilityConstraintService` (`application.usecase`): implementa `FormationVisibilityConstraintUseCase`.
  - `list()` autoriza con `FormationOccurrenceContextAuthorizer.authorize(...)`: valida la propiedad de la ocurrencia
    y que `formationId` pertenezca a ella, antes de consultar el puerto.
  - `create()`, `update()` y `delete()` autorizan con `FormationOccurrenceAuthorizationPort.requireOwnedOccurrence`,
    que valida solo la propiedad de la ocurrencia. La pertenencia de la formación en esas operaciones la comprueba
    el adapter de persistencia, no el autorizador de contexto completo.
- `FormationVisibilityConstraintController` (`adapter.in.web`): expone los endpoints. Toma la identidad del
  director de `Authentication`, no del payload.
- `FormationPlanningExceptionHandler`: el controller se agrega a `assignableTypes`. No cambia el manejo de los
  controllers existentes.

## Endpoints

Base: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/visibility-constraints`

| Método | Ruta | Request | Respuesta exitosa |
|---|---|---|---|
| GET | `/` | — | `200` con una lista de restricciones |
| POST | `/` | `{"protectedSingerId": 6}` | `201` con la restricción creada |
| PUT | `/{constraintId}` | `{"active": false}` | `200` con la restricción actualizada |
| DELETE | `/{constraintId}` | — | `204` sin cuerpo |

Respuesta de una restricción:

```json
{
  "id": 5,
  "formationId": 4,
  "protectedSingerId": 6,
  "active": true,
  "createdAt": "2026-11-25T10:00:00"
}
```

## Códigos de respuesta

| Situación | Código |
|---|---|
| Operación exitosa | `200`, `201` o `204` según el método |
| Sin autenticación | `401` |
| Director sin ocurrencia propia (otro coro o concierto ajeno) | `404`, por la autorización compartida del proyecto |
| Formación fuera del contexto indicado | `404` (`FormationNotFoundException`) |
| Restricción inexistente o fuera del contexto (PUT, DELETE) | `404` |
| Cantante protegido inexistente, inactivo o no asignado a la obra (POST) | `400` |
| Payload inválido (campos nulos) | `400` por validación de Spring |

## Reglas de autorización

- La identidad del director sale de `Authentication.getName()`. El email nunca viaja en el request.
- GET valida la propiedad de la ocurrencia y que `formationId` pertenezca a esa ocurrencia, mediante
  `FormationOccurrenceContextAuthorizer.authorize(...)`.
- Si la formación no pertenece a la ocurrencia, GET responde `404` (`FormationNotFoundException`) y no se consulta
  el puerto de restricciones.
- POST, PUT y DELETE validan la propiedad de la ocurrencia con `requireOwnedOccurrence(email, choirId, concertId,
  repertoireItemId)`. Estas operaciones no usan el autorizador de contexto completo.
- El adapter de persistencia filtra cada `UPDATE`, `DELETE` y `SELECT` por el contexto completo: coro, concierto,
  repertorio y formación. Una restricción de otra formación no se lee ni se modifica.

## Tests

- `FormationVisibilityConstraintServiceTests` (Mockito y `StepVerifier`, 9 casos):
  - `list()`: contexto completo; formación fuera de la ocurrencia produce `FormationNotFoundException` sin consultar
    restricciones; director sin ocurrencia propia no llega al puerto; la consulta ocurre solo después de la autorización.
  - `create()`: autorización antes de delegar; director no autorizado no llega al puerto; los errores del puerto llegan intactos.
  - `update()`: pasa todos los identificadores de contexto y el flag `active`.
  - `delete()`: el `false` del puerto se propaga.
- `FormationVisibilityConstraintControllerMappingTests` (caso de uso mockeado, 7 casos): delegación con la identidad de
  `Authentication`; `201` en create; `400` para cantante rechazado; `404` en update con restricción inexistente;
  `204` y `404` en delete; `FormationNotFoundException` se propaga desde list para que el handler responda `404`.
- `FormationVisibilityConstraintControllerTests` (Spring, 4 casos): sin autenticación, GET, POST, PUT y DELETE
  responden `401`.
- Cobertura ya existente en `FormationVisibilityConstraintPersistenceAdapterTests` (12 casos), que los requisitos del
  ticket reutilizan: contexto cruzado en update y delete, listado aislado por contexto, formación que no coincide,
  cantante inactivo y no asignado.

## Validación

**Versión validada:** commit `d5a7eb3` (`fix(CHOIR-152): validate formation membership in visibility constraint list`),
publicado en `feature/CHOIR-23-artistic-constraints` sobre `e084dd4`.

- Gradle (H2): 783 pruebas, 10 omitidas, 0 fallas, 0 errores.
- GitHub Actions: workflow `CI` en `success` tanto en `push` como en `pull_request` sobre `d5a7eb3`.
  - `test` (H2): `success`.
  - `test-prod` (PostgreSQL 16): `success`.
- Checks del PR #115: 4 exitosos, 0 fallidos, 0 pendientes.

Historial de validación: la versión `7596608` (API de visibilidad sin la corrección de pertenencia en GET) se validó con
779 pruebas, 10 omitidas, 0 fallas y 0 errores, y CI `success` en `test` y `test-prod`.

## Limitaciones funcionales

- La restricción no define todavía la regla de visibilidad: no hay orientación escénica, dirección de visión ni
  geometría de obstrucción. Crear una restricción no produce ningún resultado de evaluación.
- Solo se puede cambiar `active`; no se modifica el cantante protegido. Para cambiarlo hay que eliminar y crear.
- Se permiten varias restricciones para el mismo cantante hasta que exista la regla de obstrucción, según la
  prueba de persistencia existente.
- Un cantante protegido sin posición en el borrador puede registrarse; la evaluación posterior la reportará como no
  evaluable, nunca como cumplimiento.
- La validación de payloads (campos nulos) se garantiza con `@Valid` de Spring, pero no hay prueba HTTP autenticada
  que la verifique en esta entrega.
- No existe un test HTTP autenticado de extremo a extremo que verifique el `404` de GET para una formación ajena o
  inexistente. El caso está cubierto a nivel de servicio (`FormationVisibilityConstraintServiceTests` verifica la
  excepción y que el puerto no se consulta), de propagación del controller (`FormationVisibilityConstraintControllerMappingTests`)
  y del mapeo del exception handler, que ya asocia `FormationNotFoundException` con `404`. Es una cobertura por capas,
  no una falla de implementación; un test de integración autenticado queda como tarea separada.
