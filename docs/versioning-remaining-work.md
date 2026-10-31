# Trabajo pendiente del plan de versionado CHOIR-31

Este documento conserva en el repositorio el alcance de las fases pendientes
del plan de versionado de formaciones. Es una referencia de trabajo basada en
Jira al 2026-10-31; Jira sigue siendo la fuente de verdad si el alcance cambia.

## Orden y dependencias

| Orden | Ticket | Alcance | Dependencia |
|---|---|---|---|
| 1 | [CHOIR-105](https://rubengarcia0510.atlassian.net/browse/CHOIR-105) | Verificar migraciones y compatibilidad de datos existentes | CHOIR-104 |
| 2 | [CHOIR-106](https://rubengarcia0510.atlassian.net/browse/CHOIR-106) | Ejecutar la matriz integral de aceptación de versionado | Completar y revisar las fases 2 a 7; comienza después de CHOIR-105 |

Las fases anteriores del plan son CHOIR-31 y CHOIR-100 a CHOIR-104. No se
considera completada la iniciativa CHOIR-31 hasta verificar las fases
pendientes y registrar en Jira únicamente resultados comprobados.

## CHOIR-105 — Migraciones y compatibilidad

### Objetivo

Validar que el esquema y los datos históricos soporten el versionado, la
selección explícita, la auditoría y las referencias de transición sin perder
información.

### Alcance

- Revisar todas las migraciones Flyway y el estado del esquema hasta la última
  migración.
- No editar ni renombrar migraciones ya aplicadas. Si se requiere un cambio,
  agregar una migración nueva.
- Revisar claves foráneas, nulabilidad, restricciones e integridad entre coro,
  concierto, obra, formación y versión.
- Identificar datos históricos incompletos y decidir explícitamente su
  tratamiento; no inventar selecciones ni snapshots para completar datos.
- Probar tanto una base vacía como una actualización desde un esquema o dataset
  anterior representativo.

### Criterios de aceptación

- [ ] Flyway aplica todas las migraciones desde cero sin errores.
- [ ] Actualizar un esquema existente conserva formaciones, versiones
  publicadas y transiciones históricas.
- [ ] Las restricciones impiden selecciones de versiones que no pertenezcan a
  la combinación correcta.
- [ ] No hay backfill silencioso ni datos históricos ficticios.
- [ ] Los checksums de migraciones ya aplicadas permanecen intactos.
- [ ] Las pruebas de migración e integración pasan.
- [ ] Las limitaciones de datos antiguos quedan documentadas.

### Restricciones y validación

- No cambiar migraciones ya aplicadas ni fabricar datos históricos.
- Si se requiere una corrección de esquema, usar una nueva migración Flyway.
- Validar las migraciones y la compatibilidad sobre los motores/configuraciones
  del proyecto, incluyendo H2 local y PostgreSQL 16 en CI.
- Preservar el límite existente: Flyway usa JDBC; la persistencia de runtime
  usa R2DBC.

## CHOIR-106 — Matriz integral de aceptación

### Objetivo

Cerrar el trabajo de versionado y publicación de formaciones solo después de
verificar conjuntamente todos los contratos, invariantes y fases anteriores.

### Alcance

- Consolidar una matriz que enlace cada criterio con pruebas concretas.
- Ejecutar pruebas de arquitectura, autorización, catálogo y borradores,
  numeración, snapshots, publicación, selección, auditoría, referencias
  históricas, duplicación, API y migraciones.
- Revisar el diff completo para detectar cambios no relacionados y confirmar
  que no quedan servicios legacy con consumidores sin migrar.
- Documentar en Jira los resultados reales y las brechas restantes.

### Criterios de aceptación

- [ ] Cada invariante de CHOIR-31 tiene al menos una prueba identificable.
- [ ] La suite completa pasa y `git diff --check` pasa.
- [ ] No hay SQL en casos de uso/servicios ni lógica de negocio en controladores.
- [ ] Las versiones publicadas, snapshots, auditoría y transiciones históricas
  permanecen inmutables.
- [ ] No hay cambios no relacionados ni migraciones históricas modificadas.
- [ ] La documentación y Jira reflejan solo lo realmente verificado.
- [ ] No se cierra CHOIR-31 si quedan brechas críticas.

### Dependencias y orden

CHOIR-106 se ejecuta después de completar y revisar las fases 2 a 7, incluida
CHOIR-105. Esta fase es una verificación integral: no sustituye la
implementación ni la validación específica que corresponde a las fases previas.

## Al retomar el trabajo

1. Consultar primero este documento y continuar por CHOIR-105.
2. Antes de implementar un ticket, verificar que su alcance y criterios sigan
   coincidiendo con Jira.
3. Mantener el trabajo de cada ticket en su propia rama basada en `main`.
4. Actualizar esta referencia solo cuando cambie el alcance pendiente; registrar
   en Jira los resultados de verificación, sin declarar resultados no
   ejecutados.
