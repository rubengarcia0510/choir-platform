# CHOIR-67 — Integrar reglas especiales en la planificación de transiciones

- Estado al snapshot: En curso.
- Fuente: [CHOIR-67 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-67).

## Objetivo

Integrar las reglas de escena de CHOIR-20, administradas/persistidas por
CHOIR-65/66, al cálculo y recálculo entre versiones exactas de formaciones.

## Contrato y criterios de aceptación

- Identificar la obra de un concierto con `(concert_id, repertoire_item_id)`;
  una obra puede aparecer una sola vez por concierto.
- Usar versiones exactas de origen y destino, y validar su contexto de
  coro/concierto/obra.
- Evaluar reglas activas en su orden definido, soportando grupos identificados
  y cantidades/orden, incluidos movimiento y simultaneidad.
- Versionar de forma inmutable cada creación, edición, reordenamiento y
  desactivación de reglas, conservando los grupos necesarios para reproducirla.
  El cálculo y su resultado identifican las revisiones exactas utilizadas.
- El recálculo vuelve a evaluar reglas activas con entradas explícitas y genera
  un resultado nuevo; no muta publicaciones anteriores.
- Las reglas desactivadas se excluyen de cálculos nuevos sin modificar su
  historial.
- Una regla activa incumplible produce una advertencia estructurada con
  referencia a la regla y un motivo legible; no se ignora ni bloquea
  automáticamente.
- Ante advertencias, se exige una decisión explícita del director. Solo una
  aceptación permite publicar; revisar o descartar impide publicar. La decisión
  queda registrada y auditable en el mecanismo de publicación existente.
- Una transición publicada conserva referencias exactas a obra, formaciones y
  revisiones de reglas. Cambios posteriores no alteran su historial ni la
  decisión registrada.
- Con iguales obra, snapshots, revisiones y parámetros, el resultado funcional
  es reproducible.
- Hay pruebas de contexto inválido, reglas cumplidas/incumplidas, ambos modos de
  salida, orden/simultaneidad, versionado/desactivación, recálculo, decisión e
  inmutabilidad histórica.
- Los cambios de esquema requeridos usan nuevas migraciones Flyway, sin editar
  migraciones aplicadas ni inventar datos históricos no determinables.

## Coordinación y límites

- CHOIR-20 define el contrato de dominio; CHOIR-65 persiste y CHOIR-66 ofrece
  CRUD/autorización. No duplicar CRUD.
- CHOIR-22 define formaciones por obra; CHOIR-31 define publicación/selección y
  snapshots. Usar identificadores de versión exactos.
- Coordinar con CHOIR-24 el cálculo determinista de coste/riesgo, sin duplicar
  su algoritmo de distancias, cruces o puntuación.
- CHOIR-25 genera y rankea alternativas; CHOIR-28/29 adapta ausencias. No
  implementar esos algoritmos aquí.
- CHOIR-68 cubre verificación transversal; este ticket incluye pruebas propias
  de la integración que implementa.
- Mantener `(concert_id, repertoire_item_id)`; no permitir repetición de una
  obra en un mismo concierto agregando identidad de ocurrencia.

## Definición de terminado registrada en Jira

Documentar en Jira el contrato y las decisiones de persistencia antes de
cerrar. La implementación debe tener pruebas automatizadas relevantes y
ejecución local/CI satisfactoria, sin duplicar CRUD ni el algoritmo de
CHOIR-24/25; revisar mediante PR hacia la rama de integración acordada.
CHOIR-68 completa después la regresión y documentación transversal.
