# CHOIR-89 — Crear formación predeterminada agrupada por cuerda vocal

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-89 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-89).

## Objetivo y reglas funcionales

Ofrecer una distribución inicial automática de coreutas agrupada por las
cuerdas vocales configuradas en el coro, como punto de partida al crear una
formación.

- Usar las cuerdas/secciones realmente configuradas para ese coro; no asumir
  cuatro cuerdas ni un catálogo fijo.
- El director puede mover coreutas y mezclar cuerdas libremente luego.
- La agrupación es una conveniencia, no una restricción espacial ni regla de
  proximidad obligatoria.
- No sobrescribir formaciones personalizadas existentes.
- Integrarse con el flujo actual de creación/edición de borradores.

## Criterios de aceptación

- El director puede generar una formación inicial agrupada por las cuerdas del
  coro.
- Se admiten distintas cantidades y nombres de cuerdas.
- La distribución se puede editar manualmente.
- Generarla no altera formaciones guardadas distintas de la que se crea/edita
  explícitamente.
- Hay pruebas para cantidades de cuerdas, edición posterior y preservación de
  formaciones existentes.
