# CHOIR-123 — Integrar métricas, reglas y resultado reproducible de transición

**Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-123  
**Parent:** CHOIR-24  
**Dominio principal:** `transition`  
**Dependencias:** CHOIR-121, CHOIR-122, CHOIR-20/67 y CHOIR-120.

## Objetivo

Componer los resultados geométricos y temporales con la evaluación existente de reglas de escena y exponer un resultado completo, trazable y reproducible para una transición entre versiones exactas.

## Alcance funcional

1. Integrar las métricas de CHOIR-121 y los hallazgos de CHOIR-122.
2. Reutilizar `SceneRuleEvaluator` y el flujo actual de `TransitionPlanningServiceImpl`.
3. Consumir CHOIR-20/67 y sus revisiones versionadas sin duplicar CRUD, selección de reglas ni lógica de evaluación.
4. Producir advertencias comprensibles y con códigos estables para métricas, cruces, conflictos temporales, reglas incumplidas y reglas no evaluables.
5. Identificar en el resultado:
   - `transitionId`
   - `transitionVersionId`
   - `fromFormationVersionId`
   - `toFormationVersionId`
   - `repertoireItemId`
   - Revisiones exactas de reglas consideradas.
6. Definir el contrato HTTP sobre el flujo existente de planificación/evaluación.
7. Documentar el contrato REST y sus límites para el consumidor frontend.
8. Preservar la trazabilidad histórica de las evaluaciones y referencias exactas utilizadas.
9. Agregar pruebas de integración del flujo completo y validar persistencia en H2 y PostgreSQL.

## Contrato HTTP

Se extiende el flujo existente de evaluación; no se agrega otro endpoint:

- `POST /api/choirs/{choirId}/transitions/{transitionId}/versions/{transitionVersionId}/rule-evaluations`
  crea una evaluación y conserva el request existente de asignaciones opcionales.
- `GET` sobre la misma ruta devuelve el historial en orden de creación.
- Ambos endpoints identifican la transición y versión exactas en la ruta y
  requieren autenticación/autorización del director propietario.
- El snapshot agrega `displacementMetrics` y `trajectoryAnalysis`, junto con
  las referencias ya existentes a snapshots, regla y revisión exacta. Cada
  cruce conserva su estado temporal; advertencias globales siguen en
  `warnings` y las de una regla en `results[].warnings`.
- `CONFIRMED` se expone como advertencia `VIOLATED` y afecta el estado global;
  `NOT_CONCURRENT` se expone como advertencia `SATISFIED`; evidencia temporal
  insuficiente o inconsistente se expone como `NOT_EVALUABLE`. La política de
  publicación vigente bloquea cualquier advertencia no evaluable y requiere
  aceptación explícita para las violaciones ordinarias.
- Las evaluaciones históricas creadas antes de esta extensión devuelven
  `null` en los dos campos nuevos: no se reconstruyen a partir de snapshots
  actuales.

Esta ruta corresponde al `POST` y `GET` ya implementados en
`TransitionVersionController`; no duplica el caso de uso ni crea un endpoint
paralelo. El contrato REST detallado está en
`docs/transition-version-publication-contract.md`.

## Historia e inmutabilidad

- Conservar referencias exactas a snapshots y revisiones de reglas.
- No reescribir evaluaciones históricas por cambios posteriores.
- Preservar evidencia y referencias originales al publicar.
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

---

## Instrucciones operativas para los agentes

### Fase 1 — Planner: análisis obligatorio

El Planner trabaja sobre `feature/CHOIR-24`.

En esta fase:
- No modificar archivos.
- No crear commits ni ramas de implementación.
- No delegar tareas al Implementer.
- No iniciar cambios de código.

Inspeccionar como mínimo:

1. Implementaciones y pruebas de CHOIR-121 y CHOIR-122.
2. `TransitionPlanningServiceImpl` y sus casos de uso.
3. `TransitionRuleEvaluationSnapshot`.
4. `TransitionRuleEvaluationHistoryAdapter`.
5. Puertos, contratos y adapters relacionados.
6. Migraciones y esquemas de H2/PostgreSQL.
7. `TransitionVersionController`, DTOs y documentación REST.
8. Instrucciones generales y específicas del repositorio.

Entregar un plan verificable que incluya:

- Archivos concretos a modificar, crear o eliminar.
- Responsabilidad de cada cambio y capa arquitectónica correspondiente.
- Cómo se orquestarán los calculadores de desplazamiento y trayectorias.
- Cómo se integrarán los resultados con la evaluación de reglas de escena.
- Cómo se ampliará el snapshot sin romper consumidores existentes.
- Estrategia de persistencia, recuperación histórica y compatibilidad con registros previos.
- Migraciones necesarias y compatibilidad H2/PostgreSQL.
- Contrato HTTP propuesto, contrastado con los controladores actuales.
- Pruebas unitarias, de integración y API, indicando clases y escenarios concretos.
- Riesgos de concurrencia, determinismo e inmutabilidad.
- Conflictos potenciales con ramas o archivos asignados a otros agentes.
- Orden de implementación y dependencias entre cambios.

El Planner debe identificar explícitamente cualquier archivo compartido con otros trabajos. No asumir que dos tareas son independientes solo porque tienen distinto número.

No proponer un endpoint paralelo sin demostrar que el existente no puede extenderse.

**La entrega de esta fase es exclusivamente el plan. Debe esperar aprobación antes de implementar.**

### Fase 2 — Implementer: ejecución aprobada

Solo después de aprobar el plan:

- Implementar exactamente el alcance aprobado.
- Respetar las responsabilidades y archivos identificados.
- No modificar el dominio `formation` ni sus contratos internos.
- Mantener SQL exclusivamente en adapters.
- Agregar pruebas focalizadas y ejecutar la suite correspondiente.
- Validar persistencia y recuperación histórica en H2 y PostgreSQL.
- Demostrar reproducibilidad e inmutabilidad.
- Documentar decisiones y cambios del contrato REST.
- No ampliar el alcance a CHOIR-25/26.

Si aparece una necesidad fuera del plan o un conflicto con otro agente, detener ese cambio y reportarlo al Planner.

### Fase 3 — Reviewer: revisión independiente

El Reviewer no modifica código.

Debe revisar:
- Correspondencia entre plan aprobado y diff.
- Límites entre dominios.
- Contratos y compatibilidad HTTP.
- Migraciones y persistencia histórica.
- Determinismo e inmutabilidad.
- Cobertura y resultados de CI, incluyendo H2 y PostgreSQL.
- Documentación y criterios de aceptación.

Debe informar defectos concretos y bloquear la aprobación si existe SQL fuera de adapters, acoplamiento a internals de `formation`, pérdida de evidencia histórica o pruebas insuficientes.

### Integración y entrega

- El PR de CHOIR-123 debe tener como base `feature/CHOIR-24`.
- No apuntar directamente a `main`.
- No integrar la rama completa a `main` hasta completar CHOIR-123 y validar el alcance del padre CHOIR-24.
- El Planner debe informar dependencias, solapamientos y conflictos antes de delegar.
- Cada agente debe dejar evidencia de archivos modificados, pruebas ejecutadas y resultado de revisión.
