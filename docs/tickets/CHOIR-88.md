# CHOIR-88 — Probar persistencia, permisos y validación de restricciones

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-88 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-88).

## Objetivo y alcance

Asegurar integridad funcional de bloqueos y restricciones mediante pruebas:

- Bloqueo, desbloqueo y movimiento de posiciones bloqueadas.
- Restricciones de proximidad, movilidad y visibilidad definidas para el dominio.
- Autorización del director y validación de movimientos inválidos.
- Persistencia y errores sin efectos parciales.

## Criterios de aceptación

- Hay pruebas unitarias y de integración para los casos relevantes.
- Se cubren movimientos permitidos y rechazados.
- Las pruebas no dependen de un optimizador automático aún inexistente.
- La suite relevante pasa en CI.
