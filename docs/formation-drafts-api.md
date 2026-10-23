# API de borradores de formaciones

## Alcance

Esta API permite a un director consultar las formaciones de una inclusión
del repertorio en un concierto, crear una formación con su borrador inicial,
consultar ese borrador y reemplazar sus posiciones.

Todas las rutas requieren autenticación.

## Ruta base

`/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}`

Los identificadores de coro, concierto, inclusión del repertorio, formación
y versión deben ser positivos.

## Endpoints

### Listar formaciones

`GET {base}/formations`

Devuelve las formaciones asociadas a la inclusión del repertorio indicada.

- `200 OK`: consulta completada.
- `400 Bad Request`: identificadores o parámetros inválidos.
- `401 Unauthorized`: falta autenticación válida.
- `404 Not Found`: la inclusión solicitada no está disponible para el director.

### Crear una formación

`POST {base}/formations`

Crea una formación y su versión inicial en estado de borrador.

Content-Type: `application/json`

Ejemplo de solicitud:

```json
{
  "name": "Formación principal",
  "description": "Distribución para el concierto"
}
```

Reglas del cuerpo:

- "name" es obligatorio, no puede estar en blanco y admite hasta 160
  caracteres. Se eliminan los espacios al principio y al final.
- "description" es opcional y admite hasta 1000 caracteres.

Respuestas:

- "201 Created": formación y borrador inicial creados.
- "400 Bad Request": cuerpo o datos inválidos.
- "401 Unauthorized": falta autenticación válida.
- "404 Not Found": la inclusión del repertorio no está disponible
  para el director.

La respuesta incluye los identificadores de la formación y de la versión
inicial, además del nombre, la descripción, el número de versión y su estado.

### Consultar el borrador

`GET {base}/formations/{formationId}/draft`

Devuelve los datos de la formación, su versión de borrador y sus posiciones.

Cada posición expone el identificador del cantante, los datos de identidad
vocal guardados como snapshot, la etiqueta y las coordenadas.

Respuestas:

- "200 OK": borrador encontrado.
- "400 Bad Request": identificadores inválidos.
- "401 Unauthorized": falta autenticación válida.
- "404 Not Found": no existe un borrador accesible para esa inclusión
  del repertorio y formación.

### Reemplazar las posiciones del borrador

`PUT {base}/formations/{formationId}/versions/{versionId}/positions`

Reemplaza la lista completa de posiciones de la versión de borrador.
No es una operación de agregado incremental.

Content-Type: `application/json`

Ejemplo de solicitud:

```json
{
  "positions": [
    {
      "singerId": 123,
      "positionLabel": "Soprano 1",
      "x": 25.0,
      "y": 40.0
    }
  ]
}
```

Reglas de cada posición:

- "singerId" es obligatorio y debe ser positivo.
- "positionLabel" es opcional y admite hasta 80 caracteres.
- "x" e "y" son obligatorios y deben estar entre 0 y 100, inclusive.
- No se admiten elementos nulos ni identificadores de cantante repetidos.
- "positions" es obligatoria. Una lista vacía es válida y permite quitar
  todas las posiciones del borrador.

Respuestas:

- "200 OK": reemplazo completado.
- "400 Bad Request": solicitud inválida o coordenadas fuera de rango.
- "401 Unauthorized": falta autenticación válida.
- "404 Not Found": la formación o versión no pertenece a la inclusión
  del repertorio solicitada o no es accesible para el director.
- "409 Conflict": la versión no se encuentra en un estado que permita
  modificar sus posiciones.

Autorización y aislamiento entre coros

Las operaciones se realizan en el contexto del coro, concierto e inclusión
del repertorio indicados en la ruta. La autorización se verifica antes de
acceder al catálogo de formaciones.

Un director no puede consultar ni modificar formaciones pertenecientes a
otro coro. La API puede responder "404 Not Found" para recursos ajenos,
evitando revelar su existencia.

Integridad del reemplazo

El reemplazo debe conservar la lista anterior si la solicitud es rechazada
por validación, autorización, estado de versión o por un error durante
la persistencia.

La operación reemplaza la lista completa: la implementación debe persistir
el cambio de forma atómica para evitar que un error deje las posiciones
parcialmente actualizadas.

Seguridad

No se deben incluir credenciales, cookies de sesión ni tokens CSRF en
ejemplos compartidos o documentación versionada. Las solicitudes autenticadas
de modificación deben cumplir la protección CSRF configurada por la aplicación.

### Duplicar una formación

`POST {base}/formations/{formationId}/duplicate`

Crea una formación nueva a partir de una formación existente y copia las
posiciones de una de sus versiones publicadas. La formación nueva recibe
una versión inicial en estado `DRAFT`, con número de versión `1`.
La formación original no se modifica.

Content-Type: `application/json`

Ejemplo de solicitud:

```json
{
  "name": "Formación alternativa",
  "description": "Distribución alternativa para el concierto",
  "sourceVersionId": 123
}
```

Campos del cuerpo:

- "name" es obligatorio, no puede estar en blanco y admite hasta 160
  caracteres. Se eliminan los espacios al principio y al final.
- "description" es opcional y admite hasta 1000 caracteres.
- "sourceVersionId" es opcional y, si se informa, debe ser un identificador
  positivo de una versión publicada perteneciente a la formación de origen.

Selección de la versión de origen:

- Si se informa "sourceVersionId", se utiliza esa versión publicada.
- Si se omite, se selecciona la versión publicada con el mayor número
  de versión de la formación de origen.
- Si no existe una versión publicada elegible, la duplicación se rechaza.
- Las posiciones copiadas conservan sus datos y snapshots, incluidos los
  datos de identidad vocal, etiquetas y coordenadas.

Respuestas:

- "201 Created": formación duplicada y borrador inicial creados. La respuesta
  incluye "id", "name", "description", "draftVersionId", "versionNumber"
  y "status".
- "400 Bad Request": cuerpo inválido, identificadores no positivos o
  formación/versión de origen no válida para la duplicación.
- "401 Unauthorized": falta autenticación válida.
- "404 Not Found": la inclusión del repertorio no está disponible para
  el director.
- "409 Conflict": la formación de origen no tiene una versión publicada
  elegible cuando no se proporciona "sourceVersionId".

La operación requiere autenticación y protección CSRF para la solicitud
de modificación, conforme a la configuración de seguridad de la aplicación.
La autorización de la inclusión del repertorio se verifica antes de ejecutar
la duplicación.
