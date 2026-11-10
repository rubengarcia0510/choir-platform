# Roadmap de ejecución y dependencias — Backend CHOIR

Jira es la fuente de verdad para el estado y los criterios de aceptación.
Este documento resume el orden técnico y las dependencias.

## 1. Trabajo completado en main

| Ticket | Descripción | Estado |
|---|---|---|
| CHOIR-84 | Bloqueo y desbloqueo de posiciones | Completado |
| CHOIR-89 | Formación predeterminada por cuerda | Completado |
| CHOIR-99 | Limpieza fiable de pruebas PostgreSQL | Completado |
| CHOIR-105 | Migraciones y compatibilidad | Completado |
| CHOIR-106 | Matriz de aceptación de versionado | Completado |
| CHOIR-109 | Posiciones iniciales agrupadas por cuerda | Completado |
| CHOIR-20 | Reglas especiales de escena | Completado |
| CHOIR-67 | Integración de reglas en planificación | Completado |
| CHOIR-120 | Evaluación de secuencias temporales | Completado |

## 2. Fase 1 — Restricciones de formaciones

1. CHOIR-85: restricciones opcionales de proximidad.
2. CHOIR-86: restricciones de movilidad y visibilidad.
3. CHOIR-87: validación de movimientos contra restricciones.
4. CHOIR-88: pruebas de persistencia, permisos y validación.

CHOIR-84 ya está completada y es la base de esta fase.

## 3. Fase 2 — Reglas especiales de escena

CHOIR-20 y CHOIR-67 están finalizadas.

CHOIR-120 completó la evaluación de secuencias temporales y aporta
la evidencia temporal que consumirá el cálculo de transiciones.

No reabrir estas responsabilidades dentro de CHOIR-24.

## 4. Fase 3 — Cálculo y optimización de transiciones

### CHOIR-24 — Modelar y puntuar transiciones

El trabajo se divide en tres subtareas:

| Subtarea | Responsabilidad |
|---|---|
| CHOIR-121 | Distancias y métricas de desplazamiento |
| CHOIR-122 | Cruces geométricos y conflictos temporales |
| CHOIR-123 | Integración, reglas, resultado e historial |

Orden técnico recomendado:

1. CHOIR-121 define las métricas geométricas.
2. CHOIR-122 consume snapshots y evidencia temporal para detectar cruces.
3. CHOIR-123 integra los resultados y expone el contrato completo.

Las pruebas unitarias deben formar parte de cada subtarea. CHOIR-123
incorpora las pruebas de integración del flujo completo.

### Tickets relacionados

- CHOIR-23: decisiones artísticas y restricciones del optimizador.
- CHOIR-25: generación y ranking de alternativas; consume CHOIR-24.
- CHOIR-26: preferencias artísticas y ponderación de criterios; depende
  del ranking de alternativas.

CHOIR-24 no debe implementar el ranking ni un score ponderado.

## 5. Fase 4 — Adaptación ante ausencias

- CHOIR-28: recalcular formaciones y transiciones ante ausencias.
- CHOIR-29: comparar y aplicar la adaptación.

Estas tareas consumen los resultados de planificación y transición.
No deben duplicar el cálculo geométrico de CHOIR-24.

## 6. Fase 5 — Ensayo y vista individual

- CHOIR-30: modo ensayo y reproducción de transiciones.
- CHOIR-32: vista individual de posiciones y movimientos.
- CHOIR-117: frontend del modo ensayo.

CHOIR-30 consume la información temporal y los resultados del backend.
La integración visual pertenece al repositorio `choir-platform-web`.

## 7. Reglas de coordinación

- CHOIR-22 y CHOIR-31 son responsables de formaciones y versionado.
- CHOIR-20/67 son responsables de reglas especiales y su evaluación.
- CHOIR-120 es responsable de la captura y evaluación temporal.
- CHOIR-24 consume esas capacidades y calcula métricas y advertencias.
- CHOIR-25/26 generan y ponderan alternativas.
- CHOIR-28/29 adaptan ante ausencias.
- CHOIR-30/32 consumen los resultados para ensayo y vista individual.

Mantener la arquitectura hexagonal. La lógica de dominio no debe depender
de Spring ni de infraestructura. SQL debe permanecer exclusivamente
en adapters de persistencia.

Las rutas HTTP de documentos técnicos son propuestas hasta que se
contrasten con Jira y los controladores existentes.
