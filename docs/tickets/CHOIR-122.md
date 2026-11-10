# CHOIR-122 — Detectar cruces y conflictos de trayectorias

**Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-122  
**Parent:** CHOIR-24  
**Dominio:** `transition`  
**Dependencias:** snapshots/versiones de formación y evidencia temporal de CHOIR-120.

## Objetivo

Detectar intersecciones geométricas y determinar cuándo representan un riesgo temporal de transición, sin confundir un cruce espacial con un conflicto simultáneo.

## Alcance funcional

1. Construir segmentos de desplazamiento usando las posiciones correspondientes de los snapshots FROM y TO exactos.
2. Detectar intersecciones entre segmentos.
3. Identificar a los coreutas involucrados en cada hallazgo.
4. Reutilizar los pasos temporales capturados por CHOIR-120 para la versión de transición.
5. Utilizar la secuencia temporal explícita para distinguir movimientos concurrentes de movimientos realizados en tandas diferentes.
6. Diferenciar en el resultado:
   - Cruce geométrico.
   - Conflicto temporal confirmado.
   - Conflicto temporal no evaluable por falta o inconsistencia de evidencia.

## Límites geométricos y temporales

El análisis representa cada desplazamiento como un segmento recto entre las
coordenadas FROM y TO y usa comparaciones decimales exactas. Los extremos del
segmento forman parte de la intersección: compartir un extremo cuenta como
cruce geométrico. Segmentos colineales cuentan si sus intervalos se solapan,
incluyendo un único extremo compartido; segmentos paralelos disjuntos no
cuentan. Un movimiento nulo no constituye un segmento de trayectoria y se
excluye de la detección. Pares de coreutas se reportan una sola vez en orden
ascendente por ID.

La evaluación temporal es independiente del hallazgo geométrico y usa solo la
evidencia explícita de CHOIR-120:

- Evidencia válida requiere referencias y acciones presentes, IDs de
  transición y snapshots coincidentes, secuencias numeradas desde 1 sin
  huecos, una referencia temporal común por secuencia y referencias
  estrictamente crecientes entre secuencias.
- Pasos repetidos del mismo coreuta dentro de una secuencia o cualquier
  evidencia malformada/inconsistente dejan el conflicto temporal como
  `NOT_EVALUABLE`.
- Dos coreutas del cruce con pasos válidos en una misma secuencia producen
  `CONFIRMED`.
- Si ambos tienen evidencia válida pero solo aparecen en secuencias distintas,
  producen `NOT_CONCURRENT`; no se infiere simultaneidad entre tandas.
- Si falta evidencia para cualquiera de los dos, producen `NOT_EVALUABLE`.
- Sin cruces geométricos no se crea un hallazgo temporal.

Estas categorías describen evidencia, no reglas de formación: `CONFIRMED` no
invoca ni implica una violación de restricciones de `formation`. Las
referencias temporales solo validan y agrupan la evidencia; no se usan para
inferir velocidades, duraciones ni trayectorias dentro de una secuencia.

No inferir simultaneidad desde la geometría. No tratar como simultáneos movimientos de secuencias diferentes.

Los pasos de CHOIR-120 aportan secuencia, referencia, acción y evidencia temporal disponible. No representan una animación física detallada.

Por lo tanto:

- No inventar velocidades.
- No inventar duraciones.
- No inferir trayectorias curvas.
- No deducir tiempos a partir de posiciones.
- No volver a implementar la captura de movimientos de CHOIR-120.

## Diseño y límites

- La geometría y su evaluación pertenecen a `transition`.
- No importar ni invocar validadores de proximidad, movilidad, visibilidad o bloqueos de `formation`.
- No modificar modelos, servicios, repositorios ni adapters de `formation`.
- Un cruce geométrico o conflicto temporal no equivale a una violación de una restricción de formación.
- Mantener ambos resultados semánticamente separados.
- No modificar snapshots ni evidencia temporal histórica.

## Fuera de alcance

- Score ponderado o ranking: CHOIR-25/26.
- Generación de formaciones.
- Validación de restricciones configurables de formación.
- Captura o edición de pasos temporales: CHOIR-120.

## Pruebas requeridas

- Intersección geométrica.
- Ausencia de intersección.
- Extremos compartidos.
- Segmentos coincidentes.
- Segmentos paralelos.
- Movimientos nulos.
- Movimientos en tandas temporales diferentes.
- Movimientos con evidencia de secuencia coincidente.
- Evidencia temporal ausente o inconsistente.
- Distinción entre cruce geométrico y conflicto temporal.
- Reproducibilidad e inmutabilidad de los datos de entrada.

## Criterios de aceptación

- Los cruces se detectan determinísticamente, incluyendo casos límite.
- Cada hallazgo identifica participantes y tipo.
- La simultaneidad solo se afirma cuando existe evidencia temporal explícita suficiente.
- Sin evidencia suficiente, el resultado es no evaluable y no una afirmación de conflicto confirmado.
- Un cruce sin simultaneidad se distingue de un conflicto temporal.
- No se alteran snapshots ni evidencia histórica.

## Coordinación

CHOIR-122 es independiente del cálculo de distancias agregado de CHOIR-121. Ambos resultados serán integrados por CHOIR-123.

Antes de introducir una dependencia o modificar clases de otro dominio, revisar `docs/architecture/domain-boundaries.md` y acordar el contrato correspondiente.
