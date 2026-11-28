# CHOIR-153 — Escenarios físicos reutilizables y snapshots históricos

Historia padre de las etapas CHOIR-155 (dominio), CHOIR-156 (aplicación), CHOIR-157 (persistencia) y CHOIR-158 (API HTTP y contrato para CHOIR-25).

## 1. Modelo geométrico y convención de coordenadas

- **Unidad canónica:** centímetros. Las medidas se persisten como `DECIMAL(10,3)`; los valores con más de tres decimales se rechazan, no se redondean.
- **Origen:** esquina delantera izquierda, mirando desde el público hacia el escenario.
- **Eje X:** izquierda a derecha. **Eje Y:** frente hacia el fondo.
- **Contorno:** polígono simple de vértices ordenados (mínimo tres). El orden define el polígono.
- **Accesos:** segmentos sobre una arista del contorno.
- **Elementos:** superficies rectangulares 2D `(x, y, widthCm, depthCm)` con `elevationCm`.
- **Tipos de elemento:** `BENCH`, `PIANO`, `FURNITURE`, `OBSTACLE`, `STEP`, `PLATFORM`.
- **Elevación:** respecto del nivel de referencia. Obligatoria. Positiva en `STEP` y `PLATFORM`; cero en el resto.

**Reglas geométricas** (`StageGeometryValidator`, aritmética exacta con fracciones):
- El contorno es simple, con área no nula y sin autointersecciones.
- Vértices, accesos y elementos quedan dentro de las dimensiones y del contorno.
- Ningún elemento invade el exterior del contorno, ni siquiera cuando sus esquinas están dentro (caso de muesca cóncava).
- Las superficies 2D no se solapan con área positiva; los elementos adyacentes sí se permiten.

El sistema no impone simetrías, grillas ni distribuciones a los coreutas: modela el espacio físico.

## 2. Persistencia

Migración `V42__reusable_stage_templates_and_snapshots.sql`:

- `stage_templates`: plantilla reutilizable de un coro. Clave `(id, choir_id)` para aislar por coro.
- `stage_boundary_points`, `stage_accesses`, `stage_elements`: detalles de la plantilla. Clave `(stage_template_id, seq_no)`; FK compuesta `(stage_template_id, choir_id)`.
- `concert_stage_snapshots`: copia histórica asociada a una inclusión. Única por `(concert_id, repertoire_item_id)`. FKs compuestas a `concerts`, `repertoire_items` y `concert_repertoire`, con el mismo coro.
- `concert_stage_snapshot_boundary_points`, `concert_stage_snapshot_accesses`, `concert_stage_snapshot_elements`: copia completa de la geometría. Clave `(snapshot_id, seq_no)`.

Restricciones relevantes:
- `CHECK` de dimensiones positivas, coordenadas no negativas, extremos de acceso distintos y tipos válidos.
- `CHECK` de elevación: `> 0` en `STEP` y `PLATFORM`, `= 0` en el resto.
- `source_template_id` tiene FK con `RESTRICT`. Las plantillas se archivan y no se borran.

`seq_no` conserva el orden de los detalles. La lectura exige secuencias consecutivas desde cero.

## 3. Snapshots e inmutabilidad

- Asociar un escenario a una inclusión crea un `StageSnapshot`, que copia toda la geometría en ese momento.
- El snapshot se lee desde sus propias tablas. Nunca se reconstruye desde la plantilla.
- Modificar o archivar la plantilla no altera los snapshots existentes (verificado en pruebas de integración).
- Una inclusión admite un único snapshot: una segunda asociación responde 409.
- El adaptador de snapshots solo inserta y lee. No existe ningún método de actualización ni de borrado de snapshots, ni siquiera indirecto.

## 4. Autorización

- La propiedad la decide `ResourceAuthorizationService`, sin cambios.
- El adaptador de escena distingue dos casos cuando el servicio rechaza con 404:
  - el recurso **existe** y pertenece a otro director → **403**;
  - el recurso **no existe** en el coro indicado → **404**.
- La distinción se hace con consultas de existencia dentro del adaptador de persistencia. No se expone ninguna consulta al controller.
- Nota de seguridad: un director autenticado puede deducir si un `choirId` existe. Este comportamiento se aceptó como decisión de producto.

## 5. Endpoints

Base de plantillas: `/api/choirs/{choirId}/stages`

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/choirs/{choirId}/stages` | 200, lista de plantillas |
| GET | `/api/choirs/{choirId}/stages/{stageId}` | 200 |
| POST | `/api/choirs/{choirId}/stages` | 201 |
| PUT | `/api/choirs/{choirId}/stages/{stageId}` | 200 |
| DELETE | `/api/choirs/{choirId}/stages/{stageId}` | 204 (archivado lógico) |

Asociación con una inclusión: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/stage`

| Método | Resultado |
|---|---|
| POST | 201 con la geometría histórica creada |
| GET | 200 con la geometría histórica |

### Ejemplo: crear plantilla

Solicitud:

```json
{
  "name": "Escenario principal",
  "widthCm": 800,
  "depthCm": 600,
  "boundary": [
    { "x": 0, "y": 0 },
    { "x": 800, "y": 0 },
    { "x": 800, "y": 600 },
    { "x": 0, "y": 600 }
  ],
  "accesses": [
    { "name": "Puerta", "start": { "x": 300, "y": 0 }, "end": { "x": 400, "y": 0 } }
  ],
  "elements": [
    { "name": "Escalón", "type": "STEP", "x": 100, "y": 500, "widthCm": 200, "depthCm": 30, "elevationCm": 15.125 }
  ]
}
```

Respuesta 201: el mismo contenido con `id`, `choirId` y `archived: false`.

### Ejemplo: asociar escenario a una inclusión

`POST .../repertoire/9/stage` con cuerpo `{ "stageId": 10 }` → 201 con la geometría del snapshot (ver sección 7).

## 6. Códigos de error

| Código | Cuándo |
|---|---|
| 400 | Datos inválidos: geometría, medidas, elevación, cuerpo mal formado, IDs no positivos |
| 401 | Sin sesión autenticada |
| 403 | El recurso existe y pertenece a otro director |
| 404 | El coro, la plantilla o la inclusión no existen en el coro indicado |
| 409 | Plantilla archivada, asociación duplicada o conflicto de unicidad |

Las respuestas usan `ProblemDetail` con mensajes fijos. No exponen detalles de SQL ni de infraestructura.

## 7. Contrato de lectura para CHOIR-25

`GET /api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/stage` devuelve la geometría histórica de la inclusión:

```json
{
  "snapshotId": 42,
  "version": 1,
  "widthCm": 800.000,
  "depthCm": 600.000,
  "contour": [
    { "x": 0.000, "y": 0.000 },
    { "x": 800.000, "y": 0.000 },
    { "x": 800.000, "y": 600.000 },
    { "x": 0.000, "y": 600.000 }
  ],
  "accesses": [
    { "name": "Puerta", "start": { "x": 300.000, "y": 0.000 }, "end": { "x": 400.000, "y": 0.000 } }
  ],
  "elements": [
    { "name": "Banco", "type": "BENCH", "x": 100.000, "y": 100.000, "widthCm": 200.000, "depthCm": 40.000, "elevationCm": 0.000 },
    { "name": "Escalón", "type": "STEP", "x": 100.000, "y": 500.000, "widthCm": 200.000, "depthCm": 30.000, "elevationCm": 15.125 }
  ]
}
```

- `snapshotId` identifica la copia histórica; `version` es 1 porque los snapshots son inmutables.
- Las superficies ocupadas son las huellas `(x, y, widthCm, depthCm)` de los elementos.
- Las elevaciones son las de `elevationCm`, en centímetros.

CHOIR-25 debe consumir este contrato mediante el puerto `StageGeometryQueryUseCase.geometry(...)` o este endpoint. No debe acceder a tablas ni repositorios de escena.

## 8. Limitaciones conocidas

- La visibilidad de los cantantes y las reglas de transición no forman parte de este ticket; corresponden a CHOIR-25.
- La inmutabilidad de los snapshots se garantiza por el adaptador (solo inserción) y se verifica en pruebas. No hay triggers de base de datos, para mantener compatibilidad con H2 y PostgreSQL.
- El 403 para coros ajenos revela la existencia de un `choirId` a cualquier director autenticado (ver sección 4).
- La regla de tres decimales limita la precisión de las medidas a una milésima de centímetro.

## 9. Pruebas

- Dominio: `StageGeometryValidatorTests` (22 casos), `StageSnapshotTests`.
- Aplicación: `StageTemplateManagementServiceTests`, `StageSnapshotServiceTests`, `StageGeometryQueryServiceTests`, `StageGeometryTests`.
- Persistencia (H2 y PostgreSQL 16): `StageTemplatePersistenceAdapterTests`, `StageSnapshotPersistenceAdapterTests`, `StageSchemaIntegrityTests`, `StageAuthorizationAdapterTests`.
- Web: `StageWebMapperTests`, `StageControllerMappingTests`, `StageExceptionHandlerTests`.
- HTTP de extremo a extremo con sesiones reales: `StageHttpIntegrationTests` (401, 403, 404, 201, 409, 400 y geometría con elevaciones).
