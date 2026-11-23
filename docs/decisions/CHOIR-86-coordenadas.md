# CHOIR-86 — Escala y orientación del escenario

## Estado

**Aprobada.** Las coordenadas son porcentajes normalizados entre 0 y 100, inclusive.

## Convención

- X: horizontal, de izquierda a derecha desde la perspectiva del director.
- Y: profundidad. y = 0 es el fondo (borde superior de la UI); y = 100 es el frente
  (borde inferior de la UI).
- Origen (0, 0): esquina superior izquierda.
- Sin coordenada Z.

## Evidencia del mapeo real del frontend

Verificado en `choir-platform-web` (`formation-stage.component.html` y `.ts`):

- La plantilla posiciona cada coreuta con `[style.left.%]="displayX(singer)"` y
  `[style.top.%]="displayY(singer)"`.
- `displayX` y `displayY` devuelven `singer.x` y `singer.y` sin transformación; solo
  sustituyen el valor durante el arrastre o el movimiento por teclado.
- `.stage-back` está en `top: 0` y `.stage-front` en `bottom: 0`.

Conclusión: `left` = x y `top` = y. La convención coincide con la UI. No se realizó
ninguna transformación.

## Evidencia del backend

- `FormationDraftController.PositionUpdateRequest` valida `x` e `y` con
  `@DecimalMin("0.0")` y `@DecimalMax("100.0")`, desde el commit `b939ef1`
  (2026-10-22). Esa es la vía de guardado de posiciones.
- `formation_positions.x_coordinate` e `y_coordinate` son `DECIMAL(10, 3) NOT NULL`.
- No hay migraciones que sembren posiciones.

## Decisión sobre movilidad

- `MobilityRegion` valida los cuatro límites en [0, 100], inclusive, con
  `minX < maxX` y `minY < maxY`.
- La tabla `formation_mobility_constraints` tiene CHECK de rango (migración `V41`).
- Los DTO de entrada del controlador usan `@DecimalMin`/`@DecimalMax`; una solicitud
  fuera de rango responde HTTP 400.

## Datos existentes

- La tabla de movilidad (`V39`, `V40`, `V41`) no está desplegada en producción en el
  momento de esta decisión; sus filas solo pueden existir en bases de desarrollo.
- No se ejecutaron conversiones de datos. Si una base de desarrollo tuviera filas
  fuera de rango, la migración `V41` falla y esas filas se corrigen manualmente.
- No pude verificar el contenido de bases de producción ni de desarrollo desde este
  entorno.

## Límites pendientes

- La semántica de una «fila accesible» (la región concreta de cada zona) es configurable
  por restricción y no está prefijada.
- Los límites de la UI `[5, 95]` (X) y `[8, 92]` (Y) son visuales y no forman parte del
  contrato. No se usan para validar.
