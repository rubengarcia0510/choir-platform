# CHOIR-24 — Modelar y puntuar transiciones entre formaciones

- **Estado:** Pendiente.
- **Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-24
- **Fuente de verdad:** Jira.
- **Repositorio:** `choir-platform`.
- **Subtareas:** CHOIR-121, CHOIR-122 y CHOIR-123.

## 1. Objetivo

Calcular de forma determinística el coste y los riesgos de una transición
entre dos versiones exactas de una formación, sin modificar las decisiones
artísticas del director.

El resultado debe incluir métricas de desplazamiento, cruces y conflictos
temporales, evaluación de reglas activas y advertencias comprensibles.

No debe generar ni rankear alternativas.

## 2. Dependencias y contexto

Dependencias funcionales ya finalizadas:

- CHOIR-20 y CHOIR-67: reglas especiales de escena y evaluación.
- CHOIR-22: formaciones asociadas a ocurrencias concretas.
- CHOIR-31: ciclo de vida y selección explícita de versiones.
- CHOIR-69: identidad de cada ocurrencia del repertorio.
- CHOIR-120: captura y evaluación de pasos temporales.

CHOIR-24 consume estos contratos. No debe duplicar sus responsabilidades.

## 3. Descomposición de trabajo

### CHOIR-121 — Métricas de desplazamiento

https://rubengarcia0510.atlassian.net/browse/CHOIR-121

Responsabilidad:

- Consumir snapshots FROM y TO exactos.
- Emparejar posiciones por identidad de coreuta.
- Calcular distancias euclidianas individuales.
- Calcular distancia total, distancia individual máxima y cantidad de
  coreutas que se desplazan.
- Definir precisión y normalización numérica.
- Tratar posiciones faltantes, duplicadas o no emparejables explícitamente.

No debe modificar formaciones ni versiones publicadas.

### CHOIR-122 — Cruces y conflictos de trayectorias

https://rubengarcia0510.atlassian.net/browse/CHOIR-122

Responsabilidad:

- Detectar intersecciones entre segmentos de desplazamiento FROM/TO.
- Identificar los coreutas involucrados.
- Reutilizar los pasos temporales capturados por CHOIR-120.
- Distinguir cruces geométricos de conflictos temporalmente simultáneos.
- Informar como no evaluable cuando la evidencia temporal sea insuficiente.

Límites importantes:

- No inventar velocidades, duraciones ni trayectorias curvas.
- No inferir temporalidad desde posiciones estáticas.
- No considerar simultáneos movimientos de secuencias diferentes.
- Definir los casos de segmentos coincidentes, paralelos, extremos
  compartidos y movimientos nulos.

### CHOIR-123 — Integración y resultado reproducible

https://rubengarcia0510.atlassian.net/browse/CHOIR-123

Responsabilidad:

- Integrar las métricas de CHOIR-121 y los hallazgos de CHOIR-122.
- Reutilizar SceneRuleEvaluator y TransitionPlanningServiceImpl.
- Consumir las revisiones versionadas de CHOIR-20/67.
- Producir advertencias identificables y comprensibles.
- Exponer un resultado que identifique las versiones exactas utilizadas.
- Preservar la trazabilidad histórica.
- Documentar el contrato REST para el frontend.
- Incorporar pruebas de integración y validar H2 y PostgreSQL.

## 4. Contrato de entrada

La evaluación debe identificar:

- Coro y concierto válidos.
- Ocurrencia concreta de repertorio.
- Versión exacta de formación FROM.
- Versión exacta de formación TO.
- Versión concreta de transición.
- Revisiones activas de reglas aplicables.
- Parámetros utilizados por el cálculo.
- Evidencia temporal disponible para la transición.

No se permite resolver una versión consultando simplemente la versión actual
de una formación.

Los mismos snapshots, parámetros y revisiones deben producir el mismo
resultado funcional.

## 5. Estado técnico existente en main

Ya existen componentes que deben reutilizarse:

- `TransitionPlanningServiceImpl`: orquestación de la evaluación de reglas.
- `SceneRuleEvaluator`: evaluación de reglas de escena.
- `TransitionMovementStep`: evidencia temporal de movimientos.
- `TransitionRuleEvaluationSnapshot`: historial de evaluación.
- `TransitionRuleEvaluationHistoryAdapter`: persistencia y recuperación
  del historial.
- `TransitionPlanningPositionRepository`: acceso a posiciones de planificación.

La lógica geométrica nueva debe respetar la arquitectura hexagonal.

SQL exclusivamente en adapters de persistencia.

No duplicar la captura de movimientos de CHOIR-120 ni el CRUD de reglas
de CHOIR-20.

## 6. Resultado esperado

El resultado funcional debe identificar:

- transitionId y transitionVersionId.
- fromFormationVersionId y toFormationVersionId.
- repertoireItemId.
- Revisiones exactas de reglas evaluadas.
- Distancias y métricas agregadas.
- Cruces geométricos detectados.
- Conflictos temporales confirmados o no evaluables.
- Resultados de reglas.
- Advertencias con códigos estables y explicaciones comprensibles.

La fecha de ejecución puede variar entre evaluaciones; la reproducibilidad
se verifica sobre el contenido funcional calculado para las mismas entradas.

## 7. Contrato HTTP

El endpoint propuesto anteriormente en este documento no constituye
un contrato definitivo.

CHOIR-123 debe revisar los controladores y servicios existentes, definir
si corresponde extender el flujo actual o agregar una ruta y documentar
el contrato definitivo antes de implementar la integración frontend.

## 8. Fuera de alcance

- Generación y ranking de alternativas: CHOIR-25.
- Preferencias artísticas y ponderación de criterios: CHOIR-26.
- Adaptación por ausencias: CHOIR-28/29.
- Captura de pasos temporales: CHOIR-120.
- CRUD y versionado de reglas: CHOIR-20/67.
- Edición visual de formaciones: CHOIR-21/22.

No agregar un score ponderado sin una definición funcional aprobada.
CHOIR-24 calcula y describe una transición; no decide qué alternativa
debe elegir el director.

## 9. Criterios globales de aceptación

- Se utilizan versiones exactas e identificables de origen y destino.
- Se calculan distancias y movimientos de forma determinística.
- Se detectan cruces y conflictos con evidencia suficiente.
- Las reglas activas se evalúan usando sus revisiones exactas.
- Las reglas incumplidas o no evaluables producen advertencias.
- El resultado conserva referencias a las versiones utilizadas.
- Las evaluaciones históricas no cambian ante ediciones posteriores.
- No se modifican formaciones ni snapshots publicados.
- Hay pruebas unitarias y de integración.
- Las pruebas relevantes pasan con H2 y PostgreSQL.
- El contrato REST queda documentado.

