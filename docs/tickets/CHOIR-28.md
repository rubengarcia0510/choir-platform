# CHOIR-28 — Recalcular formaciones y transiciones ante ausencias

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-28 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-28).

## Objetivo

Adaptar automáticamente las formaciones afectadas cuando faltan coreutas.

## Criterios de aceptación

- Las personas ausentes se excluyen del cálculo.
- Se conservan las decisiones artísticas bloqueadas siempre que exista una
  solución.
- Se recalculan las transiciones afectadas.
- Se informa cuando no existe una solución que respete todas las restricciones.
