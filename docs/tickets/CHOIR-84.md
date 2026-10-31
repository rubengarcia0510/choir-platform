# CHOIR-84 — Implementar bloqueo y desbloqueo de posiciones de coreutas

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-84 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-84).

## Objetivo y alcance

Permitir al director fijar una posición artística para evitar movimientos
accidentales. Persistir el estado bloqueado y evitar que las operaciones
habituales cambien sus coordenadas. El director autorizado puede bloquear y
desbloquear; el backend expone el estado al frontend para mostrarlo claramente.

El bloqueo se limita a las coordenadas de esa formación: no impide quitar al
coreuta de la formación ni incluirlo en otra.

## Criterios de aceptación

- Una posición bloqueada conserva sus coordenadas ante intentos de movimiento.
- El director puede desbloquearla y luego moverla.
- El backend valida el bloqueo; no depende solo del frontend.
- Se respetan las autorizaciones existentes.
