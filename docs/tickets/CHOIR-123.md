# CHOIR-123 — Integrar métricas, reglas y resultado reproducible de transición

**Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-123  
**Parent:** CHOIR-24  
**Dominio principal:** `transition`  
**Dependencias:** CHOIR-121, CHOIR-122, CHOIR-20/67 y CHOIR-120.

## Objetivo

Componer los resultados geométricos y temporales con la evaluación existente de reglas de escena y exponer un resultado completo, trazable y reproducible para una transición entre versiones exactas.

## Alcance funcional

1. Integrar las métricas de CHOIR-121 y los hallazgos de CHOIR-122.
2. Reutilizar la evaluación existente de `SceneRuleEvaluator` y el flujo actual de `TransitionPlanningServiceImpl`.
3. Consumir CHOIR-20/67 y sus revisiones versionadas sin duplicar CRUD, selección de reglas ni lógica de evaluación.
4. Producir advertencias comprensibles y con códigos estables para:
   - Métricas de desplazamiento.
   - Cruces y conflictos temporales.
   - Reglas incumplidas.
   - Reglas no evaluables.
5. Identificar en el resultado:
   - `transitionId`.
   - `transitionVersionId`.
   - `fromFormationVersionId`.
   - `toFormationVersionId`.
   - `repertoireItemId`.
   - Revisiones exactas de reglas consideradas.
6. Definir el contrato HTTP sobre el flujo existente de planificación/evaluación.
7. Documentar el contrato REST y sus límites para el consumidor frontend.
8. Preservar la trazabilidad histórica de las evaluaciones y referencias exactas utilizadas.
9. Agregar pruebas de integración del flujo completo y validar persistencia en H2 y PostgreSQL.

## Contrato HTTP

No asumir como definitivo el endpoint propuesto en `docs/tickets/CHOIR-24.md`.

Antes de implementar:

- Revisar los controladores y contratos HTTP existentes.
- Identificar el flujo actual de planificación y evaluación.
- Confirmar cómo se identifican las versiones y transiciones.
- Definir la ruta y el contrato final sin duplicar endpoints ni casos de uso.
- Documentar la decisión en este ticket y en la especificación REST.

## Historia e inmutabilidad

- El resultado debe conservar las referencias exactas a snapshots y revisiones de reglas.
- Las evaluaciones históricas no deben reescribirse por cambios posteriores.
- La publicación debe preservar la evidencia y referencias originales.
- Repetir el cálculo con los mismos snapshots, parámetros y revisiones debe producir el mismo contenido funcional.

## Límites entre dominios

- Integrar CHOIR-121/122 con las reglas existentes de `scene`.
- CHOIR-20/67 aporta reglas especiales de escena.
- No confundir reglas de escena con restricciones configurables de `formation` de CHOIR-85/86/87.
- No importar validadores, entidades internas, repositorios ni adapters de `formation`.
- No incorporar el motor de restricciones de formación al resultado de CHOIR-24.
- No crear interfaces genéricas anticipadas para conectar `formation` y `transition`.
- Si una necesidad futura requiere evaluar restricciones de formación en una transición, registrar ese alcance por separado y definir primero un contrato pequeño con dirección de dependencia explícita.
- No acoplar la persistencia histórica a detalles internos de persistencia de otros dominios.

## Fuera de alcance

- Score ponderado o ranking de alternativas: CHOIR-25/26.
- Generación o modificación de formaciones.
- Captura de pasos temporales: CHOIR-120.
- CRUD o duplicación de reglas de escena: CHOIR-20.
- Motor de restricciones de formación: CHOIR-85/86/87.

## Pruebas requeridas

- Flujo completo exitoso.
- Integración de métricas y hallazgos geométricos/temporales.
- Reglas satisfechas.
- Reglas incumplidas.
- Reglas no evaluables.
- Datos incompletos.
- Identificación de versiones exactas de formación y reglas.
- Reproducibilidad funcional.
- Inmutabilidad y consulta histórica.
- Persistencia y transacciones en H2.
- Persistencia y transacciones en PostgreSQL.
- Contrato HTTP y errores relevantes.

## Criterios de aceptación

- El resultado combina métricas, cruces/conflictos, evaluación de reglas y advertencias.
- Identifica las versiones exactas de formación y reglas utilizadas.
- Repetir el cálculo con los mismos datos y parámetros produce el mismo contenido funcional.
- La consulta histórica conserva referencias y evidencia originales.
- Ediciones posteriores no alteran la interpretación histórica.
- Las pruebas cubren éxito, datos incompletos, reglas satisfechas/incumplidas/no evaluables, reproducibilidad e inmutabilidad.
- H2 y PostgreSQL quedan validados.
- El contrato REST queda documentado y contrastado con los controladores existentes.

## Coordinación

CHOIR-121 y CHOIR-122 deben estar disponibles antes de completar la integración. CHOIR-123 es el único responsable de componer ambos resultados con las reglas de escena y exponer el resultado completo.

No anticipar interfaces compartidas ni modificar clases de `formation` sin un acuerdo explícito y un alcance aprobado.
