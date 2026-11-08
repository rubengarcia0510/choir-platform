# CHOIR-88 — Probar persistencia, permisos y validación de restricciones

- **Estado:** ⏳ Pendiente (Fase 1 — Motor de Restricciones)
- **Fuente de verdad:** [CHOIR-88 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-88)
- **Dependencias técnicas previas:** CHOIR-85, CHOIR-86, CHOIR-87
- **Desbloquea:** CHOIR-23, una vez completadas sus otras dependencias.
- **Frontend consumidor:** N/A (Suite integral de backend)

---

## 1. Objetivo y Alcance

Garantizar la **integridad funcional, transaccional y de seguridad** de todo el motor de restricciones espaciales y bloqueos desarrollado en la Fase 1:
1. Bloqueo, desbloqueo y rechazo de movimientos sobre posiciones bloqueadas (CHOIR-84 y CHOIR-87).
2. Restricciones de proximidad, movilidad y visibilidad (CHOIR-85 y CHOIR-86).
3. Autorización multitenant: comprobar que un director no puede ver ni modificar restricciones de formaciones de otro coro.
4. Pruebas concurrentes en las bases de datos configuradas por el repositorio, comprobando atomicidad y ausencia de escrituras parciales. No exigir H2 si el proyecto no cuenta con un driver/perfil R2DBC H2 soportado.

---

## 2. Instrucciones de Implementación

1. **Pruebas de Arquitectura (ArchUnit):**
   * Verificar que las clases de validación de restricciones no dependan de R2DBC, Spring Web ni librerías de persistencia.
2. **Pruebas de Integración Web (`@WebFluxTest` / `WebTestClient`):**
   * Comprobar códigos HTTP:
     * `200 / 201` para solicitudes válidas.
     * `400` para cuerpos malformados o parámetros inválidos.
     * `403 Forbidden` al consultar o alterar restricciones pertenecientes a otro director o coro.
     * `409 Conflict` cuando el payload viola una restricción espacial o bloqueo.
3. **Pruebas de Persistencia y Transaccionalidad (R2DBC con PostgreSQL 16 y los perfiles adicionales que el repositorio soporte explícitamente):**
   * Simular fallo en una de las restricciones durante un guardado múltiple y verificar que la base de datos hace rollback completo, sin guardar posiciones parciales ni corromper el borrador.

---

## 3. Criterios de Aceptación

- [ ] Las pruebas requeridas pasan en los perfiles de base de datos soportados por el repositorio, incluyendo PostgreSQL 16.
- [ ] No quedan excepciones no controladas (`500 Internal Server Error`).
- [ ] Se verifica explícitamente el aislamiento por `choirId`.
- [ ] Se comprueba la inmutabilidad de versiones históricas frente a modificaciones en restricciones de borradores nuevos.
- [ ] El workflow de GitHub Actions finaliza con éxito en todos los checks.
