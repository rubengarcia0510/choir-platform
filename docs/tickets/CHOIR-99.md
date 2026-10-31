# CHOIR-99 — Hacer fiable la limpieza de datos de pruebas de evaluación en PostgreSQL

- Estado al snapshot: En curso.
- Fuente: [CHOIR-99 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-99).

## Problema y trabajo

La limpieza en `TransitionRuleEvaluationTests` no respeta bien el orden y las
dependencias entre entidades en PostgreSQL. Puede dejar residuos o fallar por
claves foráneas, haciendo que la regresión dependa del motor o del orden de
ejecución.

- Revisar dependencias entre evaluaciones, resultados, advertencias, decisiones
  y entidades padre.
- Eliminar primero dependientes y luego entidades referenciadas.
- No desactivar restricciones ni debilitar aserciones funcionales.
- Mantener la cobertura de inmutabilidad del historial de evaluaciones.

## Criterios de aceptación

- La prueba corre repetidamente en PostgreSQL sin residuos ni fallos de FK al
  limpiar.
- La limpieza no elimina ni modifica datos antes de terminar las aserciones del
  historial inmutable.
- La suite relevante pasa con H2 y PostgreSQL.
- No se agregan `System.out`/`System.err` ni dependencias de orden entre tests.
