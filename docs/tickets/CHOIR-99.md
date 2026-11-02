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

## Estrategia de aislamiento

En PostgreSQL, las evaluaciones, sus resultados y advertencias, y la evidencia
y decisiones de publicación son historial inmutable. Cada una de las dos suites
de evaluación crea una base PostgreSQL descartable antes de inicializar Spring
y configura JDBC/Flyway y R2DBC para usarla. La base se elimina al terminar la
clase, incluso si fallan aserciones; un shutdown hook cubre fallos durante la
inicialización del contexto o el teardown. Así se conservan las restricciones
de inmutabilidad sin dejar fixtures en la base compartida. En H2 se mantiene la
limpieza por filas, eliminando primero dependientes y luego entidades padre.

El job `test-prod` repite tres veces ambas suites en PostgreSQL antes de la
suite completa, para verificar aislamiento y teardown reproducibles. Las dos
suites pasaron localmente con H2: 4 pruebas ejecutadas y una comprobación
específica de PostgreSQL omitida en `TransitionRuleEvaluationTests`, y una
prueba ejecutada en `TransitionRuleEvaluationHistoricalImmutabilityTests`.
Docker/PostgreSQL no están disponibles localmente; falta confirmar ese job en
CI.
