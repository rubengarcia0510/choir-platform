# Choir Platform — modelo de dominio

## Entidades y relaciones

- Director administra uno o varios coros.
- Coro pertenece a un director y contiene coreutas, repertorio, conciertos, formaciones y transiciones.
- Coreuta pertenece a un coro.
- Concierto pertenece a un coro y tiene repertorio ordenado.
- Ítem de repertorio pertenece a un coro; `concert_repertoire` asocia obras a conciertos y conserva su orden.
- La captura de elenco conserva nombre, cuerda vocal e identidad/nombre de sección vocal tal como eran al capturar el elenco. La referencia al coreuta es opcional para no perder la captura si se elimina esa ficha. La identidad y el nombre de sección son datos históricos sin FK a la sección mutable; renombrar o reasignar un coreuta no modifica la captura.
- `concert_vocal_assignments` representa la asignación de coreutas a divisiones vocales por obra y concierto. Al publicar el concierto, `concert_vocal_assignment_snapshots` conserva una copia histórica por obra y coreuta: nombre, cuerda, sección y número de división. La referencia al coreuta puede quedar nula si se elimina la ficha; los datos descriptivos capturados se conservan.
- Formación identifica una formación lógica. Cada versión de formación tiene número propio y estado; sus posiciones pertenecen a esa versión, no a la formación abstracta. Las posiciones conservan nombre, cuerda, identidad/nombre de sección y, cuando se conoce, división vocal. Las capturas no dependen del nombre actual de la sección.
- Transición identifica una transición lógica. Cada versión de transición conserva sus versiones de formación de origen y destino.
- Ausencia pertenece a un coreuta y puede estar asociada a un concierto.

## Flujo de publicación y captura histórica

El endpoint `POST /api/choirs/{choirId}/concerts/{concertId}/publish` delega en `ConcertPublicationService`. El servicio comprueba que el concierto pertenezca al coro indicado, que el director autenticado sea su propietario y que el concierto esté en estado `PLANNED`.

Dentro de una transacción, la publicación cambia el estado a `PUBLISHED`, registra `published_at` y captura:
- El elenco activo del coro en `concert_cast_snapshots`.
- Las asignaciones vocales por cada ítem del repertorio en `concert_vocal_assignment_snapshots`, incluyendo el número de división correspondiente.

Si el concierto ya no está en estado `PLANNED`, la operación falla en lugar de volver a publicar y duplicar las capturas. La actualización condicional protege también frente a intentos concurrentes. El cambio de estado y las capturas deben confirmarse o revertirse juntos.

Los snapshots son registros históricos: no deben reconstruirse ni actualizarse desde los datos actuales del coreuta o de la sección. Para datos históricos anteriores a la introducción de una columna, se conserva `NULL` cuando no existe evidencia para determinar el valor; no se inventan divisiones.

## Decisiones de persistencia

1. `V1__initial_schema.sql` se conserva sin cambios. El dominio se agrega mediante migraciones posteriores.
2. Las claves primarias son numéricas generadas por la base de datos.
3. Las restricciones `CHECK`, `UNIQUE` y las claves foráneas protegen estados válidos, numeración de versiones y relaciones básicas.
4. Las capturas del elenco y de las posiciones conservan nombres, cuerdas e identidad/nombre de sección vocal aunque cambien las fichas actuales o se renombre una sección. Las columnas históricas de sección no tienen FK a la sección mutable.
5. La división es opcional en capturas antiguas y generales. La migración no inventa divisiones históricas; las asignaciones por obra se representan en las tablas de asignación vocal y su snapshot.
6. Una versión de formación publicada debe permanecer inmutable; los cambios posteriores se representan con una versión nueva, no sobrescribiendo la publicada. Esta regla debe validarse en los servicios de dominio y sus pruebas.
7. Las reglas que relacionan coro, concierto, coreuta, repertorio, asignación vocal y formación deben validarse en la capa de servicio además de las claves foráneas.
8. La información histórica de sección/división está disponible en las tablas de snapshots y posiciones para que los servicios de planificación puedan consultarla. La integración de esas divisiones como restricciones del algoritmo puede desarrollarse en una fase posterior.

## Alcance pendiente

La captura de asignaciones vocales por obra se implementa en CHOIR-61. La gestión y validación completa de las asignaciones por obra y de los planes vocales continúa en CHOIR-62 y CHOIR-63. La documentación de este flujo no implica que esas tareas bloqueantes estén terminadas.
