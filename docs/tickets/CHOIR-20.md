# CHOIR-20 — Configurar reglas especiales de escena por canción

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-20 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-20).

## Objetivo y alcance

Permitir expresar, por canción del repertorio, casos artísticos que no pueden
modelarse como una transición genérica. Las reglas pertenecen a la canción y
se aplican al planificar sus transiciones; no forman parte de una transición
concreta ni reemplazan formaciones o snapshots históricos.

El modelo soporta reglas ordenadas y editables para movimientos/salidas
especiales y agrupaciones del elenco (por ejemplo, una salida 4+4). Se admiten
grupos identificados explícitamente y reglas basadas en cantidades/orden, cuya
composición concreta se resuelve durante la planificación. La regla expresa
grupos afectados, orden o simultaneidad y las condiciones relevantes de
movimiento/salida.

Las reglas se definen a nivel de obra y se evalúan para su inclusión en un
concierto usando versiones concretas de formación. La misma obra aparece como
máximo una vez por concierto; se usa `(concert_id, repertoire_item_id)` sin
agregar una identidad de ocurrencia.

## Criterios de aceptación

- Una canción puede tener cero o más reglas especiales con orden explícito.
- El director autorizado puede crear, editar, reordenar y desactivar reglas.
- Una regla puede representar movimientos/salidas y agrupaciones, incluida
  una división como 4+4 con la composición necesaria para aplicarla.
- Las reglas activas se recuperan como restricciones para planificar y
  recalcular; no se descartan silenciosamente.
- Una regla desactivada no se aplica a cálculos nuevos y no altera transiciones
  ya publicadas.
- Si una regla activa no puede satisfacerse, se presenta un conflicto y una
  advertencia comprensible; el director decide si continúa. No hay descarte
  silencioso ni bloqueo automático.
- Cada transición publicada conserva la referencia a la versión exacta de
  cada regla usada.
- Hay pruebas de persistencia, autorización, CRUD y aplicación al cálculo y
  recálculo. Se cubren reglas incumplibles y la decisión del director.

## Restricciones y fuera de alcance

- Los tipos de regla deben ser datos estructurados que el backend pueda
  interpretar; texto libre no reemplaza los parámetros evaluables.
- No reconstruir snapshots históricos a partir de las fichas actuales de
  coreutas.
- No implementar optimización/ranking (CHOIR-24/25), editor visual
  (CHOIR-21) ni adaptación automática por ausencias (CHOIR-28/29).
- No cambiar asignaciones vocales ni snapshots históricos existentes.
