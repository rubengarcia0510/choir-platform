# CHOIR-87 — Validar movimientos contra bloqueos y restricciones de formación

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-87 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-87).

## Objetivo y alcance

Centralizar en backend la validación de movimientos manuales frente a bloqueos
y restricciones configuradas. Validar cada cambio antes de persistir y devolver
información accionable si el movimiento no está permitido. Mantener separadas
las reglas del dominio de HTTP y persistencia, según la arquitectura hexagonal.

No asumir ni implementar un optimizador automático inexistente.

## Criterios de aceptación

- Los movimientos permitidos se guardan normalmente.
- Los movimientos no permitidos se rechazan sin cambios parciales.
- El frontend puede mostrar el motivo del rechazo.
- La validación no existe únicamente en el frontend.
