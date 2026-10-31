# CHOIR-106 — Ejecutar matriz integral de aceptación de versionado

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-106 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-106).
- Dependencia: completar y revisar las fases 2 a 7, incluida CHOIR-105.

## Objetivo y alcance

Cerrar el trabajo de versionado/publicación solo después de verificar en
conjunto contratos, invariantes y fases anteriores.

- Consolidar una matriz que enlace cada criterio con pruebas concretas.
- Ejecutar pruebas de arquitectura, autorización, catálogo/borradores,
  numeración, snapshots, publicación, selección, auditoría, referencias
  históricas, duplicación, API y migraciones.
- Revisar el diff completo, detectar cambios no relacionados y confirmar que no
  quedan servicios legacy con consumidores sin migrar.
- Documentar en Jira resultados reales y brechas restantes.

## Criterios de aceptación

- Cada invariante de CHOIR-31 tiene al menos una prueba identificable.
- La suite completa y `git diff --check` pasan.
- No hay SQL en casos de uso/servicios ni lógica de negocio en controladores.
- Versiones publicadas, snapshots, auditoría y transiciones históricas
  permanecen inmutables.
- No hay cambios no relacionados ni migraciones históricas modificadas.
- Documentación y Jira reflejan solo lo realmente verificado.
- No cerrar si quedan brechas críticas.
