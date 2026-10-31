# CHOIR-85 — Definir y validar restricciones opcionales de proximidad entre coreutas

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-85 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-85).

## Objetivo y alcance

Permitir al director configurar relaciones espaciales opcionales entre
coreutas específicos, sin imponer agrupamiento automático por cuerda/sección.
Definir un modelo explícito y una condición espacial validable
determinísticamente, consultable y editable por el director autorizado.

## Criterios de aceptación

- Se pueden crear, consultar, modificar y eliminar restricciones de proximidad.
- Los movimientos incompatibles se rechazan con una explicación útil.
- La validación ocurre en backend y se refleja en el editor.
- Las restricciones se limitan al contexto de la formación correspondiente.
- No se agrega una regla implícita de cercanía a la sección vocal.
