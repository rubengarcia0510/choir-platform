# CHOIR-105 — Verificar migraciones y compatibilidad de datos existentes

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-105 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-105).
- Dependencia: CHOIR-104.

## Objetivo

Validar que el esquema y los datos históricos soporten versionado, selección
explícita, auditoría y referencias de transición sin perder información.

## Alcance

- Revisar migraciones Flyway y el estado del esquema hasta la última migración.
- No editar ni renombrar migraciones aplicadas; agregar una migración nueva por
  cada cambio requerido.
- Revisar claves foráneas, nulabilidad, restricciones e integridad entre coro,
  concierto, obra, formación y versión.
- Identificar datos históricos incompletos y decidir su tratamiento
  explícitamente; no inventar selecciones ni snapshots para backfill.
- Probar una base vacía y una actualización desde un esquema/dataset anterior
  representativo.

## Criterios de aceptación

- Flyway aplica todas las migraciones desde cero sin errores.
- Actualizar esquema existente conserva formaciones, versiones publicadas y
  transiciones históricas.
- Las restricciones evitan seleccionar versiones ajenas a la combinación
  correcta.
- No hay backfill silencioso ni datos históricos ficticios.
- Los checksums de migraciones aplicadas permanecen intactos.
- Pruebas de migración e integración pasan.
- Las limitaciones de datos antiguos quedan documentadas.
