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

Definir explícitamente el comportamiento para:

- Segmentos paralelos.
- Segmentos que no se intersecan.
- Extremos compartidos.
- Segmentos coincidentes.
- Movimientos nulos.
- Movimientos en secuencias temporales diferentes.
- Evidencia temporal ausente, incompleta o inconsistente.

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
