# Especificaciones de tickets CHOIR

Este directorio contiene snapshots locales de los tickets Jira CHOIR para que
Copilot pueda recuperar el alcance y los criterios sin volver a descubrirlos
desde cero.

## Alcance del inventario

- Fuente: proyecto Jira CHOIR.
- Consulta: tickets sin resolución, excluyendo épicas contenedoras.
- Snapshot consultado: 2026-10-31.
- Incluye tickets en curso y por hacer; no implica que todos estén listos para
  comenzar ni que sus dependencias estén satisfechas.
- Los estados son los observados en Jira al generar este inventario y pueden
  cambiar.
- Jira sigue siendo la fuente de verdad ante cambios posteriores de alcance,
  criterios, estado o dependencias.

Hay **21 tickets no resueltos** en este snapshot. Abrí el archivo por clave para
ver el objetivo, el alcance, los criterios de aceptación y las restricciones
registradas:

| Ticket | Estado al snapshot | Título |
|---|---|---|
| [CHOIR-20](CHOIR-20.md) | Por hacer | Configurar reglas especiales de escena por canción |
| [CHOIR-23](CHOIR-23.md) | Por hacer | Bloquear decisiones artísticas y definir restricciones |
| [CHOIR-24](CHOIR-24.md) | Por hacer | Modelar y puntuar transiciones entre formaciones |
| [CHOIR-25](CHOIR-25.md) | Por hacer | Generar y rankear alternativas de transición |
| [CHOIR-26](CHOIR-26.md) | Por hacer | Permitir preferencias artísticas del director en la optimización |
| [CHOIR-28](CHOIR-28.md) | Por hacer | Recalcular formaciones y transiciones ante ausencias |
| [CHOIR-29](CHOIR-29.md) | Por hacer | Comparar y aplicar adaptación por ausencia |
| [CHOIR-30](CHOIR-30.md) | Por hacer | Modo ensayo y reproducción de transiciones |
| [CHOIR-32](CHOIR-32.md) | Por hacer | Vista de coreuta con sus posiciones y movimientos |
| [CHOIR-67](CHOIR-67.md) | En curso | Integrar reglas especiales en la planificación de transiciones |
| [CHOIR-68](CHOIR-68.md) | Por hacer | Verificar reglas especiales y documentación de CHOIR-20 |
| [CHOIR-82](CHOIR-82.md) | Por hacer | Incorporar CI para choir-platform-web |
| [CHOIR-84](CHOIR-84.md) | Por hacer | Implementar bloqueo y desbloqueo de posiciones de coreutas |
| [CHOIR-85](CHOIR-85.md) | Por hacer | Definir y validar restricciones opcionales de proximidad entre coreutas |
| [CHOIR-86](CHOIR-86.md) | Por hacer | Modelar restricciones configurables de movilidad y visibilidad |
| [CHOIR-87](CHOIR-87.md) | Por hacer | Validar movimientos contra bloqueos y restricciones de formación |
| [CHOIR-88](CHOIR-88.md) | Por hacer | Probar persistencia, permisos y validación de restricciones |
| [CHOIR-89](CHOIR-89.md) | Por hacer | Crear formación predeterminada agrupada por cuerda vocal |
| [CHOIR-99](CHOIR-99.md) | En curso | Hacer fiable la limpieza de datos de pruebas de evaluación en PostgreSQL |
| [CHOIR-105](CHOIR-105.md) | Por hacer | Verificar migraciones y compatibilidad de datos existentes |
| [CHOIR-106](CHOIR-106.md) | Por hacer | Ejecutar matriz integral de aceptación de versionado |

## Cómo usar los specs

1. Identificá la clave Jira del ticket solicitado.
2. Leé el archivo correspondiente antes de explorar o editar código.
3. Revisá dependencias, límites y fuera de alcance para no duplicar trabajo de
   otros tickets.
4. Confirmá en Jira que el alcance sigue vigente antes de implementar.
5. Si un ticket sin archivo pasa a estar en alcance, consultá Jira y agregá su
   spec con la misma convención `CHOIR-<número>.md`.
