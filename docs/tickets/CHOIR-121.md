# CHOIR-121 — Calcular métricas de desplazamiento entre snapshots

**Jira:** https://rubengarcia0510.atlassian.net/browse/CHOIR-121  
**Parent:** CHOIR-24  
**Dominio:** `transition`  
**Dependencias:** snapshots/versiones de formación disponibles mediante CHOIR-22/31.

## Objetivo

Calcular métricas determinísticas de movimiento comparando los snapshots exactos FROM y TO de una transición.

## Alcance funcional

1. Consumir las versiones exactas identificadas por el contexto de transición. No buscar una versión actual ni resolver versiones por nombre.
2. Reutilizar las posiciones disponibles en el módulo de planificación.
3. Emparejar posiciones por identidad de coreuta.
4. Calcular distancia euclidiana individual en el plano del escenario.
5. Calcular:
   - Distancia total recorrida.
   - Distancia individual máxima.
   - Cantidad de coreutas que efectivamente se desplazan.
6. Definir y documentar precisión y normalización numérica.
7. Tratar explícitamente posiciones faltantes, duplicadas, inválidas y participantes sin correspondencia.
8. Identificar en el resultado las versiones exactas utilizadas.

## Diseño y límites

- La lógica de cálculo debe permanecer en dominio/aplicación.
- SQL exclusivamente en adapters.
- Mantener el cálculo independiente de las restricciones configurables de `formation`.
- No importar ni invocar validadores de proximidad, movilidad, visibilidad o bloqueos de `formation`.
- No modificar modelos, servicios, repositorios ni adapters de `formation`.
- No compartir clases de cálculo entre `formation` y `transition`.
- No modificar snapshots ni generar formaciones nuevas.

## Fuera de alcance

- Detección de cruces y conflictos temporales: CHOIR-122.
- Ranking o score ponderado: CHOIR-25/26.
- Generación o modificación de formaciones.
- Evaluación de restricciones configurables de formación.

## Pruebas requeridas

- Distancia cero.
- Movimiento horizontal y vertical.
- Movimiento diagonal.
- Varios coreutas y agregación de distancias.
- Precisión y normalización numérica.
- Posiciones faltantes, duplicadas o inválidas.
- Participantes sin correspondencia.
- Identidad de las versiones de entrada.
- Reproducibilidad con entradas idénticas.
- Inmutabilidad de snapshots.

## Criterios de aceptación

- Las distancias individuales y agregadas se calculan desde los snapshots FROM/TO exactos.
- El resultado identifica las versiones utilizadas.
- Los casos sin movimiento y los datos incompletos tienen comportamiento explícito y probado.
- La misma entrada produce exactamente el mismo resultado.
- Ninguna formación ni versión publicada resulta modificada.

## Precisión y datos incompletos

- Cada distancia individual se redondea a seis decimales con `HALF_UP`.
- La distancia total suma las distancias individuales ya normalizadas; el
  máximo individual usa esos mismos valores.
- `movingSingerCount` cuenta todo coreuta cuyas coordenadas FROM y TO difieren
  geométricamente, antes del redondeo. Una distancia positiva menor que media
  unidad de la sexta cifra decimal puede mostrarse como `0.000000` y aun así
  contar como movimiento. En `0.000001` y por encima se muestra al menos
  `0.000001`; el conteo no aplica un umbral de distancia.
- Por ello, el conteo describe el movimiento geométrico y las distancias
  describen magnitudes redondeadas para presentación; un conteo positivo con
  total normalizado cero es válido. Ambos resultados son determinísticos.
- Se excluyen del cálculo y se reportan las identidades inválidas, posiciones
  duplicadas, coordenadas ausentes y participantes sin contraparte.
- El resultado conserva los IDs exactos de transición y de ambos snapshots.

## Coordinación

CHOIR-121 debe poder desarrollarse sin modificar responsabilidades de `formation` ni depender de CHOIR-122. CHOIR-123 consumirá sus resultados.

Antes de introducir una dependencia o modificar clases de otro dominio, revisar `docs/architecture/domain-boundaries.md` y acordar el contrato correspondiente.
