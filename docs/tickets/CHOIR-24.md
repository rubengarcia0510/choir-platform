# CHOIR-24 — Modelar y puntuar transiciones entre formaciones

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-24 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-24).

## Objetivo

Calcular determinísticamente el coste y los riesgos de una transición escénica
entre dos versiones concretas de una formación, sin cambiar las decisiones
artísticas del director.

## Contrato de entrada

- La transición usa una versión exacta de origen y otra de destino; no alcanza
  con el nombre de una formación ni con consultar su versión actual.
- Ambas versiones deben pertenecer al contexto válido de coro/concierto y sus
  snapshots deben estar disponibles.
- Se reciben las restricciones activas de la obra incluida en el concierto,
  incluidas las reglas especiales CHOIR-20, identificando la versión evaluada.
- Con los mismos snapshots, parámetros y versiones de restricciones, el
  resultado debe ser reproducible.

## Criterios de aceptación

- Se calcula la distancia total recorrida por el elenco.
- Se contabilizan movimientos, cruces y movimientos simultáneos relevantes.
- Se detectan trayectorias potencialmente conflictivas y se explican con
  advertencias identificables.
- Las reglas activas se evalúan en el contexto de la obra y las versiones
  exactas. Una regla insatisfecha produce una advertencia comprensible; no se
  ignora ni bloquea automáticamente la decisión artística.
- El resultado identifica versiones de origen/destino y reglas consideradas,
  además de métricas y advertencias.
- Hay pruebas para movimientos/distancias, cruces, conflictos, reglas
  satisfechas/insatisfechas, reproducibilidad y referencias de versión.
- Una transición publicada conserva las referencias exactas de formación y
  reglas; ediciones posteriores no cambian su interpretación histórica.

## Límites y coordinación

- CHOIR-22 define formaciones por inclusión de obra; CHOIR-31 gestiona versiones
  y selección explícita de la versión publicada.
- CHOIR-20/67 define reglas especiales e integración funcional; consumir sus
  entradas/versiones sin duplicar CRUD.
- CHOIR-25 genera y rankea alternativas; este ticket calcula y describe cada
  transición, no decide cuál elige el director.
- Adaptación por ausencias corresponde a CHOIR-28/29.
- No inferir asociaciones históricas si la inclusión de obra no puede
  determinarse de forma fiable.
