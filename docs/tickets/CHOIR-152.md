# CHOIR-152 — Exponer restricciones de visibilidad mediante API

## Alcance

API REST para gestionar restricciones de visibilidad de una formación. Reutiliza el modelo
`FormationVisibilityConstraint`, el puerto `FormationVisibilityConstraintPort` y el adapter de persistencia
existentes, sin cambios de esquema ni de SQL. No implementa geometría de visibilidad ni integra la visibilidad
en el guardado de posiciones.

## Componentes

- `FormationVisibilityConstraintService` (`application.usecase`): implementa `FormationVisibilityConstraintUseCase`.
  Autoriza al director con `FormationOccurrenceAuthorizationPort.requireOwnedOccurrence` antes de delegar en el puerto.
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
- Toda operación pasa primero por `requireOwnedOccurrence(email, choirId, concertId, repertoireItemId)`.
- El adapter de persistencia filtra cada `UPDATE`, `DELETE` y `SELECT` por el contexto completo: coro, concierto,
  repertorio y formación. Una restricción de otra formación no se lee ni se modifica.

## Tests

- `FormationVisibilityConstraintServiceTests` (Mockito y `StepVerifier`, 6 casos): contexto completo en list, update
  y delete; autorización antes de delegar en create; un director no autorizado nunca llega al puerto; el
  `false` de delete se propaga; los errores del puerto llegan intactos.
- `FormationVisibilityConstraintControllerMappingTests` (caso de uso mockeado, 6 casos): delegación con la
  identidad de `Authentication`; `201` en create; `400` para cantante rechazado; `404` en update con restricción
  inexistente; `204` y `404` en delete.
- `FormationVisibilityConstraintControllerTests` (Spring, 4 casos): sin autenticación, GET, POST, PUT y DELETE
  responden `401`.
- Cobertura ya existente en `FormationVisibilityConstraintPersistenceAdapterTests`, que los requisitos del ticket
  reutilizan: contexto cruzado en update y delete, listado aislado por contexto, formación que no coincide,
  cantante inactivo y no asignado.

## Validación

- Gradle (H2), `./gradlew test --tests "*FormationVisibilityConstraint*"`: `BUILD SUCCESSFUL`.
  - `FormationVisibilityConstraintServiceTests`: 6/6.
  - `FormationVisibilityConstraintControllerMappingTests`: 6/6.
  - `FormationVisibilityConstraintControllerTests`: 4/4.
  - `FormationVisibilityConstraintPersistenceAdapterTests` (existente): 12/12.
- Suite completa (H2): 779 pruebas, 10 omitidas, 0 fallas, 0 errores.
- CI sobre `7596608`: ejecuciones del workflow `CI` (push y pull_request) en `success`. PR #115: 4 checks exitosos,
  incluidos `test` (H2) y `test-prod` (PostgreSQL 16).

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
