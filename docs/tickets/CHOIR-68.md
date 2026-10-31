# CHOIR-68 — Verificar reglas especiales y documentación de CHOIR-20

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-68 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-68).

## Objetivo y alcance

Completar pruebas de integración/regresión de CHOIR-20 y documentar las
decisiones de dominio. Es una verificación transversal: no reimplementar el
CRUD de CHOIR-66 ni la integración de cálculo de CHOIR-67.

Verificar:

- Persistencia, autorización, CRUD, orden y desactivación.
- Grupos identificados y reglas por cantidades/orden cuya composición se
  resuelve al planificar.
- Caso 4+4 y suficiencia de datos sin reconstruir snapshots desde fichas
  actuales de coreutas.
- Evaluación de una inclusión concreta de obra en un concierto con versiones
  exactas de formación.
- Recálculo con reglas activas y exclusión de reglas desactivadas de cálculos
  nuevos.
- Para reglas activas incumplibles, advertencia comprensible y decisión del
  director; no ignorar ni bloquear automáticamente.
- Referencias exactas de reglas/formaciones en transiciones publicadas y
  estabilidad histórica ante cambios posteriores.
- Documentación del modelo y decisiones funcionales de los criterios.

## Criterios de aceptación

- Las pruebas de integración/regresión cubren los casos enumerados y pasan en
  los motores soportados.
- La documentación distingue reglas definidas por obra de su evaluación en
  una inclusión concreta de concierto.
- La documentación explica el versionado de reglas y referencias históricas
  desde transiciones publicadas.
- No se duplica lógica de producción de CHOIR-66 ni CHOIR-67.

## Fuera de alcance

No implementar/rediseñar CRUD (CHOIR-66), aplicación al motor de cálculo
(CHOIR-67), optimización/ranking (CHOIR-24/25), editor visual (CHOIR-21),
adaptación automática por ausencias (CHOIR-28/29) ni resolver aquí toda la
migración de ocurrencias/alternativas de formación. Coordinar cambios de
dominio compartidos con CHOIR-22, CHOIR-24 y CHOIR-31.
