# CHOIR-86 — Modelar restricciones configurables de movilidad y visibilidad

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-86 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-86).

## Objetivo y alcance

Definir restricciones de movilidad y visibilidad con semántica concreta y
validable. Persistirlas en el contexto de la formación y permitir su gestión
por el director autorizado. Evitar aceptar texto libre que el sistema no pueda
hacer cumplir.

## Criterios de aceptación

- Cada tipo de restricción tiene semántica y validación documentadas.
- El backend determina si un movimiento propuesto incumple una restricción.
- El director puede consultar y editar las restricciones.
- El rechazo explica qué regla concreta se incumplió.
