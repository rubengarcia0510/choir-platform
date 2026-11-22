# CHOIR-86 — Rango de coordenadas del escenario (decisión pendiente)

## Estado

**Pendiente de confirmación.** No está confirmado si el rango 0..100 es un
requisito obligatorio. Hasta confirmarlo, el dominio no impone ningún rango.

## Verificación de la fuente

- **Jira:** no consultado. El conector de Atlassian no está conectado en este
  entorno, así que los criterios se tomaron de `docs/tickets/CHOIR-86.md`, copia
  del repositorio. Si el ticket en Jira difiere, esta decisión debe revisarse.

## Qué dice el ticket

- La condición espacial de movilidad se basa en «coordenadas normalizadas del
  escenario (`x`/`y` entre `0` y `100`)».
- En la misma línea: «antes de implementar hay que acordar qué región representa
  una fila accesible y cómo se configura».
- El criterio «Se definen la escala, orientación del escenario y los datos de
  dominio necesarios» está sin marcar.

Lectura: el rango aparece como escala prevista, pero el propio ticket condiciona
su uso a un acuerdo posterior. No es una regla cerrada.

## Evidencia en el código

- **Backend:** los DTO de posiciones no tienen restricciones de rango. Las
  columnas `x_coordinate` e `y_coordinate` son `DECIMAL(10, 3) NOT NULL`, sin
  `CHECK` de rango.
- **Frontend (`choir-platform-web`, `formation-stage.component.ts`):** el
  movimiento se convierte en porcentaje del área del escenario (`× 100`) y se
  limita con `clamp` a X en `[5, 95]` e Y en `[8, 92]`.

Lectura: la UI trabaja de facto con una escala de 0 a 100, pero el backend no la
exige, y los límites `[5, 95]` y `[8, 92]` no están documentados como límites
físicos del escenario ni como contrato.

## Decisión provisional

1. `MobilityRegion` y `MobilityEvaluator` no imponen rango: la validación de
   límites sigue siendo solo `minX < maxX` y `minY < maxY`.
2. No se cambia el dominio ni los tests por este punto.

## Recomendación

Confirmar con quien define el escenario (diseño o producto):

1. Si `x`/`y` son porcentajes del área de escenario (0 a 100), como sugiere la UI.
2. Si `[5, 95]` y `[8, 92]` son límites físicos del escenario o solo límites
   visuales de la UI.
3. Qué región representa una «fila accesible» (el ejemplo del ticket) y cómo se
   configura.

Si se confirma la escala 0 a 100, la validación de rango de regiones y
posiciones se implementa en CHOIR-87, junto con los validadores. No se
implementa antes de esa confirmación.

## Preguntas abiertas

- ¿El rango 0 a 100 es requisito o referencia descriptiva?
- ¿Los límites de la UI son un contrato?
- ¿La región de «fila accesible» es un rectángulo por coreuta, o una región
  compartida por tipo de fila?
